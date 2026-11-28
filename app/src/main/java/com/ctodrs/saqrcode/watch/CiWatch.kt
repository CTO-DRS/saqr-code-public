package com.ctodrs.saqrcode.watch

import org.json.JSONObject

/**
 * v0.1.77 · Quiet CI watch. Looks at the newest GitHub Actions run of one repo and speaks up only
 * when a new run fails, or when a run passes right after a failure. A normal pass says nothing.
 */
object CiWatch {
    data class Run(val id: Long, val completed: Boolean, val conclusion: String, val branch: String, val sha: String)
    data class Msg(val title: String, val body: String)

    private val repoRe = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")
    fun validRepo(s: String) = repoRe.matches(s.trim())

    fun parseLatest(json: String): Run? = runCatching {
        val r = JSONObject(json).getJSONArray("workflow_runs").optJSONObject(0) ?: return null
        Run(r.getLong("id"), r.optString("status") == "completed", r.optString("conclusion"), r.optString("head_branch"), r.optString("head_sha").take(7))
    }.getOrNull()

    /** State kept between checks: "<lastRunId>:<ok|fail>". */
    fun parseState(s: String): Pair<Long, Boolean>? {
        val p = s.split(":"); if (p.size != 2) return null
        return (p[0].toLongOrNull() ?: return null) to (p[1] == "fail")
    }

    fun encodeState(id: Long, failed: Boolean) = "$id:" + if (failed) "fail" else "ok"

    /** Returns the message to show (or null) and the new state to store. */
    fun decide(repo: String, state: String, run: Run?, ar: Boolean): Pair<Msg?, String> {
        if (run == null || !run.completed) return null to state
        val prev = parseState(state)
        if (prev != null && prev.first == run.id) return null to state
        val failed = run.conclusion == "failure" || run.conclusion == "timed_out"
        val newState = encodeState(run.id, failed)
        val where = repo + " · " + run.branch + " · " + run.sha
        val msg = when {
            failed -> Msg(if (ar) "فشل البناء" else "CI failed", where)
            prev?.second == true && run.conclusion == "success" -> Msg(if (ar) "عاد البناء للنجاح" else "CI is green again", where)
            else -> null
        }
        return msg to newState
    }
}
