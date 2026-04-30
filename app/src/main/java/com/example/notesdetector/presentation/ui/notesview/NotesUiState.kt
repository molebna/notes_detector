package com.example.notesdetector.presentation.ui.notesview

import com.example.notesdetector.data.NoteEvent
import com.example.notesdetector.data.TabNote

data class NotesUiState(
    val isLoading: Boolean = true,
    val fileName: String = "Unknown file",
    val audioUri: String? = null,
    val tabNotes: List<TabNote> = emptyList(),
    val noteEvents: List<NoteEvent> = emptyList(),
    val errorMessage: String? = null
)
