package com.ctodrs.saqrcode.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle
import com.termux.terminal.WcWidth

/** A run of characters with one resolved style (colours are ARGB ints). */
data class TermRun(
    val text: String, val fg: Int, val bg: Int?, val bold: Boolean = false, val italic: Boolean = false,
    val underline: Boolean = false, val strike: Boolean = false, val dim: Boolean = false, val cursor: Boolean = false,
)

/** Saqr's terminal palette: 16 ANSI colours tuned for the graphite background, plus default fg/bg/cursor. */
data class TermPalette(val ansi: IntArray, val fg: Int, val bg: Int, val cursor: Int) {
    companion object {
        val Saqr = TermPalette(
            intArrayOf(
                0xFF1B1F25.toInt(), 0xFFF2555A.toInt(), 0xFF46C98B.toInt(), 0xFFE9B44C.toInt(),
                0xFF5AA9F0.toInt(), 0xFFC38BF5.toInt(), 0xFF4FC9C0.toInt(), 0xFFD3D9E0.toInt(),
                0xFF5C6470.toInt(), 0xFFFF7B7F.toInt(), 0xFF6FE0A8.toInt(), 0xFFFFD27A.toInt(),
                0xFF86C2FF.toInt(), 0xFFDCB2FF.toInt(), 0xFF7EE3DA.toInt(), 0xFFFFFFFF.toInt(),
            ),
            fg = 0xFFD3D9E0.toInt(), bg = 0xFF0E1013.toInt(), cursor = 0xFFE9B44C.toInt(),
        )
    }
}

/**
 * Turns the Termux emulator's screen buffer (scrollback + visible rows) into styled runs.
 * Handles indexed 16/256 colours, 24-bit truecolor, bold-brightening, inverse, dim, wide chars.
 */
object TermRender {
    /** Resolves an encoded Termux colour (index or 0xff_rrggbb truecolor) against the palette. */
    fun color(code: Int, emulator: TerminalEmulator?, p: TermPalette): Int = when {
        (code.toLong() and 0xff000000L) == 0xff000000L -> code // truecolor
        code in 0..15 -> p.ansi[code]
        code == TextStyle.COLOR_INDEX_FOREGROUND -> p.fg
        code == TextStyle.COLOR_INDEX_BACKGROUND -> p.bg
        code == TextStyle.COLOR_INDEX_CURSOR -> p.cursor
        code in 16..255 -> emulator?.mColors?.mCurrentColors?.getOrNull(code) ?: xterm256(code)
        else -> p.fg
    }

    /** Standard xterm 256-colour cube / greyscale for indices 16..255. */
    fun xterm256(i: Int): Int {
        if (i < 16) return TermPalette.Saqr.ansi[i]
        if (i >= 232) { val v = 8 + (i - 232) * 10; return argb(v, v, v) }
        val n = i - 16
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        return argb(steps[n / 36], steps[(n / 6) % 6], steps[n % 6])
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Rows [firstRow, lastRow] where 0 is the top visible row and negatives are scrollback. */
    fun lines(e: TerminalEmulator, p: TermPalette, maxScrollback: Int = 1500, cursorOn: Boolean = true): List<List<TermRun>> {
        val screen = e.screen
        val top = -minOf(screen.activeTranscriptRows, maxScrollback)
        val out = ArrayList<List<TermRun>>(e.mRows - top)
        val reverse = e.isReverseVideo
        for (row in top until e.mRows) {
            val cursorCol = if (cursorOn && row == e.cursorRow && e.shouldCursorBeVisible()) e.cursorCol else -1
            out += row(e, row, p, cursorCol, reverse)
        }
        // trim trailing empty rows below the cursor so the view hugs the content
        var last = out.lastIndex
        val cursorIndex = e.cursorRow - top
        while (last > cursorIndex && out[last].all { it.text.isBlank() && it.bg == null && !it.cursor }) last--
        return out.subList(0, last + 1)
    }

    private fun row(e: TerminalEmulator, row: Int, p: TermPalette, cursorCol: Int, reverseVideo: Boolean): List<TermRun> {
        val screen = e.screen
        val line = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row))
        val text = line.mText
        val used = line.spaceUsed
        val runs = ArrayList<TermRun>()
        val sb = StringBuilder()
        var curKey: Long? = null
        var curCursor = false
        fun flush() {
            if (sb.isEmpty()) return
            runs += styleRun(sb.toString(), curKey ?: 0L, e, p, curCursor, reverseVideo)
            sb.setLength(0)
        }
        var col = 0
        var idx = 0
        val cols = e.mColumns
        while (col < cols) {
            val style = line.getStyle(col)
            val atCursor = col == cursorCol
            val cp: Int; val len: Int
            if (idx < used) {
                val ch = text[idx]
                if (Character.isHighSurrogate(ch) && idx + 1 < used) { cp = Character.toCodePoint(ch, text[idx + 1]); len = 2 } else { cp = ch.code; len = 1 }
            } else { cp = ' '.code; len = 1 }
            val w = WcWidth.width(cp).coerceAtLeast(1)
            val past = idx >= used
            // stop at the end of meaningful content (keep styled backgrounds and the cursor)
            if (past && col > cursorCol && TextStyle.decodeBackColor(style) == TextStyle.COLOR_INDEX_BACKGROUND && (TextStyle.decodeEffect(style) and TextStyle.CHARACTER_ATTRIBUTE_INVERSE) == 0) {
                if (allBlankFrom(line, col, cols, cursorCol)) break
            }
            if (style != curKey || atCursor != curCursor) { flush(); curKey = style; curCursor = atCursor }
            sb.appendCodePoint(cp)
            if (atCursor) { flush(); curCursor = false; curKey = null }
            idx += if (past) 0 else len
            // zero-width combining chars that follow belong to this cell
            while (idx < used) {
                val nc = text[idx].code
                if (WcWidth.width(nc) == 0 && !Character.isHighSurrogate(text[idx])) { sb.append(text[idx]); idx++ } else break
            }
            col += w
        }
        flush()
        return runs
    }

    private fun allBlankFrom(line: com.termux.terminal.TerminalRow, from: Int, cols: Int, cursorCol: Int): Boolean {
        for (c in from until cols) {
            if (c == cursorCol) return false
            val s = line.getStyle(c)
            if (TextStyle.decodeBackColor(s) != TextStyle.COLOR_INDEX_BACKGROUND) return false
        }
        return true
    }

    fun styleRun(text: String, style: Long, e: TerminalEmulator?, p: TermPalette, cursor: Boolean, reverseVideo: Boolean = false): TermRun {
        val effect = TextStyle.decodeEffect(style)
        var fgCode = TextStyle.decodeForeColor(style)
        val bgCode = TextStyle.decodeBackColor(style)
        val bold = effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD != 0
        if (bold && fgCode in 0..7) fgCode += 8 // classic bold-is-bright
        var fg = color(fgCode, e, p)
        var bg: Int? = if (bgCode == TextStyle.COLOR_INDEX_BACKGROUND) null else color(bgCode, e, p)
        var inverse = effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0
        if (reverseVideo) inverse = !inverse
        if (inverse) { val b = bg ?: p.bg; bg = fg; fg = b }
        if (cursor) { bg = p.cursor; fg = p.bg }
        if (effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE != 0) fg = bg ?: p.bg
        return TermRun(
            text, fg, bg, bold = bold, italic = effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC != 0,
            underline = effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE != 0,
            strike = effect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH != 0,
            dim = effect and TextStyle.CHARACTER_ATTRIBUTE_DIM != 0, cursor = cursor,
        )
    }

    /** Escape sequences for the extra-keys row (normal vs application cursor mode). */
    fun key(name: String, appCursor: Boolean = false): String = when (name) {
        "ESC" -> "\u001b"
        "TAB" -> "\t"
        "UP" -> if (appCursor) "\u001bOA" else "\u001b[A"
        "DOWN" -> if (appCursor) "\u001bOB" else "\u001b[B"
        "RIGHT" -> if (appCursor) "\u001bOC" else "\u001b[C"
        "LEFT" -> if (appCursor) "\u001bOD" else "\u001b[D"
        "HOME" -> "\u001b[H"
        "END" -> "\u001b[F"
        "PGUP" -> "\u001b[5~"
        "PGDN" -> "\u001b[6~"
        "DEL" -> "\u001b[3~"
        "BKSP" -> "\u007f"
        "ENTER" -> "\r"
        else -> name
    }

    /** Ctrl+<c>: maps a-z / @[\]^_ / space to C0 control bytes (Ctrl-C = 0x03, Ctrl-D = 0x04…). */
    fun ctrl(c: Char): String? = when (val u = c.uppercaseChar()) {
        in '@'..'_' -> (u.code - 64).toChar().toString()
        ' ' -> "\u0000"
        '?' -> "\u007f"
        else -> null
    }
}

/** Off-screen emulator for previews/tests: feeds bytes without a process. */
class HeadlessTerminal(cols: Int, rows: Int) {
    private val out = object : com.termux.terminal.TerminalOutput() {
        override fun write(data: ByteArray?, offset: Int, count: Int) {}
        override fun titleChanged(oldTitle: String?, newTitle: String?) {}
        override fun onCopyTextToClipboard(text: String?) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
    }
    val emulator = TerminalEmulator(out, cols, rows, 500, NoopClient())
    fun feed(s: String): HeadlessTerminal { val b = s.toByteArray(Charsets.UTF_8); emulator.append(b, b.size); return this }
}
