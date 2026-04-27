package com.example.notesdetector.data.local

import android.content.Context
import com.example.notesdetector.data.NotesFile
import com.example.notesdetector.data.TabNote
import com.example.notesdetector.data.TabNoteEntity
import com.example.notesdetector.domain.transcription.NotesMapper

class TabNotesRepository private constructor(context: Context) {

    private val dbHelper = TabNotesDatabaseHelper(context.applicationContext)

    fun saveTabNotes(audioUri: String, audioName: String?, tabNotes: List<TabNote>): Long {
        return dbHelper.saveTabNotes(audioUri, audioName, tabNotes)
    }

    fun getLatestTabNotes(): TabNoteEntity? {
        return dbHelper.getLatestTabNotes()
    }

    fun getAllTabNotes(): List<TabNoteEntity> {
        return dbHelper.getAllTabNotes()
    }

    fun getAllNotesFiles(): List<NotesFile> {
        return getAllTabNotes().map { NotesMapper.toNotesFile(it) }
    }

    companion object {
        @Volatile
        private var instance: TabNotesRepository? = null

        fun getInstance(context: Context): TabNotesRepository {
            return instance ?: synchronized(this) {
                instance ?: TabNotesRepository(context).also { instance = it }
            }
        }
    }
}
