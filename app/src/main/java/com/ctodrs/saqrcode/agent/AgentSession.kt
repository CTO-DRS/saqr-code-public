package com.ctodrs.saqrcode.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.git.GitService
import com.ctodrs.saqrcode.git.Redact
import com.ctodrs.saqrcode.terminal.LinuxEnv
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** A proposed file change awaiting (or past) approval. */
data class FileChange(val path: String, val before: String?, val after: String?) {
    val isDelete get() = after == null
    val isNew get() = before == null
}

/** One entry of the agent's live plan (maintained by the model through `update_plan`). */
data class PlanItem(val title: String, val status: String) {
    val done get() = status == "done"
    val active get() = status == "in_progress"
}

sealed class ChatItem {
    val key: Long = nextKey++
    val createdAt: Long = System.currentTimeMillis()

    /** [text] is what the bubble shows (e.g. "/fix"); the model may have received an expanded prompt. */
    class User(val text: String) : ChatItem() {
        /** Checkpoint recorded before this message's turn changed files; null when none (or restored). */
        var checkpointId by mutableStateOf<String?>(null)
    }
    class Assistant : ChatItem() {
        var text by mutableStateOf("")
        var streaming by mutableStateOf(true)
    }
    class Tool(val name: String, val summary: String) : ChatItem() {
        var result by mutableStateOf<String?>(null)
        var ok by mutableStateOf(true)
        var durationMs by mutableLongStateOf(-1L)
    }
    /** File changes to approve, or (when [action] is set) a non-file action such as `git push`. */
    class Approval(val changes: List<FileChange>, val action: String? = null, val actionKind: ActionKind = ActionKind.PUSH) : ChatItem() {
        enum class ActionKind { PUSH, COMMIT }
        var decision by mutableStateOf<Boolean?>(null)
        var durationMs by mutableLongStateOf(-1L)
        val deferred = CompletableDeferred<Boolean>()
    }
    /** An `ask_user` card. While [status] is PENDING the agent loop waits on [deferred]. */
    class AskUser(val request: AskRequest, val argsJson: String, val restored: Boolean = false) : ChatItem() {
        enum class Status { PENDING, ANSWERED, SKIPPED, CANCELLED }
        var answers by mutableStateOf(AskUserTool.initialAnswers(request))
        var status by mutableStateOf(Status.PENDING)
        var durationMs by mutableLongStateOf(-1L)
        val deferred = CompletableDeferred<String>()
        val canSubmit get() = AskUserTool.canSubmit(request, answers)
        fun update(q: AskQuestion, a: AskAnswer) { if (status == Status.PENDING) answers = answers + (q.id to a) }
    }
    class Notice(val text: String, val isError: Boolean = false, val kind: Kind = if (isError) Kind.ERROR else Kind.INFO, val count: Int = -1) : ChatItem() {
        enum class Kind { INFO, WARNING, ERROR, STOPPED, STEP_LIMIT, RESTORED }
    }

    companion object { private var nextKey = 1L }
}

/**
 * The agent loop: stream a model turn, execute its tool calls, feed results back, repeat
 * until the model answers without tools (or limits hit). File changes are gated by
 * approval (always for 2+ files, or for every change when "approve all" is on) and every
 * applied batch gets a restore point first.
 */
class AgentSession(
    val projectName: String,
    private val projectDir: File,
    checkpointRoot: File,
    private val settings: SettingsStore,
    private val linux: LinuxEnv,
    private val git: GitService = GitService(null, settings.accounts),
    pendingAskFile: File = File(checkpointRoot.parentFile ?: checkpointRoot, "pending_ask/$projectName.json"),
    val ledger: UsageLedger = UsageLedger(File(checkpointRoot.parentFile ?: checkpointRoot, "usage/usage.json")),
) {
    val items = mutableStateListOf<ChatItem>()
    var running by mutableStateOf(false); private set
    /** Model round-trips used by the current/last turn (bounded by [MAX_STEPS]). */
    var step by mutableIntStateOf(0); private set
    /** Rough context size in tokens (chars / 3.6) of system prompt + history. */
    var contextTokens by mutableIntStateOf(0); private set
    val plan = mutableStateListOf<PlanItem>()
    val checkpoints = Checkpoints(projectDir, File(checkpointRoot, projectName))
    /** Tokens used in this chat session, per model. */
    val sessionUsage = mutableStateMapOf<String, Usage>()
    /** Bumps on every recorded model call so the meter can re-read today / this month. */
    var usageVersion by mutableIntStateOf(0); private set
    /** The user message whose turn is running (checkpoints attach to it). */
    private var currentUser: ChatItem.User? = null
    /** Told to the model with the next message, e.g. that the user restored a checkpoint. */
    private var pendingNote: String? = null

    private val history = ArrayList<Msg>()
    private val touched = LinkedHashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val pendingAsk = PendingAskStore(pendingAskFile)

    init {
        // The app was killed while a question was open: show the card again. Its answers are
        // sent as a normal message, since the loop that asked no longer exists.
        pendingAsk.load()?.let { items += ChatItem.AskUser(it, "", restored = true) }
    }

    /** Send [text] to the agent; the chat bubble shows [display] (e.g. the slash command as typed). */
    fun send(text: String, display: String = text) {
        if (running || text.isBlank()) return
        // typing instead of answering a card restored after a restart dismisses it
        items.filterIsInstance<ChatItem.AskUser>().filter { it.restored && it.status == ChatItem.AskUser.Status.PENDING }.forEach {
            it.status = ChatItem.AskUser.Status.CANCELLED; pendingAsk.clear()
        }
        val user = ChatItem.User(display.trim().ifEmpty { text.trim() })
        items += user
        currentUser = user
        val note = pendingNote?.let { "[$it]\n\n" }.orEmpty()
        pendingNote = null
        history += Msg.User(note + text.trim())
        running = true
        step = 0
        job = scope.launch {
            try { loop() }
            catch (e: CancellationException) { items += ChatItem.Notice("⏹", kind = ChatItem.Notice.Kind.STOPPED) }
            catch (e: Exception) { items += ChatItem.Notice(e.message ?: e.toString(), isError = true) }
            finally {
                items.filterIsInstance<ChatItem.Approval>().filter { it.decision == null }.forEach { it.deferred.cancel() }
                items.filterIsInstance<ChatItem.AskUser>().filter { it.status == ChatItem.AskUser.Status.PENDING && !it.restored }.forEach {
                    it.status = ChatItem.AskUser.Status.CANCELLED; it.deferred.cancel(); pendingAsk.clear()
                }
                items.filterIsInstance<ChatItem.Assistant>().forEach { it.streaming = false }
                items.filterIsInstance<ChatItem.Tool>().filter { it.result == null }.forEach {
                    it.ok = false; it.result = "(stopped)"; it.durationMs = System.currentTimeMillis() - it.createdAt
                }
                running = false
            }
        }
    }

    fun stop() { job?.cancel() }

    fun clear() {
        stop(); pendingAsk.clear(); items.clear(); history.clear(); touched.clear(); plan.clear(); step = 0; contextTokens = 0
        sessionUsage.clear(); currentUser = null; pendingNote = null; usageVersion++
    }

    /** Revert the project to just before [point]'s turn (and drop it and every newer checkpoint). */
    fun restoreCheckpoint(point: Checkpoints.Point): Checkpoints.Plan {
        val plan = checkpoints.restore(point)
        val remaining = checkpoints.list().map { it.id }.toSet()
        items.filterIsInstance<ChatItem.User>().forEach { if (it.checkpointId != null && it.checkpointId !in remaining) it.checkpointId = null }
        items += ChatItem.Notice(point.prompt, kind = ChatItem.Notice.Kind.RESTORED, count = plan.changedCount)
        val files = (plan.restored + plan.deleted).take(30).joinToString(", ")
        pendingNote = "Note: the user restored the project to the checkpoint taken before their message \"${point.prompt}\". " +
            "Files reverted: ${files.ifEmpty { "none" }}. Re-read files before editing them."
        return plan
    }

    /** Record one model call's usage in this session and in the persistent ledger. */
    private suspend fun recordUsage(model: String, u: Usage) {
        sessionUsage[model] = (sessionUsage[model] ?: Usage.ZERO) + u
        withContext(Dispatchers.IO) { runCatching { ledger.record(model, u) } }
        usageVersion++
    }

    fun decide(item: ChatItem.Approval, approve: Boolean) {
        if (item.decision != null) return
        item.decision = approve
        item.deferred.complete(approve)
    }

    /** Submit ([skip] = false) or skip an `ask_user` card. */
    fun answer(item: ChatItem.AskUser, skip: Boolean) {
        if (item.status != ChatItem.AskUser.Status.PENDING) return
        if (!skip && !item.canSubmit) return
        item.status = if (skip) ChatItem.AskUser.Status.SKIPPED else ChatItem.AskUser.Status.ANSWERED
        item.durationMs = System.currentTimeMillis() - item.createdAt
        pendingAsk.clear()
        if (item.restored) {
            send(AskUserTool.asUserMessage(item.request, if (skip) null else item.answers))
        } else {
            item.deferred.complete(if (skip) AskUserTool.skipped() else AskUserTool.serialize(item.request, item.answers))
        }
    }

    private suspend fun askUser(a: JSONObject?): String {
        val req = AskUserTool.parse(a)
            ?: return "Error: ask_user needs a non-empty `questions` array; each question needs `id`, `prompt` and `kind`."
        val card = ChatItem.AskUser(req, a.toString())
        items += card
        withContext(Dispatchers.IO) { pendingAsk.save(card.argsJson) }
        return card.deferred.await()
    }

    private fun client(): Pair<LlmClient, String> {
        val id = settings.activeProvider
        val def = Providers.byId(id)
        val cfg = settings.config(id)
        require(cfg.model.isNotBlank()) { "No model selected for ${def.displayName}" }
        return LlmClient(def, cfg.baseUrl, cfg.model, settings.apiKey(id)) to cfg.model
    }

    private suspend fun loop() {
        val (llm, model) = client()
        for (step in 0 until MAX_STEPS) {
            trimHistory()
            this.step = step + 1
            val bubble = ChatItem.Assistant()
            items += bubble
            val sys = withContext(Dispatchers.IO) { SystemPrompt.build(projectDir, touched, linux.ready) }
            val inputChars = sys.length + history.sumOf { size(it) }
            contextTokens = (inputChars / 3.6).toInt()
            val streamed = StringBuffer()
            val turn = llm.complete(sys, history, Tools.specs, object : StreamListener {
                override fun onText(delta: String) {
                    streamed.append(delta)
                    scope.launch { bubble.text = streamed.toString() }
                }
            })
            val used = turn.usage ?: UsageParser.estimate(inputChars, turn.text.length + turn.toolCalls.sumOf { it.argsJson.length + it.name.length })
            if (!used.estimated && used.input > 0) contextTokens = used.input.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            recordUsage(model, used)
            // make sure the bubble shows the final text even if a delta launch is pending
            bubble.text = turn.text
            bubble.streaming = false
            if (turn.text.isBlank()) items.remove(bubble)
            history += Msg.Assistant(turn.text, turn.toolCalls)
            if (turn.toolCalls.isEmpty()) return
            executeTools(turn.toolCalls)
        }
        items += ChatItem.Notice("Step limit reached ($MAX_STEPS). Send a message to continue.", kind = ChatItem.Notice.Kind.STEP_LIMIT)
    }

    private suspend fun executeTools(calls: List<ToolCall>) {
        val parsed = calls.map { c -> c to (runCatching { JSONObject(c.argsJson) }.getOrNull()) }
        val results = HashMap<String, String>()

        // 1) Prepare all mutating calls of this turn as one batch.
        val changes = LinkedHashMap<String, FileChange>() // path -> cumulative change
        val changeCallIds = HashMap<String, String>()      // callId -> path
        withContext(Dispatchers.IO) {
            for ((c, a) in parsed) {
                if (c.name !in Tools.MUTATING) continue
                if (a == null) { results[c.id] = "Error: invalid JSON arguments (output may have been cut off). Split large files into smaller writes."; continue }
                try {
                    val f = Tools.resolve(projectDir, a.optString("path"))
                    val rel = Tools.rel(projectDir, f)
                    val prev = changes[rel]
                    val current = if (prev != null) prev.after else if (f.isFile) f.readText() else null
                    val after: String? = when (c.name) {
                        Tools.WRITE -> a.optString("content")
                        Tools.EDIT -> {
                            requireNotNull(current) { "File does not exist: $rel" }
                            Tools.applyEdit(current, a.optString("old_string"), a.optString("new_string"), a.optBoolean("replace_all"))
                        }
                        else -> { requireNotNull(current) { "File does not exist: $rel" }; null }
                    }
                    changes[rel] = FileChange(rel, prev?.before ?: (if (f.isFile) f.readText() else null), after)
                    changeCallIds[c.id] = rel
                } catch (e: Exception) {
                    results[c.id] = "Error: ${e.message}"
                }
            }
        }

        if (changes.isNotEmpty()) {
            val needApproval = settings.approveAll || changes.size >= 2
            val approved = if (needApproval) {
                val card = ChatItem.Approval(changes.values.toList())
                items += card
                card.deferred.await().also { card.durationMs = System.currentTimeMillis() - card.createdAt }
            } else true
            if (approved) {
                val user = currentUser
                val point = withContext(Dispatchers.IO) {
                    val cp = runCatching {
                        checkpoints.record(user?.key?.toString() ?: "turn", user?.text ?: changes.keys.joinToString(", "), changes.keys)
                    }.getOrNull()
                    for (ch in changes.values) {
                        val f = File(projectDir, ch.path)
                        if (ch.after == null) f.delete() else { f.parentFile?.mkdirs(); f.writeText(ch.after) }
                    }
                    cp
                }
                if (point != null && user != null) user.checkpointId = point.id
                touched += changes.keys
                if (!needApproval) {
                    val ch = changes.values.first()
                    items += ChatItem.Approval(listOf(ch)).also { it.decision = true; it.durationMs = 0; it.deferred.complete(true) }
                }
                changeCallIds.forEach { (id, path) ->
                    val ch = changes[path]!!
                    results[id] = when {
                        ch.after == null -> "Deleted $path."
                        else -> "OK: $path saved (${ch.after.lines().size} lines)."
                    }
                }
            } else {
                changeCallIds.keys.forEach { results[it] = "The user REJECTED these changes. Nothing was written. Ask what they would like instead." }
            }
        }

        // 2) Run the rest in order.
        for ((c, a) in parsed) {
            if (c.name in Tools.MUTATING) continue
            if (c.name == Tools.PLAN) { results[c.id] = updatePlan(a); continue }
            if (c.name == Tools.ASK) { results[c.id] = askUser(a); continue }
            val args = a ?: JSONObject()
            val item = ChatItem.Tool(c.name, Tools.summarizeArgs(c.name, args))
            items += item
            val out = try {
                when (c.name) {
                    Tools.GIT_PUSH -> gitPush(args)
                    Tools.GIT_COMMIT -> gitCommit(args)
                    else -> runTool(c.name, args)
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { item.ok = false; "Error: " + Redact.text(e.message ?: e.toString(), settings.githubToken) }
            if (out.startsWith("Error") || out.startsWith("Blocked") || Regex("""^\[exit [1-9]""").containsMatchIn(out) || out.startsWith("[timed out]")) item.ok = false
            item.durationMs = System.currentTimeMillis() - item.createdAt
            item.result = out
            results[c.id] = out
        }

        for (c in calls) history += Msg.ToolResult(c.id, c.name, results[c.id] ?: "Error: not executed")
    }

    private fun updatePlan(a: JSONObject?): String {
        val arr = a?.optJSONArray("items") ?: return "Error: `items` array is required."
        val next = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim().ifEmpty { return@mapNotNull null }
            val st = o.optString("status", "pending").let { if (it in setOf("pending", "in_progress", "done")) it else "pending" }
            PlanItem(title.take(160), st)
        }.take(20)
        plan.clear(); plan.addAll(next)
        return "Plan updated (${next.count { it.done }}/${next.size} done)."
    }

    /** `git_push` always needs an explicit yes from the user, whatever the approval mode. */
    private suspend fun gitPush(a: JSONObject): String {
        val summary = withContext(Dispatchers.IO) { GitTools.describePush(a, projectDir, git) }
        val card = ChatItem.Approval(emptyList(), action = summary)
        items += card
        val ok = card.deferred.await().also { card.durationMs = System.currentTimeMillis() - card.createdAt }
        if (!ok) return "The user REJECTED the push. Nothing was pushed."
        return withContext(Dispatchers.IO) { GitTools.push(a, projectDir, git) }
    }

    /** `git_commit` also waits for an explicit yes, showing the message that will be committed. */
    private suspend fun gitCommit(a: JSONObject): String {
        val msg = a.optString("message").trim()
        if (msg.isEmpty()) return "Error: `message` is required."
        val card = ChatItem.Approval(emptyList(), action = "git commit -m \"" + msg.lineSequence().first() + "\"" +
            msg.lineSequence().drop(1).joinToString("\n").let { if (it.isBlank()) "" else "\n\n" + it.trim() },
            actionKind = ChatItem.Approval.ActionKind.COMMIT)
        items += card
        val ok = card.deferred.await().also { card.durationMs = System.currentTimeMillis() - card.createdAt }
        if (!ok) return "The user REJECTED the commit. Nothing was committed. Ask what they would like to change."
        return runTool(Tools.GIT_COMMIT, a)
    }

    private suspend fun runTool(name: String, a: JSONObject): String = withContext(Dispatchers.IO) {
        when (name) {
            in Tools.GIT -> GitTools.run(name, a, projectDir, git, settings.gitName, settings.gitEmail)
            Tools.LIST -> Tools.listFiles(projectDir, a.optString("path", "."), a.optBoolean("recursive", true))
            Tools.READ -> Tools.readFile(projectDir, a.optString("path"),
                a.optInt("start_line", 0).takeIf { it > 0 }, a.optInt("end_line", 0).takeIf { it > 0 })
            Tools.SEARCH -> Tools.search(projectDir, a.optString("query"), a.optBoolean("regex"), a.optString("path", "."))
            Tools.RUN -> {
                val cmd = a.optString("command")
                val v = SafetyPolicy.check(cmd)
                if (!v.allowed) "Blocked by safety policy: ${v.reason}. Do not retry this command."
                else {
                    val r = linux.exec(projectDir, cmd, a.optLong("timeout_sec", 120).coerceIn(5, 600))
                    buildString {
                        append(if (r.timedOut) "[timed out]\n" else "[exit ${r.exitCode}]\n")
                        append(r.output.ifBlank { "(no output)" })
                    }
                }
            }
            else -> "Error: unknown tool $name"
        }
    }

    /** Keep the context bounded: shrink old tool outputs first. */
    private fun trimHistory() {
        var total = history.sumOf { size(it) }
        var i = 0
        while (total > MAX_CONTEXT_CHARS && i < history.size - 6) {
            val m = history[i]
            if (m is Msg.ToolResult && m.content.length > 300) {
                total -= m.content.length - 60
                history[i] = m.copy(content = m.content.take(60) + "…[old output trimmed]")
            }
            i++
        }
    }

    private fun size(m: Msg) = when (m) {
        is Msg.User -> m.text.length
        is Msg.Assistant -> m.text.length + m.toolCalls.sumOf { it.argsJson.length }
        is Msg.ToolResult -> m.content.length
    }

    companion object {
        const val MAX_STEPS = 40
        const val MAX_CONTEXT_CHARS = 180_000
    }
}

object SystemPrompt {
    fun build(projectDir: File, touched: Collection<String>, linuxReady: Boolean): String {
        val rules = File(projectDir, "AGENTS.md").takeIf { it.isFile }?.readText()?.take(4000)
        return buildString {
            appendLine("""You are Saqr (صقر), an expert software engineer working as a coding agent inside the Saqr Code Android app.
You operate on the user's project through tools. The project root is the current directory (also mounted at /workspace in the terminal).

# Language
- Always reply in the language the user writes in. If they write Arabic (any dialect: Gulf/Khaleeji, Saudi, Egyptian, Levantine, or Modern Standard), reply in clear Modern Standard Arabic with a light friendly tone, and understand dialect words (e.g. "سوّ" = make, "أبي/ابغى" = I want, "وش/ايش" = what, "زين" = good, "عدّل" = edit, "شغّل" = run).
- Write code, identifiers, file names and commit messages in English. Code comments follow the user's language only if they ask.

# How to work
- Explore first when the request depends on existing code (list_files, search, read_file). Don't guess file contents.
- Make changes with write_file (new files / full rewrites) or edit_file (targeted edits). Always put `path` first in arguments.
- ZERO LAZY CODE: never write placeholders such as "// ...rest unchanged" or "TODO: implement". Write complete, working files. Split very large files into several smaller files.
- Group related file changes in the same turn: changes touching 2+ files are shown to the user as one plan for approval. If the user rejects, stop and ask what they want.
- Verify when practical by running commands (tests, linters, `node file.js`, `python3 file.py`).
- When a decision from the user materially changes the work (stack/platform choice, design direction, scope) and you can't infer it, call ask_user instead of asking in prose: at most ~4 short questions, each with a recommended option and sensible defaults, written in the user's language. Don't ask about things you can decide yourself or discover from the code. If the answer is {"skipped":true}, go with your recommended defaults.
- For tasks with 3+ distinct steps, call update_plan first with a short step list, then update it as steps finish (statuses: pending, in_progress, done).
- If the user mentions a file as @path, read it before answering.
- Be concise. When done, give a short summary of what changed and how to run it. Do not paste whole files back to the user.

# Git
- Use the git_* tools (git_status, git_diff, git_log, git_commit, git_branch, git_pull, git_push) rather than running `git` in the terminal; they use the user's linked account and commit identity.
- Only commit or push when the user asks. git_push always shows the user an approval card; never force-push unless asked.

# Terminal
""")
            if (linuxReady) appendLine("- run_command executes in Alpine Linux (proot, no root on the phone). Install packages with `apk add` (e.g. `apk add nodejs npm python3 git`). Servers must bind 127.0.0.1. Commands are non-interactive; pass -y flags.")
            else appendLine("- The Linux environment is NOT installed yet; run_command uses Android's limited /system/bin/sh (toybox). If the task needs node/python/etc., tell the user to install Linux from the Terminal tab.")
            appendLine("- Dangerous commands (rm -rf /, curl|sh, eval, sudo, etc.) are blocked by policy.")
            appendLine()
            appendLine("# PROJECT MAP")
            appendLine(Tools.projectMap(projectDir))
            if (touched.isNotEmpty()) {
                appendLine()
                appendLine("# FILES CREATED / MODIFIED IN THIS SESSION")
                touched.forEach { appendLine("- $it") }
            }
            if (rules != null) {
                appendLine()
                appendLine("# PROJECT-SPECIFIC RULES (AGENTS.md)")
                appendLine(rules)
            }
        }
    }
}
