package com.example.notesdetector.presentation.ui.transcription

import com.example.notesdetector.data.NoteEvent
import com.example.notesdetector.data.TabNote

data class TranscriptionUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedAudioUri: String? = null,
    val navigateToResult: Boolean = false,
    val notes: List<NoteEvent> = emptyList(),
    val tabNotes: List<TabNote> = emptyList(),
)
