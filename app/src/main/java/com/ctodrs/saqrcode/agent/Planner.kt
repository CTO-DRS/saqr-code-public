package com.ctodrs.saqrcode.agent

import org.json.JSONArray
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

    /**
     * A parsed plan: ordered task titles. v0.16: optionally each task's [roles] id and its
     * [deps] (0-based indices of earlier tasks) — plans without them parse exactly as before.
     */
    data class Plan(val tasks: List<String>, val roles: List<String?> = emptyList(), val deps: List<List<Int>> = emptyList()) {
        val valid get() = tasks.isNotEmpty()
        fun roleOf(i: Int): String? = roles.getOrNull(i)
        fun depsOf(i: Int): List<Int> = deps.getOrNull(i).orEmpty()
        val hasTeamFields get() = roles.any { it != null } || deps.any { it.isNotEmpty() }
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
        // v0.16: optional "id", "role" and "dependsOn" per task; entries without a title are dropped
        val kept = (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val t = obj.optString("title").trim()
            if (t.isEmpty()) null else obj
        }.take(MAX_TEAM_TASKS)
        if (kept.isEmpty()) return null
        val tasks = kept.map { it.optString("title").trim().take(120) }
        val idIndex = HashMap<String, Int>()
        kept.forEachIndexed { i, obj -> obj.optString("id").trim().lowercase().takeIf { it.isNotEmpty() }?.let { idIndex.putIfAbsent(it, i) } }
        fun ref(v: Any?): Int? = when (v) {
            is Number -> v.toInt() - 1
            is String -> {
                val k = v.trim().lowercase()
                idIndex[k] ?: k.removePrefix("t").removePrefix("#").toIntOrNull()?.let { it - 1 }
            }
            else -> null
        }
        val roles = kept.map { obj -> obj.optString("role").trim().lowercase().takeIf { it.isNotEmpty() } }
        val deps = kept.mapIndexed { i, obj ->
            val raw = obj.opt("dependsOn") ?: obj.opt("depends_on")
            val list = when (raw) {
                is JSONArray -> (0 until raw.length()).mapNotNull { ref(raw.opt(it)) }
                null -> emptyList()
                else -> listOfNotNull(ref(raw))
            }
            list.filter { it in tasks.indices && it != i }.distinct()
        }
        val team = roles.any { it != null } || deps.any { it.isNotEmpty() }
        // a classic plan keeps the classic 6-task cap; a team plan may use up to MAX_TEAM_TASKS
        return if (!team) Plan(tasks.take(MAX_TASKS)) else Plan(tasks, roles, deps)
    }

    /**
     * v0.16 Chief mode: the planner also assigns each task a role (from [roles]) and its
     * dependencies, so independent work is visible to the Chief. Old-style answers still parse.
     */
    fun teamSystem(roles: Collection<AgentRole>): String = SYSTEM.substringBefore("Rules:") +
        "Rules:\n" +
        "- Each task MAY also have \"id\" (\"t1\", \"t2\"…), \"role\" and \"dependsOn\" (ids of earlier tasks it needs):\n" +
        "  {\"tasks\":[{\"id\":\"t1\",\"title\":\"…\",\"role\":\"architect\"},{\"id\":\"t2\",\"title\":\"…\",\"role\":\"developer\",\"dependsOn\":[\"t1\"]}]}\n" +
        "- Allowed roles: " + roles.filter { it.kind != AgentRole.Kind.PLANNING }.joinToString(", ") { it.id } + ". Use only these; use the fewest tasks that do the job.\n" +
        "- 2 to $MAX_TEAM_TASKS tasks. Reviews come after the work they review. No cycles.\n" +
        "- Titles are imperative and short (< 80 chars), in the same language as the user's request.\n" +
        "- Do not write code or file contents. Plans only."

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
    /** v0.16: a team plan (with roles/dependencies) may hold a few more tasks. */
    const val MAX_TEAM_TASKS = 8
    const val MIN_WORDS = 6
    const val MIN_CHARS = 40
}
