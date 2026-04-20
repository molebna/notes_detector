package com.example.notesdetector.presentation.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.notesdetector.data.NotesFile

class HomeViewModel : ViewModel() {

    val notes = listOf(
        NotesFile("Guitar melody", "12.03.2026"),
        NotesFile("Piano theme", "10.03.2026"),
        NotesFile("Test recording", "08.03.2026")
    )
}