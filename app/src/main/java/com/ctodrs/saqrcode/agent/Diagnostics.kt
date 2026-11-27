package com.ctodrs.saqrcode.agent

/**
 * v0.1.21: pulls "file:line: message" diagnostics out of raw compiler / linter / test output so the
 * verifier can hand the coder a short, exact list ahead of the raw tail. Pure and Android-free.
 */
object Diagnostics {
    data class Diag(val file: String, val line: Int, val message: String) {
        override fun toString() = "$file:$line: $message"
    }

    // kotlinc:   e: file:///p/A.kt:12:5 Unresolved reference: x   |   e: /p/A.kt: (12, 5): msg
    private val kotlinUrl = Regex("""^e: (?:file://)?(\S+?\.kts?):(\d+)(?::\d+)?:?\s+(.+)$""")
    private val kotlinParen = Regex("""^e: (\S+?\.kts?): \((\d+), \d+\): (.+)$""")
    // gcc/clang/javac/eslint-unix/go/rustc-short:  path:12:5: error: msg   |   path:12: error: msg
    private val unix = Regex("""^(\S+?\.[A-Za-z0-9]{1,5}):(\d+)(?::\d+)?:\s*(?:(?:fatal )?(?:error|Error|warning)\b:?\s*)?(.+)$""")
    // tsc:  src/a.ts(12,5): error TS2304: msg
    private val tsc = Regex("""^(\S+?\.[tj]sx?)\((\d+),\d+\):\s*(?:error\s+)?(.+)$""")
    // python:  File "x.py", line 12, in f   (the message is the exception line that follows)
    private val pyFrame = Regex("""^\s*File "([^"]+)", line (\d+)""")
    private val pyExc = Regex("""^([A-Za-z_][\w.]*(?:Error|Exception|Warning)):?\s*(.*)$""")

    private fun lineDiag(l: String): Diag? {
        kotlinParen.find(l)?.let { return Diag(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].trim()) }
        kotlinUrl.find(l)?.let { return Diag(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].trim()) }
        tsc.find(l)?.let { return Diag(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].trim()) }
        unix.find(l)?.let { m ->
            val msg = m.groupValues[3].trim()
            if (msg.isNotEmpty()) return Diag(m.groupValues[1], m.groupValues[2].toInt(), msg)
        }
        return null
    }

    fun parse(output: String, limit: Int = 25): List<Diag> {
        val out = LinkedHashSet<Diag>()
        var lastFrame: Pair<String, Int>? = null
        for (raw in output.lineSequence()) {
            val l = raw.trimEnd()
            if (l.isBlank()) continue
            val d = lineDiag(l)
            if (d != null) { out += d }
            else {
                val fr = pyFrame.find(l)
                if (fr != null) lastFrame = fr.groupValues[1] to fr.groupValues[2].toInt()
                else {
                    val ex = pyExc.find(l)
                    val f = lastFrame
                    if (ex != null && f != null) {
                        out += Diag(f.first, f.second, (ex.groupValues[1] + ": " + ex.groupValues[2]).trim().trimEnd(':'))
                        lastFrame = null
                    }
                }
            }
            if (out.size >= limit) break
        }
        return out.toList()
    }

    /** A compact block for the verifier message, or "" when nothing structured was found. */
    fun digest(output: String?, limit: Int = 15): String {
        if (output.isNullOrBlank()) return ""
        val d = parse(output, limit)
        if (d.isEmpty()) return ""
        val files = d.map { it.file }.distinct()
        return buildString {
            appendLine("${d.size} diagnostic(s) in ${files.size} file(s):")
            d.forEach { appendLine("- $it") }
        }.trimEnd()
    }
}
