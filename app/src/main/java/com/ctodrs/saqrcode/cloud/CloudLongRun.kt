package com.ctodrs.saqrcode.cloud

import kotlinx.coroutines.delay

/**
 * v0.1.73 · Long cloud runs.
 *
 * One cloud exec call is capped at 10 minutes. A long build or test run is started in the
 * background on the cloud machine, then polled with short calls until it ends, so the phone can
 * sleep or lose signal between polls without killing the job. Output streams line by line.
 */
object CloudLongRun {
    const val MAX_TOTAL_SEC = 40 * 60 // the sandbox itself lives 45 minutes

    fun jobId(seed: Long): String = "j" + kotlin.math.abs(seed).toString()

    fun startCommand(id: String, command: String): String {
        val q = "'" + command.replace("'", "'\\''") + "'"
        return "nohup sh -c $q > \"\$HOME/$id.log\" 2>&1 < /dev/null; echo \$? > \"\$HOME/$id.exit\"".let {
            "( $it ) > /dev/null 2>&1 &\necho started"
        }
    }

    fun pollCommand(id: String, fromLine: Int): String =
        "tail -n +${fromLine + 1} \"\$HOME/$id.log\" 2>/dev/null; echo \"__SAQR_EXIT__\$(cat \"\$HOME/$id.exit\" 2>/dev/null)\""

    /** Splits a poll result into new output lines and the exit code (null while running). */
    fun parsePoll(out: String): Pair<List<String>, Int?> {
        val lines = out.lines()
        val marker = lines.lastOrNull { it.startsWith("__SAQR_EXIT__") } ?: return lines.filter { it.isNotEmpty() } to null
        val body = lines.takeWhile { !it.startsWith("__SAQR_EXIT__") }.filter { it.isNotEmpty() }
        val code = marker.removePrefix("__SAQR_EXIT__").trim().toIntOrNull()
        return body to code
    }

    suspend fun run(
        sb: CloudSandbox,
        command: String,
        totalSec: Int = MAX_TOTAL_SEC,
        pollMs: Long = 5_000,
        now: () -> Long = System::currentTimeMillis,
        wait: suspend (Long) -> Unit = { delay(it) },
        onLine: (String) -> Unit = {},
    ): CloudExecResult {
        val started = now()
        val id = jobId(started)
        val s = sb.exec(startCommand(id, command), 60)
        if (s.exitCode != 0) return CloudExecResult(s.exitCode, s.output, false, now() - started)
        val all = StringBuilder()
        var seen = 0
        val cap = totalSec.coerceIn(10, MAX_TOTAL_SEC) * 1000L
        while (true) {
            wait(pollMs)
            val p = sb.exec(pollCommand(id, seen), 30)
            val (lines, code) = parsePoll(p.output)
            lines.forEach { all.appendLine(it); onLine(it) }
            seen += lines.size
            if (code != null) return CloudExecResult(code, all.toString().trimEnd(), false, now() - started)
            if (now() - started > cap) {
                sb.exec("pkill -f $id; true", 20)
                return CloudExecResult(124, all.toString().trimEnd(), true, now() - started)
            }
        }
    }
}
