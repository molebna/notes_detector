package com.example.notesdetector.data.local

import android.content.Context
import com.example.notesdetector.data.TabNote
import com.example.notesdetector.data.TabNoteEntity

class TabNotesRepository private constructor(context: Context) {

    private val dbHelper = TabNotesDatabaseHelper(context.applicationContext)

    fun saveTabNotes(audioUri: String, tabNotes: List<TabNote>): Long {
        return dbHelper.saveTabNotes(audioUri, tabNotes)
    }

    fun getLatestTabNotes(): TabNoteEntity? {
        return dbHelper.getLatestTabNotes()
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
