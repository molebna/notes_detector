package com.example.notesdetector.data

data class TabNoteEntity(
    val id: Long,
    val audioUri: String,
    val audioName: String?,
    val tabNotes: List<TabNote>,
    val createdAt: Long
)
