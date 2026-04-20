package com.example.notesdetector.presentation.ui.transcription

data class TranscriptionUiState(
    val isLoading: Boolean = false,
    val transcription: String = "",
    val errorMessage: String? = null,
    val selectedAudioUri: String? = null
)
