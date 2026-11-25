package com.ctodrs.saqrcode.agent

import java.io.File

/**
 * v0.13 «عقل الصقر» · فهرس المشروع · Project index.
 *
 * A lightweight in-memory symbol index (files, classes, objects, interfaces, enums, functions)
 * built on demand from the project tree and refreshed when stale. It feeds the `project_search`
 * tool so the model can locate "where does X live" with a file:line answer instead of listing
 * directories or reading whole files. Acceptance: first result under 200 ms on a 500-file
 * project — the walk reads only symbol-bearing files, and the query itself is a single ranked
 * scan over the in-memory list.
 *
 * Arabic-aware: queries and symbol names are matched through [MemoryBank.normalize] (alef
 * forms, taa marbouta, yaa, diacritics) so «الشبكه» finds الشبكة.kt too.
 *
 * Pure Kotlin (no Android imports) so the whole pipeline is unit-tested.
 */
class ProjectIndex(private val root: File, private val maxAgeMs: Long = 20_000) {

    enum class Kind(val id: String) {
        FILE("file"), CLASS("class"), OBJECT("object"), INTERFACE("interface"),
        ENUM("enum"), FUNCTION("fun");

        companion object {
            fun byId(id: String?): Kind = entries.firstOrNull { it.id == id } ?: FILE
        }
    }

    data class Entry(val path: String, val line: Int, val name: String, val kind: Kind, val nameN: String)

    data class Hit(val path: String, val line: Int, val name: String, val kind: Kind) {
        fun toJson(): org.json.JSONObject = org.json.JSONObject()
            .put("path", path).put("line", line).put("name", name).put("kind", kind.id)
    }

    private val entries = ArrayList<Entry>()
    @Volatile private var builtAt = 0L

    /** Number of indexed symbols (files count as symbols too). */
    val size: Int get() = synchronized(this) { entries.size }

    /** When the index was last built (epoch ms), 0 if never. */
    val builtAtMs: Long get() = builtAt

    // ------------------------------------------------------------------ build

    /** (Re)builds the index by walking the project tree (skipping [Tools.IGNORED_DIRS]).
     *  Files are scanned on a small worker pool — parallel reads keep the cold build well under
     *  the 200ms acceptance on 500-file projects, on this sandbox and on real devices alike. */
    fun rebuild() {
        val files = ArrayList<File>()
        walk(root) { f -> files.add(f) }
        if (files.size > MAX_FILES) files.subList(MAX_FILES, files.size).clear()
        val collected = java.util.Collections.synchronizedList(ArrayList<Entry>(files.size * 8))
        val threads = THREADS
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        try {
            val chunk = (files.size + threads - 1) / threads
            val futures = (0 until threads).map { t ->
                pool.submit {
                    val from = t * chunk
                    val to = minOf(files.size, from + chunk)
                    for (i in from until to) collect(files[i], collected)
                }
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
        synchronized(this) {
            entries.clear()
            entries.addAll(collected)
            builtAt = System.currentTimeMillis()
        }
        onRebuilt?.let { cb -> runCatching { cb() } }
    }

    /**
     * v0.17: called after every rebuild (the project's files changed or the index aged out), so
     * the memory engine can re-check its file refs and flag stale ones.
     */
    @Volatile var onRebuilt: (() -> Unit)? = null

    /** v0.17: the project's files changed (the agent applied edits) — the next query rebuilds. */
    fun invalidate() { builtAt = 0L }

    private fun ensureFresh() {
        val stale = synchronized(this) { builtAt == 0L || System.currentTimeMillis() - builtAt > maxAgeMs }
        if (stale) rebuild()
    }

    private fun walk(dir: File, visit: (File) -> Unit) {
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in files.sortedBy { it.name }) {
            if (f.isDirectory) {
                if (f.name in Tools.IGNORED_DIRS) continue
                walk(f, visit)
            } else visit(f)
        }
    }

    /** Collects path + symbols from one file; returns 1 if it was scanned, 0 if skipped. */
    private fun collect(f: File, out: MutableList<Entry>): Int {
        val rel = f.relativeTo(root).invariantSeparatorsPath
        val ext = f.extension.lowercase()
        val scanner = SCANNER_EXT[ext] ?: return 0.also {
            if (TEXT_NO_SYMBOL_EXT.contains(ext) || ext.isEmpty()) out.add(Entry(rel, 0, f.name, Kind.FILE, MemoryBank.normalize(f.name)))
        }
        if (f.length() > MAX_FILE_BYTES) return 0
        val text = runCatching { f.readText(Charsets.UTF_8) }.getOrNull() ?: return 0
        out.add(Entry(rel, 0, f.name, Kind.FILE, MemoryBank.normalize(f.name)))
        text.lineSequence().forEachIndexed { i, line ->
            val sym = scanSymbols(line, scanner)
            if (sym != null) out.add(Entry(rel, i + 1, sym.second, sym.first, MemoryBank.normalize(sym.second)))
        }
        return 1
    }

    // ------------------------------------------------------------------ search

    /** Ranked search: exact name → prefix → Arabic-normalized prefix → contains → path. */
    fun search(queryRaw: String, limit: Int = 30): List<Hit> {
        val q = queryRaw.trim()
        if (q.isEmpty()) return emptyList()
        ensureFresh()
        val ql = q.lowercase()
        val qn = MemoryBank.normalize(q)
        val scored = synchronized(this) {
            entries.mapNotNull { e ->
                val nameL = e.name.lowercase()
                val score = when {
                    nameL == ql -> 0
                    nameL.startsWith(ql) -> 1
                    qn.isNotEmpty() && e.nameN.startsWith(qn) -> 2
                    nameL.contains(ql) -> 3
                    qn.isNotEmpty() && e.nameN.contains(qn) -> 4
                    e.path.lowercase().contains(ql) -> 5
                    else -> null
                } ?: return@mapNotNull null
                score to e
            }
        }
        return scored
            .sortedWith(compareBy({ it.first }, { it.second.path.length }, { it.second.line }, { it.second.name.length }))
            .take(limit.coerceIn(1, 100))
            .map { (_, e) -> Hit(e.path, e.line, e.name, e.kind) }
    }

    /** Compact rendering for the model: `path:line · kind · name` + totals footer. */
    fun render(query: String, limit: Int = 30): String {
        val hits = search(query, limit)
        if (hits.isEmpty()) return "No match for “${query.trim()}” (index: $size symbols)."
        return hits.joinToString("\n") { "${it.path}:${it.line} · ${it.kind.id} · ${it.name}" } +
            "\n(${hits.size} of $size symbols)"
    }

    companion object {
        private const val MAX_FILES = 4000
        private const val MAX_FILE_BYTES = 512L * 1024
        private val THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

        /** Text files worth listing as FILE entries even without a symbol scanner. */
        private val TEXT_NO_SYMBOL_EXT = setOf("md", "txt", "json", "xml", "html", "css", "yml", "yaml", "csv", "gradle", "properties", "toml", "pro")

        /** per-extension scanner flags: (allow `def`, allow JS `const` arrows). */
        private val SCANNER_EXT: Map<String, Pair<Boolean, Boolean>> = mapOf(
            "kt" to (false to false), "kts" to (false to false),
            "java" to (false to false),
            "py" to (true to false),
            "js" to (false to true), "jsx" to (false to true),
            "ts" to (false to true), "tsx" to (false to true),
            "mjs" to (false to true), "cjs" to (false to true),
        )

        private fun isIdent(c: Char) = c.isLetterOrDigit() || c == '_' // Arabic identifiers included

        /** Next identifier token starting at/after [from]; returns (token, endIndex) or null. */
        private fun identAt(line: String, from: Int): Pair<String, Int>? {
            var j = from
            val n = line.length
            while (j < n && !isIdent(line[j])) j++
            if (j >= n) return null
            val s = j
            while (j < n && isIdent(line[j])) j++
            return line.substring(s, j) to j
        }

        /** Single-pass symbol scanner — no regex engines, O(line) with a tiny constant.
         *  Finds one symbol per line: `enum class X`, `class X`, `object X`, `interface X`,
         *  `fun x(…)`, `def x(…)`, `record X`, and JS `const x = (…) =>` / `function x(…)`. */
        private fun scanSymbols(line: String, flags: Pair<Boolean, Boolean>): Pair<Kind, String>? {
            val allowDef = flags.first
            val allowConst = flags.second
            var i = 0
            val n = line.length
            var prev = ""
            while (true) {
                val t = identAt(line, i) ?: return null
                val (tok, end) = t
                when (tok) {
                    "class" -> {
                        val name = identAt(line, end)?.first ?: return null
                        return (if (prev == "enum") Kind.ENUM else Kind.CLASS) to name
                    }
                    "interface" -> return Kind.INTERFACE to (identAt(line, end)?.first ?: return null)
                    "object", "record" -> return Kind.OBJECT to (identAt(line, end)?.first ?: return null)
                    "fun" -> {
                        val t2 = identAt(line, end) ?: return null
                        if (t2.first == "interface") { // Kotlin `fun interface`
                            return Kind.INTERFACE to (identAt(line, t2.second)?.first ?: return null)
                        }
                        // a function's name must be followed by '(' — kills val/getter noise
                        var k = t2.second
                        while (k < n && line[k] == ' ') k++
                        if (k < n && line[k] == '(') return Kind.FUNCTION to t2.first
                        i = t2.second; prev = tok; continue
                    }
                    "function" -> if (allowConst) { // JS family
                        val t2 = identAt(line, end) ?: return null
                        var k = t2.second
                        while (k < n && line[k] == ' ') k++
                        if (k < n && line[k] == '(') return Kind.FUNCTION to t2.first
                        i = t2.second; prev = tok; continue
                    }
                    "def" -> if (allowDef) {
                        val t2 = identAt(line, end) ?: return null
                        var k = t2.second
                        while (k < n && line[k] == ' ') k++
                        if (k >= n || line[k] == '(') return Kind.FUNCTION to t2.first
                        i = t2.second; prev = tok; continue
                    }
                    "const", "let", "var" -> if (allowConst) {
                        val t2 = identAt(line, end)
                        if (t2 == null) { i = end; prev = tok; continue }
                        val eq = line.indexOf('=', end)
                        val rest = if (eq >= 0) line.substring(eq) else ""
                        if (rest.contains("=>") || rest.contains("function") || rest.contains("async")) {
                            return Kind.FUNCTION to t2.first
                        }
                        i = t2.second; prev = tok; continue
                    }
                }
                i = end; prev = tok
            }
        }
    }
}
