package com.ctodrs.saqrcode.editor

/** Plain editor state (kept free of Compose types so it is unit-testable). */
data class EditorState(val text: String, val selStart: Int = text.length, val selEnd: Int = selStart)

/**
 * Undo/redo history. Consecutive single-character typing (or backspacing) at an advancing caret within
 * [coalesceMs] is merged into one undo step; a newline, a pause, a caret jump, a paste or any other edit
 * starts a new step. Bounded by [maxSteps] and [maxChars] total stored text.
 */
class EditHistory(private val maxSteps: Int = 300, private val maxChars: Long = 8_000_000L, private val coalesceMs: Long = 1000L) {
    private enum class Kind { INSERT, DELETE, OTHER }

    private val undoStack = ArrayDeque<EditorState>()
    private val redoStack = ArrayDeque<EditorState>()
    private var lastKind = Kind.OTHER
    private var lastCaret = -1
    private var lastTime = 0L

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val undoSize get() = undoStack.size

    /** Call on every change. Selection-only changes are ignored. */
    fun record(old: EditorState, new: EditorState, now: Long = System.currentTimeMillis()) {
        if (old.text == new.text) return
        val kind: Kind
        val caret: Int
        val dLen = new.text.length - old.text.length
        if (dLen == 1 && new.selStart == new.selEnd && new.selStart > 0 && old.selStart == old.selEnd &&
            new.selStart == old.selStart + 1 && new.text[new.selStart - 1] != '\n' &&
            new.text.regionMatches(0, old.text, 0, old.selStart) &&
            new.text.regionMatches(new.selStart, old.text, old.selStart, old.text.length - old.selStart)) {
            kind = Kind.INSERT; caret = new.selStart
        } else if (dLen == -1 && old.selStart == old.selEnd && new.selStart == new.selEnd && new.selStart == old.selStart - 1 &&
            old.text[new.selStart] != '\n') {
            kind = Kind.DELETE; caret = new.selStart
        } else { kind = Kind.OTHER; caret = -1 }

        val coalesce = kind != Kind.OTHER && kind == lastKind && now - lastTime <= coalesceMs &&
            ((kind == Kind.INSERT && caret == lastCaret + 1) || (kind == Kind.DELETE && caret == lastCaret - 1)) &&
            undoStack.isNotEmpty()
        if (!coalesce) push(old)
        redoStack.clear()
        lastKind = kind; lastCaret = caret; lastTime = now
    }

    /** Force the next edit to start a new undo step (e.g. after save or a toolbar action). */
    fun breakGroup() { lastKind = Kind.OTHER }

    fun undo(current: EditorState): EditorState? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        breakGroup()
        return prev
    }

    fun redo(current: EditorState): EditorState? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        breakGroup()
        return next
    }

    fun clear() { undoStack.clear(); redoStack.clear(); breakGroup() }

    private fun push(s: EditorState) {
        undoStack.addLast(s)
        while (undoStack.size > maxSteps) undoStack.removeFirst()
        var total = undoStack.sumOf { it.text.length.toLong() }
        while (total > maxChars && undoStack.size > 1) total -= undoStack.removeFirst().text.length
    }
}
