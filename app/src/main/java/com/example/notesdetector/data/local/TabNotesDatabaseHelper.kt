package com.example.notesdetector.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.notesdetector.data.TabNote
import com.example.notesdetector.data.TabNoteEntity
import org.json.JSONArray
import org.json.JSONObject

class TabNotesDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TAB_TRANSCRIPTIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_AUDIO_URI TEXT NOT NULL,
                $COLUMN_AUDIO_NAME TEXT,
                $COLUMN_TAB_NOTES TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                ALTER TABLE $TABLE_TAB_TRANSCRIPTIONS
                ADD COLUMN $COLUMN_AUDIO_NAME TEXT
                """.trimIndent()
            )
        }
    }

    fun saveTabNotes(audioUri: String, audioName: String?, tabNotes: List<TabNote>): Long {
        val values = ContentValues().apply {
            put(COLUMN_AUDIO_URI, audioUri)
            put(COLUMN_AUDIO_NAME, audioName)
            put(COLUMN_TAB_NOTES, tabNotes.toJson())
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE_TAB_TRANSCRIPTIONS, null, values)
    }

    fun getLatestTabNotes(): TabNoteEntity? {
        val cursor = readableDatabase.query(
            TABLE_TAB_TRANSCRIPTIONS,
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
            return TabNoteEntity(
                id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                audioUri = it.getString(it.getColumnIndexOrThrow(COLUMN_AUDIO_URI)),
                audioName = it.getStringOrNull(COLUMN_AUDIO_NAME),
                tabNotes = it.getString(it.getColumnIndexOrThrow(COLUMN_TAB_NOTES)).toTabNotes(),
                createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
            )
        }
    }

    fun getAllTabNotes(): List<TabNoteEntity> {
        val result = mutableListOf<TabNoteEntity>()

        val cursor = readableDatabase.query(
            TABLE_TAB_TRANSCRIPTIONS,
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
                    val entity = TabNoteEntity(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        audioUri = it.getString(it.getColumnIndexOrThrow(COLUMN_AUDIO_URI)),
                        audioName = it.getStringOrNull(COLUMN_AUDIO_NAME),
                        tabNotes = it.getString(it.getColumnIndexOrThrow(COLUMN_TAB_NOTES)).toTabNotes(),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
                    )

                    result.add(entity)

                } while (it.moveToNext())
            }
        }

        return result
    }

    fun getTabNotesById(id: Long): TabNoteEntity? {
        val cursor = readableDatabase.query(
            TABLE_TAB_TRANSCRIPTIONS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        cursor.use {
            if (!it.moveToFirst()) return null

            return TabNoteEntity(
                id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                audioUri = it.getString(it.getColumnIndexOrThrow(COLUMN_AUDIO_URI)),
                audioName = it.getStringOrNull(COLUMN_AUDIO_NAME),
                tabNotes = it.getString(it.getColumnIndexOrThrow(COLUMN_TAB_NOTES)).toTabNotes(),
                createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
            )
        }
    }

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

    private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
        val columnIndex = getColumnIndex(columnName)
        if (columnIndex == -1 || isNull(columnIndex)) return null
        return getString(columnIndex)
    }

    companion object {
        private const val DATABASE_NAME = "tab_notes.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_TAB_TRANSCRIPTIONS = "tab_transcriptions"
        private const val COLUMN_ID = "id"
        private const val COLUMN_AUDIO_URI = "audio_uri"
        private const val COLUMN_AUDIO_NAME = "audio_name"
        private const val COLUMN_TAB_NOTES = "tab_notes"
        private const val COLUMN_CREATED_AT = "created_at"
    }
}
