package com.voidlinux.feature.terminal

import android.graphics.Color

/**
 * Tampon de l'écran terminal.
 * Gère les cellules (caractère + couleur avant/arrière + attributs).
 */
class TerminalBuffer(
    var cols: Int = 80,
    var rows: Int = 24
) {

    data class Cell(
        var char: Char = ' ',
        var fg: Int = Color.WHITE,
        var bg: Int = Color.BLACK,
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var reverse: Boolean = false
    )

    private var grid: Array<Array<Cell>> = createGrid(cols, rows)

    var cursorCol: Int = 0
        private set
    var cursorRow: Int = 0
        private set

    var scrollTop: Int = 0
    var scrollBottom: Int = rows - 1

    private fun createGrid(c: Int, r: Int): Array<Array<Cell>> =
        Array(r) { Array(c) { Cell() } }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val newGrid = createGrid(newCols, newRows)
        for (r in 0 until minOf(rows, newRows)) {
            for (c in 0 until minOf(cols, newCols)) {
                newGrid[r][c] = grid[r][c]
            }
        }
        grid = newGrid
        cols = newCols
        rows = newRows
        cursorCol = cursorCol.coerceAtMost(cols - 1)
        cursorRow = cursorRow.coerceAtMost(rows - 1)
        scrollBottom = rows - 1
    }

    fun getCell(row: Int, col: Int): Cell? =
        grid.getOrNull(row)?.getOrNull(col)

    fun setCell(row: Int, col: Int, cell: Cell) {
        grid.getOrNull(row)?.set(col, cell)
    }

    fun writeChar(ch: Char, fg: Int, bg: Int, bold: Boolean = false,
                  italic: Boolean = false, underline: Boolean = false,
                  reverse: Boolean = false) {
        if (cursorCol >= cols) {
            cursorCol = 0
            newLine()
        }
        val cell = grid[cursorRow][cursorCol]
        cell.char = ch
        cell.fg = fg
        cell.bg = bg
        cell.bold = bold
        cell.italic = italic
        cell.underline = underline
        cell.reverse = reverse
        cursorCol++
    }

    fun newLine() {
        cursorCol = 0
        cursorRow++
        if (cursorRow > scrollBottom) {
            scrollUp()
            cursorRow = scrollBottom
        }
    }

    fun scrollUp() {
        for (r in scrollTop until scrollBottom) {
            grid[r] = grid[r + 1].copyOf()
        }
        grid[scrollBottom] = Array(cols) { Cell() }
    }

    fun clear() {
        grid = createGrid(cols, rows)
        cursorCol = 0
        cursorRow = 0
    }

    fun clearLine() {
        for (c in 0 until cols) {
            grid[cursorRow][c] = Cell()
        }
    }

    fun moveCursor(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, cols - 1)
    }

    fun moveCursorRelative(dRow: Int, dCol: Int) {
        moveCursor(cursorRow + dRow, cursorCol + dCol)
    }

    fun backspace() {
        if (cursorCol > 0) {
            cursorCol--
            grid[cursorRow][cursorCol] = Cell()
        }
    }

    fun insertChars(count: Int) {
        val n = count.coerceIn(1, cols - cursorCol)
        for (c in cols - 1 downTo cursorCol + n) {
            grid[cursorRow][c] = grid[cursorRow][c - n]
        }
        for (c in cursorCol until cursorCol + n) {
            grid[cursorRow][c] = Cell()
        }
    }

    fun eraseChars(count: Int) {
        val end = (cursorCol + count.coerceAtLeast(1)).coerceAtMost(cols)
        for (c in cursorCol until end) {
            grid[cursorRow][c] = Cell()
        }
    }

    fun deleteChar() {
        for (c in cursorCol until cols - 1) {
            grid[cursorRow][c] = grid[cursorRow][c + 1]
        }
        grid[cursorRow][cols - 1] = Cell()
    }
}