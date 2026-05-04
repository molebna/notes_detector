package com.example.notesdetector.presentation.ui.notesview

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notesdetector.R
import com.example.notesdetector.presentation.ui.views.SheetMusicView
import com.example.notesdetector.presentation.ui.views.TablatureView
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class NotesViewFragment : Fragment(R.layout.fragment_notes_view) {

    private val viewModel: NotesViewModel by viewModels()

    private var isTabMode = true

    private lateinit var tabView: TablatureView
    private lateinit var sheetMusicView: SheetMusicView
    private lateinit var fileNameText: android.widget.TextView
    private var transcribedPlaybackUri: String? = null
    private var originalAudioUri: String? = null
    private var useTranscribedPlayback = true
    private var mediaPlayer: MediaPlayer? = null
    private var playbackMidiFile: File? = null
    private var pendingMidiName: String = "transcription.mid"

    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var exportMidiButton: Button
    private lateinit var togglePlaybackSourceButton: Button

    private lateinit var toggleNotesView: Button

    private val handler = Handler(Looper.getMainLooper())

    private val exportMidiLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/midi")) { uri ->
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.midi_export_cancelled, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        runCatching {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                viewModel.exportToMidi(output)
            } ?: error("Could not open destination")
        }.onSuccess {
            Toast.makeText(
                requireContext(),
                getString(R.string.midi_export_success, uri.toString()),
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(
                requireContext(),
                getString(R.string.midi_export_failed, it.message ?: "Unknown error"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileNameText = view.findViewById(R.id.fileNameText)
        tabView = view.findViewById(R.id.tabView)
        sheetMusicView = view.findViewById(R.id.sheetMusicView)

        seekBar = view.findViewById(R.id.seekBar)
        playButton = view.findViewById(R.id.playButton)
        pauseButton = view.findViewById(R.id.pauseButton)
        stopButton = view.findViewById(R.id.stopButton)
        toggleNotesView = view.findViewById(R.id.toggleNotesView)
        exportMidiButton = view.findViewById(R.id.exportMidiButton)
        togglePlaybackSourceButton = view.findViewById(R.id.togglePlaybackSourceButton)

        playButton.setOnClickListener { playAudio() }
        pauseButton.setOnClickListener { pauseAudio() }
        stopButton.setOnClickListener { stopAudio() }
        toggleNotesView.setOnClickListener { toggleNotesView() }
        exportMidiButton.setOnClickListener { exportMidi() }
        togglePlaybackSourceButton.setOnClickListener { togglePlaybackSource() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    fileNameText.text = state.errorMessage ?: state.fileName
                    tabView.setNotes(state.tabNotes)
                    sheetMusicView.setNotes(state.noteEvents)
                    originalAudioUri = state.audioUri
                    preparePlaybackSource(state)
                    updatePlaybackModeUi()
                    exportMidiButton.isEnabled = state.noteEvents.isNotEmpty() && state.errorMessage == null
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        releaseMediaPlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun releasePlayer() {
        releaseMediaPlayer()
        playbackMidiFile?.delete()
        playbackMidiFile = null
        transcribedPlaybackUri = null
    }

    private fun playAudio() {
        val uriValue = activePlaybackUri() ?: run {
            Toast.makeText(requireContext(), getString(R.string.no_playback_source), Toast.LENGTH_SHORT).show()
            return
        }

        if (mediaPlayer == null) {
            val mp = MediaPlayer.create(requireContext(), Uri.parse(uriValue))

            if (mp == null) {
                Toast.makeText(requireContext(), "Cannot play audio", Toast.LENGTH_SHORT).show()
                return
            }

            mediaPlayer = mp.apply {
                setOnCompletionListener {
                    stopAudio()
                }
            }

            seekBar.max = mediaPlayer!!.duration
        }

        mediaPlayer?.start()
        updateSeekBar()
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
    }

    private fun stopAudio() {
        mediaPlayer?.let {
            it.pause()
            it.seekTo(0)
        }
        seekBar.progress = 0
    }

    private fun updateSeekBar() {
        handler.post(object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        seekBar.progress = it.currentPosition
                        handler.postDelayed(this, 100)
                    }
                }
            }
        })
    }


    private fun preparePlaybackSource(state: NotesUiState) {
        playbackMidiFile?.delete()
        playbackMidiFile = null
        transcribedPlaybackUri = null

        if (state.noteEvents.isEmpty()) {
            return
        }

        try {
            val midiFile = viewModel.preparePlaybackMidiFile(requireContext().cacheDir)
            playbackMidiFile = midiFile
            transcribedPlaybackUri = Uri.fromFile(midiFile).toString()
        } catch (exception: IOException) {
            Toast.makeText(
                requireContext(),
                getString(R.string.midi_export_failed, exception.message ?: "Unknown error"),
                Toast.LENGTH_SHORT
            ).show()
        } catch (exception: IllegalStateException) {
            // No notes available for playback.
        }
    }

    private fun activePlaybackUri(): String? {
        return if (useTranscribedPlayback) {
            transcribedPlaybackUri ?: originalAudioUri
        } else {
            originalAudioUri ?: transcribedPlaybackUri
        }
    }

    private fun togglePlaybackSource() {
        useTranscribedPlayback = !useTranscribedPlayback
        stopAudio()
        releaseMediaPlayer()
        updatePlaybackModeUi()
    }

    private fun updatePlaybackModeUi() {
        useTranscribedPlayback = if (useTranscribedPlayback) {
            transcribedPlaybackUri != null || originalAudioUri == null
        } else {
            originalAudioUri == null && transcribedPlaybackUri != null
        }
        togglePlaybackSourceButton.text = if (useTranscribedPlayback) {
            getString(R.string.playback_mode_transcribed)
        } else {
            getString(R.string.playback_mode_original)
        }
        togglePlaybackSourceButton.isEnabled = transcribedPlaybackUri != null && originalAudioUri != null
    }

    private fun toggleNotesView() {

        isTabMode = !isTabMode

        if (isTabMode) {
            tabView.visibility = View.VISIBLE
            sheetMusicView.visibility = View.GONE
            toggleNotesView.text = getString(R.string.show_sheet_music)
        } else {
            tabView.visibility = View.GONE
            sheetMusicView.visibility = View.VISIBLE
            toggleNotesView.text = getString(R.string.show_tabs)
        }
    }

    private fun exportMidi() {
        val state = viewModel.uiState.value
        if (state.noteEvents.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_notes_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        val safeName = state.fileName.substringBeforeLast('.').ifBlank { "transcription" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        pendingMidiName = "${safeName}.mid"
        exportMidiLauncher.launch(pendingMidiName)
    }
}
