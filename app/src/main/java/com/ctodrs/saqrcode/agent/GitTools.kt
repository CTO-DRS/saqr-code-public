package com.ctodrs.saqrcode.agent

import com.ctodrs.saqrcode.git.GitService
import com.ctodrs.saqrcode.git.Redact
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Agent-facing git tools backed by [GitService] (JGit). Everything here is blocking and pure
 * enough to unit-test on the JVM; `git_push` is gated by user approval in [AgentSession].
 */
object GitTools {
    private fun schema(props: JSONObject, required: List<String> = emptyList()) =
        JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required))
    private fun p(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

    val specs: List<ToolSpec> = listOf(
        ToolSpec(Tools.GIT_STATUS, "Show the git status of the project: current branch, upstream ahead/behind, staged/unstaged/untracked/conflicted files.",
            schema(JSONObject())),
        ToolSpec(Tools.GIT_DIFF, "Show a unified diff. Default: unstaged worktree changes (including untracked files). staged=true: what will be committed.",
            schema(JSONObject()
                .put("path", p("string", "Limit to one file (relative path)"))
                .put("staged", p("boolean", "Diff the index against HEAD instead of the worktree")))),
        ToolSpec(Tools.GIT_LOG, "Show recent commits (newest first).",
            schema(JSONObject().put("max_count", p("integer", "How many commits (default 15, max 100)")))),
        ToolSpec(Tools.GIT_COMMIT, "Commit changes. By default stages ALL changes (including new files) first. Use a concise, conventional message. If the repo isn't initialised yet, it is initialised on 'main'.",
            schema(JSONObject()
                .put("message", p("string", "Commit message"))
                .put("paths", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "Only stage these paths (instead of everything)"))
                .put("amend", p("boolean", "Amend the previous commit")), listOf("message"))),
        ToolSpec(Tools.GIT_BRANCH, "List, create, switch or delete branches.",
            schema(JSONObject()
                .put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf("list", "create", "switch", "delete"))).put("description", "Default: list"))
                .put("name", p("string", "Branch name (for create/switch/delete). For switch, 'origin/x' creates a tracking branch."))
                .put("start_point", p("string", "Optional start point for create")))),
        ToolSpec(Tools.GIT_PULL, "Fetch and merge the upstream of the current branch. Uses the linked account's token for the remote host.",
            schema(JSONObject().put("remote", p("string", "Remote name, default 'origin'")))),
        ToolSpec(Tools.GIT_PUSH, "Push a branch to a remote. Always requires the user's approval in the app. Never force unless the user explicitly asked.",
            schema(JSONObject()
                .put("remote", p("string", "Remote name, default 'origin'"))
                .put("branch", p("string", "Branch, default the current branch"))
                .put("force", p("boolean", "Force push (only if the user asked)")))),
    )

    /** Runs every git tool except push (push needs approval first; see [push]). */
    fun run(name: String, a: JSONObject, dir: File, git: GitService, authorName: String, authorEmail: String): String = when (name) {
        Tools.GIT_STATUS -> status(dir, git)
        Tools.GIT_DIFF -> {
            requireRepo(dir, git)
            git.diff(dir, a.optString("path").ifBlank { null }, a.optBoolean("staged"), 60_000).ifBlank { "No differences." }
        }
        Tools.GIT_LOG -> {
            val log = git.log(dir, a.optInt("max_count", 15).coerceIn(1, 100))
            if (log.isEmpty()) "No commits yet." else log.joinToString("\n") { c ->
                val refs = if (c.refs.isEmpty()) "" else " (" + c.refs.joinToString(", ") + ")"
                "${c.shortId}$refs ${fmt(c.time)} ${c.author}: ${c.message}"
            }
        }
        Tools.GIT_COMMIT -> commit(a, dir, git, authorName, authorEmail)
        Tools.GIT_BRANCH -> branch(a, dir, git)
        Tools.GIT_PULL -> {
            requireRepo(dir, git)
            val r = git.pull(dir, a.optString("remote").ifBlank { "origin" }, authorName, authorEmail)
            buildString {
                append("Pull: ${r.status}")
                r.newHead?.let { append(" → ${it.take(7)}") }
                if (r.conflicts.isNotEmpty()) append("\nConflicts (resolve, then git_commit):\n" + r.conflicts.joinToString("\n") { "  $it" })
            }
        }
        else -> "Error: unknown git tool $name"
    }

    /** Human summary of what a push will do, shown on the approval card. */
    fun describePush(a: JSONObject, dir: File, git: GitService): String {
        val remote = a.optString("remote").ifBlank { "origin" }
        val st = git.status(dir, withCounts = false)
        val branch = a.optString("branch").ifBlank { st.branch ?: "?" }
        val url = git.remoteUrl(dir, remote)?.let { Redact.text(it) } ?: "(no remote '$remote')"
        val force = if (a.optBoolean("force")) " --force" else ""
        return "git push$force $remote $branch\n$url" + if (st.ahead > 0) "\n${st.ahead} commit(s) ahead" else ""
    }

    fun push(a: JSONObject, dir: File, git: GitService): String {
        requireRepo(dir, git)
        val remote = a.optString("remote").ifBlank { "origin" }
        val r = git.push(dir, remote, a.optString("branch").ifBlank { null }, a.optBoolean("force"))
        return (if (r.ok) "Pushed to $remote.\n" else "Error: push rejected.\n") + r.lines.joinToString("\n")
    }

    private fun status(dir: File, git: GitService): String {
        val st = git.status(dir, withCounts = false)
        if (!st.isRepo) return "Not a git repository. git_commit initialises one."
        return buildString {
            append("On branch ${st.branch ?: "(detached)"}")
            st.upstream?.let { append(" → $it (ahead ${st.ahead}, behind ${st.behind})") }
            if (!st.hasCommits) append("\nNo commits yet.")
            if (st.state != "SAFE") append("\nState: ${st.state}")
            if (st.clean) { append("\nWorking tree clean."); return@buildString }
            val conflicts = st.conflicts
            if (conflicts.isNotEmpty()) append("\nConflicts:\n" + conflicts.joinToString("\n") { "  ${it.path}" })
            val staged = st.staged.filter { !it.conflict }
            if (staged.isNotEmpty()) append("\nStaged:\n" + staged.joinToString("\n") { "  ${it.staged!!.name.lowercase()}: ${it.path}" })
            val unstaged = st.unstaged.filter { !it.conflict }
            if (unstaged.isNotEmpty()) append("\nNot staged:\n" + unstaged.joinToString("\n") { "  ${it.unstaged!!.name.lowercase()}: ${it.path}" })
        }
    }

    private fun commit(a: JSONObject, dir: File, git: GitService, name: String, email: String): String {
        val msg = a.optString("message").trim()
        if (msg.isEmpty()) return "Error: message is required."
        if (!git.isRepo(dir)) git.init(dir)
        val paths = a.optJSONArray("paths")?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
        if (paths.isNotEmpty()) {
            paths.forEach { Tools.resolve(dir, it) } // reject paths outside the project
            git.stage(dir, paths)
        } else git.stageAll(dir)
        val c = git.commit(dir, msg, name, email, amend = a.optBoolean("amend"))
        return "Committed ${c.shortId}: ${c.message} (as ${c.author} <${c.email}>)"
    }

    private fun branch(a: JSONObject, dir: File, git: GitService): String {
        requireRepo(dir, git)
        val n = a.optString("name").trim()
        return when (a.optString("action").ifBlank { "list" }) {
            "list" -> git.branches(dir).joinToString("\n") { (if (it.current) "* " else "  ") + it.shortName + " " + it.headShort }.ifBlank { "No branches yet (make a first commit)." }
            "create" -> { require(n.isNotEmpty()) { "name is required" }; git.createBranch(dir, n, true, a.optString("start_point").ifBlank { null }); "Created and switched to '$n'." }
            "switch" -> { require(n.isNotEmpty()) { "name is required" }; git.checkout(dir, n); "Switched to '${git.status(dir, false).branch}'." }
            "delete" -> { require(n.isNotEmpty()) { "name is required" }; git.deleteBranch(dir, n); "Deleted branch '$n'." }
            else -> "Error: action must be list, create, switch or delete."
        }
    }

    private fun requireRepo(dir: File, git: GitService) = require(git.isRepo(dir)) { "Not a git repository." }

    private fun fmt(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))
}
