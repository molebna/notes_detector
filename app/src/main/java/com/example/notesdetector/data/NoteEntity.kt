package com.example.notesdetector.data

data class NoteEntity(
    val id: Long,
    val audioUri: String,
    val audioName: String?,
    val timeSignature: String,
    val tabNotes: List<TabNote>,
    val noteEvents: List<NoteEvent>,
    val createdAt: Long
)
