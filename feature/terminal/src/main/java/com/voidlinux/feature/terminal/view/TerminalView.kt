package com.voidlinux.feature.terminal.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.voidlinux.feature.terminal.AnsiParser
import com.voidlinux.feature.terminal.TerminalBuffer
import kotlin.math.ceil

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var buffer: TerminalBuffer = TerminalBuffer()
        set(value) {
            field = value
            parser = AnsiParser(value)
        }

    // Le parseur garde l'état (couleurs, séquence ESC coupée en deux) entre deux lectures
    private var parser: AnsiParser = AnsiParser(buffer)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 30f
        color = Color.parseColor("#00FF88")
    }

    private val boldPaint = Paint(textPaint).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val cursorPaint = Paint().apply {
        color = Color.parseColor("#00FF88")
        style = Paint.Style.FILL
    }

    private var cellWidth = 0f
    private var cellHeight = 0f
    private var cursorVisible = true
    private var blinkRunnable: Runnable? = null

    var onInput: ((String) -> Unit)? = null
    var onResize: ((cols: Int, rows: Int) -> Unit)? = null

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        startBlink()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateGrid(w, h)
    }

    private fun recalculateGrid(w: Int, h: Int) {
        val metrics = textPaint.fontMetrics
        cellHeight = ceil(metrics.descent - metrics.ascent)
        cellWidth = textPaint.measureText("M")

        val cols = (w / cellWidth).toInt().coerceAtLeast(10)
        val rows = (h / cellHeight).toInt().coerceAtLeast(4)

        if (cols != buffer.cols || rows != buffer.rows) {
            buffer.resize(cols, rows)
            onResize?.invoke(cols, rows)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val fm = textPaint.fontMetrics
        val baselineOffset = -fm.ascent

        for (r in 0 until buffer.rows) {
            for (c in 0 until buffer.cols) {
                val cell = buffer.getCell(r, c) ?: continue

                val x = c * cellWidth
                val y = r * cellHeight

                if (cell.bg != Color.BLACK || cell.reverse) {
                    bgPaint.color = if (cell.reverse) cell.fg else cell.bg
                    canvas.drawRect(x, y, x + cellWidth, y + cellHeight, bgPaint)
                }

                if (cell.char != ' ') {
                    val paint = if (cell.bold) boldPaint else textPaint
                    paint.color = if (cell.reverse) cell.bg else cell.fg
                    canvas.drawText(
                        cell.char.toString(),
                        x,
                        y + baselineOffset,
                        paint
                    )
                }
            }
        }

        if (cursorVisible && isFocused) {
            val cx = buffer.cursorCol * cellWidth
            val cy = buffer.cursorRow * cellHeight
            canvas.drawRect(cx, cy, cx + cellWidth, cy + cellHeight, cursorPaint)
        }
    }

    fun writeText(text: String) {
        parser.feed(text)
        postInvalidate()
    }

    fun sendInput(data: String) {
        onInput?.invoke(data)
    }

    private fun startBlink() {
        blinkRunnable = object : Runnable {
            override fun run() {
                cursorVisible = !cursorVisible
                postInvalidateOnAnimation()
                postDelayed(this, 500)
            }
        }
        postDelayed(blinkRunnable!!, 500)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blinkRunnable?.let { removeCallbacks(it) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            return true
        }
        return super.onTouchEvent(event)
    }
}