package com.example.notesdetector.presentation.ui.transcription

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notesdetector.data.local.TabNotesRepository
import com.example.notesdetector.data.utils.FileUtils.getFileNameFromUri
import com.example.notesdetector.domain.transcription.TabMapper
import com.example.notesdetector.domain.transcription.TfliteAudioTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val transcriber = TfliteAudioTranscriber(application.applicationContext)
    private val repository = TabNotesRepository.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(TranscriptionUiState())
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()
    private var progressJob: Job? = null

    fun setAudioUri(uri: String) {
        if (_uiState.value.selectedAudioUri == uri) return
        _uiState.update { it.copy(selectedAudioUri = uri, errorMessage = null, navigateToResult = false) }
    }

    fun updateTimeSignature(value: String) {
        _uiState.update { it.copy(timeSignature = value, errorMessage = null) }
    }

    fun transcribeSelectedAudio() {
        val state = _uiState.value
        val uri = state.selectedAudioUri ?: return
        val normalizedTimeSignature = state.timeSignature.trim()
        val isValidTimeSignature = Regex("^\\d+\\/\\d+$").matches(normalizedTimeSignature)
        if (!isValidTimeSignature) {
            _uiState.update { it.copy(errorMessage = "Enter a valid time signature, e.g. 4/4") }
            return
        }
        transcribe(Uri.parse(uri), normalizedTimeSignature)
    }

    private fun transcribe(uri: Uri, timeSignature: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, progressPercent = 0, errorMessage = null, navigateToResult = false) }
            startProgressUpdates()
            val result = runCatching { transcriber.transcribe(uri) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { noteEvents ->
                        val tabNotes = TabMapper.map(noteEvents)
                        val audioName = getFileNameFromUri(getApplication(), uri.toString())
                        repository.saveTabNotes(
                            audioUri = uri.toString(),
                            audioName = audioName,
                            tabNotes = tabNotes,
                            noteEvents = noteEvents,
                            timeSignature = timeSignature
                        )
                        progressJob?.cancel()
                        state.copy(
                            isLoading = false,
                            progressPercent = 100,
                            notes = noteEvents,
                            tabNotes = tabNotes,
                            navigateToResult = true
                        )
                    },
                    onFailure = {
                        progressJob?.cancel()
                        state.copy(
                            isLoading = false,
                            progressPercent = 0,
                            errorMessage = it.message ?: "Transcription failed"
                        )
                    }
                )
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive && _uiState.value.isLoading) {
                delay(250L)
                _uiState.update { state ->
                    val next = (state.progressPercent + 3).coerceAtMost(95)
                    state.copy(progressPercent = next)
                }
            }
        }
    }

    fun onNavigated() {
        _uiState.update {
            it.copy(navigateToResult = false)
        }
    }
}
