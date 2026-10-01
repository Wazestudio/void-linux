package com.voidlinux.feature.terminal

import android.graphics.Color

/**
 * Parseur ANSI/VT100 minimal.
 * Traduit les séquences d'échappement en opérations sur le TerminalBuffer.
 */
class AnsiParser(private val buffer: TerminalBuffer) {

    enum class State { TEXT, ESCAPE, CSI, OSC, CHARSET }

    private var state = State.TEXT
    private val csiBuffer = StringBuilder()

    // Couleurs ANSI standard
    private val ansiColors = intArrayOf(
        Color.parseColor("#000000"), // noir
        Color.parseColor("#FF5555"), // rouge
        Color.parseColor("#50FA7B"), // vert
        Color.parseColor("#F1FA8C"), // jaune
        Color.parseColor("#6272A4"), // bleu
        Color.parseColor("#BD93F9"), // magenta
        Color.parseColor("#8BE9FD"), // cyan
        Color.parseColor("#F8F8F2")  // blanc
    )

    // Attributs courants
    private var currentFg: Int = Color.parseColor("#00FF88")
    private var currentBg: Int = Color.BLACK
    private var bold = false
    private var italic = false
    private var underline = false
    private var reverse = false

    fun feed(data: String) {
        for (ch in data) {
            when (state) {
                State.TEXT -> handleText(ch)
                State.ESCAPE -> handleEscape(ch)
                State.CSI -> handleCsi(ch)
                State.OSC -> handleOsc(ch)
                State.CHARSET -> state = State.TEXT // choix de jeu de caractères : on ignore le caractère suivant
            }
        }
    }

    private fun handleText(ch: Char) {
        when (ch) {
            '\u001B' -> state = State.ESCAPE
            '\n' -> buffer.newLine()
            '\r' -> buffer.moveCursor(buffer.cursorRow, 0)
            '\t' -> {
                val next = ((buffer.cursorCol / 8) + 1) * 8
                buffer.moveCursor(buffer.cursorRow, next)
            }
            '\b' -> buffer.backspace()
            else -> {
                if (ch.code >= 32) {
                    buffer.writeChar(
                        ch, currentFg, currentBg,
                        bold, italic, underline, reverse
                    )
                }
            }
        }
    }

    private fun handleEscape(ch: Char) {
        when (ch) {
            '[' -> {
                state = State.CSI
                csiBuffer.clear()
            }
            ']' -> state = State.OSC
            '(', ')', '*', '+' -> state = State.CHARSET
            else -> state = State.TEXT
        }
    }

    private fun handleCsi(ch: Char) {
        if (ch in '0'..'9' || ch == ';' || ch == '?') {
            csiBuffer.append(ch)
            return
        }

        val params = parseParams(csiBuffer.toString())

        when (ch) {
            'm' -> applySgr(params)
            'H', 'f' -> {
                val row = (params.getOrNull(0) ?: 1) - 1
                val col = (params.getOrNull(1) ?: 1) - 1
                buffer.moveCursor(row, col)
            }
            'A' -> buffer.moveCursorRelative(-(params.getOrNull(0) ?: 1), 0)
            'B' -> buffer.moveCursorRelative(params.getOrNull(0) ?: 1, 0)
            'C' -> buffer.moveCursorRelative(0, params.getOrNull(0) ?: 1)
            'D' -> buffer.moveCursorRelative(0, -(params.getOrNull(0) ?: 1))
            'J' -> {
                when (params.getOrNull(0) ?: 0) {
                    2 -> buffer.clear()
                    0 -> {
                        buffer.clearLine()
                        for (r in buffer.cursorRow + 1 until buffer.rows) {
                            for (c in 0 until buffer.cols) buffer.setCell(r, c, TerminalBuffer.Cell())
                        }
                    }
                }
            }
            'K' -> buffer.clearLine()
            'P' -> buffer.deleteChar()
            'G' -> buffer.moveCursor(buffer.cursorRow, (params.getOrNull(0) ?: 1) - 1)
            'd' -> buffer.moveCursor((params.getOrNull(0) ?: 1) - 1, buffer.cursorCol)
            '@' -> buffer.insertChars(params.getOrNull(0) ?: 1)
            'X' -> buffer.eraseChars(params.getOrNull(0) ?: 1)
            else -> { /* non supporté */ }
        }

        state = State.TEXT
    }

    private fun handleOsc(ch: Char) {
        // OSC : ignorer jusqu'à BEL ou ST
        if (ch == '\u0007') state = State.TEXT
        else if (ch == '\u001B') state = State.ESCAPE // ESC \ (ST) termine aussi un OSC
    }

    private fun parseParams(s: String): List<Int> {
        if (s.isEmpty()) return emptyList()
        return s.split(';').mapNotNull { it.toIntOrNull() }
    }

    private fun applySgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetAttributes()
            return
        }

        for (p in params) {
            when (p) {
                0 -> resetAttributes()
                1 -> bold = true
                3 -> italic = true
                4 -> underline = true
                7 -> reverse = true
                22 -> bold = false
                23 -> italic = false
                24 -> underline = false
                27 -> reverse = false
                in 30..37 -> currentFg = ansiColors[p - 30]
                in 40..47 -> currentBg = ansiColors[p - 40]
                39 -> currentFg = Color.parseColor("#00FF88")
                49 -> currentBg = Color.BLACK
                in 90..97 -> currentFg = ansiColors[p - 90 + 8]
                in 100..107 -> currentBg = ansiColors[p - 100 + 8]
            }
        }
    }

    private fun resetAttributes() {
        currentFg = Color.parseColor("#00FF88")
        currentBg = Color.BLACK
        bold = false
        italic = false
        underline = false
        reverse = false
    }
}