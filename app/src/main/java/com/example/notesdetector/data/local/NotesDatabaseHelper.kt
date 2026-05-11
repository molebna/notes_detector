package com.example.notesdetector.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.notesdetector.data.NoteEvent
import com.example.notesdetector.data.TabNote
import com.example.notesdetector.data.NoteEntity
import org.json.JSONArray
import org.json.JSONObject

class NotesDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRANSCRIPTIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_AUDIO_URI TEXT NOT NULL,
                $COLUMN_AUDIO_NAME TEXT,
                $COLUMN_TAB_NOTES TEXT NOT NULL,
                $COLUMN_NOTE_EVENTS TEXT,
                $COLUMN_TIME_SIGNATURE TEXT NOT NULL DEFAULT "4/4",
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                ALTER TABLE $TABLE_TRANSCRIPTIONS
                ADD COLUMN $COLUMN_AUDIO_NAME TEXT
                """.trimIndent()
            )
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                ALTER TABLE $TABLE_TRANSCRIPTIONS
                ADD COLUMN $COLUMN_NOTE_EVENTS TEXT
                """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            db.execSQL("""
                ALTER TABLE $TABLE_TRANSCRIPTIONS
                ADD COLUMN $COLUMN_TIME_SIGNATURE TEXT NOT NULL DEFAULT '4/4'
                """.trimIndent())
        }
    }

    fun saveTabNotes(
        audioUri: String,
        audioName: String?,
        tabNotes: List<TabNote>,
        noteEvents: List<NoteEvent>,
        timeSignature: String
    ): Long {
        val values = ContentValues().apply {
            put(COLUMN_AUDIO_URI, audioUri)
            put(COLUMN_AUDIO_NAME, audioName)
            put(COLUMN_TAB_NOTES, tabNotes.toJson())
            put(COLUMN_NOTE_EVENTS, noteEvents.toJson())
            put(COLUMN_TIME_SIGNATURE, timeSignature)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE_TRANSCRIPTIONS, null, values)
    }

    fun getLatestTabNotes(): NoteEntity? {
        val cursor = readableDatabase.query(
            TABLE_TRANSCRIPTIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC",
            "1"
        )

        cursor.use {
            if (!it.moveToFirst()) return null
            return NoteEntity(
                id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                audioUri = it.getString(it.getColumnIndexOrThrow(COLUMN_AUDIO_URI)),
                audioName = it.getStringOrNull(COLUMN_AUDIO_NAME),
                timeSignature = it.getStringOrNull(COLUMN_TIME_SIGNATURE) ?: "4/4",
                tabNotes = it.getString(it.getColumnIndexOrThrow(COLUMN_TAB_NOTES)).toTabNotes(),
                noteEvents = it.getStringOrNull(COLUMN_NOTE_EVENTS)?.toNoteEvents().orEmpty(),
                createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
            )
        }
    }

    fun getAllTabNotes(): List<NoteEntity> {
        val result = mutableListOf<NoteEntity>()

        val cursor = readableDatabase.query(
            TABLE_TRANSCRIPTIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC"
        )

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val entity = NoteEntity(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        audioUri = it.getString(it.getColumnIndexOrThrow(COLUMN_AUDIO_URI)),
                        audioName = it.getStringOrNull(COLUMN_AUDIO_NAME),
                        timeSignature = it.getStringOrNull(COLUMN_TIME_SIGNATURE) ?: "4/4",
                        tabNotes = it.getString(it.getColumnIndexOrThrow(COLUMN_TAB_NOTES)).toTabNotes(),
                        noteEvents = it.getStringOrNull(COLUMN_NOTE_EVENTS)?.toNoteEvents().orEmpty(),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
                    )

                    result.add(entity)

                } while (it.moveToNext())
            }
        }

        return result
    }

    fun getTabNotesById(id: Long): NoteEntity? {
        val cursor = readableDatabase.query(
            TABLE_TRANSCRIPTIONS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        cursor.use {
            if (!it.moveToFirst()) return null

            return NoteEntity(
                id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                audioUri = it.getString(it.getColumnIndexOrThrow(COLUMN_AUDIO_URI)),
                audioName = it.getStringOrNull(COLUMN_AUDIO_NAME),
                timeSignature = it.getStringOrNull(COLUMN_TIME_SIGNATURE) ?: "4/4",
                tabNotes = it.getString(it.getColumnIndexOrThrow(COLUMN_TAB_NOTES)).toTabNotes(),
                noteEvents = it.getStringOrNull(COLUMN_NOTE_EVENTS)?.toNoteEvents().orEmpty(),
                createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
            )
        }
    }

    fun renameTabNotes(id: Long, newName: String): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_AUDIO_NAME, newName)
        }

        val rowsUpdated = writableDatabase.update(
            TABLE_TRANSCRIPTIONS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )

        return rowsUpdated > 0
    }

    fun deleteTabNotes(id: Long): Boolean {
        val rowsDeleted = writableDatabase.delete(
            TABLE_TRANSCRIPTIONS,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )

        return rowsDeleted > 0
    }

    @JvmName("tabNotesToJson")
    private fun List<TabNote>.toJson(): String {
        val jsonArray = JSONArray()
        forEach { note ->
            jsonArray.put(
                JSONObject().apply {
                    put("stringIndex", note.stringIndex)
                    put("fret", note.fret)
                    put("time", note.time.toDouble())
                }
            )
        }
        return jsonArray.toString()
    }

    private fun String.toTabNotes(): List<TabNote> {
        val array = JSONArray(this)
        return buildList {
            for (index in 0 until array.length()) {
                val note = array.getJSONObject(index)
                add(
                    TabNote(
                        stringIndex = note.getInt("stringIndex"),
                        fret = note.getInt("fret"),
                        time = note.getDouble("time").toFloat()
                    )
                )
            }
        }
    }

    @JvmName("noteEventsToJson")
    private fun List<NoteEvent>.toJson(): String {
        val jsonArray = JSONArray()
        forEach { note ->
            jsonArray.put(
                JSONObject().apply {
                    put("startSec", note.startSec.toDouble())
                    put("endSec", note.endSec.toDouble())
                    put("midi", note.midi)
                    put("name", note.name)
                    put("peak", note.peak.toDouble())
                }
            )
        }
        return jsonArray.toString()
    }

    private fun String.toNoteEvents(): List<NoteEvent> {
        val array = JSONArray(this)
        return buildList {
            for (index in 0 until array.length()) {
                val note = array.getJSONObject(index)
                add(
                    NoteEvent(
                        startSec = note.getDouble("startSec").toFloat(),
                        endSec = note.getDouble("endSec").toFloat(),
                        midi = note.getInt("midi"),
                        name = note.getString("name"),
                        peak = note.getDouble("peak").toFloat()
                    )
                )
            }
        }
    }

    private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
        val columnIndex = getColumnIndex(columnName)
        if (columnIndex == -1 || isNull(columnIndex)) return null
        return getString(columnIndex)
    }

    companion object {
        private const val DATABASE_NAME = "notes.db"
        private const val DATABASE_VERSION = 4

        private const val TABLE_TRANSCRIPTIONS = "transcriptions"
        private const val COLUMN_ID = "id"
        private const val COLUMN_AUDIO_URI = "audio_uri"
        private const val COLUMN_AUDIO_NAME = "audio_name"
        private const val COLUMN_TIME_SIGNATURE = "time_signature"
        private const val COLUMN_TAB_NOTES = "tab_notes"
        private const val COLUMN_NOTE_EVENTS = "note_events"
        private const val COLUMN_CREATED_AT = "created_at"
    }
}
