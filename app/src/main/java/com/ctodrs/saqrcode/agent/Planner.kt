package com.ctodrs.saqrcode.agent

import org.json.JSONObject

/**
 * v0.8 · مرحلة المخطط الصريحة · Explicit planner stage of the agent team.
 *
 * Before the coder loop starts on a substantial request (team mode), a cheap model turns the
 * user's request into a small task plan: titles only, no code. The plan is shown as the
 * progress pill and handed to the coder as context, so it starts with structure instead of
 * improvising. This is distinct from the coder's own `update_plan` tool (which it still uses
 * to mark progress while working). Pure parsing lives in [Planner] and is unit-tested.
 */
object Planner {

    const val SYSTEM = """You are the planner agent of a coding team (Saqr Code app). The user gives a request; you output ONLY a JSON object — no markdown, no prose — with a short ordered task plan for the coder agent:
{"tasks":[{"title":"<short step in the user's language>"},{"title":"<…>"}]}
Rules:
- 2 to 6 tasks, each one concrete step of real work (explore, implement, wire, verify).
- Titles are imperative and short (< 80 chars), in the same language as the user's request.
- No tasks for questions you can decide yourself; if info is missing, make the first task "Explore the relevant files" so the coder checks.
- Do not write code or file contents. Plans only."""

    /** The one-shot user prompt for the planner. */
    fun buildRequest(userRequest: String): String =
        "## Request\n" + userRequest.take(2_000).ifBlank { "(empty — propose a minimal plan to explore the project)" } +
            "\n\nOutput the JSON plan only."

    /** A parsed plan: ordered task titles. */
    data class Plan(val tasks: List<String>) {
        val valid get() = tasks.isNotEmpty()
    }

    /** Extracts `{"tasks":[{"title":…},…]}` from the model text (tolerates fences and prose). */
    fun parse(text: String): Plan? {
        val s = text.trim()
            .replace(Regex("""^```[a-zA-Z]*"""), "").removeSuffix("```").trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { JSONObject(s.substring(start, end + 1)) }.getOrNull() ?: return null
        val arr = o.optJSONArray("tasks") ?: return null
        val tasks = (0 until arr.length()).mapNotNull { i ->
            val t = arr.optJSONObject(i)?.optString("title")?.trim().orEmpty()
            if (t.isEmpty()) null else t.take(120)
        }
        if (tasks.isEmpty()) return null
        return Plan(tasks.take(MAX_TASKS))
    }

    /** The planner's plan rendered into the coder's history. */
    fun planMessage(plan: Plan): String = buildString {
        appendLine("[Planner] Suggested task plan for this request:")
        plan.tasks.forEachIndexed { i, t -> appendLine("${i + 1}. $t") }
        appendLine("Follow it; adjust with update_plan as reality demands. Start with task 1 now.")
    }

    /** To [PlanItem]s for the visible pill (all start pending). */
    fun toPlanItems(plan: Plan): List<PlanItem> = plan.tasks.map { PlanItem(it, "pending") }

    /** Requests that should skip the planner: trivial chat, pure questions, slash-driven flows. */
    fun worthPlanning(userRequest: String): Boolean {
        val t = userRequest.trim()
        if (t.isEmpty() || t.startsWith("/")) return false
        val words = t.split(Regex("\\s+")).size
        return words >= MIN_WORDS || t.length >= MIN_CHARS
    }

    const val MAX_TASKS = 6
    const val MIN_WORDS = 6
    const val MIN_CHARS = 40
}
