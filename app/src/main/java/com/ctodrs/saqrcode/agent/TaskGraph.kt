package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.16 «فريق الوكلاء» · the team's task graph.
 *
 * The Chief turns a request into subtasks, each owned by one [AgentRole] and depending on
 * earlier subtasks. The graph is immutable (every change returns a new graph) so it can be
 * shown as the plan pill, persisted inside [TaskState] and resumed after the app is killed.
 * Pure logic, unit-tested in V016FeaturesTest.
 */
enum class SubtaskStatus(val id: String) {
    PENDING("pending"), RUNNING("running"), BLOCKED("blocked"), DONE("done"), FAILED("failed");

    val finished get() = this == DONE || this == FAILED || this == BLOCKED

    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: PENDING }
}

data class Subtask(
    val id: String,
    val title: String,
    val role: AgentRole,
    val dependsOn: List<String> = emptyList(),
    val status: SubtaskStatus = SubtaskStatus.PENDING,
    /** Failed attempts so far (reassignment rule: twice → Debugger once → failed). */
    val failures: Int = 0,
    /** Already handed to the Debugger once. */
    val reassigned: Boolean = false,
    /** The role's report (capped), handed to dependents as context. */
    val output: String = "",
    /** Files this subtask changed. */
    val files: List<String> = emptyList(),
    /** Why it failed / was blocked, for the user. */
    val note: String = "",
)

class TaskGraph(val tasks: List<Subtask>) {

    private val byId: Map<String, Subtask> = tasks.associateBy { it.id }

    operator fun get(id: String): Subtask? = byId[id]

    val isEmpty get() = tasks.isEmpty()

    /** Dependency ids that point at no task (dropped by [sanitized]). */
    fun unknownDeps(): List<String> = tasks.flatMap { t -> t.dependsOn.filter { it !in byId } }.distinct()

    /** A copy without self-dependencies, duplicates or dependencies on unknown ids. */
    fun sanitized(): TaskGraph = TaskGraph(tasks.map { t ->
        t.copy(dependsOn = t.dependsOn.filter { it != t.id && it in byId }.distinct())
    })

    /** The ids of one dependency cycle (first id repeated at the end), or null when the graph is acyclic. */
    fun findCycle(): List<String>? {
        val state = HashMap<String, Int>() // 1 = on the stack, 2 = done
        val stack = ArrayList<String>()
        fun dfs(id: String): List<String>? {
            state[id] = 1; stack += id
            for (d in byId[id]?.dependsOn.orEmpty()) {
                if (d !in byId) continue
                when (state[d]) {
                    1 -> return stack.subList(stack.indexOf(d), stack.size).toList() + d
                    2 -> {}
                    else -> dfs(d)?.let { return it }
                }
            }
            stack.removeAt(stack.size - 1); state[id] = 2
            return null
        }
        for (t in tasks) if (state[t.id] == null) dfs(t.id)?.let { return it }
        return null
    }

    /** Ids in dependency order (Kahn; ties keep the original order), or null when there is a cycle. */
    fun topoOrder(): List<String>? {
        val indeg = tasks.associate { t -> t.id to t.dependsOn.count { it in byId } }.toMutableMap()
        val out = ArrayList<String>()
        val done = HashSet<String>()
        while (out.size < tasks.size) {
            val next = tasks.firstOrNull { it.id !in done && indeg[it.id] == 0 } ?: return null
            out += next.id; done += next.id
            tasks.filter { next.id in it.dependsOn }.forEach { indeg[it.id] = indeg[it.id]!! - 1 }
        }
        return out
    }

    /** Pending subtasks whose dependencies are all DONE, in graph order. */
    fun ready(): List<Subtask> = tasks.filter { t ->
        t.status == SubtaskStatus.PENDING && t.dependsOn.all { byId[it]?.status == SubtaskStatus.DONE }
    }

    /**
     * The ready subtasks that may run side by side: read-only roles only (readers and verdict
     * roles never change files), at most [limit]. Empty when the first ready subtask is a worker.
     */
    fun independentReady(limit: Int): List<Subtask> {
        val r = ready()
        if (r.isEmpty() || !r.first().role.readOnly) return emptyList()
        return r.filter { it.role.readOnly }.take(limit.coerceAtLeast(1))
    }

    /** Share of subtasks that are finished (done, failed or blocked), 0..100. */
    fun progress(): Int = if (tasks.isEmpty()) 100 else tasks.count { it.status.finished } * 100 / tasks.size

    val allDone get() = tasks.isNotEmpty() && tasks.all { it.status == SubtaskStatus.DONE }
    val anyFailed get() = tasks.any { it.status == SubtaskStatus.FAILED || it.status == SubtaskStatus.BLOCKED }

    fun update(id: String, f: (Subtask) -> Subtask): TaskGraph = TaskGraph(tasks.map { if (it.id == id) f(it) else it })

    fun add(t: Subtask): TaskGraph = TaskGraph(tasks + t)

    /** Ids depending on [id] directly or through other subtasks. */
    fun dependentsOf(id: String): Set<String> {
        val out = LinkedHashSet<String>()
        var frontier = setOf(id)
        while (frontier.isNotEmpty()) {
            val next = tasks.filter { t -> t.id !in out && t.dependsOn.any { it in frontier } }.map { it.id }.toSet()
            out += next; frontier = next
        }
        return out
    }

    /** Every pending dependent of a failed [id] becomes BLOCKED (it can never run). */
    fun blockDependentsOf(id: String): TaskGraph {
        val dep = dependentsOf(id)
        return TaskGraph(tasks.map {
            if (it.id in dep && it.status == SubtaskStatus.PENDING) it.copy(status = SubtaskStatus.BLOCKED, note = "blocked by $id") else it
        })
    }

    /** All dependencies of [id], transitively, in graph order. */
    fun ancestorsOf(id: String): List<Subtask> {
        val seen = LinkedHashSet<String>()
        fun walk(x: String) { byId[x]?.dependsOn?.forEach { if (seen.add(it)) walk(it) } }
        walk(id)
        return tasks.filter { it.id in seen }
    }

    /** After a restart: a subtask cut off mid-run goes back to pending. */
    fun resetRunning(): TaskGraph = TaskGraph(tasks.map { if (it.status == SubtaskStatus.RUNNING) it.copy(status = SubtaskStatus.PENDING) else it })

    /** The plan pill: role-prefixed titles; running → in_progress, done → done, failed/blocked marked in the title. */
    fun toPlanItems(arabic: Boolean = false): List<PlanItem> = tasks.map { t ->
        val mark = when (t.status) { SubtaskStatus.FAILED -> " ✗"; SubtaskStatus.BLOCKED -> " ⏸"; else -> "" }
        PlanItem("${t.role.displayName(arabic)} · ${t.title}$mark".take(160), when (t.status) {
            SubtaskStatus.RUNNING -> "in_progress"
            SubtaskStatus.DONE -> "done"
            else -> "pending"
        })
    }

    companion object {
        const val MAX_TASKS = 10

        /** Subtasks from a planner [plan]: role and dependencies when given, else the [fallback] role in sequence. */
        fun fromPlan(plan: Planner.Plan, fallback: AgentRole, allowed: Set<AgentRole>, sequentialWhenNoDeps: Boolean = true): TaskGraph {
            val n = plan.tasks.size.coerceAtMost(MAX_TASKS)
            val anyDeps = (0 until n).any { plan.depsOf(it).isNotEmpty() }
            val list = (0 until n).map { i ->
                val r = AgentRoles.byId(plan.roleOf(i))?.takeIf { it in allowed && it.kind != AgentRole.Kind.PLANNING } ?: fallback
                val deps = when {
                    anyDeps -> plan.depsOf(i).filter { it in 0 until n && it != i }.map { "t${it + 1}" }
                    sequentialWhenNoDeps && i > 0 -> listOf("t$i")
                    else -> emptyList()
                }
                Subtask("t${i + 1}", plan.tasks[i], r, deps)
            }
            return TaskGraph(list).sanitized()
        }
    }
}

object TaskGraphCodec {
    fun toJson(g: TaskGraph): JSONArray = JSONArray().apply {
        g.tasks.forEach { t ->
            put(JSONObject()
                .put("id", t.id).put("title", t.title).put("role", t.role.id)
                .put("dependsOn", JSONArray(t.dependsOn))
                .put("status", t.status.id)
                .put("failures", t.failures).put("reassigned", t.reassigned)
                .put("output", t.output.take(OUTPUT_KEEP))
                .put("files", JSONArray(t.files.take(40)))
                .put("note", t.note.take(300)))
        }
    }

    /** Lenient: unknown roles fall back to Developer, broken entries are dropped; null when nothing usable. */
    fun fromJson(arr: JSONArray?): TaskGraph? {
        arr ?: return null
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim().ifEmpty { return@mapNotNull null }
            val deps = o.optJSONArray("dependsOn")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            val files = o.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            Subtask(
                id = id, title = o.optString("title").ifBlank { id }, role = AgentRoles.byId(o.optString("role")) ?: AgentRole.DEVELOPER,
                dependsOn = deps, status = SubtaskStatus.from(o.optString("status")),
                failures = o.optInt("failures", 0).coerceAtLeast(0), reassigned = o.optBoolean("reassigned", false),
                output = o.optString("output"), files = files, note = o.optString("note"),
            )
        }
        return if (list.isEmpty()) null else TaskGraph(list)
    }

    const val OUTPUT_KEEP = 2_000
}
