package com.example.notesdetector.presentation.ui.notesview

import android.app.Application
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notesdetector.data.local.TabNotesRepository
import com.example.notesdetector.data.utils.FileUtils.getFileNameFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TabNotesRepository.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        loadLatestTabNotes()
    }

    fun loadLatestTabNotes() {
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
                                fileName = getFileNameFromUri(context = getApplication(), filePath = entity.audioUri)
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
}
