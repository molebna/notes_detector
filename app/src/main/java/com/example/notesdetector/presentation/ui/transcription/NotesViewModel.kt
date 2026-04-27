package com.example.notesdetector.presentation.ui.transcription

import android.app.Application
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notesdetector.data.local.TabNotesRepository
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
                                fileName = getFileNameFromUri(entity.audioUri)
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

    private fun getFileNameFromUri(filePath: String): String {
        val uri = filePath.toUri()
        var name = "Unknown file"

        val cursor = getApplication<Application>().contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }

        return name.substringBeforeLast(".")
    }
}
