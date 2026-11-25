package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * v0.13 «عقل الصقر» · بنك الذاكرة · Memory bank.
 *
 * A per-project, on-disk memory the agent keeps about the people and the project it works with:
 * durable decisions (stack, architecture), user preferences (language, style, workflow) and
 * facts (entry points, conventions). The bank is loaded into the system prompt at the start of
 * every turn, and the model maintains it through the `memory_save` / `memory_forget` tools.
 * The user owns the file: it lives in the project under `.saqr/memory.json` (git-ignored by
 * Saqr's default .gitignore, hidden from the project map) and is fully editable from the
 * Memory sheet. Secrets are refused by a cheap heuristic — memory is plain JSON, not a vault.
 *
 * Pure Kotlin (no Android imports) so the whole pipeline is unit-tested.
 */
class MemoryBank(
    private val dir: File,
    /** v0.17: the file inside [dir] — `memory.json` for a project, `global.json` for the long-term layer. */
    private val fileName: String = FILE_NAME,
    /** v0.17: which layer this bank is. The GLOBAL layer only keeps user preferences, never file refs. */
    val layer: Layer = Layer.PROJECT,
) {

    enum class Kind(val id: String) {
        PREFERENCE("preference"), DECISION("decision"), FACT("fact");

        companion object {
            fun byId(id: String?): Kind = entries.firstOrNull { it.id == id } ?: FACT
        }
    }

    /** v0.17 memory engine: the layer a bank belongs to (session memory lives with the chat session). */
    enum class Layer { PROJECT, GLOBAL }

    /**
     * v0.17 typed entries. [NOTE] is every entry saved before v0.17 (migrated as-is) and any
     * plain fact; [LESSON] is an error → solution pair captured when a run turned failing checks green.
     */
    enum class Type(val id: String, val kind: Kind) {
        NOTE("note", Kind.FACT),
        PREFERENCE("preference", Kind.PREFERENCE),
        DECISION("decision", Kind.DECISION),
        ARCHITECTURE("architecture", Kind.DECISION),
        DEPENDENCY("dependency", Kind.FACT),
        CONVENTION("convention", Kind.FACT),
        LESSON("lesson", Kind.FACT);

        companion object {
            fun byId(id: String?): Type? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }

            /** The type a pre-v0.17 `kind` maps to when a new entry is saved without a type. */
            fun forKind(kind: Kind): Type = when (kind) {
                Kind.PREFERENCE -> PREFERENCE
                Kind.DECISION -> DECISION
                Kind.FACT -> NOTE
            }
        }
    }

    data class Entry(
        val key: String,
        val value: String,
        val kind: Kind,
        val createdAt: Long,
        val updatedAt: Long,
        /** v0.17: typed category (pre-v0.17 entries load as [Type.NOTE]). */
        val type: Type = Type.NOTE,
        /** v0.17: who wrote it — "agent", "user", "auto:gate" (lesson capture) or "legacy" (migrated). */
        val source: String = "",
        /** v0.17: project-relative files this memory is about (checked for staleness). */
        val files: List<String> = emptyList(),
        /** v0.17: how many times the same lesson/memory was confirmed (dedupe counter). */
        val hits: Int = 1,
        /** v0.17: a referenced file no longer exists in the project. */
        val stale: Boolean = false,
    ) {
        /** Single-line rendering of the value (newlines become " · " for the prompt). */
        val flatValue: String get() = value.replace(Regex("\\s+"), " ").trim()
    }

    private val entries = LinkedHashMap<String, Entry>() // normalized key → entry
    private val file get() = File(dir, fileName)

    /** v0.17: bumps on every change so the relevance index knows when to rebuild. */
    @Volatile var version: Int = 0
        private set

    init {
        load()
    }

    // ------------------------------------------------------------------ read

    /** All entries, grouped order: preferences → decisions → facts, newest first inside a group. */
    val all: List<Entry>
        get() = synchronized(this) {
            KIND_ORDER.flatMap { k -> entries.values.filter { it.kind == k }.sortedByDescending { it.updatedAt } }
        }

    val count: Int get() = synchronized(this) { entries.size }

    fun isEmpty(): Boolean = count == 0

    /** Normalized-substring search over keys and values. */
    fun search(query: String): List<Entry> {
        val q = normalize(query)
        if (q.isEmpty()) return all
        return synchronized(this) { all.filter { normalize(it.key).contains(q) || normalize(it.value).contains(q) } }
    }

    /**
     * The system-prompt block, inside [maxChars]: grouped lines `key — value`, newest first,
     * preferences → decisions → facts. Empty groups are skipped; empty string when empty.
     */
    fun render(maxChars: Int = PROMPT_BUDGET): String = renderEntries(all, maxChars)

    /**
     * v0.17: renders a chosen subset (the relevance-ranked top-K) in the same grouped format as
     * [render]. Within a group the given order is kept; lessons, file refs and stale refs are tagged.
     */
    fun renderEntries(chosen: List<Entry>, maxChars: Int = PROMPT_BUDGET): String {
        val out = StringBuilder()
        for (kind in KIND_ORDER) {
            val group = chosen.filter { it.kind == kind }
            if (group.isEmpty()) continue
            val header = when (kind) {
                Kind.PREFERENCE -> "User preferences"
                Kind.DECISION -> "Decisions"
                Kind.FACT -> "Project facts"
            }
            val lines = ArrayList<String>()
            var used = out.length + header.length + 2
            for (e in group) {
                val line = "- " + lineOf(e)
                if (used + line.length > maxChars) break
                lines += line; used += line.length + 1
            }
            if (lines.isEmpty()) continue
            out.append('[').append(header).append("]\n")
            lines.forEach { out.append(it).append('\n') }
            if (out.length >= maxChars) break
        }
        return out.toString().trimEnd()
    }

    private fun lineOf(e: Entry): String = buildString {
        if (e.type in TAGGED_TYPES) append('(').append(e.type.id).append(") ")
        append(e.key.trim()).append(" — ").append(e.flatValue.take(MAX_VALUE_LEN))
        if (e.files.isNotEmpty()) append(" [files: ").append(e.files.take(4).joinToString(", ")).append(']')
        if (e.stale) append(" [stale: a referenced file no longer exists]")
    }

    /** v0.17: entries of one [Type]. */
    fun byType(type: Type): List<Entry> = all.filter { it.type == type }

    // ------------------------------------------------------------------ write

    /**
     * Upsert a memory: the same normalized key updates the existing entry (keeps `createdAt`),
     * a new key appends. Evicts the oldest-updated entry when over [MAX_ENTRIES].
     * Returns a short model-facing result. Secrets are refused.
     */
    fun save(
        rawKey: String, rawValue: String, kind: Kind,
        type: Type? = null, source: String = "agent", files: List<String> = emptyList(),
    ): String {
        val key = rawKey.trim()
        val value = rawValue.trim()
        if (key.isEmpty() || value.isEmpty()) return "Error: key and value are required."
        if (key.length > MAX_KEY_LEN) return "Error: key too long (max $MAX_KEY_LEN chars)."
        if (value.length > MAX_VALUE_LEN) return "Error: value too long (max $MAX_VALUE_LEN chars) — keep memories short."
        val cleanFiles = files.map { it.trim().removePrefix("./").removePrefix("/workspace/") }.filter { it.isNotEmpty() && it.length <= 200 }.distinct().take(MAX_FILES)
        val secret = looksLikeSecret(key) || looksLikeSecret(value) || cleanFiles.any { looksLikeSecret(it) }
        if (secret) return "Refused: this looks like a secret (password/token/key). Memory is plain JSON on disk — never store secrets."
        val nk = normalize(key)
        if (nk.isEmpty()) return "Error: key must contain letters or digits."
        var t = type ?: Type.forKind(kind)
        var k = if (type != null) type.kind else kind
        if (layer == Layer.GLOBAL) {
            // the long-term layer keeps only the user's own preferences — never project facts or file refs
            if (cleanFiles.isNotEmpty()) return "Refused: global memory keeps user preferences only — no project files."
            t = Type.PREFERENCE; k = Kind.PREFERENCE
        }
        synchronized(this) {
            val now = System.currentTimeMillis()
            val old = entries[nk]
            entries[nk] = Entry(key, value, k, old?.createdAt ?: now, now, t, source.take(40),
                cleanFiles.ifEmpty { old?.files.orEmpty() }, (old?.hits ?: 0) + 1)
            evict()
            persist()
        }
        val shown = entries.size
        return "Remembered ($shown/$MAX_ENTRIES)."
    }

    /** Over the cap: stale entries go first, then the oldest-updated one. */
    private fun evict() {
        while (entries.size > MAX_ENTRIES) {
            val victim = entries.entries.filter { it.value.stale }.minByOrNull { it.value.updatedAt }?.key
                ?: entries.minByOrNull { it.value.updatedAt }?.key
                ?: break
            entries.remove(victim)
        }
    }

    /**
     * v0.17 lesson capture: store an error → solution pair. A lesson whose error signature is
     * the same (normalized key) or near-identical (token overlap ≥ [LESSON_DEDUPE]) updates the
     * existing lesson — newest solution, hits + 1, original createdAt — instead of adding a copy.
     * Returns (result, deduped).
     */
    fun saveLesson(signature: String, solution: String, files: List<String> = emptyList(), source: String = "auto:gate"): Pair<String, Boolean> {
        val sig = signature.trim().take(MAX_KEY_LEN - 8)
        if (sig.isEmpty() || solution.isBlank()) return "Error: empty lesson." to false
        val key = "lesson: $sig"
        val sigTokens = com.ctodrs.saqrcode.agent.memory.MemoryRecall.tokens(sig).toSet()
        val dup = synchronized(this) {
            entries.entries.firstOrNull { (nk, e) ->
                e.type == Type.LESSON && (nk == normalize(key) ||
                    com.ctodrs.saqrcode.agent.memory.MemoryRecall.jaccard(sigTokens, com.ctodrs.saqrcode.agent.memory.MemoryRecall.tokens(e.key.removePrefix("lesson:")).toSet()) >= LESSON_DEDUPE)
            }?.value
        }
        val value = solution.trim().take(MAX_VALUE_LEN)
        return if (dup != null) {
            val r = synchronized(this) {
                val nk = normalize(dup.key)
                val merged = (files + dup.files).distinct().take(MAX_FILES)
                if (looksLikeSecret(value)) return "Refused: this looks like a secret (password/token/key). Memory is plain JSON on disk — never store secrets." to false
                entries[nk] = dup.copy(value = value, updatedAt = System.currentTimeMillis(), files = merged, hits = dup.hits + 1, stale = false)
                persist()
                "Lesson updated (seen ${dup.hits + 1}×)."
            }
            r to true
        } else save(key, value, Kind.FACT, Type.LESSON, source, files) to false
    }

    /** v0.17: removes exactly this entry (the Memory sheet's delete). */
    fun remove(entry: Entry): Boolean = synchronized(this) {
        val nk = normalize(entry.key)
        (entries.remove(nk) != null).also { if (it) persist() }
    }

    /**
     * v0.17: flags entries whose referenced files no longer exist ([exists] answers for a
     * project-relative path). Returns how many entries are stale now. Called whenever the
     * project index is invalidated (files changed) — nothing is deleted, the user decides.
     */
    fun refreshStale(exists: (String) -> Boolean): Int = synchronized(this) {
        var changed = false
        for ((nk, e) in entries.entries.toList()) {
            if (e.files.isEmpty()) continue
            val st = e.files.any { !exists(it) }
            if (st != e.stale) { entries[nk] = e.copy(stale = st); changed = true }
        }
        if (changed) persist()
        entries.values.count { it.stale }
    }

    /**
     * Forget by key (exact normalized match first, then substring over keys and values).
     * Returns a model-facing summary of what was removed.
     */
    fun forget(rawQuery: String): String {
        val q = rawQuery.trim()
        if (q.isEmpty()) return "Error: pass the key to forget."
        val nq = normalize(q)
        val removed = synchronized(this) {
            val exact = entries.keys.firstOrNull { it == nq }
            val hits = when {
                exact != null -> listOf(exact)
                else -> entries.filter { (k, e) -> k.contains(nq) || normalize(e.value).contains(nq) }.keys.toList()
            }
            if (hits.isEmpty()) emptyList()
            else hits.mapNotNull { entries.remove(it) }.also { if (it.isNotEmpty()) persist() }
        }
        return when {
            removed.isEmpty() -> "Nothing matched \"${q.take(80)}\" — memory unchanged."
            else -> "Forgot: " + removed.joinToString(", ") { it.key.take(60) } + "."
        }
    }

    /** Clears the bank (from the Memory sheet). */
    fun clear() = synchronized(this) {
        entries.clear(); persist()
    }

    /** v0.17: what is on disk now (another bank object for the same file may have written it). */
    fun reload() = synchronized(this) { entries.clear(); load(); version++ }

    // ------------------------------------------------------------------ disk

    private fun load() {
        val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() ?: return
        runCatching {
            val o = JSONObject(text)
            val schema = o.optInt("v", 1)
            val arr = o.optJSONArray("entries") ?: return
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val key = e.optString("key").trim()
                val value = e.optString("value").trim()
                if (key.isEmpty() || value.isEmpty()) continue
                val nk = normalize(key)
                if (nk.isEmpty() || nk in entries) continue
                val kind = Kind.byId(e.optString("kind"))
                // v0.17 migration: a v1 entry has no type → NOTE (its kind, timestamps and text are kept as-is)
                val type = if (schema < 2) Type.NOTE else Type.byId(e.optString("type")) ?: Type.NOTE
                val files = e.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                entries[nk] = Entry(
                    key = key.take(MAX_KEY_LEN),
                    value = value.take(MAX_VALUE_LEN),
                    kind = kind,
                    createdAt = e.optLong("createdAt"),
                    updatedAt = e.optLong("updatedAt"),
                    type = type,
                    source = if (schema < 2) "legacy" else e.optString("source"),
                    files = files.take(MAX_FILES),
                    hits = e.optInt("hits", 1).coerceAtLeast(1),
                    stale = e.optBoolean("stale", false),
                )
            }
        } // a corrupt file just means an empty bank; the next save rewrites it
    }

    private fun persist() {
        version++
        runCatching {
            dir.mkdirs()
            val arr = JSONArray()
            for (e in all) arr.put(
                JSONObject()
                    .put("key", e.key)
                    .put("value", e.value)
                    .put("kind", e.kind.id)
                    .put("createdAt", e.createdAt)
                    .put("updatedAt", e.updatedAt)
                    .put("type", e.type.id)
                    .put("source", e.source)
                    .put("files", JSONArray(e.files))
                    .put("hits", e.hits)
                    .put("stale", e.stale),
            )
            SessionStore.atomicWrite(file, JSONObject().put("v", SCHEMA).put("entries", arr).toString())
        }
    }

    companion object {
        const val FILE_NAME = "memory.json"
        /** v0.17 schema 2: typed entries (type, source, files, hits, stale). v1 files still load (as NOTE). */
        const val SCHEMA = 2
        const val GLOBAL_FILE = "global.json"
        /** v0.17: raised from 60 — relevance retrieval injects only the top-K, so a larger bank no longer costs prompt space. */
        const val MAX_ENTRIES = 200
        const val MAX_KEY_LEN = 120
        const val MAX_VALUE_LEN = 800
        const val MAX_FILES = 8
        const val PROMPT_BUDGET = 2_000
        /** Token overlap at which two lesson signatures are the same lesson. */
        const val LESSON_DEDUPE = 0.75

        private val KIND_ORDER = listOf(Kind.PREFERENCE, Kind.DECISION, Kind.FACT)
        private val TAGGED_TYPES = setOf(Type.LESSON, Type.ARCHITECTURE, Type.DEPENDENCY, Type.CONVENTION)

        /** Patterns that suggest a secret: assignment to a secret-ish name, known token shapes, or Arabic secret words. */
        private val SECRET_PATTERNS = listOf(
            Regex("""(?i)(password|passwd|secret|api[_-]?key|token|private[_-]?key)\s*[:=]"""),
            Regex("""(?u)(كلمة\s*السر|كلمه\s*السر|كلمة\s*المرور|كلمه\s*المرور|رمز\s*الدخول|المفتاح\s*الخاص)"""),
            Regex("""\b(sk-|ghp_|gho_|github_pat_|AKIA|xoxb-|xoxp-)[A-Za-z0-9_\-]{8,}"""),
            Regex("""\b[A-Za-z0-9+/]{40,}\b"""),
        )

        fun looksLikeSecret(s: String) = SECRET_PATTERNS.any { it.containsMatchIn(s) }

        private val SHARED = HashMap<String, MemoryBank>()

        /**
         * v0.17: one bank object per file for the whole process. The global layer is opened by
         * every project's session; sharing the object means one project's save can never be
         * overwritten by another session's stale copy.
         */
        fun shared(dir: File, fileName: String, layer: Layer): MemoryBank = synchronized(SHARED) {
            val path = File(dir, fileName).absolutePath
            SHARED.getOrPut(path) { MemoryBank(dir, fileName, layer) }
        }

        // ------------------------------------------------ Arabic-aware key normalization

        // precompiled once — normalize() runs per key in recall and per symbol in the project index
        private val HARAKAT_RE = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
        private val ALEF_RE = Regex("[أإآٱ]")

        /**
         * Unifies Arabic spelling variants so «التطبيق», «التّطبيق» and «إلتطبيق» are one key:
         * diacritics + tatweel removed, alef forms (أ إ آ ٱ) → ا, ى → ي, ة → ه, ؤ → و, ئ → ي,
         * then only letters and digits survive (any script), lowercased.
         */
        fun normalize(s: String): String {
            var t = s
            // strip harakat, sukun, shadda, small Qur'anic marks, dagger alef, tatweel
            t = t.replace(HARAKAT_RE, "")
            t = t.replace(ALEF_RE, "ا")
            t = t.replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
            return t.filter { it.isLetterOrDigit() }.lowercase()
        }
    }
}
