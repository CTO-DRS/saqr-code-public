package com.ctodrs.saqrcode.device

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Where a device command can run: the phone's own shell, Root, Shizuku, a wireless ADB session. */
interface DeviceEngine {
    val id: String
    /** Cheap, side-effect free check, run before every operation. */
    suspend fun available(): Boolean
    suspend fun exec(command: String, timeoutMs: Long): CommandResult
}

/** The app's own unprivileged shell. Always present; sees only what the app itself can see. */
class LocalShellEngine : DeviceEngine {
    override val id = "local"
    override suspend fun available() = true
    override suspend fun exec(command: String, timeoutMs: Long): CommandResult = runProcess(id, arrayOf("sh", "-c", command), command, timeoutMs)
}

/** Root through `su`, only when the device really has it. Never used unless asked for. */
class RootEngine : DeviceEngine {
    override val id = "root"
    override suspend fun available(): Boolean = runCatching { runProcess(id, arrayOf("su", "-c", "id"), "id", 3000).let { it.success && it.output.contains("uid=0") } }.getOrDefault(false)
    override suspend fun exec(command: String, timeoutMs: Long): CommandResult = runProcess(id, arrayOf("su", "-c", command), command, timeoutMs)
}

internal suspend fun runProcess(engine: String, argv: Array<String>, shown: String, timeoutMs: Long): CommandResult =
    runStarted(engine, shown, timeoutMs) { ProcessBuilder(*argv).redirectErrorStream(false).start() }

internal suspend fun runStarted(engine: String, shown: String, timeoutMs: Long, start: () -> Process): CommandResult = withContext(Dispatchers.IO) {
    val t0 = System.currentTimeMillis()
    try {
        val p = start()
        val out = StringBuilder(); val err = StringBuilder()
        val ro = Thread { runCatching { p.inputStream.bufferedReader().forEachLine { if (out.length < 200_000) out.appendLine(it) } } }.also { it.start() }
        val re = Thread { runCatching { p.errorStream.bufferedReader().forEachLine { if (err.length < 50_000) err.appendLine(it) } } }.also { it.start() }
        val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!done) { p.destroyForcibly(); p.waitFor(1, TimeUnit.SECONDS) }
        ro.join(500); re.join(500)
        CommandResult(shown, engine, out.toString().trimEnd(), err.toString().trimEnd(), if (done) p.exitValue() else -1, System.currentTimeMillis() - t0, timedOut = !done)
    } catch (e: Exception) {
        CommandResult(shown, engine, "", e.message ?: e.toString(), -1, System.currentTimeMillis() - t0)
    }
}

/** An append-only JSON-lines record of every command: what, where, risk, decision, outcome. Never stores secrets. */
class DeviceAuditLog(private val file: File, private val clock: () -> Long = { System.currentTimeMillis() }) {
    @Synchronized fun record(command: String, engine: String, risk: AdbRisk, decision: String, result: CommandResult?) {
        runCatching {
            file.parentFile?.mkdirs()
            val o = JSONObject().put("t", clock()).put("cmd", redact(command).take(500)).put("engine", engine).put("risk", risk.name).put("decision", decision)
            if (result != null) o.put("exit", result.exitCode).put("ms", result.durationMs).put("timedOut", result.timedOut)
            file.appendText(o.toString() + "\n")
        }
    }

    fun entries(limit: Int = 200): List<JSONObject> =
        runCatching { file.readLines().takeLast(limit).mapNotNull { runCatching { JSONObject(it) }.getOrNull() } }.getOrDefault(emptyList())

    companion object {
        private val SECRET = Regex("""(?i)(password|passwd|token|secret|pairing[-_ ]?code|api[-_ ]?key)(=|\s+)\S+""")
        fun redact(s: String) = SECRET.replace(s) { it.groupValues[1] + "=***" }
    }
}

/**
 * One front door for device commands. It classifies the command, asks the user for anything
 * above SAFE (the caller supplies [confirm]; the model can never approve its own command),
 * picks the engine, enforces a timeout and a concurrency cap, and writes the audit log.
 */
class CommandEngine(
    private val engines: List<DeviceEngine>,
    private val audit: DeviceAuditLog,
    maxParallel: Int = 3,
    private val confirm: suspend (command: String, verdict: AdbRiskClassifier.Verdict) -> Boolean,
) {
    private val gate = Semaphore(maxParallel)

    suspend fun run(command: String, prefer: String? = null, timeoutMs: Long = 30_000): CommandResult {
        val verdict = AdbRiskClassifier.classify(command)
        val engine = pick(prefer)
            ?: return refuse(command, prefer ?: "none", verdict, "no engine available").also { audit.record(command, prefer ?: "none", verdict.risk, "no_engine", it) }
        // SAFE runs as is; SENSITIVE and DANGEROUS each need an explicit yes, asked fresh every time.
        if (verdict.risk != AdbRisk.SAFE && !confirm(command, verdict)) {
            return refuse(command, engine.id, verdict, "the user declined").also { audit.record(command, engine.id, verdict.risk, "declined", it) }
        }
        val res = gate.withPermit { engine.exec(command, timeoutMs.coerceIn(1_000, 600_000)) }
        audit.record(command, engine.id, verdict.risk, if (verdict.risk == AdbRisk.SAFE) "auto" else "approved", res)
        return res
    }

    private suspend fun pick(prefer: String?): DeviceEngine? {
        if (prefer != null) return engines.firstOrNull { it.id == prefer }?.takeIf { it.available() }
        // Default is the least privileged engine that works, never Root unless it was asked for.
        return engines.firstOrNull { it.id != "root" && it.available() }
    }

    private fun refuse(command: String, engine: String, v: AdbRiskClassifier.Verdict, why: String) =
        CommandResult(command, engine, "", "", -1, 0, refused = why + (if (v.reasons.isNotEmpty()) " (" + v.reasons.joinToString("; ") + ")" else ""))
}
