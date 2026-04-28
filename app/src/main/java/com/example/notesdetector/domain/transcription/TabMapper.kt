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

    private const val TIME_THRESHOLD = 0.05f

    fun map(notes: List<NoteEvent>): List<TabNote> {

        val sorted = notes.sortedBy { it.startSec }
        val groups = mutableListOf<MutableList<NoteEvent>>()

        for (note in sorted) {
            val group = groups.lastOrNull()

            if (group == null) {
                groups.add(mutableListOf(note))
            } else {
                val timeDiff = note.startSec - group.first().startSec

                if (timeDiff <= TIME_THRESHOLD) {
                    group.add(note)
                } else {
                    groups.add(mutableListOf(note))
                }
            }
        }

        val result = mutableListOf<TabNote>()

        for (group in groups) {

            val groupTime = group.first().startSec
            val usedStrings = mutableSetOf<Int>()

            for (note in group) {
                val tab = findBestString(note, usedStrings, groupTime)
                if (tab != null) {
                    usedStrings.add(tab.stringIndex)
                    result.add(tab)
                }
            }
        }

        return result
    }

    private fun findBestString(
        note: NoteEvent,
        usedStrings: Set<Int>,
        forcedTime: Float
    ): TabNote? {

        val midi = note.midi

        var best: TabNote? = null
        var minFret = Int.MAX_VALUE

        for (i in strings.indices) {

            if (i in usedStrings) continue

            val openMidi = strings[i]
            val fret = midi - openMidi

            if (fret in 0..20) {
                if (fret < minFret) {
                    minFret = fret
                    best = TabNote(
                        stringIndex = i,
                        fret = fret,
                        time = forcedTime
                    )
                }
            }
        }

        return best
    }
}