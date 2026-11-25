package com.ctodrs.saqrcode.agent.memory

import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.ContextCompactor
import com.ctodrs.saqrcode.agent.HistoryRebuilder
import com.ctodrs.saqrcode.agent.MemoryBank
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.git.Redact
import org.json.JSONObject

/*
 * v0.17 «محرك الذاكرة» · Saqr Memory Engine — three layers on top of the v0.13 memory bank:
 *
 *  1. Session  — the chat itself (+ ContextCompactor inside a turn) and, new, a rolling summary
 *                of the session's old turns stored with the session file ([SessionMemory]).
 *  2. Project  — `.saqr/memory.json`: typed entries (decision, architecture, dependency,
 *                convention, lesson, note) with source, timestamps and file refs; lessons are
 *                captured automatically when a run turns failing checks green ([Lessons]).
 *  3. Global   — an app-private, cross-project store of the user's own preferences, only when
 *                the user enabled it and only through an explicit `scope: "global"` save or a
 *                promotion from the Memory sheet. Project memory never enters it.
 *
 * Retrieval injects only the top-K relevant entries ([MemoryRecall]) when the setting is on;
 * off, the prompt gets the whole bank exactly as before. A secret guard sits in front of every
 * layer ([MemorySafety]). Pure Kotlin, unit-tested in V017FeaturesTest.
 */

/** The secret guard every layer shares: the v0.13 heuristic plus the user's real configured secrets (via [Redact]). */
object MemorySafety {
    /** False when [text] looks like a secret or contains one of [secrets] (API keys, tokens the user configured). */
    fun allowed(text: String, vararg secrets: String?): Boolean =
        !MemoryBank.looksLikeSecret(text) && Redact.text(text, *secrets) == text

    /** Redacts known secrets and drops any line that still looks like one (for auto-captured text such as test output). */
    fun scrub(text: String, vararg secrets: String?): String =
        Redact.text(text, *secrets).lineSequence().filterNot { MemoryBank.looksLikeSecret(it) }.joinToString("\n").trim()

    const val REFUSED = "Refused: this looks like a secret (password/token/key). Memory is plain JSON on disk — never store secrets."
}

object MemoryEngine {

    data class Config(val relevance: Boolean = false, val topK: Int = MemoryRecall.DEFAULT_K, val global: Boolean = false)

    const val GLOBAL_BUDGET = 1_000
    const val GLOBAL_HEADER = "[Long-term user preferences — all projects]"
    const val GLOBAL_OFF = "Refused: global (cross-project) memory is off. Saved nothing. The user can enable it in Settings → Memory engine; " +
        "until then save it for this project (scope \"project\")."

    /**
     * The `# MEMORY` block for the system prompt. Relevance off → the whole project bank, exactly
     * the v0.13 rendering. On → pinned preferences + the top-K entries for [query].
     * The global layer is appended only when [Config.global] is on.
     */
    fun block(project: MemoryBank, global: MemoryBank?, cfg: Config, query: String, index: MemoryRecall.Index? = null): String {
        val p = if (!cfg.relevance) project.render()
        else project.renderEntries(MemoryRecall.select(project.all, query, cfg.topK, index))
        val g = if (cfg.global && global != null && !global.isEmpty()) renderGlobal(global) else ""
        return listOf(p, g).filter { it.isNotBlank() }.joinToString("\n")
    }

    fun renderGlobal(global: MemoryBank, maxChars: Int = GLOBAL_BUDGET): String {
        val sb = StringBuilder(GLOBAL_HEADER).append('\n')
        for (e in global.all) {
            val line = "- ${e.key.trim()} — ${e.flatValue}"
            if (sb.length + line.length > maxChars) break
            sb.append(line).append('\n')
        }
        return if (sb.length == GLOBAL_HEADER.length + 1) "" else sb.toString().trimEnd()
    }

    /** `files` as an array or a comma-separated string. */
    fun files(a: JSONObject): List<String> {
        a.optJSONArray("files")?.let { arr -> return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() } }
        return a.optString("files").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * The `memory_save` tool: routes to the project bank (default) or — only with
     * `scope: "global"` and the setting on — the global bank. Every layer refuses secrets,
     * including the user's real configured keys/tokens ([secrets]).
     */
    fun save(a: JSONObject, project: MemoryBank, global: MemoryBank?, globalEnabled: Boolean, vararg secrets: String?): String {
        val key = a.optString("key")
        val value = a.optString("value")
        if (!MemorySafety.allowed(key, *secrets) || !MemorySafety.allowed(value, *secrets)) return MemorySafety.REFUSED
        val type = MemoryBank.Type.byId(a.optString("type").takeIf { it.isNotBlank() })
        val kind = if (a.has("kind")) MemoryBank.Kind.byId(a.optString("kind")) else type?.kind ?: MemoryBank.Kind.FACT
        val scope = a.optString("scope", "project").trim().lowercase()
        if (scope == "global") {
            if (!globalEnabled || global == null) return GLOBAL_OFF
            return global.save(key, value, MemoryBank.Kind.PREFERENCE, MemoryBank.Type.PREFERENCE, "agent").let {
                if (it.startsWith("Remembered")) "$it (global — applies to all projects)" else it
            }
        }
        return project.save(key, value, kind, type, "agent", files(a))
    }

    /** `memory_forget`: the project bank as before; the global bank too when the model asks for scope "global" and it is on. */
    fun forget(a: JSONObject, project: MemoryBank, global: MemoryBank?, globalEnabled: Boolean): String {
        val key = a.optString("key")
        if (a.optString("scope").trim().lowercase() == "global") {
            if (!globalEnabled || global == null) return GLOBAL_OFF.replace("Saved nothing", "Nothing removed")
            return global.forget(key)
        }
        return project.forget(key)
    }

    /**
     * Promote a project preference to the global layer (the user's explicit approval from the
     * Memory sheet). Only preferences, only without file refs, only when the layer is on.
     */
    fun promote(e: MemoryBank.Entry, global: MemoryBank?, globalEnabled: Boolean): String {
        if (!globalEnabled || global == null) return GLOBAL_OFF
        if (e.kind != MemoryBank.Kind.PREFERENCE) return "Refused: only preferences can become global."
        if (e.files.isNotEmpty()) return "Refused: global memory keeps user preferences only — no project files."
        return global.save(e.key, e.value, MemoryBank.Kind.PREFERENCE, MemoryBank.Type.PREFERENCE, "user")
    }
}

/**
 * Error → solution lessons. When a run's completion gate goes from FAILED to PASSED, the first
 * meaningful error line becomes the lesson's signature (line numbers, addresses and long
 * numbers stripped so the same error matches next time) and the fix — files changed since the
 * failure plus the coder's own one-line summary — its solution. Matching lessons are surfaced
 * to the Debugger and to the coder's `[Verifier]` feedback.
 */
object Lessons {
    private val ERROR_LINE = Regex("""(?i)(\berror\b|\bexception\b|\bfailed\b|\bfailure\b|assert\w*|traceback|unresolved|cannot find|not found|undefined|expected .* but|خطأ|فشل)""")
    private val NOISE = Regex("""^\s*(\[exit -?\d+]|\[timed out]|## |>|at |\s*$)""")

    /** Normalized error signature from the failing checks' output, or null when nothing looks like an error. */
    fun signature(lint: String?, tests: String?): String? {
        val text = listOfNotNull(tests, lint).joinToString("\n")
        // the first error line that says something (a bare "FAILED" line names no error)
        return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !NOISE.containsMatchIn(it) && ERROR_LINE.containsMatchIn(it) }
            .map { normalizeError(it) }
            .firstOrNull { MemoryRecall.tokens(it).size >= 2 }
    }

    fun normalizeError(line: String): String = line
        .replace(Regex("""(?:[\w.-]+/)+([\w.-]+)"""), "$1")       // paths → basename
        .replace(Regex(""":\d+(:\d+)?"""), "")                  // :line:col
        .replace(Regex("""\(\d+(,\s*\d+)?\)"""), "")             // (line, col)
        .replace(Regex("""\b0x[0-9a-fA-F]+\b"""), "")
        .replace(Regex("""\b\d{3,}\b"""), "N")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(110)

    /** The lesson's solution text: what changed and the coder's own summary. */
    fun solution(files: Collection<String>, coderSummary: String): String = buildString {
        val summary = coderSummary.lineSequence().map { it.trim().trimStart('#', '-', '*', ' ') }.firstOrNull { it.length > 3 }.orEmpty().take(240)
        append("Fixed")
        if (files.isNotEmpty()) append(" by changing ").append(files.take(6).joinToString(", "))
        if (summary.isNotEmpty()) append(": ").append(summary)
        append('.')
    }

    /** Lessons whose signature matches [errorText] (≥ 2 shared tokens, or all of a short signature), best first. */
    fun matching(bank: MemoryBank, errorText: String, max: Int = 3): List<MemoryBank.Entry> {
        val lessons = bank.byType(MemoryBank.Type.LESSON)
        if (lessons.isEmpty() || errorText.isBlank()) return emptyList()
        val errTokens = MemoryRecall.tokens(errorText).toSet()
        return MemoryRecall.Index(lessons).rank(errorText).map { it.first }.filter { e ->
            val sig = MemoryRecall.tokens(e.key.removePrefix("lesson:")).toSet()
            val shared = sig.count { it in errTokens }
            shared >= minOf(2, sig.size)
        }.take(max)
    }

    /** The section appended for the Debugger / the `[Verifier]` feedback; empty when no lesson matches. */
    fun section(lessons: List<MemoryBank.Entry>): String = if (lessons.isEmpty()) "" else buildString {
        append("\n\n## Lessons from earlier fixes in this project (same error seen before)\n")
        lessons.forEach { append("- When: ").append(it.key.removePrefix("lesson:").trim()).append(" → ").append(it.flatValue).append('\n') }
        append("Check whether the same fix applies before trying something new.")
    }
}

/** v0.17 session layer: the rolling summary kept with a session. [turns] user turns are covered; the kept part starts at the user turn whose prompt starts with [anchor]. */
data class SessionSummary(val text: String, val turns: Int, val anchor: String, val at: Long) {
    fun toJson(): JSONObject = JSONObject().put("text", text).put("turns", turns).put("anchor", anchor).put("at", at)

    companion object {
        fun fromJson(o: JSONObject?): SessionSummary? {
            o ?: return null
            val t = o.optString("text").trim()
            val n = o.optInt("turns", 0)
            if (t.isEmpty() || n <= 0) return null
            return SessionSummary(t, n, o.optString("anchor"), o.optLong("at"))
        }
    }
}

object SessionMemory {
    /** History (chars) after which old turns are rolled into the summary. */
    const val ROLL_CHARS = 60_000
    /** The newest user turns always stay verbatim. */
    const val KEEP_TURNS = 4
    const val ANCHOR_LEN = 80
    const val MARKER = "[SESSION SUMMARY OF EARLIER TURNS]"

    fun anchorOf(u: ChatItem.User) = u.prompt.take(ANCHOR_LEN)

    private fun userIndices(items: List<ChatItem>) = items.indices.filter { items[it] is ChatItem.User }

    /** Item index where the verbatim part starts, or null when [s] no longer fits the items (edited, restored, other session). */
    fun start(items: List<ChatItem>, s: SessionSummary?): Int? {
        s ?: return null
        val users = userIndices(items)
        if (s.turns >= users.size) return null
        val idx = users[s.turns]
        return if (anchorOf(items[idx] as ChatItem.User) == s.anchor) idx else null
    }

    /** The model's history: summary note + verbatim recent turns when the summary fits; otherwise the full rebuild (pre-v0.17 behaviour). */
    fun rebuild(items: List<ChatItem>, s: SessionSummary?): List<Msg> {
        val i = start(items, s) ?: return HistoryRebuilder.rebuild(items)
        return listOf(Msg.User(MARKER + "\n" + s!!.text)) + HistoryRebuilder.rebuild(items.subList(i, items.size))
    }

    data class Roll(val toItem: Int, val turns: Int, val anchor: String, val rendered: String)

    /**
     * When the verbatim part is over [threshold] chars and has more than [keep] user turns, the
     * turns before the last [keep] are rolled into a new summary (with the previous summary as
     * its first input — that is what makes it rolling). Null when nothing needs rolling.
     */
    fun planRoll(items: List<ChatItem>, current: SessionSummary?, threshold: Int = ROLL_CHARS, keep: Int = KEEP_TURNS): Roll? {
        val from = start(items, current) ?: 0
        val prev = if (start(items, current) != null) current else null
        val kept = items.subList(from, items.size)
        val chars = HistoryRebuilder.rebuild(kept).sumOf { m ->
            when (m) { is Msg.User -> m.text.length; is Msg.Assistant -> m.text.length + m.toolCalls.sumOf { it.argsJson.length }; is Msg.ToolResult -> m.content.length }
        }
        if (chars <= threshold) return null
        val users = userIndices(items).filter { it >= from }
        if (users.size <= keep) return null
        val cutItem = users[users.size - keep]
        val turnsBefore = userIndices(items).count { it < cutItem }
        val body = ContextCompactor.renderForSummary(HistoryRebuilder.rebuild(items.subList(from, cutItem)))
        val rendered = (prev?.let { "PREVIOUS SUMMARY (keep what still matters):\n${it.text}\n\nNEWER TURNS:\n" } ?: "") + body
        return Roll(cutItem, turnsBefore, anchorOf(items[cutItem] as ChatItem.User), rendered)
    }

    fun apply(roll: Roll, summary: String, now: Long = System.currentTimeMillis()): SessionSummary? =
        summary.trim().takeIf { it.length >= 40 }?.let { SessionSummary(it, roll.turns, roll.anchor, now) }
}
