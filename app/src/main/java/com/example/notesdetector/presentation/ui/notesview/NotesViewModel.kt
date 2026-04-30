package com.example.notesdetector.presentation.ui.notesview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.notesdetector.data.local.TabNotesRepository
import com.example.notesdetector.data.utils.FileUtils.getFileNameFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
}
