package com.example.notesdetector.domain.transcription

import android.icu.text.SimpleDateFormat
import com.example.notesdetector.data.NotesFile
import com.example.notesdetector.data.NoteEntity
import java.util.Date
import java.util.Locale

object NotesMapper {

    fun toNotesFile(entity: NoteEntity): NotesFile {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        val date = Date(entity.createdAt)

        val formattedDate = formatter.format(date)

        return NotesFile(
            id = entity.id,
            title = entity.audioName ?: entity.audioUri,
            date = formattedDate
        )
    }
}
