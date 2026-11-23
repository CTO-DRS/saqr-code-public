package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tool schemas exposed to the model + pure file helpers. */
object Tools {
    const val LIST = "list_files"
    const val READ = "read_file"
    const val WRITE = "write_file"
    const val EDIT = "edit_file"
    const val DELETE = "delete_file"
    const val RUN = "run_command"
    const val SEARCH = "search"
    const val PLAN = "update_plan"
    const val GIT_STATUS = "git_status"
    const val GIT_DIFF = "git_diff"
    const val GIT_LOG = "git_log"
    const val GIT_COMMIT = "git_commit"
    const val GIT_BRANCH = "git_branch"
    const val GIT_PULL = "git_pull"
    const val GIT_PUSH = "git_push"

    val GIT = setOf(GIT_STATUS, GIT_DIFF, GIT_LOG, GIT_COMMIT, GIT_BRANCH, GIT_PULL, GIT_PUSH)

    val MUTATING = setOf(WRITE, EDIT, DELETE)

    private fun schema(props: JSONObject, required: List<String>) =
        JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required))

    private fun p(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

    val specs: List<ToolSpec> = listOf(
        ToolSpec(LIST, "List files and folders in the project. Paths are relative to the project root.",
            schema(JSONObject()
                .put("path", p("string", "Folder relative to project root. Default '.'"))
                .put("recursive", p("boolean", "List recursively (max 400 entries). Default true")), emptyList())),
        ToolSpec(READ, "Read a text file. Optionally a 1-based inclusive line range.",
            schema(JSONObject()
                .put("path", p("string", "File path relative to project root"))
                .put("start_line", p("integer", "First line (1-based)"))
                .put("end_line", p("integer", "Last line (inclusive)")), listOf("path"))),
        ToolSpec(WRITE, "Create or fully overwrite a file with complete content. Put `path` first. Never write placeholders like '...rest of code'.",
            schema(JSONObject()
                .put("path", p("string", "File path relative to project root"))
                .put("content", p("string", "The complete file content")), listOf("path", "content"))),
        ToolSpec(EDIT, "Replace an exact snippet in an existing file. old_string must match exactly once unless replace_all is true. Prefer this for small changes.",
            schema(JSONObject()
                .put("path", p("string", "File path relative to project root"))
                .put("old_string", p("string", "Exact existing text to replace"))
                .put("new_string", p("string", "Replacement text"))
                .put("replace_all", p("boolean", "Replace every occurrence")), listOf("path", "old_string", "new_string"))),
        ToolSpec(DELETE, "Delete a file in the project.",
            schema(JSONObject().put("path", p("string", "File path relative to project root")), listOf("path"))),
        ToolSpec(RUN, "Run a shell command in the project's Linux terminal (Alpine via proot, cwd=/workspace = project root). Use `apk add <pkg>` to install tools. Non-interactive only; servers must listen on 127.0.0.1. Dangerous commands are blocked.",
            schema(JSONObject()
                .put("command", p("string", "Shell command"))
                .put("timeout_sec", p("integer", "Timeout in seconds (default 120, max 600)")), listOf("command"))),
        ToolSpec(SEARCH, "Search file contents in the project (case-insensitive substring, or regex).",
            schema(JSONObject()
                .put("query", p("string", "Text or regex to search for"))
                .put("regex", p("boolean", "Treat query as a regular expression"))
                .put("path", p("string", "Folder to search, default '.'")), listOf("query"))),
        ToolSpec(PLAN, "Create or replace your step-by-step plan for the current task. The user sees it as a progress pill. Send the full list every time.",
            schema(JSONObject().put("items", JSONObject().put("type", "array").put("description", "Ordered steps")
                .put("items", JSONObject().put("type", "object")
                    .put("properties", JSONObject()
                        .put("title", p("string", "Short step title in the user's language"))
                        .put("status", JSONObject().put("type", "string").put("enum", JSONArray(listOf("pending", "in_progress", "done")))))
                    .put("required", JSONArray(listOf("title", "status"))))), listOf("items"))),
    ) + GitTools.specs

    private val IGNORED_DIRS = setOf(".git", "node_modules", "build", ".gradle", "__pycache__", ".venv", "dist", ".saqr")

    /** Resolve a model-supplied path safely inside [root]. */
    fun resolve(root: File, path: String?): File {
        val clean = (path ?: ".").trim().removePrefix("/workspace").trimStart('/').ifEmpty { "." }
        val f = File(root, clean).canonicalFile
        val r = root.canonicalFile
        require(f.path == r.path || f.path.startsWith(r.path + File.separator)) { "Path escapes the project: $path" }
        return f
    }

    fun rel(root: File, f: File): String = f.canonicalFile.relativeTo(root.canonicalFile).path.ifEmpty { "." }

    fun listFiles(root: File, path: String?, recursive: Boolean, max: Int = 400): String {
        val dir = resolve(root, path)
        if (!dir.isDirectory) return "Not a directory: $path"
        val out = ArrayList<String>()
        fun walk(d: File, depth: Int) {
            val children = (d.listFiles() ?: emptyArray()).sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            for (c in children) {
                if (out.size >= max) return
                if (c.isDirectory && c.name in IGNORED_DIRS) { out += rel(root, c) + "/ (skipped)"; continue }
                out += if (c.isDirectory) rel(root, c) + "/" else "${rel(root, c)} (${c.length()} B)"
                if (recursive && c.isDirectory && depth < 12) walk(c, depth + 1)
            }
        }
        walk(dir, 0)
        if (out.isEmpty()) return "(empty)"
        return out.joinToString("\n") + if (out.size >= max) "\n…(limit reached)" else ""
    }

    fun projectMap(root: File, max: Int = 150): String = listFiles(root, ".", true, max)

    fun readFile(root: File, path: String?, start: Int?, end: Int?, maxChars: Int = 40_000): String {
        val f = resolve(root, path)
        if (!f.isFile) return "File not found: $path"
        if (f.length() > 2_000_000) return "File too large (${f.length()} bytes)"
        val lines = f.readText().split('\n')
        val s = ((start ?: 1).coerceAtLeast(1)) - 1
        val e = (end ?: lines.size).coerceAtMost(lines.size)
        if (s >= e && lines.isNotEmpty() && !(s == 0 && e == 0)) return "Empty range (file has ${lines.size} lines)"
        val body = lines.subList(s, maxOf(s, e)).mapIndexed { i, l -> "${s + i + 1}| $l" }.joinToString("\n")
        val header = "$path (${lines.size} lines)\n"
        return header + if (body.length > maxChars) body.take(maxChars) + "\n…[truncated; use start_line/end_line]" else body
    }

    /** Applies an exact replacement; returns new content or throws with a helpful message. */
    fun applyEdit(content: String, old: String, new: String, replaceAll: Boolean): String {
        require(old.isNotEmpty()) { "old_string is empty" }
        val count = content.windowedCount(old)
        if (count == 0) {
            // tolerate CRLF / trailing-whitespace differences
            val norm = old.replace("\r\n", "\n")
            if (norm != old && content.contains(norm)) return applyEdit(content, norm, new, replaceAll)
            throw IllegalArgumentException("old_string not found. Re-read the file and copy the exact text.")
        }
        if (count > 1 && !replaceAll) throw IllegalArgumentException("old_string matches $count times; add more context or set replace_all.")
        return if (replaceAll) content.replace(old, new) else content.replaceFirst(old, new)
    }

    private fun String.windowedCount(sub: String): Int {
        var i = 0; var c = 0
        while (true) { i = indexOf(sub, i); if (i < 0) return c; c++; i += sub.length }
    }

    fun search(root: File, query: String, regex: Boolean, path: String?, maxHits: Int = 120): String {
        val dir = resolve(root, path)
        val re = if (regex) runCatching { Regex(query, RegexOption.IGNORE_CASE) }.getOrElse { return "Bad regex: ${it.message}" } else null
        val hits = ArrayList<String>()
        dir.walkTopDown().onEnter { it == dir || it.name !in IGNORED_DIRS }.filter { it.isFile && it.length() < 1_000_000 }.forEach { f ->
            if (hits.size >= maxHits) return@forEach
            val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
            if (text.contains('\u0000')) return@forEach
            text.split('\n').forEachIndexed { i, line ->
                if (hits.size < maxHits) {
                    val ok = re?.containsMatchIn(line) ?: line.contains(query, ignoreCase = true)
                    if (ok) hits += "${rel(root, f)}:${i + 1}: ${line.trim().take(200)}"
                }
            }
        }
        return if (hits.isEmpty()) "No matches." else hits.joinToString("\n") + if (hits.size >= maxHits) "\n…(more matches omitted)" else ""
    }

    fun summarizeArgs(name: String, args: JSONObject): String = when (name) {
        RUN -> args.optString("command")
        SEARCH -> "\"" + args.optString("query") + "\""
        LIST -> args.optString("path", ".")
        GIT_COMMIT -> args.optString("message").lineSequence().firstOrNull().orEmpty().take(80)
        GIT_BRANCH -> args.optString("action", "list") + (args.optString("name").takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
        GIT_PUSH, GIT_PULL -> args.optString("remote", "origin") + (args.optString("branch").takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
        GIT_LOG -> "-n " + args.optInt("max_count", 15)
        GIT_STATUS -> "."
        GIT_DIFF -> args.optString("path").ifBlank { if (args.optBoolean("staged")) "--staged" else "." }
        else -> args.optString("path")
    }
}
