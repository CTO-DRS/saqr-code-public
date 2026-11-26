package com.ctodrs.saqrcode.ui.chat

import java.io.File

/**
 * Majlis: finds the files an assistant reply hands to the user (zip, apk, pdf, ...) so the chat can show a
 * file card instead of a bare sandbox path. Pure and testable: it only resolves names that exist inside [root].
 */
object Deliverables {
    val EXTENSIONS = setOf("zip", "apk", "aab", "jar", "tar", "gz", "tgz", "7z", "pdf", "csv", "json", "xlsx", "docx", "pptx", "png", "jpg", "jpeg", "svg", "mp4", "txt", "md", "html", "log", "db", "sql")
    private const val MAX_CARDS = 5
    private const val SEARCH_DEPTH = 4

    // sandbox:/workspace/a.zip, /workspace/a.zip, ./a.zip, build/out/app.apk, a.zip
    private val token = Regex("""(?:sandbox:)?[\w\u0600-\u06FF@%+=:,./\\-]*[\w\u0600-\u06FF-]\.(?:${EXTENSIONS.joinToString("|")})\b""", RegexOption.IGNORE_CASE)

    /** Files named in [text] that really exist under [root], in order of appearance, without duplicates. */
    fun find(text: String, root: File): List<File> {
        if (text.isBlank() || !root.isDirectory) return emptyList()
        val canonRoot = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, File>()
        for (m in token.findAll(text)) {
            val f = resolve(m.value, canonRoot) ?: continue
            out.putIfAbsent(f.path, f)
            if (out.size >= MAX_CARDS) break
        }
        return out.values.toList()
    }

    /** Maps a model-written path to a real file inside [root], or null. Never leaves [root]. */
    fun resolve(raw: String, root: File): File? {
        var p = raw.removePrefix("sandbox:").replace('\\', '/').trim().trimEnd('.', ',', ')', '`')
        if (p.startsWith("//")) return null
        for (prefix in listOf("/workspace/", "workspace/", "./", "/")) if (p.startsWith(prefix)) { p = p.removePrefix(prefix); break }
        if (p.isBlank() || p.split('/').any { it == ".." }) return null
        val direct = File(root, p).let { runCatching { it.canonicalFile }.getOrNull() }
        if (direct != null && direct.isFile && direct.path.startsWith(root.path + File.separator)) return direct
        // the model may name a file by a path from inside the Linux sandbox; fall back to the file name
        val name = p.substringAfterLast('/')
        return root.walkTopDown().maxDepth(SEARCH_DEPTH)
            .onEnter { it.name != ".git" && it.name != "node_modules" && it.name != ".gradle" }
            .firstOrNull { it.isFile && it.name == name }
    }

    fun human(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(java.util.Locale.US, bytes / 1024.0)
        else -> "%.1f MB".format(java.util.Locale.US, bytes / 1024.0 / 1024.0)
    }

    fun mime(f: File): String = when (f.extension.lowercase()) {
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        "pdf" -> "application/pdf"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "svg" -> "image/svg+xml"
        "mp4" -> "video/mp4"
        "json" -> "application/json"; "csv" -> "text/csv"; "html" -> "text/html"
        "txt", "md", "log", "sql" -> "text/plain"
        else -> "application/octet-stream"
    }
}
