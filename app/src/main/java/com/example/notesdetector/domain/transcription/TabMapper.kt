package com.example.notesdetector.domain.transcription

import com.example.notesdetector.data.NoteEvent
import com.example.notesdetector.data.TabNote

object TabMapper {
    private val strings = listOf(
        40, // E2
        45, // A2
        50, // D3
        55, // G3
        59, // B3
        64  // E4
    )

    fun map(notes: List<NoteEvent>): List<TabNote> {
        return notes.mapNotNull { note ->
            findBestString(note)
        }
    }

    private fun findBestString(note: NoteEvent): TabNote? {
        val midi = note.midi

        var best: TabNote? = null
        var minFret = Int.MAX_VALUE

        for (i in strings.indices) {
            val openMidi = strings[i]
            val fret = midi - openMidi

            if (fret in 0..20) {
                if (fret < minFret) {
                    minFret = fret
                    best = TabNote(
                        stringIndex = i,
                        fret = fret,
                        time = note.startSec
                    )
                }
            }
        }

        return best
    }
}