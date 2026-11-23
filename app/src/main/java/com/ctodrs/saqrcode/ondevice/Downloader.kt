package com.ctodrs.saqrcode.ondevice

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * v0.8 · مُنزّل محصّن · Hardened downloader for the on-device build components.
 *
 * Every component (ubuntu-base, cmdline-tools, Gradle, aapt2) is described by a [Downloader.Artifact]:
 * a pinned SHA-256 (taken from the publisher's official checksum) plus optional mirror URLs tried
 * in order. Downloads are resumable: a leftover `.part` file is continued with an HTTP Range
 * request when the server answers 206, otherwise restarted. The final file is verified against
 * the pin before anything consumes it — a truncated or tampered download fails loudly instead of
 * producing a broken build environment. Pure helpers ([Downloader.sha256Hex], [Elf.isArm64],
 * [Hints.forMessage]) are unit-tested in V08FeaturesTest.
 */
object Downloader {

    /** A build component: mirror URLs tried in order, and the SHA-256 the file must match. */
    data class Artifact(val name: String, val sha256: String, val urls: List<String>) {
        val primary: String get() = urls.first()
        fun valid(): Boolean =
            sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } && urls.isNotEmpty()
    }

    /** Lowercase hex SHA-256 of [file]. */
    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Content-Range "bytes 12345-" / "bytes 12345-999/67890" → the start offset, or null. */
    fun rangeStart(contentRange: String?): Long? {
        val m = Regex("""bytes\s+(\d+)-""", RegexOption.IGNORE_CASE).find(contentRange ?: return null) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    /**
     * Download [artifact] to [to], resuming a leftover `.part` when possible and verifying the
     * pinned SHA-256. [onProgress] gets (written bytes, total bytes or -1). Returns [to].
     * @throws IOException after every mirror failed, with the last reason.
     */
    fun download(
        http: OkHttpClient,
        artifact: Artifact,
        to: File,
        maxBytes: Long = 2L * 1024 * 1024 * 1024,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File {
        require(artifact.valid()) { "Artifact ${artifact.name} has an invalid pin — refusing to download" }
        to.parentFile?.mkdirs()
        val part = File(to.path + ".part")
        if (to.isFile && to.length() > 0) {
            if (sha256Hex(to) == artifact.sha256.lowercase()) return to // already cached, verified
            to.delete()
        }
        var lastReason = "no mirror tried"
        for (url in artifact.urls) {
            try {
                fetch(http, url, part, maxBytes, onProgress)
                val got = sha256Hex(part)
                if (!got.equals(artifact.sha256.lowercase(), ignoreCase = true)) {
                    part.delete()
                    lastReason = "checksum mismatch from $url (got $got)"
                    continue
                }
                if (!part.renameTo(to)) { part.copyTo(to, overwrite = true); part.delete() }
                return to
            } catch (e: Exception) {
                lastReason = "${e.message ?: e.javaClass.simpleName} ($url)"
            }
        }
        throw IOException("Download failed for ${artifact.name}: $lastReason")
    }

    private fun fetch(http: OkHttpClient, url: String, part: File, maxBytes: Long, onProgress: (Long, Long) -> Unit) {
        val already = if (part.isFile) part.length() else 0L
        val rb = Request.Builder().url(url).header("User-Agent", "SaqrCode/0.8")
        if (already > 0) rb.header("Range", "bytes=$already-")
        http.newCall(rb.build()).execute().use { resp ->
            val body = resp.body ?: throw IOException("empty body (HTTP ${resp.code})")
            when {
                already > 0 && resp.code == 200 -> pipe(body, part, 0, truncate = true, maxBytes, onProgress) // Range ignored
                resp.code == 206 -> {
                    val start = rangeStart(resp.header("Content-Range"))
                    if (start == null || start != already) pipe(body, part, 0, truncate = true, maxBytes, onProgress)
                    else pipe(body, part, already, truncate = false, maxBytes, onProgress)
                }
                resp.code == 200 -> pipe(body, part, 0, truncate = true, maxBytes, onProgress)
                else -> throw IOException("HTTP ${resp.code}")
            }
        }
    }

    /** Streams [body] into [part]; with [truncate]=false appends from [startAt] (a resumed download). */
    private fun pipe(body: ResponseBody, part: File, startAt: Long, truncate: Boolean, maxBytes: Long, onProgress: (Long, Long) -> Unit) {
        val remaining = body.contentLength()
        if (remaining > 0 && startAt + remaining > maxBytes) throw IOException("file larger than expected (${startAt + remaining} bytes)")
        var written = startAt
        FileOutputStream(part, !truncate).use { out ->
            body.byteStream().use { ins ->
                val buf = ByteArray(1 shl 16)
                var sinceProgress = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    written += n
                    sinceProgress += n
                    if (sinceProgress >= PROGRESS_STEP) {
                        onProgress(written, if (remaining > 0) startAt + remaining else -1)
                        sinceProgress = 0
                    }
                    if (written > maxBytes) throw IOException("download exceeded $maxBytes bytes")
                }
            }
        }
        val expected = if (remaining > 0) startAt + remaining else -1L
        if (expected > 0 && written != expected) throw IOException("connection lost at $written/$expected bytes")
        onProgress(written, expected)
    }

    /**
     * Resolve an asset download URL from a GitHub release API endpoint (mirrors tried in order).
     * Picks the first asset whose name contains [nameMustContain] and, when [extension] is set,
     * ends with it; a non-matching asset is remembered as a fallback.
     */
    fun releaseAssetUrl(http: OkHttpClient, apiUrls: List<String>, nameMustContain: String, extension: String? = null): String {
        var lastReason = "no API answered"
        for (api in apiUrls) {
            try {
                val req = Request.Builder().url(api)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SaqrCode/0.8").build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val assets = JSONObject(resp.body!!.string()).optJSONArray("assets")
                        ?: throw IOException("no assets array")
                    var fallback = ""
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name")
                        if (!name.lowercase().contains(nameMustContain.lowercase())) continue
                        val url = a.optString("browser_download_url")
                        if (url.isEmpty()) continue
                        if (extension == null) return url
                        if (name.endsWith(extension)) return url
                        if (extension == ".tar.gz" && name.endsWith(".tgz")) return url
                        if (fallback.isEmpty()) fallback = url
                    }
                    if (fallback.isNotEmpty()) return fallback
                    throw IOException("no asset matching \"$nameMustContain\"")
                }
            } catch (e: Exception) {
                lastReason = "${e.message ?: e.javaClass.simpleName} ($api)"
            }
        }
        throw IOException("release lookup failed: $lastReason")
    }

    const val PROGRESS_STEP = 256L * 1024
}

/** Minimal ELF header check so a fetched aapt2 is rejected early when it is not an arm64 binary. */
object Elf {
    fun isArm64(bytes: ByteArray): Boolean {
        if (bytes.size < 20) return false
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()) return false
        if (bytes[4] != 2.toByte()) return false // ELFCLASS64
        // e_machine (little-endian, offset 18..19): EM_AARCH64 = 183 = 0xB7
        return (bytes[18].toInt() and 0xFF) == 0xB7 && bytes[19] == 0.toByte()
    }

    fun isArm64(f: File): Boolean {
        if (!f.isFile || f.length() < 20) return false
        val head = ByteArray(20)
        f.inputStream().use { ins ->
            var read = 0
            while (read < 20) {
                val n = ins.read(head, read, 20 - read)
                if (n < 0) break
                read += n
            }
        }
        return isArm64(head)
    }
}

/** Friendly, actionable hints appended to bootstrap/build failures in the log. */
object Hints {
    /** Returns a short hint for a known failure class, or null when nothing applies. */
    fun forMessage(message: String?): String? {
        val m = (message ?: "").lowercase()
        return when {
            listOf("outofmemory", "out of memory", "cannot allocate memory", "killed", "oom", "bad_alloc").any { it in m } ->
                "Not enough memory: close background apps, or use the Lite Web template (builds in minutes)."
            listOf("unable to resolve", "failed to connect", "timeout", "timed out", "econnrefused", "network is unreachable", "connection reset", "checksum mismatch", "http 5").any { it in m } ->
                "Network problem: check the phone's connection, then tap Prepare again — finished steps are skipped."
            "proot" in m || "tracee" in m ->
                "proot failed: reinstall Linux from the Terminal tab, and make sure no VPN or security app interferes."
            "no space left" in m || "storage" in m ->
                "Storage full: free space (the build environment needs about 1.5 GB) and retry."
            "sdkmanager" in m ->
                "sdkmanager failed: usually network. Retry after checking the connection."
            "apt" in m ->
                "apt failed: usually a mirror hiccup. Tap Prepare again; it resumes where it stopped."
            "gradle exited" in m || "compilation error" in m || ".kt:" in m || "e: " in m ->
                "The project code has an error: read the file:line in the log above, or ask the agent to fix it."
            "aapt2" in m ->
                "aapt2 could not be fetched: set a direct mirror URL in Settings → On-device build, or copy a binary to files/linux/android-sdk/build-tools/35.0.0/aapt2."
            else -> null
        }
    }
}
