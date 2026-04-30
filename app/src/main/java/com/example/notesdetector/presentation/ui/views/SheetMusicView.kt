package com.example.notesdetector.presentation.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.notesdetector.data.NoteEvent
import kotlin.math.roundToInt

class SheetMusicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ─── Public API ───────────────────────────────────────────────────────────

    fun setNotes(notes: List<NoteEvent>) {
        this.notes = notes.sortedBy { it.startSec }
        requestLayout()
        invalidate()
    }

    // ─── Layout constants (dp → px resolved in onSizeChanged) ────────────────

    private val dp = context.resources.displayMetrics.density

    // Staff geometry
    private var staffTop       = 0f   // y of top staff line
    private var lineSpacing    = 0f   // distance between staff lines (= one step)
    private var staffHeight    = 0f   // 4 * lineSpacing
    private var noteRadius     = 0f   // oval half-height  = lineSpacing / 2
    private var noteRadiusW    = 0f   // oval half-width   = noteRadius * 1.55
    private var stemLength     = 0f   // 3.5 * lineSpacing

    // Horizontal layout
    private var marginLeft     = 0f
    private var marginRight    = 0f
    private var clefWidth      = 0f
    private var timeSigWidth   = 0f
    private var noteAreaStart  = 0f   // x where notes begin
    private var noteAreaWidth  = 0f   // usable width for time mapping

    private var viewHeight     = 0f

    // ─── State ───────────────────────────────────────────────────────────────

    private var notes: List<NoteEvent> = emptyList()

    // ─── Paints ──────────────────────────────────────────────────────────────

    private val staffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 1.5f * dp
        style = Paint.Style.STROKE
    }

    private val barlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 1.5f * dp
        style = Paint.Style.STROKE
    }

    private val noteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val noteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    private val ledgerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    private val accidentalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL_AND_STROKE
        textAlign = Paint.Align.CENTER
    }

    private val clefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL_AND_STROKE
        textAlign = Paint.Align.LEFT
    }

    private val timeSigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // ─── Measure / layout ────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 }
            ?: (300 * dp).toInt()

        // Staff height ≈ 28 % of width, clamped between 80 dp and 160 dp
        val desiredStaffH = (w * 0.28f).coerceIn(80 * dp, 160 * dp)
        val totalH = (desiredStaffH * 3.6f).roundToInt()   // room for ledger lines + clef
        setMeasuredDimension(w, totalH)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        viewHeight    = h.toFloat()
        marginLeft    = 16 * dp
        marginRight   = 16 * dp

        // Five staff lines → 4 gaps
        staffHeight   = h * 0.40f
        lineSpacing   = staffHeight / 4f
        noteRadius    = lineSpacing * 0.52f
        noteRadiusW   = noteRadius  * 1.55f
        stemLength    = lineSpacing * 3.5f

        // Centre the staff vertically (a bit above mid to leave ledger room below)
        staffTop      = h * 0.28f

        clefWidth     = lineSpacing * 4.5f
        timeSigWidth  = lineSpacing * 2.5f

        marginLeft    = 18 * dp
        noteAreaStart = marginLeft + clefWidth + timeSigWidth + 8 * dp
        noteAreaWidth = w - noteAreaStart - marginRight

        // Update stroke widths relative to lineSpacing
        staffPaint.strokeWidth   = lineSpacing * 0.075f
        barlinePaint.strokeWidth = lineSpacing * 0.075f
        stemPaint.strokeWidth    = lineSpacing * 0.10f
        ledgerPaint.strokeWidth  = lineSpacing * 0.10f
        noteStrokePaint.strokeWidth = lineSpacing * 0.06f

        accidentalPaint.textSize = lineSpacing * 1.6f
        clefPaint.textSize       = staffHeight * 1.18f   // large enough to span 5 lines
        timeSigPaint.textSize    = lineSpacing * 1.55f
    }

    // ─── Draw ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawStaff(canvas)
        drawClef(canvas)
        drawTimeSignature(canvas)
        drawBarlines(canvas)
        if (notes.isNotEmpty()) drawNotes(canvas)
    }

    // ── Staff lines ──────────────────────────────────────────────────────────

    private fun drawStaff(canvas: Canvas) {
        val x0 = marginLeft
        val x1 = width - marginRight
        for (i in 0..4) {
            val y = staffTop + i * lineSpacing
            canvas.drawLine(x0, y, x1, y, staffPaint)
        }
        // Left barline (full height)
        canvas.drawLine(x0, staffTop, x0, staffTop + staffHeight, barlinePaint)
        // Right barline (double)
        val thin = 1.5f * dp
        val thick = 4f * dp
        canvas.drawLine(x1 - thick - thin - 2 * dp, staffTop, x1 - thick - thin - 2 * dp, staffTop + staffHeight, barlinePaint)
        canvas.drawRect(x1 - thick, staffTop, x1, staffTop + staffHeight, barlinePaint.also { it.style = Paint.Style.FILL })
        barlinePaint.style = Paint.Style.STROKE
    }

    // ── Treble clef (drawn with paths, no font dependency) ───────────────────

    private fun drawClef(canvas: Canvas) {
        // We draw a simplified but recognisable treble clef using a series of
        // Bezier curves anchored to the staff geometry.
        val x  = marginLeft + 6 * dp
        // The "curl" of the treble clef sits on the G line (2nd line from bottom = line index 1)
        val gLineY = staffTop + 3 * lineSpacing   // G4 is the 2nd line (index 3 from top = index 1 from bottom)
        val s  = lineSpacing

        val path = Path()

        // Vertical spine: from above staff down through curl
        val spineX   = x + s * 0.55f
        val spineTop = staffTop - s * 1.0f
        val spineBot = staffTop + staffHeight + s * 0.55f

        // 1. Spine (straight-ish descending line, slightly curved)
        path.moveTo(spineX, spineTop)
        path.cubicTo(
            spineX + s * 0.12f, staffTop,
            spineX + s * 0.12f, gLineY - s * 0.3f,
            spineX,             gLineY + s * 0.1f
        )
        path.cubicTo(
            spineX - s * 0.05f, gLineY + s * 0.5f,
            spineX - s * 0.05f, spineBot - s * 0.2f,
            spineX + s * 0.0f,  spineBot
        )

        // 2. Bottom curl
        path.cubicTo(
            spineX - s * 0.55f, spineBot + s * 0.25f,
            spineX - s * 0.9f,  spineBot - s * 0.1f,
            spineX - s * 0.5f,  spineBot - s * 0.55f
        )
        path.cubicTo(
            spineX - s * 0.1f,  spineBot - s * 1.0f,
            spineX + s * 0.4f,  spineBot - s * 0.9f,
            spineX + s * 0.4f,  spineBot - s * 0.55f
        )

        // 3. Upper swirl around the G line
        val swirlCX = spineX + s * 0.1f
        val swirlCY = gLineY
        path.moveTo(swirlCX + s * 0.6f, swirlCY)
        path.cubicTo(
            swirlCX + s * 0.6f, swirlCY - s * 0.7f,
            swirlCX - s * 0.6f, swirlCY - s * 0.7f,
            swirlCX - s * 0.5f, swirlCY
        )
        path.cubicTo(
            swirlCX - s * 0.4f, swirlCY + s * 0.55f,
            swirlCX + s * 0.5f, swirlCY + s * 0.45f,
            swirlCX + s * 0.5f, swirlCY - s * 0.1f
        )

        val clefPaintDraw = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = lineSpacing * 0.22f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, clefPaintDraw)
    }

    // ── 4/4 time signature ───────────────────────────────────────────────────

    private fun drawTimeSignature(canvas: Canvas) {
        val cx = marginLeft + clefWidth + timeSigWidth / 2f
        val midStaff = staffTop + staffHeight / 2f
        canvas.drawText("4", cx, midStaff - lineSpacing * 0.05f, timeSigPaint)
        canvas.drawText("4", cx, midStaff + lineSpacing * 1.0f, timeSigPaint)
    }

    // ── Bar lines (every 4 beats, derived from note timing) ──────────────────

    private fun drawBarlines(canvas: Canvas) {
        if (notes.isEmpty()) return
        val totalDur = notes.maxOf { it.endSec }
        if (totalDur <= 0f) return

        // Assume quarter-note = 0.5 s (120 bpm) as default; draw every ~2 s (4 beats)
        val barDuration = 2.0f
        var t = barDuration
        while (t < totalDur) {
            val x = timeToX(t, totalDur)
            canvas.drawLine(x, staffTop, x, staffTop + staffHeight, barlinePaint)
            t += barDuration
        }
    }

    // ─── Note rendering ───────────────────────────────────────────────────────

    private fun drawNotes(canvas: Canvas) {
        val totalDur = notes.maxOf { it.endSec }
        if (totalDur <= 0f) return

        for (note in notes) {
            val x = timeToX(note.startSec, totalDur)
            drawNote(canvas, note, x)
        }
    }

    /**
     * Draw a single note head + stem + ledger lines + accidental.
     *
     * Staff position system (steps from middle C = C4, midi 60):
     *   In treble clef the staff lines are E4 F4 G4 A4 B4 (bottom → top).
     *   We compute how many diatonic steps the note sits above E4 (midi 64).
     *   Every 2 steps = 1 lineSpacing downward from the top staff line.
     */
    private fun drawNote(canvas: Canvas, note: NoteEvent, x: Float) {
        val (staffStep, accidental) = midiToStaffStep(note.midi)

        // staffStep 0  = E4 (bottom staff line)
        // staffStep 8  = E5 (top staff line)
        // positive step → higher pitch → lower y
        // y of bottom staff line = staffTop + staffHeight
        val bottomLineY = staffTop + staffHeight
        val noteY       = bottomLineY - staffStep * (lineSpacing / 2f)

        val isFilled    = true   // whole notes would be hollow; use filled for all for now
        val stemUp      = staffStep < 4   // stem up when below middle of staff

        // ── Ledger lines ─────────────────────────────────────────────────────
        drawLedgerLines(canvas, staffStep, x, noteY)

        // ── Accidental ───────────────────────────────────────────────────────
        if (accidental != 0) {
            val symbol = if (accidental == 1) "♯" else "♭"
            canvas.drawText(symbol, x - noteRadiusW - lineSpacing * 0.55f, noteY + noteRadius * 0.38f, accidentalPaint)
        }

        // ── Note head ────────────────────────────────────────────────────────
        val oval = RectF(
            x - noteRadiusW,
            noteY - noteRadius,
            x + noteRadiusW,
            noteY + noteRadius
        )
        // Slight rotation for a typographic look
        canvas.save()
        canvas.rotate(-12f, x, noteY)
        if (isFilled) {
            canvas.drawOval(oval, noteFillPaint)
        } else {
            canvas.drawOval(oval, noteStrokePaint)
        }
        canvas.restore()

        // ── Stem ─────────────────────────────────────────────────────────────
        val stemX   = if (stemUp) x + noteRadiusW else x - noteRadiusW
        val stemY0  = noteY + (if (stemUp) -noteRadius * 0.3f else noteRadius * 0.3f)
        val stemY1  = if (stemUp) noteY - stemLength else noteY + stemLength
        canvas.drawLine(stemX, stemY0, stemX, stemY1, stemPaint)
    }

    // ── Ledger lines ─────────────────────────────────────────────────────────

    private fun drawLedgerLines(canvas: Canvas, staffStep: Int, x: Float, noteY: Float) {
        val ledgerHalfW = noteRadiusW * 1.5f
        val bottomLineY = staffTop + staffHeight

        // Below staff: steps 0, -2, -4 … (E4 = 0, D4 = -1, C4 = -2 = middle C, …)
        if (staffStep <= -2) {
            var step = 0
            while (step >= staffStep) {
                if (step % 2 == 0) {
                    val ly = bottomLineY - step * (lineSpacing / 2f)
                    canvas.drawLine(x - ledgerHalfW, ly, x + ledgerHalfW, ly, ledgerPaint)
                }
                step -= 2
            }
        }

        // Above staff: steps 8 = E5 (top line), 10 = G5, 12 = B5, …
        if (staffStep >= 10) {
            var step = 10
            while (step <= staffStep) {
                val ly = bottomLineY - step * (lineSpacing / 2f)
                canvas.drawLine(x - ledgerHalfW, ly, x + ledgerHalfW, ly, ledgerPaint)
                step += 2
            }
        }
    }

    // ─── Music theory helpers ─────────────────────────────────────────────────

    /**
     * Convert a MIDI note number to:
     *   - staffStep: diatonic half-step position in the treble staff
     *                0  = E4 (bottom line)
     *                2  = F4 (first space)
     *                4  = G4 (second line)
     *                6  = A4 (second space)
     *                8  = B4 (third line)   … etc.
     *                Negative values fall below the staff.
     *   - accidental: -1 = flat, 0 = natural, +1 = sharp
     *
     * The mapping follows standard Western notation chromatic → diatonic.
     */
    private fun midiToStaffStep(midi: Int): Pair<Int, Int> {
        // Chromatic pitch class → (diatonic note index 0-6, accidental)
        // Diatonic index: C=0 D=1 E=2 F=3 G=4 A=5 B=6
        val chromaticToNote = arrayOf(
            Pair(0,  0),   // C  natural
            Pair(0,  1),   // C# / Db → render as C#
            Pair(1,  0),   // D  natural
            Pair(1,  1),   // D#
            Pair(2,  0),   // E  natural
            Pair(3,  0),   // F  natural
            Pair(3,  1),   // F#
            Pair(4,  0),   // G  natural
            Pair(4,  1),   // G#
            Pair(5,  0),   // A  natural
            Pair(5,  1),   // A# / Bb
            Pair(6,  0)    // B  natural
        )

        val octave  = midi / 12 - 1          // MIDI 60 = C4 → octave 4
        val pc      = midi % 12              // pitch class 0-11
        val (diatonicNote, accidental) = chromaticToNote[pc]

        // Diatonic steps from C in one octave: C D E F G A B = 0 1 2 3 4 5 6
        // Convert to staff steps (each diatonic note = 1 step in our system)
        // E4 = bottom staff line = step 0; midi 64
        // E4: octave=4, diatonic=2 → reference = (4 * 7) + 2 = 30
        val referenceSteps = (4 * 7) + 2   // E4

        val noteSteps = (octave * 7) + diatonicNote
        val staffStep = noteSteps - referenceSteps

        return Pair(staffStep, accidental)
    }

    // ─── Coordinate mapping ───────────────────────────────────────────────────

    private fun timeToX(timeSec: Float, totalDuration: Float): Float {
        val t = (timeSec / totalDuration).coerceIn(0f, 1f)
        return noteAreaStart + t * noteAreaWidth
    }
}