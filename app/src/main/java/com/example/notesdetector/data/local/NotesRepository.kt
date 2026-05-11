package com.example.notesdetector.data.local

import android.content.Context
import com.example.notesdetector.data.NoteEvent
import com.example.notesdetector.data.NotesFile
import com.example.notesdetector.data.TabNote
import com.example.notesdetector.data.NoteEntity
import com.example.notesdetector.domain.transcription.NotesMapper

class NotesRepository private constructor(context: Context) {

    private val dbHelper = NotesDatabaseHelper(context.applicationContext)

    fun saveTabNotes(
        audioUri: String,
        audioName: String?,
        tabNotes: List<TabNote>,
        noteEvents: List<NoteEvent>,
        timeSignature: String
    ): Long {
        return dbHelper.saveTabNotes(audioUri, audioName, tabNotes, noteEvents, timeSignature)
    }

    fun getLatestTabNotes(): NoteEntity? {
        return dbHelper.getLatestTabNotes()
    }

    fun getTabNotesById(id: Long): NoteEntity? {
        return dbHelper.getTabNotesById(id)
    }

    fun getAllTabNotes(): List<NoteEntity> {
        return dbHelper.getAllTabNotes()
    }

    fun getAllNotesFiles(): List<NotesFile> {
        return getAllTabNotes().map { NotesMapper.toNotesFile(it) }
    }

    fun renameNoteFile(id: Long, newName: String): Boolean {
        return dbHelper.renameTabNotes(id, newName)
    }

    fun deleteNoteFile(id: Long): Boolean {
        return dbHelper.deleteTabNotes(id)
    }

    companion object {
        @Volatile
        private var instance: NotesRepository? = null

        fun getInstance(context: Context): NotesRepository {
            return instance ?: synchronized(this) {
                instance ?: NotesRepository(context).also { instance = it }
            }
        }
    }
}
