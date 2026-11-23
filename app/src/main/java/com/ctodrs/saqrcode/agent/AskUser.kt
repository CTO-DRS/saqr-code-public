package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * `ask_user`: the model asks the user one to a few structured questions (stack choice, design
 * direction, scope…). The agent loop pauses on an in-chat card until the user submits or skips.
 * Everything in this file is pure (no Android / Compose) so it is unit-tested on the JVM.
 */

enum class AskKind { SINGLE, MULTI, TEXT, CONFIRM;
    companion object {
        /** Unknown or missing kinds fall back to free text so a sloppy call never breaks the chat. */
        fun parse(s: String?): AskKind = when (s?.trim()?.lowercase()) {
            "single", "radio", "choice", "select" -> SINGLE
            "multi", "multiple", "checkbox", "checkboxes" -> MULTI
            "confirm", "yes_no", "yesno", "boolean", "bool" -> CONFIRM
            else -> TEXT
        }
    }
}

data class AskOption(val id: String, val label: String, val description: String? = null, val recommended: Boolean = false)

data class AskQuestion(
    val id: String,
    val prompt: String,
    val kind: AskKind,
    val options: List<AskOption> = emptyList(),
    val allowOther: Boolean = false,
    val default: List<String> = emptyList(),
    val required: Boolean = true,
)

data class AskRequest(val title: String?, val questions: List<AskQuestion>, val submitLabel: String?)

/**
 * The user's (in-progress) answer to one question. [selected] holds option ids (for CONFIRM:
 * "yes" / "no"); [other] is non-null while "Other…" is chosen; [text] is the TEXT answer.
 */
data class AskAnswer(val selected: List<String> = emptyList(), val other: String? = null, val text: String = "")

object AskUserTool {
    const val YES = "yes"
    const val NO = "no"
    const val MAX_QUESTIONS = 6
    const val MAX_OPTIONS = 8

    // ------------------------------------------------------------------ schema

    val spec: ToolSpec by lazy {
        fun p(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)
        val option = JSONObject().put("type", "object")
            .put("properties", JSONObject()
                .put("id", p("string", "Stable short id, e.g. 'web'"))
                .put("label", p("string", "Short option title in the user's language"))
                .put("description", p("string", "One short line explaining the trade-off"))
                .put("recommended", p("boolean", "Mark your recommended option (at most one per question)")))
            .put("required", JSONArray(listOf("id", "label")))
        val question = JSONObject().put("type", "object")
            .put("properties", JSONObject()
                .put("id", p("string", "Stable short id used as the answer key"))
                .put("prompt", p("string", "The question, in the user's language"))
                .put("kind", JSONObject().put("type", "string").put("enum", JSONArray(listOf("single", "multi", "text", "confirm")))
                    .put("description", "single = pick one, multi = pick any, text = free text, confirm = yes/no"))
                .put("options", JSONObject().put("type", "array").put("items", option).put("description", "Choices for single/multi (2-6 is ideal)"))
                .put("allow_other", p("boolean", "Offer an 'Other…' free-text choice"))
                .put("default", JSONObject().put("description", "Pre-selected option id (single/confirm: 'yes'|'no'), list of ids (multi) or text")))
            .put("required", JSONArray(listOf("id", "prompt", "kind")))
        ToolSpec(Tools.ASK,
            "Ask the user up to 4 short structured questions on an in-chat card and WAIT for the answers. Use only when the user's decision materially changes the work " +
                "(stack/platform, design direction, scope). Mark a recommended option and give sensible defaults. The result is " +
                "{\"answers\":{questionId: value}} or {\"skipped\":true} (then proceed with your recommended defaults).",
            JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("title", p("string", "Card title, e.g. 'A few choices before I start'"))
                    .put("questions", JSONObject().put("type", "array").put("items", question))
                    .put("submit_label", p("string", "Submit button text (default: Approve)")))
                .put("required", JSONArray(listOf("questions"))))
    }

    // ------------------------------------------------------------------ parsing

    /** Parses tool arguments. Returns null when there is no usable question at all. */
    fun parse(a: JSONObject?): AskRequest? {
        if (a == null) return null
        val arr = a.optJSONArray("questions") ?: a.optJSONObject("question")?.let { JSONArray().put(it) } ?: return null
        val usedIds = HashSet<String>()
        val qs = (0 until arr.length()).mapNotNull { i -> parseQuestion(arr.opt(i), i, usedIds) }.take(MAX_QUESTIONS)
        if (qs.isEmpty()) return null
        return AskRequest(
            title = a.optString("title").trim().takeIf { it.isNotEmpty() }?.take(140),
            questions = qs,
            submitLabel = a.optString("submit_label").trim().takeIf { it.isNotEmpty() }?.take(40),
        )
    }

    private fun parseQuestion(raw: Any?, index: Int, usedIds: MutableSet<String>): AskQuestion? {
        val o = when (raw) {
            is JSONObject -> raw
            is String -> if (raw.isBlank()) return null else JSONObject().put("prompt", raw) // bare string => text question
            else -> return null
        }
        val prompt = o.optString("prompt").ifBlank { o.optString("question") }.ifBlank { o.optString("label") }.trim()
        var id = o.optString("id").trim().ifEmpty { "q${index + 1}" }
        while (!usedIds.add(id)) id += "_${index + 1}"
        if (prompt.isEmpty() && !o.has("options")) return null
        var kind = AskKind.parse(o.optString("kind").ifBlank { o.optString("type") })
        val optIds = HashSet<String>()
        val options = (o.optJSONArray("options") ?: JSONArray()).let { oa ->
            (0 until oa.length()).mapNotNull { j -> parseOption(oa.opt(j), j, optIds) }.take(MAX_OPTIONS)
        }
        // A kind that was never stated but options exist reads naturally as "pick one".
        if (o.optString("kind").isBlank() && o.optString("type").isBlank() && options.size >= 2) kind = AskKind.SINGLE
        val allowOther = o.optBoolean("allow_other", false)
        if ((kind == AskKind.SINGLE || kind == AskKind.MULTI) && options.isEmpty()) kind = AskKind.TEXT
        val defaults = parseDefault(o.opt("default"), kind, options)
        return AskQuestion(
            id = id.take(64),
            prompt = prompt.ifEmpty { id }.take(300),
            kind = kind,
            options = if (kind == AskKind.SINGLE || kind == AskKind.MULTI) options else emptyList(),
            allowOther = allowOther && (kind == AskKind.SINGLE || kind == AskKind.MULTI),
            default = defaults,
            required = o.optBoolean("required", true),
        )
    }

    private fun parseOption(raw: Any?, j: Int, used: MutableSet<String>): AskOption? {
        val o = when (raw) {
            is JSONObject -> raw
            is String -> if (raw.isBlank()) return null else JSONObject().put("label", raw)
            else -> return null
        }
        val label = o.optString("label").ifBlank { o.optString("title") }.trim()
        val rawId = o.optString("id").trim().ifEmpty { slug(label).ifEmpty { "o${j + 1}" } }
        if (label.isEmpty() && o.optString("id").isBlank()) return null
        var id = rawId
        while (!used.add(id)) id += "_${j + 1}"
        return AskOption(
            id = id.take(64), label = label.ifEmpty { rawId }.take(120),
            description = o.optString("description").trim().takeIf { it.isNotEmpty() }?.take(240),
            recommended = o.optBoolean("recommended", false),
        )
    }

    private fun parseDefault(d: Any?, kind: AskKind, options: List<AskOption>): List<String> {
        val raw: List<String> = when (d) {
            null, JSONObject.NULL -> emptyList()
            is JSONArray -> (0 until d.length()).map { d.opt(it).toString() }
            is Boolean -> listOf(if (d) YES else NO)
            else -> listOf(d.toString())
        }.map { it.trim() }.filter { it.isNotEmpty() }
        return when (kind) {
            AskKind.TEXT -> raw.take(1)
            AskKind.CONFIRM -> raw.firstOrNull()?.lowercase()?.let {
                when (it) { "yes", "true", "y", "1", "نعم" -> listOf(YES); "no", "false", "n", "0", "لا" -> listOf(NO); else -> emptyList() }
            } ?: emptyList()
            else -> {
                // accept ids or labels; drop unknowns
                val ids = raw.mapNotNull { v -> options.firstOrNull { it.id == v }?.id ?: options.firstOrNull { it.label.equals(v, true) }?.id }.distinct()
                if (kind == AskKind.SINGLE) ids.take(1) else ids
            }
        }
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]+"), "_").trim('_').take(32)

    // ------------------------------------------------------------------ answers

    /** Starting answers: explicit defaults, otherwise the recommended option(s). */
    fun initialAnswers(req: AskRequest): Map<String, AskAnswer> = req.questions.associate { q ->
        q.id to when (q.kind) {
            AskKind.TEXT -> AskAnswer(text = q.default.firstOrNull().orEmpty())
            AskKind.CONFIRM -> AskAnswer(selected = q.default)
            AskKind.SINGLE -> AskAnswer(selected = q.default.ifEmpty { q.options.filter { it.recommended }.take(1).map { it.id } })
            AskKind.MULTI -> AskAnswer(selected = q.default.ifEmpty { q.options.filter { it.recommended }.map { it.id } })
        }
    }

    /** Tap an option (single/multi/confirm). Choosing a regular option in a single question clears "Other". */
    fun toggle(q: AskQuestion, a: AskAnswer, optionId: String): AskAnswer = when (q.kind) {
        AskKind.MULTI -> a.copy(selected = if (optionId in a.selected) a.selected - optionId else a.selected + optionId)
        AskKind.SINGLE -> a.copy(selected = listOf(optionId), other = null)
        AskKind.CONFIRM -> a.copy(selected = listOf(optionId))
        AskKind.TEXT -> a
    }

    /** Toggle "Other…" on/off. In a single question it replaces the selected option. */
    fun toggleOther(q: AskQuestion, a: AskAnswer): AskAnswer = when {
        a.other != null -> a.copy(other = null)
        q.kind == AskKind.SINGLE -> a.copy(selected = emptyList(), other = "")
        else -> a.copy(other = "")
    }

    fun isAnswered(q: AskQuestion, a: AskAnswer?): Boolean {
        a ?: return false
        val otherOk = !a.other.isNullOrBlank()
        return when (q.kind) {
            AskKind.TEXT -> a.text.isNotBlank()
            AskKind.CONFIRM -> a.selected.size == 1 && a.selected[0] in setOf(YES, NO)
            AskKind.SINGLE -> (a.selected.size == 1 && q.options.any { it.id == a.selected[0] } && a.other == null) || (a.selected.isEmpty() && otherOk)
            AskKind.MULTI -> (a.selected.any { id -> q.options.any { it.id == id } } || otherOk) && (a.other == null || otherOk)
        }
    }

    /** Submit is enabled once every required question has a complete answer (and optional ones aren't half-filled). */
    fun canSubmit(req: AskRequest, answers: Map<String, AskAnswer>): Boolean = req.questions.all { q ->
        val a = answers[q.id]
        if (q.required) isAnswered(q, a)
        else a == null || isAnswered(q, a) || isEmpty(q, a)
    }

    private fun isEmpty(q: AskQuestion, a: AskAnswer) = when (q.kind) {
        AskKind.TEXT -> a.text.isBlank()
        else -> a.selected.isEmpty() && a.other == null
    }

    private fun optionJson(o: AskOption) = JSONObject().put("id", o.id).put("label", o.label)

    /** The tool result for the model: `{"answers":{id: value}}`, option labels included. */
    fun serialize(req: AskRequest, answers: Map<String, AskAnswer>): String {
        val out = JSONObject()
        for (q in req.questions) {
            val a = answers[q.id] ?: AskAnswer()
            if (!isAnswered(q, a)) { out.put(q.id, JSONObject.NULL); continue }
            val v: Any = when (q.kind) {
                AskKind.TEXT -> a.text.trim()
                AskKind.CONFIRM -> a.selected.first() == YES
                AskKind.SINGLE -> q.options.firstOrNull { it.id == a.selected.firstOrNull() }?.let { optionJson(it) }
                    ?: JSONObject().put("id", "other").put("label", a.other!!.trim()).put("other", true)
                AskKind.MULTI -> JSONArray().apply {
                    q.options.filter { it.id in a.selected }.forEach { put(optionJson(it)) }
                    a.other?.trim()?.takeIf { it.isNotEmpty() }?.let { put(JSONObject().put("id", "other").put("label", it).put("other", true)) }
                }
            }
            out.put(q.id, v)
        }
        return JSONObject().put("answers", out).toString()
    }

    fun skipped(): String = JSONObject().put("skipped", true)
        .put("note", "The user skipped the questions: proceed with your recommended defaults.").toString()

    /** Human-readable answer for the collapsed summary and for re-sending after an app restart. */
    fun display(q: AskQuestion, a: AskAnswer?, yes: String = "Yes", no: String = "No"): String {
        a ?: return "—"
        if (!isAnswered(q, a)) return "—"
        return when (q.kind) {
            AskKind.TEXT -> a.text.trim()
            AskKind.CONFIRM -> if (a.selected.first() == YES) yes else no
            AskKind.SINGLE, AskKind.MULTI -> (q.options.filter { it.id in a.selected }.map { it.label } +
                listOfNotNull(a.other?.trim()?.takeIf { it.isNotEmpty() })).joinToString(", ")
        }
    }

    /** After an app restart the loop that asked is gone, so the answers go back as a normal user message. */
    fun asUserMessage(req: AskRequest, answers: Map<String, AskAnswer>?): String = buildString {
        if (answers == null) { append("(Skipped your questions — go with your recommended defaults.)"); return@buildString }
        req.title?.let { appendLine(it) }
        req.questions.forEach { q -> appendLine("- ${q.prompt}: ${display(q, answers[q.id])}") }
        append(serialize(req, answers))
    }.trim()
}

/** Keeps an unanswered `ask_user` on disk so it survives an app restart. */
class PendingAskStore(private val file: File) {
    fun save(argsJson: String) = runCatching { file.parentFile?.mkdirs(); file.writeText(argsJson) }.let { }
    fun load(): AskRequest? = runCatching { if (file.isFile) AskUserTool.parse(JSONObject(file.readText())) else null }.getOrNull()
    fun clear() { runCatching { file.delete() } }
}

/**
 * Quick replies: when an assistant reply ends with a numbered list ("1. Web 2. Android" or one per
 * line, Western or Arabic-Indic digits), offer each item as a tappable chip above the composer.
 */
object QuickReplies {
    private val DIGITS = "0-9٠-٩۰-۹"
    private val LINE_ITEM = Regex("""^\s*(?:[-*]\s*)?\(?([$DIGITS]{1,2})\s*[.)\-:]\s+(.+?)\s*$""")
    private val INLINE_ITEM = Regex("""(?:^|\s)\(?([$DIGITS]{1,2})\s*[.)]\s+""")

    fun detect(text: String, max: Int = 6): List<String> {
        val lines = text.trimEnd().lines()
        if (lines.isEmpty()) return emptyList()
        // allow one short closing line after the list ("Which one?" / "أيهما تفضّل؟")
        var end = lines.lastIndex
        while (end >= 0 && lines[end].isBlank()) end--
        if (end < 0) return emptyList()
        if (!LINE_ITEM.matches(lines[end]) && lines[end].length <= 120 && !INLINE_ITEM.containsMatchIn(lines[end])) {
            end--
            while (end >= 0 && lines[end].isBlank()) end--
        }
        if (end < 0) return emptyList()

        // 1) one item per line, walking upward
        val items = ArrayList<Pair<Int, String>>()
        var i = end
        while (i >= 0) {
            val m = LINE_ITEM.matchEntire(lines[i])
            if (m != null) { items.add(0, num(m.groupValues[1]) to m.groupValues[2]); i--; continue }
            // indented continuation/description lines belong to the item above
            if (lines[i].startsWith("  ") || lines[i].startsWith("\t")) { i--; continue }
            break
        }
        // Only offer chips when the reply is actually asking something near the list.
        fun asks(from: Int) = lines.subList(from.coerceAtLeast(0), lines.size).any { it.contains('?') || it.contains('؟') }
        if (items.size >= 2 && sequential(items.map { it.first })) return if (asks(i - 1)) clean(items.map { it.second }, max) else emptyList()

        // 2) inline "1. Web 2. Android 3. iOS" on the last line
        val line = lines[end]
        val ms = INLINE_ITEM.findAll(line).toList()
        if (ms.size >= 2 && sequential(ms.map { num(it.groupValues[1]) }) && asks(end - 1)) {
            val parts = ms.mapIndexed { k, m ->
                line.substring(m.range.last + 1, if (k + 1 < ms.size) ms[k + 1].range.first else line.length)
            }
            return clean(parts, max)
        }
        return emptyList()
    }

    private fun num(s: String): Int = s.map { ch ->
        when (ch) { in '0'..'9' -> ch - '0'; in '٠'..'٩' -> ch - '٠'; in '۰'..'۹' -> ch - '۰'; else -> 0 }
    }.fold(0) { acc, d -> acc * 10 + d }

    private fun sequential(ns: List<Int>) = ns.isNotEmpty() && ns.first() == 1 && ns.zipWithNext().all { (a, b) -> b == a + 1 }

    /** Keep the label: strip markdown and any trailing explanation after " — ", " - " or ":". */
    fun label(raw: String): String {
        var s = raw.replace(Regex("""\*\*|__|`"""), "").trim()
        s = s.split(" — ", " – ", " - ", ": ", "：").first().trim()
        s = s.trimEnd(',', '،', ';', '؛', '.', ' ')
        return s
    }

    private fun clean(raw: List<String>, max: Int): List<String> {
        val out = raw.map { label(it) }.filter { it.isNotEmpty() }
        if (out.size < 2 || out.any { it.length > 60 }) return emptyList()
        return out.distinct().take(max)
    }
}
