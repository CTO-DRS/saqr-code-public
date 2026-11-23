package com.ctodrs.saqrcode.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

/**
 * One terminal tab: a real PTY session (Termux JNI) running proot+Alpine (or Android sh),
 * with a full xterm-256color/truecolor emulator. [version] bumps on every screen update.
 */
class TermTab(val id: Int, private val factory: (TerminalSessionClient) -> TerminalSession) {
    var version by mutableIntStateOf(0)
    var title by mutableStateOf("")
    var finished by mutableStateOf(false)
    var exitStatus by mutableIntStateOf(0)
    private val client = object : NoopClient() {
        override fun onTextChanged(changedSession: TerminalSession) { version++ }
        override fun onTitleChanged(changedSession: TerminalSession) { title = changedSession.title.orEmpty(); version++ }
        override fun onSessionFinished(finishedSession: TerminalSession) { finished = true; exitStatus = finishedSession.exitStatus; version++ }
    }
    var session: TerminalSession = factory(client)
        private set

    val emulator: TerminalEmulator? get() = session.emulator

    /** Starts the process on first layout and keeps the PTY size in sync. */
    fun resize(cols: Int, rows: Int) {
        if (cols < 4 || rows < 2) return
        session.updateSize(cols, rows)
        version++
    }

    fun write(text: String) {
        if (finished) return
        val b = text.toByteArray(Charsets.UTF_8)
        session.write(b, 0, b.size)
    }

    /** Pastes with bracketed-paste when the program asked for it. */
    fun paste(text: String) { if (!finished) emulator?.paste(text) ?: write(text) }

    fun restart(cols: Int, rows: Int) {
        runCatching { session.finishIfRunning() }
        finished = false
        session = factory(client)
        resize(cols, rows)
    }

    fun close() { runCatching { session.finishIfRunning() } }
}

/** All terminal tabs of a workspace. Sessions are created lazily on the main thread. */
class TerminalManager(private val linux: LinuxEnv, private val projectDir: File) {
    val tabs = mutableStateListOf<TermTab>()
    var active by mutableIntStateOf(0)
    private var nextId = 1

    val current: TermTab? get() = tabs.getOrNull(active)

    fun ensureOne(): TermTab = current ?: newTab()

    fun newTab(): TermTab {
        val tab = TermTab(nextId++) { client ->
            val cmd = linux.ptyCommand(projectDir)
            TerminalSession(cmd.executable, cmd.cwd, cmd.args, cmd.env, TRANSCRIPT_ROWS, client)
        }
        tabs += tab
        active = tabs.lastIndex
        return tab
    }

    fun closeTab(i: Int) {
        tabs.getOrNull(i)?.close()
        if (i in tabs.indices) tabs.removeAt(i)
        active = active.coerceAtMost(tabs.lastIndex).coerceAtLeast(0)
    }

    /** Restart every tab (e.g. after Linux was installed, so tabs switch from sh to Alpine). */
    fun restartAll(cols: Int, rows: Int) = tabs.forEach { it.restart(cols, rows) }

    companion object { const val TRANSCRIPT_ROWS = 3000 }
}

/** TerminalSessionClient with no-op logging; subclasses override what they need. */
open class NoopClient : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {}
    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {}
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
    override fun onPasteTextFromClipboard(session: TerminalSession?) {}
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}
