package com.ctodrs.saqrcode.cloud

import java.io.File

/**
 * v0.1.65 · What leaves the phone. Secrets and bulky build output never do.
 * Pure file walking; unit-tested on temp folders.
 */
object CloudSnapshot {
    const val MAX_FILES = 2_000
    const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
    const val MAX_FILE_BYTES = 2L * 1024 * 1024

    private val SKIP_DIRS = setOf(".git", "node_modules", "build", ".gradle", ".idea", "dist", "__pycache__", ".dart_tool", ".venv", "venv", "target", "secrets", "keystore", ".saqr")
    private val SKIP_NAMES = setOf("local.properties", "keystore.properties", "google-services.json", "id_rsa", "id_ed25519", ".npmrc", ".netrc", ".pypirc", ".git-credentials")
    private val SKIP_EXT = setOf("jks", "keystore", "p12", "pfx", "pem", "key", "apk", "aab", "zip", "jar", "so")

    /** True when a path must never be uploaded. */
    fun isSecretPath(rel: String): Boolean {
        val name = rel.substringAfterLast('/')
        val lower = name.lowercase()
        return lower == ".env" || lower.startsWith(".env.") || lower in SKIP_NAMES ||
            lower.substringAfterLast('.', "") in SKIP_EXT
    }

    data class Result(val files: List<CloudFile>, val skipped: List<String>, val truncated: Boolean) {
        val totalBytes get() = files.sumOf { it.bytes.size.toLong() }
    }

    fun collect(root: File): Result {
        val files = ArrayList<CloudFile>()
        val skipped = ArrayList<String>()
        var total = 0L
        var truncated = false
        root.walkTopDown().onEnter { it == root || it.name !in SKIP_DIRS }.filter { it.isFile }.sortedBy { it.path }.forEach { f ->
            val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
            when {
                isSecretPath(rel) -> skipped += rel
                f.length() > MAX_FILE_BYTES -> skipped += "$rel (too large)"
                files.size >= MAX_FILES || total + f.length() > MAX_TOTAL_BYTES -> { truncated = true; skipped += "$rel (limit)" }
                else -> { files += CloudFile(rel, f.readBytes()); total += f.length() }
            }
        }
        return Result(files, skipped, truncated)
    }
}
