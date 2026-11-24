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
class MemoryBank(private val dir: File) {

    enum class Kind(val id: String) {
        PREFERENCE("preference"), DECISION("decision"), FACT("fact");

        companion object {
            fun byId(id: String?): Kind = entries.firstOrNull { it.id == id } ?: FACT
        }
    }

    data class Entry(
        val key: String,
        val value: String,
        val kind: Kind,
        val createdAt: Long,
        val updatedAt: Long,
    ) {
        /** Single-line rendering of the value (newlines become " · " for the prompt). */
        val flatValue: String get() = value.replace(Regex("\\s+"), " ").trim()
    }

    private val entries = LinkedHashMap<String, Entry>() // normalized key → entry
    private val file get() = File(dir, FILE_NAME)

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
    fun render(maxChars: Int = PROMPT_BUDGET): String {
        val out = StringBuilder()
        for (kind in KIND_ORDER) {
            val group = synchronized(this) { entries.values.filter { it.kind == kind }.sortedByDescending { it.updatedAt } }
            if (group.isEmpty()) continue
            val header = when (kind) {
                Kind.PREFERENCE -> "User preferences"
                Kind.DECISION -> "Decisions"
                Kind.FACT -> "Project facts"
            }
            val lines = ArrayList<String>()
            var used = out.length + header.length + 2
            for (e in group) {
                val line = "- ${e.key.trim()} — ${e.flatValue.take(MAX_VALUE_LEN)}"
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

    // ------------------------------------------------------------------ write

    /**
     * Upsert a memory: the same normalized key updates the existing entry (keeps `createdAt`),
     * a new key appends. Evicts the oldest-updated entry when over [MAX_ENTRIES].
     * Returns a short model-facing result. Secrets are refused.
     */
    fun save(rawKey: String, rawValue: String, kind: Kind): String {
        val key = rawKey.trim()
        val value = rawValue.trim()
        if (key.isEmpty() || value.isEmpty()) return "Error: key and value are required."
        if (key.length > MAX_KEY_LEN) return "Error: key too long (max $MAX_KEY_LEN chars)."
        if (value.length > MAX_VALUE_LEN) return "Error: value too long (max $MAX_VALUE_LEN chars) — keep memories short."
        val secret = looksLikeSecret(key) || looksLikeSecret(value)
        if (secret) return "Refused: this looks like a secret (password/token/key). Memory is plain JSON on disk — never store secrets."
        val nk = normalize(key)
        if (nk.isEmpty()) return "Error: key must contain letters or digits."
        synchronized(this) {
            val now = System.currentTimeMillis()
            val old = entries[nk]
            entries[nk] = Entry(key, value, kind, old?.createdAt ?: now, now)
            while (entries.size > MAX_ENTRIES) {
                val oldest = entries.minByOrNull { it.value.updatedAt }?.key
                if (oldest == null) break
                entries.remove(oldest)
            }
            persist()
        }
        val shown = entries.size
        return "Remembered ($shown/$MAX_ENTRIES)."
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

    // ------------------------------------------------------------------ disk

    private fun load() {
        val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() ?: return
        runCatching {
            val o = JSONObject(text)
            val arr = o.optJSONArray("entries") ?: return
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val key = e.optString("key").trim()
                val value = e.optString("value").trim()
                if (key.isEmpty() || value.isEmpty()) continue
                val nk = normalize(key)
                if (nk.isEmpty() || nk in entries) continue
                entries[nk] = Entry(
                    key = key.take(MAX_KEY_LEN),
                    value = value.take(MAX_VALUE_LEN),
                    kind = Kind.byId(e.optString("kind")),
                    createdAt = e.optLong("createdAt"),
                    updatedAt = e.optLong("updatedAt"),
                )
            }
        } // a corrupt file just means an empty bank; the next save rewrites it
    }

    private fun persist() {
        runCatching {
            dir.mkdirs()
            val arr = JSONArray()
            for (e in all) arr.put(
                JSONObject()
                    .put("key", e.key)
                    .put("value", e.value)
                    .put("kind", e.kind.id)
                    .put("createdAt", e.createdAt)
                    .put("updatedAt", e.updatedAt),
            )
            SessionStore.atomicWrite(file, JSONObject().put("v", SCHEMA).put("entries", arr).toString())
        }
    }

    companion object {
        const val FILE_NAME = "memory.json"
        const val SCHEMA = 1
        const val MAX_ENTRIES = 60
        const val MAX_KEY_LEN = 80
        const val MAX_VALUE_LEN = 500
        const val PROMPT_BUDGET = 2_000

        private val KIND_ORDER = listOf(Kind.PREFERENCE, Kind.DECISION, Kind.FACT)

        /** Patterns that suggest a secret: assignment to a secret-ish name, known token shapes, or Arabic secret words. */
        private val SECRET_PATTERNS = listOf(
            Regex("""(?i)(password|passwd|secret|api[_-]?key|token|private[_-]?key)\s*[:=]"""),
            Regex("""(?u)(كلمة\s*السر|كلمه\s*السر|كلمة\s*المرور|كلمه\s*المرور|رمز\s*الدخول|المفتاح\s*الخاص)"""),
            Regex("""\b(sk-|ghp_|gho_|github_pat_|AKIA|xoxb-|xoxp-)[A-Za-z0-9_\-]{8,}"""),
            Regex("""\b[A-Za-z0-9+/]{40,}\b"""),
        )

        fun looksLikeSecret(s: String) = SECRET_PATTERNS.any { it.containsMatchIn(s) }

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
