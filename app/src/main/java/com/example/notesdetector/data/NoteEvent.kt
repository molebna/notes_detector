package com.example.notesdetector.data

data class NoteEvent (
    val startSec: Float,
    val endSec: Float,
    val midi: Int,
    val name: String,
    val peak: Float
)
