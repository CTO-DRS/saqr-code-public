package com.ctodrs.saqrcode.agent

import org.json.JSONObject

/**
 * v0.7/v0.8 · فريق الوكلاء · Team mode (Planner → Coder → Reviewer).
 *
 * The coder is the regular agent loop. After every approved batch of file changes, a second
 * model — the reviewer, optionally a different model id — receives the user's request, the
 * compact diff, and (v0.8) the output of automatic checks (lint/tests) run on the project,
 * and must answer with a strict JSON verdict. Issues go back to the coder as a user message
 * so it fixes them in the same turn (bounded review rounds, loop detector still applies).
 * Pure parsing/building is unit-tested in V07FeaturesTest and V08FeaturesTest.
 */
object Reviewer {

    data class Issue(val file: String, val line: String, val comment: String)
    data class Report(val ok: Boolean, val summary: String, val issues: List<Issue>)

    const val SYSTEM = """You are the reviewer agent in a two-agent coding team (Saqr Code). You get the user's request, the diff of the files the coder just changed, and sometimes the output of automatic checks (lint/tests) run after the changes. Judge correctness only: real bugs, broken logic, missing pieces, syntax errors, wrong file touched. A check failure caused by the diff is a real issue; a pre-existing failure unrelated to the diff is not. Do NOT ask for refactors, renames, style changes or "nice to have" improvements.
Reply with ONLY a JSON object — no markdown fences, no prose:
{"verdict":"ok","summary":"<one short sentence in the user's language>"}
or
{"verdict":"issues","summary":"<one short sentence in the user's language>","issues":[{"file":"<path>","line":"<number or ?>","comment":"<what is wrong and the concrete fix>"}]}
At most 6 issues, most important first. If the diff is unrelated to the request, that is an issue."""

    /**
     * Builds the reviewer prompt for one applied batch. [checks] is the output of automatic
     * lint/test runs (v0.8); pass null or a blank string when none ran. A check that failed
     * while the diff claims to fix it is a real issue, not style.
     */
    fun buildRequest(userRequest: String, diff: String, checks: String? = null): String = buildString {
        appendLine("## User request")
        appendLine(userRequest.take(1_200).ifBlank { "(not captured — judge the diff on its own) " })
        appendLine()
        if (!checks.isNullOrBlank()) {
            appendLine("## Automatic checks (lint/tests) run after the changes")
            appendLine(checks.take(CHECKS_MAX))
            appendLine()
        }
        appendLine("## Applied diff")
        appendLine(diff.take(14_000))
        appendLine()
        appendLine("Review these changes now. Reply with the JSON verdict only.")
    }

    /** Caps the checks section so one noisy test suite cannot drown the diff. */
    const val CHECKS_MAX = 2_500

    fun parse(text: String): Report? {
        val s = text.trim()
            .replace(Regex("""^```[a-zA-Z]*"""), "").removeSuffix("```").trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { JSONObject(s.substring(start, end + 1)) }.getOrNull() ?: return null
        val issues = ArrayList<Issue>()
        o.optJSONArray("issues")?.let { arr ->
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val c = it.optString("comment").trim()
                if (c.isNotEmpty()) issues += Issue(
                    it.optString("file").trim().ifEmpty { "?" },
                    it.optString("line").trim().ifEmpty { "?" },
                    c,
                )
            }
        }
        val verdictOk = o.optString("verdict", "ok").equals("ok", ignoreCase = true)
        return Report(verdictOk && issues.isEmpty(), o.optString("summary").trim(), issues.take(6))
    }

    /** Compact unified diff of an applied batch (for the reviewer and the chat card). */
    fun diffOf(changes: List<FileChange>, maxChars: Int = 12_000): String = buildString {
        for (ch in changes) {
            appendLine("--- ${ch.path} (${when { ch.isDelete -> "deleted"; ch.isNew -> "new file"; else -> "modified" }})")
            when {
                ch.isDelete -> appendLine("(file deleted)")
                ch.isNew -> {
                    val lines = (ch.after ?: "").split('\n')
                    lines.take(80).forEach { appendLine("+ $it") }
                    if (lines.size > 80) appendLine("+ … (${lines.size - 80} more lines)")
                }
                else -> {
                    val diff = Diff.compact(Diff.lines(ch.before ?: "", ch.after ?: ""), context = 2)
                    for (l in diff) when (l) {
                        null -> appendLine("  …")
                        is Diff.Line -> appendLine(when (l.kind) {
                            Diff.Kind.ADD -> "+ "
                            Diff.Kind.DEL -> "- "
                            Diff.Kind.SAME -> "  "
                        } + l.text.take(160))
                    }
                }
            }
            appendLine()
            if (length > maxChars) { appendLine("…(diff truncated)"); break }
        }
    }

    /** The message fed to the coder when the reviewer found issues. */
    fun feedbackMessage(report: Report): String = buildString {
        appendLine("[Reviewer] ${report.summary}".trim())
        if (report.issues.isNotEmpty()) {
            appendLine("Fix these issues now (they are real problems, not style):")
            report.issues.forEach { appendLine("- ${it.file}:${it.line}: ${it.comment}") }
        }
    }

    /**
     * v0.8: the checks block shown to the reviewer — a short header plus the raw report.
     * [results] pairs a check label ("lint", "tests") with its captured output; empty outputs
     * and no-runner cases are dropped so the section stays meaningful.
     */
    fun checksSection(results: List<Pair<String, String>>): String? {
        val useful = results.filter { it.second.isNotBlank() && !it.second.contains("No test runner detected") }
        if (useful.isEmpty()) return null
        return buildString {
            useful.forEach { (label, out) ->
                appendLine("### $label")
                appendLine(out.take(CHECKS_MAX / useful.size.coerceAtLeast(1)))
                appendLine()
            }
        }.trim()
    }
}
