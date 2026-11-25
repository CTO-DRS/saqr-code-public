package com.ctodrs.saqrcode.git

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * v0.14.2 «أجنحة مفتوحة · البناء السحابي الاختياري (BYOC)» · Cloud build (bring your own compute).
 *
 * For weak phones: the build runs on **GitHub Actions inside the user's own repository** —
 * the user's token and the user's repo, never a Saqr-side cloud. Saqr installs a tiny
 * workflow (`.github/workflows/saqr-build.yml`), dispatches it on the project's default
 * branch, watches the run with a live log tail, downloads the APK artifact and hands it
 * to the in-app installer. The debug APK installs directly (no signing setup needed).
 *
 * Everything protocol-level is pure and unit-tested in V0142FeaturesTest; the network
 * half lives in [CloudBuildClient] (thin, same discipline as GitHubApi) and the turn glue
 * in [CloudBuild] (a controller with StateFlows, like OnDeviceBuild).
 */
object CloudBuildKit {

    const val WORKFLOW_PATH = ".github/workflows/saqr-build.yml"
    const val ARTIFACT_NAME = "saqr-apk"
    const val TIMEOUT_MINUTES = 20L

    // ---------------------------------------------------------------- workflow yaml (pure)

    /**
     * The build workflow: JDK 17 (the templates compile with VERSION_17), the Android SDK,
     * Gradle installed by the action (device templates ship **no wrapper**), `assembleDebug`
     * and an artifact upload. `workflow_dispatch` only — the user triggers builds from Saqr.
     */
    fun workflowYaml(gradleVersion: String = "8.11.1"): String = """
        name: Saqr Build

        on:
          workflow_dispatch:

        permissions:
          contents: read

        jobs:
          build:
            runs-on: ubuntu-latest
            steps:
              - name: Checkout
                uses: actions/checkout@v4

              - name: Set up JDK 17
                uses: actions/setup-java@v4
                with:
                  distribution: temurin
                  java-version: '17'

              - name: Set up Android SDK
                uses: android-actions/setup-android@v3

              - name: Set up Gradle
                uses: gradle/actions/setup-gradle@v4
                with:
                  gradle-version: '$gradleVersion'

              - name: Build debug APK
                run: gradle assembleDebug --no-daemon --stacktrace

              - name: Upload APK
                uses: actions/upload-artifact@v4
                with:
                  name: $ARTIFACT_NAME
                  path: app/build/outputs/apk/debug/*.apk
                  if-no-files-found: error
    """.trimIndent()

    // ---------------------------------------------------------------- urls and bodies (pure)

    /** `https://github.com/o/r.git` / `git@github.com:o/r.git` → `(o, r)`; null when not GitHub. */
    fun ownerRepo(remoteUrl: String): Pair<String, String>? {
        val url = remoteUrl.trim().removeSuffix("/")
        val path = when {
            url.startsWith("https://github.com/") -> url.removePrefix("https://github.com/")
            url.startsWith("http://github.com/") -> url.removePrefix("http://github.com/")
            url.startsWith("git@github.com:") -> url.removePrefix("git@github.com:")
            else -> return null
        }.removeSuffix(".git")
        val parts = path.split('/')
        if (parts.size < 2) return null
        val owner = parts[0].trim()
        val repo = parts[1].trim()
        if (owner.isEmpty() || repo.isEmpty()) return null
        return owner to repo
    }

    fun contentsGetPath(owner: String, repo: String, branch: String): String =
        "/repos/$owner/$repo/contents/$WORKFLOW_PATH?ref=" + enc(branch)

    fun contentsPutPath(owner: String, repo: String): String = "/repos/$owner/$repo/contents/$WORKFLOW_PATH"

    fun repoPath(owner: String, repo: String): String = "/repos/$owner/$repo"

    fun dispatchPath(owner: String, repo: String): String =
        "/repos/$owner/$repo/actions/workflows/saqr-build.yml/dispatches"

    fun dispatchBody(branch: String): String = JSONObject().put("ref", branch).toString()

    fun runsPath(owner: String, repo: String): String =
        "/repos/$owner/$repo/actions/workflows/saqr-build.yml/runs?per_page=10"

    fun jobsPath(owner: String, repo: String, runId: Long): String =
        "/repos/$owner/$repo/actions/runs/$runId/jobs?per_page=10"

    fun jobLogPath(owner: String, repo: String, jobId: Long): String =
        "/repos/$owner/$repo/actions/jobs/$jobId/logs"

    fun artifactsPath(owner: String, repo: String, runId: Long): String =
        "/repos/$owner/$repo/actions/runs/$runId/artifacts?per_page=20"

    fun artifactZipPath(owner: String, repo: String, artifactId: Long): String =
        "/repos/$owner/$repo/actions/artifacts/$artifactId/zip"

    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")

    // ---------------------------------------------------------------- contents codec (pure)

    /** The contents API carries file bodies as base64 (with embedded line breaks). */
    fun encodeContent(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    fun decodeContent(b64: String?): String? = runCatching {
        // Strip the embedded line breaks first, then decode strictly (garbage is refused).
        Base64.getDecoder().decode(b64?.replace("\\s".toRegex(), "") ?: return null).toString(Charsets.UTF_8)
    }.getOrNull()

    // ---------------------------------------------------------------- run/job parsing (pure)

    data class Run(val id: Long, val number: Int, val status: String, val conclusion: String?, val htmlUrl: String)

    fun parseRuns(json: JSONObject): List<Run> {
        val arr = json.optJSONArray("workflow_runs") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Run(o.optLong("id"), o.optInt("run_number"), o.optString("status"),
                o.optString("conclusion").ifBlank { null }, o.optString("html_url"))
        }
    }

    /** GitHub returns runs newest-first; defensively sort by id so the newest is ours. */
    fun latestRun(runs: List<Run>): Run? = runs.maxByOrNull { it.id }

    data class Job(val id: Long, val name: String, val status: String, val conclusion: String?)

    fun parseJobs(json: JSONObject): List<Job> {
        val arr = json.optJSONArray("jobs") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Job(o.optLong("id"), o.optString("name"), o.optString("status"),
                o.optString("conclusion").ifBlank { null })
        }
    }

    enum class State { QUEUED, RUNNING, SUCCESS, FAILURE, CANCELLED }

    /** Run status + conclusion → one stable state (conclusion only counts once completed). */
    fun stateOf(status: String?, conclusion: String?): State = when {
        conclusion == null -> when (status) {
            "queued", "waiting", "pending" -> State.QUEUED
            "in_progress" -> State.RUNNING
            else -> State.QUEUED
        }
        conclusion == "success" -> State.SUCCESS
        conclusion == "cancelled" || conclusion == "timed_out" -> State.CANCELLED
        else -> State.FAILURE
    }

    // ---------------------------------------------------------------- artifacts (pure)

    data class ApkArtifact(val id: Long, val name: String, val sizeBytes: Long)

    /** The APK artifact of a finished run — skips expired entries; null when none. */
    fun pickApk(json: JSONObject): ApkArtifact? {
        val arr = json.optJSONArray("artifacts") ?: return null
        var fallback: ApkArtifact? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("expired")) continue
            val a = ApkArtifact(o.optLong("id"), o.optString("name"), o.optLong("size_in_bytes"))
            if (a.name.equals(ARTIFACT_NAME, ignoreCase = true) || a.name.contains("apk", ignoreCase = true)) return a
            if (fallback == null) fallback = a
        }
        return fallback
    }

    // ---------------------------------------------------------------- logs (pure)

    /** The live log shows the last [n] lines of the job log. */
    fun tailLines(log: String, n: Int = 14): List<String> =
        log.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.takeLast(n)

    private val ERROR = Regex("(?i)(error|failure|failed|✗|e: |exception)")

    /** The lines that explain a failed build (last [max] of them). */
    fun errorLines(log: String, max: Int = 6): List<String> =
        log.lines().filter { ERROR.containsMatchIn(it) }.map { it.trimEnd() }.filter { it.isNotBlank() }.takeLast(max)

    fun timedOut(startedAtMs: Long, nowMs: Long): Boolean =
        nowMs - startedAtMs > TIMEOUT_MINUTES * 60_000L

    // ---------------------------------------------------------------- unzip (pure-ish, file IO)

    /** Extracts the first `*.apk` entry of the artifact zip into [destDir] (path-sanitized). */
    fun unzipApk(zip: File, destDir: File): File? = runCatching {
        destDir.mkdirs()
        ZipInputStream(FileInputStream(zip)).use { z ->
            var found: File? = null
            while (found == null) {
                val e = z.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                if (!e.isDirectory && name.endsWith(".apk") && !name.contains("..")) {
                    val out = File(destDir, name)
                    out.outputStream().use { z.copyTo(it) }
                    found = out
                }
                z.closeEntry()
            }
            found
        }
    }.getOrNull()
}

/**
 * v0.14.2 · the network half of the cloud build — same discipline as [GitHubApi]: the token
 * only ever goes into the Authorization header, errors surface as [GhException] with the
 * API's own message (redacted), and binary artifact downloads write straight to a file.
 */
class CloudBuildClient(
    private val token: String,
    private val baseUrl: String = "https://api.github.com",
    private val http: OkHttpClient = GitHubApi.defaultClient,
) {
    private val JSON = "application/json".toMediaType()

    /** The repository's default branch — dispatch and contents calls need it. */
    fun defaultBranch(owner: String, repo: String): String {
        val o = JSONObject(get(CloudBuildKit.repoPath(owner, repo)))
        return o.optString("default_branch", "main").ifBlank { "main" }
    }

    /** The installed workflow's content, or null when it is not installed yet (404). */
    fun getWorkflowFile(owner: String, repo: String, branch: String): String? = runCatching {
        val o = JSONObject(get(CloudBuildKit.contentsGetPath(owner, repo, branch)))
        CloudBuildKit.decodeContent(o.optString("content"))
    }.getOrElse { e -> if (e is GhException && e.code == 404) null else throw e }

    /** Creates or updates the workflow file; true on success (200/201). */
    fun putWorkflowFile(owner: String, repo: String, branch: String, yaml: String, existingSha: String? = null): Boolean {
        val body = JSONObject()
            .put("message", "ci: Saqr build workflow")
            .put("content", CloudBuildKit.encodeContent(yaml))
            .put("branch", branch)
        if (existingSha != null) body.put("sha", existingSha)
        val resp = post(CloudBuildKit.contentsPutPath(owner, repo), body.toString())
        return resp != null
    }

    /** Dispatches the workflow on [branch] — the API answers 204 No Content on success. */
    fun dispatch(owner: String, repo: String, branch: String): Boolean {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + CloudBuildKit.dispatchPath(owner, repo))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SaqrCode-Android")
            .post(CloudBuildKit.dispatchBody(branch).toRequestBody(JSON))
        http.newCall(b.build()).execute().use { r ->
            if (r.code == 204) return true
            throw GhException(r.code, "GitHub: dispatch failed (HTTP ${r.code})")
        }
    }

    fun runs(owner: String, repo: String): JSONObject = JSONObject(get(CloudBuildKit.runsPath(owner, repo)))

    fun jobs(owner: String, repo: String, runId: Long): JSONObject = JSONObject(get(CloudBuildKit.jobsPath(owner, repo, runId)))

    /** The (plain-text) log of one job — fetched repeatedly for the live tail. */
    fun jobLog(owner: String, repo: String, jobId: Long): String = get(CloudBuildKit.jobLogPath(owner, repo, jobId))

    fun artifacts(owner: String, repo: String, runId: Long): JSONObject = JSONObject(get(CloudBuildKit.artifactsPath(owner, repo, runId)))

    /** Downloads the artifact zip (the API 302s to a signed object URL; OkHttp follows). */
    fun downloadArtifact(owner: String, repo: String, artifactId: Long, dest: File): File {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + CloudBuildKit.artifactZipPath(owner, repo, artifactId))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SaqrCode-Android")
            .get()
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw GhException(r.code, "GitHub: artifact download failed (HTTP ${r.code})")
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            return dest
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun get(path: String): String {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SaqrCode-Android")
            .get()
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw GhException(r.code, "GitHub: HTTP ${r.code}")
            return text
        }
    }

    private fun post(path: String, body: String): String? {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SaqrCode-Android")
            .post(body.toRequestBody(JSON))
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw GhException(r.code, "GitHub: HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }
}

/**
 * v0.14.2 · the controller behind the Build tab's cloud card: installs the workflow if it is
 * missing, dispatches the build, polls the run with a live log tail, downloads the APK
 * artifact and hands it to the installer. Cancel-safe (the polling loop checks a flag) and
 * time-boxed ([CloudBuildKit.TIMEOUT_MINUTES]).
 */
class CloudBuild(
    private val context: Context,
    private val remoteUrl: () -> String?,
    private val token: () -> String?,
) {
    enum class Phase { IDLE, PREPARING, RUNNING, DOWNLOADING, READY, FAILED }

    /** One-shot outcome keys the UI maps to localized strings. */
    enum class Msg { NEEDS_REMOTE, NEEDS_TOKEN, WF_INSTALLED, DISPATCHED, TIMEOUT, NO_APK, CANCELLED, FAILED }

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase

    private val _msg = MutableStateFlow<Msg?>(null)
    val msg: StateFlow<Msg?> = _msg

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val _apk = MutableStateFlow<File?>(null)
    val apk: StateFlow<File?> = _apk

    private val _runUrl = MutableStateFlow<String?>(null)
    val runUrl: StateFlow<String?> = _runUrl

    @Volatile private var cancelled = false

    /** Starts the whole flow on [scope]; a no-op while a build is already in flight. */
    fun start(scope: CoroutineScope) {
        if (_phase.value in setOf(Phase.PREPARING, Phase.RUNNING, Phase.DOWNLOADING)) return
        cancelled = false
        _msg.value = null
        _log.value = emptyList()
        _apk.value = null
        _runUrl.value = null
        scope.launch(Dispatchers.IO) { run() }
    }

    /** Stops the polling loop at the next check (in-flight HTTP calls finish quietly). */
    fun cancel() {
        cancelled = true
    }

    /** Same installer hand-off as the on-device build (FileProvider + ACTION_INSTALL_PACKAGE). */
    fun installIntent(): Intent? {
        val apkFile = _apk.value ?: return null
        val share = File(context.cacheDir, "apk").apply { mkdirs() }
        val tmp = File(share, apkFile.name)
        apkFile.copyTo(tmp, overwrite = true)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", tmp)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun appendLog(line: String) {
        _log.value = _log.value + line
    }

    private fun fail(m: Msg, logLine: String? = null) {
        logLine?.let { appendLog(it) }
        _msg.value = m
        _phase.value = Phase.FAILED
    }

    private suspend fun run() {
        val remote = remoteUrl() ?: return fail(Msg.NEEDS_REMOTE)
        val (owner, repo) = CloudBuildKit.ownerRepo(remote) ?: return fail(Msg.NEEDS_REMOTE)
        val tok = token() ?: return fail(Msg.NEEDS_TOKEN)
        val api = CloudBuildClient(tok)
        _phase.value = Phase.PREPARING
        try {
            val branch = api.defaultBranch(owner, repo)
            if (api.getWorkflowFile(owner, repo, branch) == null) {
                api.putWorkflowFile(owner, repo, branch, CloudBuildKit.workflowYaml())
                _msg.value = Msg.WF_INSTALLED
                appendLog("✓ workflow installed (.github/workflows/saqr-build.yml)")
            } else {
                appendLog("• workflow already installed")
            }
            api.dispatch(owner, repo, branch)
            _msg.value = Msg.DISPATCHED
            appendLog("✓ dispatched to GitHub Actions (ref: $branch)")
        } catch (e: Exception) {
            return fail(Msg.FAILED, "✗ ${e.message ?: "network error"}")
        }

        val startedAt = System.currentTimeMillis()
        var runId: Long? = null
        while (true) {
            if (cancelled) return fail(Msg.CANCELLED)
            if (CloudBuildKit.timedOut(startedAt, System.currentTimeMillis())) return fail(Msg.TIMEOUT, "✗ timed out after ${CloudBuildKit.TIMEOUT_MINUTES} minutes")
            delay(8000)
            val runs = runCatching { api.runs(owner, repo) }.getOrNull() ?: continue
            val r = CloudBuildKit.latestRun(CloudBuildKit.parseRuns(runs)) ?: continue
            if (runId == null) {
                runId = r.id
                _runUrl.value = r.htmlUrl
                appendLog("• run #${r.number} — ${r.status}")
            }
            when (CloudBuildKit.stateOf(r.status, r.conclusion)) {
                CloudBuildKit.State.QUEUED -> {}
                CloudBuildKit.State.RUNNING -> fetchLog(api, owner, repo, r.id)
                CloudBuildKit.State.SUCCESS -> break
                CloudBuildKit.State.FAILURE -> {
                    fetchLog(api, owner, repo, r.id)
                    val why = _log.value.lastOrNull() ?: "build failed"
                    return fail(Msg.FAILED, "✗ build failed — $why")
                }
                CloudBuildKit.State.CANCELLED -> return fail(Msg.CANCELLED, "✗ the run was cancelled on GitHub")
            }
        }

        _phase.value = Phase.DOWNLOADING
        try {
            val artifacts = api.artifacts(owner, repo, runId!!)
            val art = CloudBuildKit.pickApk(artifacts) ?: return fail(Msg.NO_APK, "✗ no APK artifact in the run")
            appendLog("• downloading ${art.name} (${art.sizeBytes / 1024} KB)")
            val zip = api.downloadArtifact(owner, repo, art.id, File(context.cacheDir, "cloudbuild/run-$runId.zip"))
            val apkFile = CloudBuildKit.unzipApk(zip, File(context.cacheDir, "cloudbuild/apk-$runId"))
                ?: return fail(Msg.NO_APK, "✗ the artifact zip had no APK inside")
            _apk.value = apkFile
            appendLog("✓ ${apkFile.name} ready (${apkFile.length() / 1024} KB)")
            _phase.value = Phase.READY
        } catch (e: Exception) {
            fail(Msg.FAILED, "✗ ${e.message ?: "download failed"}")
        }
    }

    private suspend fun fetchLog(api: CloudBuildClient, owner: String, repo: String, runId: Long) {
        val jobs = runCatching { api.jobs(owner, repo, runId) }.getOrNull() ?: return
        val job = CloudBuildKit.parseJobs(jobs).firstOrNull() ?: return
        val text = runCatching { api.jobLog(owner, repo, job.id) }.getOrNull() ?: return
        _log.value = CloudBuildKit.tailLines(text)
    }
}
