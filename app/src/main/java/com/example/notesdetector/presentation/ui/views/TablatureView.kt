package com.example.notesdetector.presentation.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.notesdetector.data.TabNote

class TablatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private var tabNotes: List<TabNote> = emptyList()

    fun setNotes(notes: List<TabNote>) {
        tabNotes = notes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = 50f
        val right = width - 50f

        val strings = 6
        val notesPerRow = 15

        val rowHeight = height / 6f
        val stringSpacing = rowHeight / 8f

        if (tabNotes.isEmpty()) return

        val sorted = tabNotes.sortedBy { it.time }
        val timeThreshold = 0.05f

        val groups = mutableListOf<MutableList<TabNote>>()

        for (note in sorted) {
            val group = groups.lastOrNull()

            if (group == null) {
                groups.add(mutableListOf(note))
            } else {
                val diff = note.time - group.first().time
                if (diff <= timeThreshold) {
                    group.add(note)
                } else {
                    groups.add(mutableListOf(note))
                }
            }
        }

        val numRows = (groups.size + notesPerRow - 1) / notesPerRow

        for (row in 0 until numRows) {
            val baseY = row * rowHeight

            for (i in 0 until strings) {
                val y = baseY + stringSpacing * (i + 1)
                canvas.drawLine(left, y, right, y, linePaint)
            }
        }

        val stepX = (right - left) / (notesPerRow + 1)

        for ((groupIndex, group) in groups.withIndex()) {

            val row = groupIndex / notesPerRow
            val col = groupIndex % notesPerRow

            val baseY = row * rowHeight
            val x = left + (col + 1) * stepX

            for (note in group) {
                val y = baseY + stringSpacing * (6 - note.stringIndex)

                val textOffset = (textPaint.descent() + textPaint.ascent()) / 2

                canvas.drawText(
                    note.fret.toString(),
                    x,
                    y - textOffset,
                    textPaint
                )
            }
        }
    }
}