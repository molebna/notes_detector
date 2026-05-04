package com.example.notesdetector.presentation.ui.notesview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.notesdetector.data.NoteEvent
import com.example.notesdetector.data.local.TabNotesRepository
import com.example.notesdetector.data.utils.FileUtils.getFileNameFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID

class NotesViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = TabNotesRepository.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        val tabNoteId = savedStateHandle.get<Long>("tabNoteId")
        if (tabNoteId == null || tabNoteId == -1L) {
            loadLatestNotes()
        } else {
            loadNotes(tabNoteId)
        }
    }

    fun loadLatestNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = runCatching { repository.getLatestTabNotes() }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { entity ->
                        if (entity == null) {
                            state.copy(
                                isLoading = false,
                                errorMessage = "No saved transcription found."
                            )
                        } else {
                            state.copy(
                                isLoading = false,
                                tabNotes = entity.tabNotes,
                                noteEvents = entity.noteEvents,
                                audioUri = entity.audioUri,
                                timeSignature = entity.timeSignature,
                                fileName = entity.audioName
                                    ?: getFileNameFromUri(context = getApplication(), filePath = entity.audioUri)
                            )
                        }
                    },
                    onFailure = {
                        state.copy(
                            isLoading = false,
                            errorMessage = it.message ?: "Failed to load saved tab notes."
                        )
                    }
                )
            }
        }
    }

    fun loadNotes(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = runCatching { repository.getTabNotesById(id) }

            _uiState.update { state ->
                result.fold(
                    onSuccess = { entity ->
                        if (entity == null) {
                            state.copy(
                                isLoading = false,
                                errorMessage = "File not found"
                            )
                        } else {
                            state.copy(
                                isLoading = false,
                                tabNotes = entity.tabNotes,
                                noteEvents = entity.noteEvents,
                                audioUri = entity.audioUri,
                                timeSignature = entity.timeSignature,
                                fileName = entity.audioName
                                    ?: getFileNameFromUri(
                                        context = getApplication(),
                                        filePath = entity.audioUri
                                    )
                            )
                        }
                    },
                    onFailure = {
                        state.copy(
                            isLoading = false,
                            errorMessage = it.message ?: "Load failed"
                        )
                    }
                )
            }
        }
    }


    @Throws(IOException::class)
    fun preparePlaybackMidiFile(cacheDir: File): File {
        val events = uiState.value.noteEvents
        if (events.isEmpty()) {
            throw IllegalStateException("No notes available")
        }

        val file = File(cacheDir, "playback-${UUID.randomUUID()}.mid")
        FileOutputStream(file).use { output ->
            output.write(buildMidiFile(events))
            output.flush()
        }
        return file
    }

    @Throws(IOException::class)
    fun exportToMidi(outputStream: OutputStream) {
        val events = uiState.value.noteEvents
        if (events.isEmpty()) {
            throw IllegalStateException("No notes available")
        }

        outputStream.use {
            it.write(buildMidiFile(events))
            it.flush()
        }
    }

    @Throws(IOException::class)
    fun exportToMidi(file: File) {
        val events = uiState.value.noteEvents
        if (events.isEmpty()) {
            throw IllegalStateException("No notes available")
        }

        FileOutputStream(file).use { output ->
            exportToMidi(output)
        }
    }

    private fun buildMidiFile(notes: List<NoteEvent>): ByteArray {
        val ticksPerQuarter = 480
        val tempoBpm = 10
        val microPerQuarter = 60_000_000 / tempoBpm
        val ticksPerSecond = ticksPerQuarter * tempoBpm / 60f

        data class MidiEvent(val tick: Int, val payload: ByteArray)

        val midiEvents = mutableListOf<MidiEvent>()
        midiEvents += MidiEvent(
            0,
            byteArrayOf(
                0xFF.toByte(),
                0x51,
                0x03,
                ((microPerQuarter shr 16) and 0xFF).toByte(),
                ((microPerQuarter shr 8) and 0xFF).toByte(),
                (microPerQuarter and 0xFF).toByte()
            )
        )

        notes.forEach { note ->
            val startTick = (note.startSec * ticksPerSecond).toInt().coerceAtLeast(0)
            val endTick = (note.endSec * ticksPerSecond).toInt().coerceAtLeast(startTick + 1)
            val velocity = (64 + note.peak * 63).toInt().coerceIn(1, 127).toByte()
            val midiNumber = note.midi.coerceIn(0, 127).toByte()

            midiEvents += MidiEvent(startTick, byteArrayOf(0x90.toByte(), midiNumber, velocity))
            midiEvents += MidiEvent(endTick, byteArrayOf(0x80.toByte(), midiNumber, 0x00))
        }

        val sortedEvents = midiEvents.sortedWith(compareBy<MidiEvent> { it.tick }.thenBy { it.payload[0].toInt() })

        val trackData = ArrayList<Byte>()
        var previousTick = 0
        sortedEvents.forEach { event ->
            val delta = event.tick - previousTick
            previousTick = event.tick
            encodeVariableLength(delta).forEach { trackData.add(it) }
            event.payload.forEach { trackData.add(it) }
        }

        trackData.addAll(listOf(0x00, 0xFF.toByte(), 0x2F, 0x00))

        val trackLength = trackData.size
        val header = ByteBuffer.allocate(14)
            .put("MThd".toByteArray())
            .putInt(6)
            .putShort(0)
            .putShort(1)
            .putShort(ticksPerQuarter.toShort())
            .array()

        val trackHeader = ByteBuffer.allocate(8)
            .put("MTrk".toByteArray())
            .putInt(trackLength)
            .array()

        return header + trackHeader + trackData.toByteArray()
    }

    private fun encodeVariableLength(value: Int): ByteArray {
        var buffer = value and 0x7F
        var currentValue = value
        val bytes = mutableListOf<Byte>()

        while (currentValue shr 7 > 0) {
            currentValue = currentValue shr 7
            buffer = buffer or ((currentValue and 0x7F) shl 8)
        }

        while (true) {
            bytes.add((buffer and 0xFF).toByte())
            if (buffer and 0x80 != 0) {
                buffer = buffer shr 8
            } else {
                break
            }
        }

        return bytes.toByteArray()
    }
}
