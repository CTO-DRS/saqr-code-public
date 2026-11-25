package com.ctodrs.saqrcode.agent

/**
 * v0.16 «فريق الوكلاء» · the small decision objects of team collaboration: what context a role
 * receives, when a failed subtask is retried or handed to the Debugger, how a rejected review is
 * escalated, when two roles' edits conflict, and how spend is counted per role.
 * Pure logic, unit-tested in V016FeaturesTest; [ChiefRunner] and [AgentSession] wire them in.
 */

/** Context scoping: a role gets the goal, its subtask, capped outputs of its dependencies and a file list — never the whole chat. */
object RoleContext {
    const val GOAL_CAP = 1_500
    const val DEP_CAP = 1_200
    const val DEPS_TOTAL = 4_000
    const val FILES_CAP = 30
    const val FEEDBACK_CAP = 2_500
    const val MAX = 9_000

    fun build(goal: String, task: Subtask, deps: List<Subtask>, files: List<String>, feedback: String? = null): String {
        val out = StringBuilder()
        out.appendLine("## Goal (the user's request)")
        out.appendLine(goal.trim().let { if (it.length > GOAL_CAP) it.take(GOAL_CAP) + " …(truncated)" else it }.ifEmpty { "(none)" })
        out.appendLine()
        out.appendLine("## Your subtask [${task.id} · ${task.role.nameEn}]")
        out.appendLine(task.title)
        if (deps.isNotEmpty()) {
            out.appendLine()
            out.appendLine("## Results from the roles you depend on")
            var used = 0
            var omitted = 0
            for (d in deps) {
                val body = d.output.trim().ifEmpty { "(no report)" }
                val capped = if (body.length > DEP_CAP) body.take(DEP_CAP) + " …(truncated)" else body
                if (used + capped.length > DEPS_TOTAL) { omitted++; continue }
                used += capped.length
                out.appendLine("### ${d.role.nameEn} · ${d.title} (${d.status.id})")
                out.appendLine(capped)
            }
            if (omitted > 0) out.appendLine("…($omitted more dependency report(s) omitted to keep the context small)")
        }
        val fs = files.filter { it.isNotBlank() }.distinct()
        if (fs.isNotEmpty()) {
            out.appendLine()
            out.appendLine("## Relevant files")
            fs.take(FILES_CAP).forEach { out.appendLine("- $it") }
            if (fs.size > FILES_CAP) out.appendLine("- …(${fs.size - FILES_CAP} more)")
        }
        if (!feedback.isNullOrBlank()) {
            out.appendLine()
            out.appendLine("## Feedback you must address now")
            out.appendLine(feedback.trim().take(FEEDBACK_CAP))
        }
        out.appendLine()
        out.append("Do your subtask now. Finish with your short report (no tool calls).")
        val s = out.toString()
        return if (s.length > MAX) s.take(MAX) + "\n…(context capped)" else s
    }
}

/** Failed subtasks: retry once, then hand to the Debugger once, then fail honestly. */
object Reassignment {
    enum class Decision { RETRY, REASSIGN_DEBUGGER, FAIL }

    /** [failures] already counts the failure that just happened. */
    fun decide(failures: Int, reassigned: Boolean, role: AgentRole, debuggerEnabled: Boolean): Decision = when {
        reassigned -> Decision.FAIL
        failures < 2 -> Decision.RETRY
        debuggerEnabled && role != AgentRole.DEBUGGER -> Decision.REASSIGN_DEBUGGER
        else -> Decision.FAIL
    }
}

/** A rejected review: the author fixes it first; a second rejection is a conflict the Chief resolves. */
object Collaboration {
    enum class Action { FIX_BY_AUTHOR, REASSIGN_DEBUGGER, ASK_USER }

    /** [rejections] counts the rejection that just happened (1 = first). */
    fun onReject(rejections: Int, debuggerUsed: Boolean, debuggerEnabled: Boolean): Action = when {
        rejections <= 1 -> Action.FIX_BY_AUTHOR
        rejections == 2 && debuggerEnabled && !debuggerUsed -> Action.REASSIGN_DEBUGGER
        else -> Action.ASK_USER
    }

    /** Upper bound of review rounds for one verdict subtask (first + author fix + debugger + one last look). */
    const val MAX_REVIEW_ROUNDS = 4
}

/** Two roles disagreeing: one undid the other's edit on the same file, or a reviewer rejected twice. */
data class TeamConflict(
    val kind: Kind,
    val file: String?,
    val roles: List<AgentRole>,
    val detail: String,
    val taskIds: List<String> = emptyList(),
) {
    enum class Kind { FILE, REJECTED_TWICE }
}

object ConflictDetector {
    data class Edit(val taskId: String, val role: AgentRole, val change: FileChange)

    private fun lines(s: String?): Set<String> = s?.lineSequence()?.map { it.trim() }?.filter { it.length >= 3 }?.toSet().orEmpty()

    /**
     * File conflicts among [edits] (in the order they were applied): a later edit by a different
     * role that removes lines an earlier role added, or deletes a file an earlier role wrote.
     * Edits by the same role, or edits that only add, never conflict.
     */
    fun fileConflicts(edits: List<Edit>): List<TeamConflict> {
        val out = ArrayList<TeamConflict>()
        val seen = HashSet<String>()
        edits.groupBy { it.change.path }.forEach { (path, list) ->
            for (j in list.indices) for (i in 0 until j) {
                val a = list[i]; val b = list[j]
                if (a.role == b.role || a.taskId == b.taskId) continue
                val added = lines(a.change.after) - lines(a.change.before)
                val removed = lines(b.change.before) - lines(b.change.after)
                val undone = added.intersect(removed)
                val deleted = b.change.after == null && a.change.after != null
                if (undone.isEmpty() && !deleted) continue
                val key = "$path|${a.taskId}|${b.taskId}"
                if (!seen.add(key)) continue
                out += TeamConflict(
                    TeamConflict.Kind.FILE, path, listOf(a.role, b.role),
                    if (deleted) "${b.role.nameEn} deleted $path after ${a.role.nameEn} wrote it"
                    else "${b.role.nameEn} removed ${undone.size} line(s) ${a.role.nameEn} added in $path",
                    listOf(a.taskId, b.taskId),
                )
            }
        }
        return out
    }

    /** Conflicts that involve [taskId] (the subtask that just finished). */
    fun newConflicts(edits: List<Edit>, taskId: String): List<TeamConflict> = fileConflicts(edits).filter { it.taskIds.lastOrNull() == taskId }
}

/** Per-role spend: aggregation, the compact chat line codec and the before-start estimate. */
object TeamCost {
    data class RoleCost(val role: AgentRole, val usage: Usage, val costUsd: Double?)

    /** Costs per role (registry order); a role's cost is null when one of its models is unpriced. */
    fun aggregate(byRole: Map<AgentRole, Map<String, Usage>>, prices: List<ModelPrice>): List<RoleCost> =
        AgentRoles.all.mapNotNull { r ->
            val m = byRole[r]?.filterValues { it.total > 0 } ?: return@mapNotNull null
            if (m.isEmpty()) return@mapNotNull null
            RoleCost(r, m.values.fold(Usage.ZERO) { a, u -> a + u }, PriceTable.cost(m, prices))
        }

    /** Total priced spend; null when any role with tokens is unpriced. */
    fun total(list: List<RoleCost>): Double? {
        var t = 0.0
        for (c in list) t += c.costUsd ?: return null
        return t
    }

    /** Persisted notice text: `role:in:out:cost;…` (cost "-" when unpriced) — rendered localized by the chat. */
    fun encode(list: List<RoleCost>): String = list.joinToString(";") { c ->
        "${c.role.id}:${c.usage.input}:${c.usage.output}:" + (c.costUsd?.let { String.format(java.util.Locale.US, "%.6f", it) } ?: "-")
    }

    data class Line(val role: AgentRole, val tokens: Long, val costUsd: Double?)

    fun decode(text: String): List<Line> = text.split(';').mapNotNull { part ->
        val p = part.split(':')
        if (p.size != 4) return@mapNotNull null
        val r = AgentRoles.byId(p[0]) ?: return@mapNotNull null
        Line(r, (p[1].toLongOrNull() ?: 0L) + (p[2].toLongOrNull() ?: 0L), p[3].toDoubleOrNull())
    }

    /** One compact line, e.g. `Developer 12.3k tok $0.0120 · Reviewer 2.1k tok $0.0010`. */
    fun line(lines: List<Line>, name: (AgentRole) -> String = { it.nameEn }): String = lines.joinToString(" · ") { l ->
        "${name(l.role)} ${tokens(l.tokens)} tok" + (l.costUsd?.let { " " + usd(it) } ?: "")
    }

    fun tokens(n: Long): String = if (n >= 1000) String.format(java.util.Locale.US, "%.1fk", n / 1000.0) else n.toString()

    fun usd(d: Double): String = "$" + String.format(java.util.Locale.US, if (d >= 1) "%.2f" else "%.4f", d)

    // ---- before-start estimate: plan size × role tier price (deliberately rough, labelled as such)
    const val EST_INPUT_PER_STEP = 8_000L
    const val EST_OUTPUT_PER_STEP = 700L

    /** Typical steps a role uses (not its cap): workers 6, readers 3, verdict/planning 1. */
    fun expectedSteps(role: AgentRole): Int = when (role.kind) {
        AgentRole.Kind.WORKER -> minOf(6, role.maxSteps)
        AgentRole.Kind.READER -> minOf(3, role.maxSteps)
        AgentRole.Kind.VERDICT, AgentRole.Kind.PLANNING -> 1
    }

    /** Estimated USD for running [roles] (one entry per subtask); null when any model is unpriced. */
    fun estimate(roles: List<AgentRole>, modelOf: (AgentRole) -> String, prices: List<ModelPrice>): Double? {
        var sum = 0.0
        for (r in roles) {
            val steps = expectedSteps(r)
            val u = Usage(EST_INPUT_PER_STEP * steps, EST_OUTPUT_PER_STEP * steps)
            sum += PriceTable.cost(modelOf(r), u, prices) ?: return null
        }
        return sum
    }
}

/** Merges the subtasks' reports into the team's final answer (honest about failures). */
object TeamResult {
    const val PER_TASK = 600

    fun merge(g: TaskGraph, arabic: Boolean): String = buildString {
        val done = g.tasks.count { it.status == SubtaskStatus.DONE }
        appendLine(if (arabic) "**نتيجة الفريق** — أُنجزت $done من ${g.tasks.size} مهام." else "**Team result** — $done of ${g.tasks.size} subtasks done.")
        for (t in g.tasks) {
            val mark = when (t.status) { SubtaskStatus.DONE -> "✓"; SubtaskStatus.FAILED -> "✗"; SubtaskStatus.BLOCKED -> "⏸"; else -> "…" }
            appendLine()
            appendLine("$mark **${t.role.displayName(arabic)}** · ${t.title}")
            val body = t.output.trim()
            if (body.isNotEmpty()) appendLine(if (body.length > PER_TASK) body.take(PER_TASK) + " …" else body)
            if (t.note.isNotBlank() && t.status != SubtaskStatus.DONE) appendLine("_${t.note.take(200)}_")
            else if (t.note.isNotBlank() && t.status == SubtaskStatus.DONE && t.role.kind == AgentRole.Kind.VERDICT) appendLine("_${t.note.take(200)}_")
        }
        val files = g.tasks.flatMap { it.files }.distinct()
        if (files.isNotEmpty()) {
            appendLine()
            append(if (arabic) "الملفات: " else "Files: ")
            append(files.take(20).joinToString(", "))
            if (files.size > 20) append(" …")
        }
    }.trim()
}

/** Arabic script in the user's text decides the language of the team's own messages. */
object TeamLang {
    fun isArabic(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        return text.count { it in '\u0600'..'\u06FF' } * 3 >= letters
    }
}
