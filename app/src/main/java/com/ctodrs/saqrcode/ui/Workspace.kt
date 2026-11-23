package com.ctodrs.saqrcode.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ctodrs.saqrcode.editor.EditHistory
import com.ctodrs.saqrcode.editor.EditorState
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.AgentSession
import com.ctodrs.saqrcode.git.GitController
import com.ctodrs.saqrcode.terminal.TerminalManager
import java.io.File

class OpenFile(val path: String, initial: String) {
    var value by mutableStateOf(TextFieldValue(initial))
    var saved by mutableStateOf(initial)
    val dirty get() = value.text != saved
    val history = EditHistory()
    /** Bumped on undo/redo/replace so the UI can refresh enabled states. */
    var historyVersion by mutableIntStateOf(0)

    private fun TextFieldValue.state() = EditorState(text, selection.min, selection.max)
    private fun EditorState.tfv() = TextFieldValue(text, TextRange(selStart.coerceIn(0, text.length), selEnd.coerceIn(0, text.length)))

    /** Apply an edit, recording it in the undo history. */
    fun edit(new: TextFieldValue) {
        if (new.text != value.text) { history.record(value.state(), new.state()); historyVersion++ }
        value = new
    }

    fun undo(): Boolean { val s = history.undo(value.state()) ?: return false; value = s.tfv(); historyVersion++; return true }
    fun redo(): Boolean { val s = history.redo(value.state()) ?: return false; value = s.tfv(); historyVersion++; return true }
}

/** Per-project state that survives navigation (chat, shell, open editor tabs). */
class Workspace(val name: String, val dir: File) {
    private val app = SaqrApp.instance
    val agent = AgentSession(name, dir, File(app.filesDir, "checkpoints"), app.settings, app.linux,
        com.ctodrs.saqrcode.git.GitService(app.filesDir, app.settings.accounts),
        store = com.ctodrs.saqrcode.agent.SessionStore(File(File(app.filesDir, "chats"), File(name).name)),
        hooks = com.ctodrs.saqrcode.service.AgentRuntime)
    val terminals by lazy { TerminalManager(app.linux, dir) }
    val git by lazy { GitController(dir, app.settings, app.filesDir) }
    var gitTab by mutableIntStateOf(0)
    val tabs = mutableStateListOf<OpenFile>()
    var activeTab by mutableIntStateOf(-1)
    var fsVersion by mutableIntStateOf(0)
    var tab by mutableIntStateOf(0)

    fun open(rel: String) {
        val idx = tabs.indexOfFirst { it.path == rel }
        if (idx >= 0) { activeTab = idx; return }
        val f = File(dir, rel)
        val text = runCatching { if (f.length() > 1_500_000) "(file too large to edit)" else f.readText() }.getOrDefault("")
        tabs += OpenFile(rel, text)
        activeTab = tabs.lastIndex
    }

    fun close(i: Int) {
        tabs.removeAt(i)
        activeTab = if (tabs.isEmpty()) -1 else minOf(activeTab, tabs.lastIndex).coerceAtLeast(0)
    }

    fun save(f: OpenFile) {
        File(dir, f.path).apply { parentFile?.mkdirs(); writeText(f.value.text) }
        f.saved = f.value.text
        fsVersion++
    }

    /** Reload clean tabs whose file changed on disk (e.g. edited by the agent). */
    fun syncFromDisk() {
        val it = tabs.listIterator()
        while (it.hasNext()) {
            val t = it.next()
            val file = File(dir, t.path)
            if (!file.exists()) continue
            if (!t.dirty) {
                val disk = runCatching { file.readText() }.getOrNull() ?: continue
                if (disk != t.saved) { t.saved = disk; t.history.breakGroup(); t.edit(TextFieldValue(disk)) }
            }
        }
    }

    companion object {
        private val map = HashMap<String, Workspace>()
        fun get(name: String): Workspace? {
            map[name]?.let { if (it.dir.isDirectory) return it }
            val p = SaqrApp.instance.projects.get(name) ?: return null
            return Workspace(name, p.dir).also { map[name] = it }
        }

        /** An already-open workspace, without creating one. */
        fun peek(name: String): Workspace? = map[name]

        /** Write every open chat to disk now (onPause / task removed). */
        fun saveAll() { map.values.forEach { runCatching { it.agent.saveNow(blocking = true) } } }
    }
}
