package com.ctodrs.saqrcode.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.OutputStream

/**
 * Basic interactive terminal: a long-lived shell with piped stdin/stdout (no PTY in v0.1,
 * so full-screen programs like vim/top are not supported).
 */
class ShellSession(private val env: LinuxEnv, private val projectDir: File) {
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private val buffer = StringBuilder()
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output
    val alive get() = process?.isAlive == true

    @Synchronized
    fun start() {
        if (alive) return
        val header = if (env.ready) "Alpine Linux ${LinuxEnv.ALPINE_VERSION} (proot) — /workspace\n"
        else "Android sh — ${projectDir.absolutePath}\n"
        append(header)
        val p = runCatching { env.processBuilder(projectDir, listOf("-i")).start() }
            .getOrElse { append("Failed to start shell: ${it.message}\n"); return }
        process = p
        stdin = p.outputStream
        Thread {
            runCatching {
                val r = p.inputStream.bufferedReader()
                val buf = CharArray(4096)
                while (true) {
                    val n = r.read(buf); if (n < 0) break
                    append(Ansi.strip(String(buf, 0, n)))
                }
            }
            append("\n[process exited]\n")
        }.apply { isDaemon = true; start() }
    }

    fun send(line: String) {
        if (!alive) start()
        append("$ $line\n")
        runCatching { stdin?.apply { write((line + "\n").toByteArray()); flush() } }
            .onFailure { append("write failed: ${it.message}\n") }
    }

    /** Clears the visible scrollback (the shell keeps running). */
    @Synchronized
    fun clear() { buffer.setLength(0); _output.value = "" }

    fun interrupt() {
        // Without a PTY we cannot send SIGINT to the foreground job; restart instead.
        restart()
    }

    fun restart() { stop(); buffer.setLength(0); start() }

    fun stop() { runCatching { process?.destroyForcibly() }; process = null }

    @Synchronized
    private fun append(s: String) {
        buffer.append(s)
        if (buffer.length > MAX) buffer.delete(0, buffer.length - MAX)
        _output.value = buffer.toString()
    }

    companion object { private const val MAX = 100_000 }
}
