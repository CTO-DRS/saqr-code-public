package com.ctodrs.saqrcode.agent

import com.ctodrs.saqrcode.git.Redact
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * v0.15 «النواة المستقلة» · persistent task state.
 *
 * Each agent run is a task with a file under `<project>/.saqr/tasks/<id>.json`: the goal, the
 * plan, the step, when it started, how much active time and spend it used, and its status.
 * It is written on every step, so after the app is killed [AgentSession.resumeInterrupted]
 * restores the plan, the step budget and the spend instead of starting from zero. A missing
 * or broken file simply means "no task" — sessions from older versions keep working.
 */
data class TaskState(
    val id: String,
    val sessionId: String,
    val goal: String,
    val plan: List<PlanItem> = emptyList(),
    val step: Int = 0,
    val startedAt: Long,
    val updatedAt: Long = startedAt,
    /** Active run time across resumes (wall time between steps while running). */
    val activeMs: Long = 0,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    /** Priced spend in USD; null when no price is known for the models used. */
    val costUsd: Double? = null,
    val status: Status = Status.RUNNING,
    /** Last verification status / stop reason, for the user and the next run. */
    val note: String = "",
    /** v0.16 Chief mode: the team's task graph (null = not a team run, and for every older task file). */
    val team: TaskGraph? = null,
) {
    enum class Status(val id: String) {
        RUNNING("running"), PAUSED("paused"), WAITING_APPROVAL("waiting_approval"),
        DONE("done"), FAILED("failed"), STOPPED("stopped");

        companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: STOPPED }
    }

    /** A run that was cut off mid-flight and can be picked up again. */
    val resumable get() = status == Status.RUNNING || status == Status.WAITING_APPROVAL || status == Status.PAUSED
}

object TaskStateCodec {
    const val VERSION = 1

    fun encode(t: TaskState): String = JSONObject()
        .put("v", VERSION)
        .put("id", t.id)
        .put("session", t.sessionId)
        .put("goal", t.goal.take(2000))
        .put("plan", JSONArray().apply { t.plan.forEach { put(JSONObject().put("title", it.title).put("status", it.status)) } })
        .put("step", t.step)
        .put("startedAt", t.startedAt)
        .put("updatedAt", t.updatedAt)
        .put("activeMs", t.activeMs)
        .put("tokensIn", t.tokensIn)
        .put("tokensOut", t.tokensOut)
        .apply { t.costUsd?.let { put("costUsd", it) } }
        .put("status", t.status.id)
        .put("note", t.note.take(500))
        .apply { t.team?.let { put("team", TaskGraphCodec.toJson(it)) } }
        .toString(2)

    /** Lenient: unknown fields are ignored, missing ones defaulted; null for unreadable input. */
    fun decode(json: String?): TaskState? = runCatching {
        if (json.isNullOrBlank()) return null
        val o = JSONObject(json)
        val id = o.optString("id").trim()
        if (id.isEmpty()) return null
        val arr = o.optJSONArray("plan") ?: JSONArray()
        val plan = (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = p.optString("title").trim().ifEmpty { return@mapNotNull null }
            PlanItem(title, p.optString("status", "pending").let { if (it in setOf("pending", "in_progress", "done")) it else "pending" })
        }
        val started = o.optLong("startedAt", 0L)
        TaskState(
            id = id,
            sessionId = o.optString("session"),
            goal = o.optString("goal"),
            plan = plan,
            step = o.optInt("step", 0).coerceAtLeast(0),
            startedAt = started,
            updatedAt = o.optLong("updatedAt", started),
            activeMs = o.optLong("activeMs", 0L).coerceAtLeast(0),
            tokensIn = o.optLong("tokensIn", 0L).coerceAtLeast(0),
            tokensOut = o.optLong("tokensOut", 0L).coerceAtLeast(0),
            costUsd = if (o.has("costUsd")) o.optDouble("costUsd").takeIf { !it.isNaN() } else null,
            status = TaskState.Status.from(o.optString("status")),
            note = o.optString("note"),
            team = TaskGraphCodec.fromJson(o.optJSONArray("team")),
        )
    }.getOrNull()

    /** A new task id: time-ordered and file-name safe. */
    fun newId(now: Long = System.currentTimeMillis()): String =
        "task-" + now.toString(36) + "-" + (1000 + (Math.random() * 9000).toInt()).toString(36)
}

/** `<project>/.saqr/tasks/` — one JSON file per task, newest kept, oldest pruned past [KEEP]. */
class TaskStore(private val dir: File) {
    private fun fileOf(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    @Synchronized
    fun save(t: TaskState) {
        dir.mkdirs()
        val f = fileOf(t.id)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(TaskStateCodec.encode(t))
        if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
        prune()
    }

    fun load(id: String): TaskState? = fileOf(id).takeIf { it.isFile }?.let { TaskStateCodec.decode(runCatching { it.readText() }.getOrNull()) }

    fun all(): List<TaskState> = (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
        .mapNotNull { TaskStateCodec.decode(runCatching { it.readText() }.getOrNull()) }
        .sortedByDescending { it.updatedAt }

    /** The newest task of [sessionId] that was cut off mid-run, if any. */
    fun latestResumable(sessionId: String): TaskState? = all().firstOrNull { it.sessionId == sessionId }?.takeIf { it.resumable }

    private fun prune() {
        val files = (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return).sortedByDescending { it.lastModified() }
        files.drop(KEEP).forEach { it.delete() }
    }

    companion object { const val KEEP = 50 }
}

/**
 * v0.15 · audit log: one JSON line per tool call in `<project>/.saqr/audit.jsonl` — what ran,
 * its risk, who decided (auto / approved / rejected / blocked), whether it worked and how long
 * it took. Secrets are redacted before anything is written; the file rotates at [MAX_BYTES]
 * (one previous generation kept as `audit.1.jsonl`).
 */
data class AuditEntry(
    val ts: Long,
    val tool: String,
    val risk: Risk,
    val decision: Decision,
    val ok: Boolean,
    val ms: Long,
    val summary: String,
    /** v0.16: the team role that made the call (null = single agent / classic team). */
    val role: String? = null,
) {
    enum class Decision(val id: String) { AUTO("auto"), APPROVED("approved"), REJECTED("rejected"), BLOCKED("blocked") }
}

object AuditLog {
    const val MAX_BYTES = 1_000_000L

    private val tokenShapes = listOf(
        Regex("""\bsk-(ant-)?[A-Za-z0-9_\-]{16,}"""),
        Regex("""\bAIza[0-9A-Za-z_\-]{30,}"""),
        Regex("""\bxox[abpr]-[A-Za-z0-9\-]{10,}"""),
    )
    private val bearer = Regex("""(?i)\bBearer\s+[A-Za-z0-9._\-+/=]{8,}""")
    private val keyValue = Regex("""(?i)\b(api[_-]?key|access[_-]?token|token|secret|password|passwd)(\s*[=:]\s*)[^\s&"',;]+""")

    /** Redacts [Redact]'s patterns, the known [secrets] and common API key shapes. */
    fun redact(text: String, vararg secrets: String?): String {
        var out = Redact.text(text, *secrets)
        for (re in tokenShapes) out = re.replace(out, "***")
        out = bearer.replace(out, "Bearer ***")
        out = keyValue.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + "***" }
        return out
    }

    fun line(e: AuditEntry, vararg secrets: String?): String = JSONObject()
        .put("ts", e.ts)
        .put("tool", e.tool)
        .put("risk", e.risk.name.lowercase())
        .put("decision", e.decision.id)
        .put("ok", e.ok)
        .put("ms", e.ms)
        .put("summary", redact(e.summary.replace('\n', ' ').take(240), *secrets))
        .apply { e.role?.let { put("role", it) } }
        .toString()

    /** Appends one entry, rotating first when the file would pass [maxBytes]. */
    @Synchronized
    fun append(file: File, e: AuditEntry, vararg secrets: String?, maxBytes: Long = MAX_BYTES) {
        val l = line(e, *secrets) + "\n"
        file.parentFile?.mkdirs()
        if (file.isFile && file.length() + l.length > maxBytes) {
            val old = File(file.parentFile, file.nameWithoutExtension + ".1.jsonl")
            old.delete()
            if (!file.renameTo(old)) file.delete()
        }
        file.appendText(l)
    }
}
