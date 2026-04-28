package com.example.notesdetector.presentation.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notesdetector.data.NotesFile
import com.example.notesdetector.data.local.TabNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TabNotesRepository.getInstance(application)

    private val _notes = MutableStateFlow<List<NotesFile>>(emptyList())
    val notes: StateFlow<List<NotesFile>> = _notes

    init {
        refreshNotes()
    }

    fun refreshNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            _notes.value = repository.getAllNotesFiles()
        }
    }
}