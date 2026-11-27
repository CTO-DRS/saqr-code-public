package com.ctodrs.saqrcode.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * v0.1.30: a local-only crash recorder. Nothing is sent anywhere: the report is written to app storage,
 * and the user decides whether to share it (Settings, About). Secrets are scrubbed before writing.
 */
object CrashLog {
    const val KEEP = 5
    const val MAX_CHARS = 8_000
    private const val DIR = "crash"

    /** A readable, redacted report. Pure, so it is JVM-testable. */
    fun format(thread: String, t: Throwable, version: String, device: String, sdk: Int, nowMs: Long): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(nowMs))
        val trace = StringBuilder()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 4) {
            trace.append(if (depth == 0) "" else "Caused by: ").append(cur.javaClass.name).append(": ").append(cur.message ?: "").append('\n')
            cur.stackTrace.take(25).forEach { trace.append("    at ").append(it).append('\n') }
            cur = cur.cause?.takeIf { it !== cur }
            depth++
        }
        val body = "Saqr Code $version · $device · Android API $sdk\n$stamp · thread: $thread\n\n$trace"
        // The report is shared outside the app: use the full redactor (provider key shapes, Bearer, key=value), not only git tokens.
        return com.ctodrs.saqrcode.agent.AuditLog.redact(body).take(MAX_CHARS)
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, DIR).apply { mkdirs() }

    /** Writes [text] as a new report and prunes to the newest [KEEP]. */
    fun record(dir: File, text: String, nowMs: Long): File {
        dir.mkdirs()
        val f = File(dir, "crash-$nowMs.txt").apply { writeText(text) }
        dir.listFiles { x -> x.name.startsWith("crash-") }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        return f
    }

    /** The newest report text, or null when there is none. */
    fun latest(dir: File): String? =
        dir.listFiles { x -> x.name.startsWith("crash-") }?.maxByOrNull { it.name }?.readText()

    fun clear(dir: File) { dir.listFiles { x -> x.name.startsWith("crash-") }?.forEach { it.delete() } }

    fun latest(ctx: Context): String? = latest(dir(ctx))
    fun clear(ctx: Context) = clear(dir(ctx))

    /** Chains in front of the existing handler: records, then lets the system do its normal crash handling. */
    fun install(ctx: Context, version: String) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e ->
            runCatching {
                record(dir(app), format(th.name, e, version, android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT, System.currentTimeMillis()), System.currentTimeMillis())
            }
            previous?.uncaughtException(th, e)
        }
    }
}
