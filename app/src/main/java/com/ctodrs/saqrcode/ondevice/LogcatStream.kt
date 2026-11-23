package com.ctodrs.saqrcode.ondevice

/**
 * v0.8 · مراقب أعطال التطبيق المبني · Crash monitor for the app built on the phone.
 *
 * After the built APK is installed, its failures surface in the same Build tab: `logcat -d`
 * (a dump, no permission needed for the app's own logs on API 26+ … but for other apps' logs
 * a non-root shell sees only its own — so the useful slice comes from `logcat -b crash`, which
 * the system writes readable summaries of) is parsed for FATAL EXCEPTION blocks of the built
 * package and shown as friendly lines. Pure parsing lives in [LogcatStream] and is unit-tested
 * in V08FeaturesTest.
 */
object LogcatStream {

    data class Crash(val packageName: String, val exception: String, val message: String, val firstFrame: String) {
        fun headline() = "$exception: $message".take(160)
    }

    /**
     * Extracts FATAL EXCEPTION blocks from logcat output and keeps those whose stack mentions
     * [pkg]. Returns at most [max] newest crashes (blocks appear oldest-first in logcat, so the
     * last ones win).
     */
    fun crashes(logcatOutput: String, pkg: String, max: Int = 5): List<Crash> {
        val out = ArrayList<Crash>()
        val lines = logcatOutput.lines()
        var i = 0
        while (i < lines.size) {
            if (!lines[i].contains(FATAL)) { i++; continue }
            // a block: FATAL EXCEPTION line, then process line, then exception + frames
            var j = i + 1
            val block = ArrayList<String>()
            while (j < lines.size && !lines[j].contains(FATAL) && j - i < 120) {
                block += lines[j]; j++
            }
            crashesFromBlock(block, pkg)?.let { out += it }
            i = j
        }
        return out.takeLast(max)
    }

    private fun crashesFromBlock(block: List<String>, pkg: String): Crash? {
        val processLine = block.firstOrNull { it.contains("Process:") } ?: return null
        val process = processLine.substringAfter("Process:").trim().substringBefore(",").trim()
        if (pkg.isNotEmpty() && process != pkg && !process.startsWith(pkg)) return null
        // strip the logcat prefix ("10-01 12:00:00.000  1234  1234 E AndroidRuntime: ") from every line
        fun strip(l: String): String {
            val m = Regex(""".*AndroidRuntime:\s?""").find(l) ?: return l.trim()
            return l.substring(m.value.length).trim()
        }
        val bodyLines = block.map(::strip).filter { it.isNotBlank() && !it.startsWith("Process:") }
        val body = bodyLines.firstOrNull() ?: return null
        val exception = body.substringBefore(":").trim().ifEmpty { "Exception" }
        val message = if (body.contains(":")) body.substringAfter(":").trim() else ""
        val frame = bodyLines.firstOrNull { it.startsWith("at ") } ?: ""
        return Crash(process, exception, message, frame)
    }

    /** Reads the crash buffer (and the main tail as fallback) through Runtime.exec. */
    fun dumpCommand(): String = "logcat -d -b crash -t 400 2>/dev/null || logcat -d -t 400"

    /**
     * The applicationId of the built project, from its app/build.gradle.kts
     * ("applicationId = "com.x.y""), or "" when undetectable (then any crash matches).
     */
    fun guessPackage(buildGradle: String): String {
        val m = Regex("""applicationId\s*=\s*["']([A-Za-z0-9_.]+)["']""").find(buildGradle) ?: return ""
        return m.groupValues[1]
    }

    /** One short friendly line per crash for the UI. */
    fun summarize(crashes: List<Crash>, max: Int = 4): List<String> = crashes.takeLast(max)
        .map { "${it.headline()}  ·  ${it.firstFrame.take(120)}" }

    const val FATAL = "FATAL EXCEPTION"
}
