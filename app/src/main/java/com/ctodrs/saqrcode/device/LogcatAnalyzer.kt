package com.ctodrs.saqrcode.device

/**
 * v0.1.76 · Reads `logcat -d` text and finds crashes and not-responding events, in plain terms.
 * Pure logic. Command: [COMMAND] (read-only, so it never asks for confirmation).
 */
object LogcatAnalyzer {
    const val COMMAND = "logcat -d -b crash,main -t 400"

    enum class Kind { CRASH, ANR }
    data class Finding(val kind: Kind, val process: String, val summary: String, val line: Int)

    private val fatal = Regex("""FATAL EXCEPTION""")
    private val proc = Regex("""Process:\s*([\w.]+)""")
    private val exc = Regex("""((?:[\w$]+\.)+[\w$]*(?:Exception|Error)\b.*)""")
    private val anr = Regex("""ANR in ([\w.]+)""")

    fun analyze(log: String): List<Finding> {
        val lines = log.lines()
        val out = mutableListOf<Finding>()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            val a = anr.find(l)
            if (a != null) {
                out += Finding(Kind.ANR, a.groupValues[1], "Not responding", i + 1)
            } else if (fatal.containsMatchIn(l)) {
                var p = ""; var s = ""
                for (j in i + 1..minOf(i + 8, lines.size - 1)) {
                    if (p.isEmpty()) proc.find(lines[j])?.let { p = it.groupValues[1] }
                    if (s.isEmpty()) exc.find(lines[j])?.let { s = it.groupValues[1].trim().take(160) }
                }
                out += Finding(Kind.CRASH, p.ifEmpty { "unknown" }, s.ifEmpty { "crash" }, i + 1)
            }
            i++
        }
        return out
    }

    /** One short line per finding, newest last. */
    fun describe(findings: List<Finding>): String =
        if (findings.isEmpty()) "No crashes or freezes in the recent log."
        else findings.takeLast(10).joinToString("\n") { (if (it.kind == Kind.ANR) "FREEZE " else "CRASH ") + it.process + ": " + it.summary }
}
