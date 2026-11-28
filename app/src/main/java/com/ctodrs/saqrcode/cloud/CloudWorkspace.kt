package com.ctodrs.saqrcode.cloud

import java.io.File
import java.security.MessageDigest

/**
 * v0.1.65 · One chat's cloud workspace. Opens a throwaway sandbox on first use, uploads the
 * project (never secrets), runs commands, and records every second against the daily cap.
 * All results are plain text for the model, with secrets redacted and output tailed.
 */
class CloudWorkspace(
    private val provider: CloudProvider,
    private val ledger: CloudLedger,
    private val limits: CloudLimits,
    private val projectDir: File,
    private val redact: (String) -> String = { it },
    private val clock: () -> Long = System::currentTimeMillis,
    /** v0.1.78: the user's saved cloud secrets, injected as environment variables for each command. */
    private val env: () -> Map<String, String> = { emptyMap() },
) {
    private var sandbox: CloudSandbox? = null
    private var uploadedHash: String? = null
    private var lastUsedAt = 0L

    val isOpen get() = sandbox != null

    fun idleExpired(): Boolean = sandbox != null && clock() - lastUsedAt > limits.idleKillSec * 1000L

    suspend fun sync(): String = guarded { sb -> syncInto(sb) }

    suspend fun run(command: String, timeoutSec: Int?, onLine: (String) -> Unit = {}): String = guarded { sb ->
        val note = syncInto(sb)
        val t = minOf(limits.commandTimeout(timeoutSec).toLong(), ledger.remainingSeconds(limits)).toInt().coerceAtLeast(1)
        val full = ProjectSecrets.envPrefix(env()) + command
        val r = if (t > 540) CloudLongRun.run(sb, full, t, onLine = onLine) else sb.exec(full, t, onLine)
        format(r) + (if (note.startsWith("Note")) "\n$note" else "")
    }

    /** Detects the project's test command from its files and runs it. */
    suspend fun test(timeoutSec: Int?, onLine: (String) -> Unit = {}): String {
        val names = projectDir.walkTopDown().maxDepth(2).filter { it.isFile }.map { it.name }.toSet()
        val cmd = CloudTests.detect(names) ?: return "Error: no test setup found (looked for gradlew, package.json with a test script, pytest files). Use cloud_run with the exact command."
        return run(cmd, timeoutSec ?: 600, onLine)
    }

    suspend fun close() {
        val sb = sandbox ?: return
        sandbox = null
        uploadedHash = null
        runCatching { sb.close() }
    }

    private suspend fun guarded(block: suspend (CloudSandbox) -> String): String {
        if (ledger.remainingSeconds(limits) <= 0) {
            return "Error: the daily cloud limit is used up (${limits.dailyMinutes} min). Continue with local tools, or ask the user to raise the limit in Settings."
        }
        val start = clock()
        return try {
            val sb = sandbox ?: provider.open().also { sandbox = it }
            val out = block(sb)
            lastUsedAt = clock()
            out
        } catch (e: CloudException) {
            close()
            "Error: ${redact(e.message ?: "cloud workspace failed")}"
        } finally {
            ledger.add(((clock() - start) + 999) / 1000)
        }
    }

    private suspend fun syncInto(sb: CloudSandbox): String {
        val snap = CloudSnapshot.collect(projectDir)
        val h = hash(snap.files)
        if (h == uploadedHash) return "Already in sync."
        sb.upload(snap.files)
        uploadedHash = h
        val extra = buildString {
            if (snap.skipped.isNotEmpty()) append("${snap.skipped.size} file(s) kept off the cloud (secrets, build output or too large).")
            if (snap.truncated) append(" Project exceeds the upload limit, so some files are missing there.")
        }.trim()
        return if (snap.truncated) "Note: $extra" else "Uploaded ${snap.files.size} files. $extra".trim()
    }

    private fun hash(files: List<CloudFile>): String {
        val md = MessageDigest.getInstance("SHA-256")
        files.forEach { md.update(it.path.toByteArray()); md.update(0); md.update(it.bytes) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun format(r: CloudExecResult): String {
        val head = (if (r.timedOut) "[cloud · timed out]" else "[cloud · exit ${r.exitCode}]") + " ${r.millis / 1000}s\n"
        val clean = redact(r.output).ifBlank { "(no output)" }
        val body = if (clean.length > MAX_OUTPUT) "…[earlier output cut]\n" + clean.takeLast(MAX_OUTPUT) else clean
        return head + body
    }

    companion object { const val MAX_OUTPUT = 6_000 }
}

object CloudTests {
    /** File names in the project's top two levels → a test command, or null when nothing is recognisable. */
    fun detect(names: Set<String>): String? = when {
        "gradlew" in names -> "chmod +x gradlew && ./gradlew test --console=plain --no-daemon"
        "package.json" in names -> "npm install --no-audit --no-fund --silent && npm test --silent"
        "requirements.txt" in names || "pyproject.toml" in names || names.any { it.startsWith("test_") && it.endsWith(".py") } ->
            "(test -f requirements.txt && pip install -q -r requirements.txt); python -m pytest -q"
        "pubspec.yaml" in names -> "flutter test"
        else -> null
    }
}
