package com.ctodrs.saqrcode.agent

/** Minimal Server-Sent-Events line parser (pure, unit tested). */
class SseParser(private val onEvent: (event: String?, data: String) -> Unit) {
    private var event: String? = null
    private val data = StringBuilder()

    fun feed(line: String) {
        if (line.isEmpty()) { dispatch(); return }
        if (line.startsWith(":")) return
        val idx = line.indexOf(':')
        val field = if (idx < 0) line else line.substring(0, idx)
        var value = if (idx < 0) "" else line.substring(idx + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> { if (data.isNotEmpty()) data.append('\n'); data.append(value) }
        }
    }

    fun finish() = dispatch()

    private fun dispatch() {
        if (data.isNotEmpty()) onEvent(event, data.toString())
        event = null
        data.setLength(0)
    }
}
