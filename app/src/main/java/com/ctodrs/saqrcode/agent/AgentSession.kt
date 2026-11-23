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
import kotlinx.coroutines.delay
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
    /** When the item was created; restored sessions keep their original time. */
    var createdAt: Long = System.currentTimeMillis()

    /**
     * [text] is what the bubble shows (e.g. "/fix"); [prompt] is what the model received (the
     * expanded command, plus any note such as a checkpoint restore).
     */
    class User(val text: String, val images: List<ImagePart> = emptyList(), val prompt: String = text) : ChatItem() {
        /** Checkpoint recorded before this message's turn changed files; null when none (or restored). */
        var checkpointId by mutableStateOf<String?>(null)
    }
    /** One model turn: its text, plus the tool calls it made and their results (so history can be rebuilt from items). */
    class Assistant : ChatItem() {
        var text by mutableStateOf("")
        var streaming by mutableStateOf(true)
        var calls: List<ToolCall> = emptyList()
        val results = LinkedHashMap<String, String>()
        /** Shown in the chat: blank turns that only called tools are hidden. */
        val visible get() = streaming || text.isNotBlank()
    }
    class Tool(val name: String, val summary: String, val callId: String = "") : ChatItem() {
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
    /**
     * An `ask_user` card. While [status] is PENDING the agent loop waits on [deferred]. A [restored]
     * card (from a saved session) resumes the real turn through [callId] when answered.
     */
    class AskUser(val request: AskRequest, val argsJson: String, val restored: Boolean = false, val callId: String? = null) : ChatItem() {
        enum class Status { PENDING, ANSWERED, SKIPPED, CANCELLED }
        var answers by mutableStateOf(AskUserTool.initialAnswers(request))
        var status by mutableStateOf(Status.PENDING)
        var durationMs by mutableLongStateOf(-1L)
        val deferred = CompletableDeferred<String>()
        val canSubmit get() = AskUserTool.canSubmit(request, answers)
        fun update(q: AskQuestion, a: AskAnswer) { if (status == Status.PENDING) answers = answers + (q.id to a) }
    }
    class Notice(val text: String, val isError: Boolean = false, val kind: Kind = if (isError) Kind.ERROR else Kind.INFO, val count: Int = -1) : ChatItem() {
        /** INTERRUPTED: a turn was cut off (app killed / phone off) and can be resumed; RESUMED: it was. COMPACTED: v0.7 context compaction; LOOP: v0.7 loop detector stopped the turn. */
        enum class Kind { INFO, WARNING, ERROR, STOPPED, STEP_LIMIT, RESTORED, INTERRUPTED, RESUMED, COMPACTED, LOOP }
    }

    /** v0.7 team mode: the reviewer's verdict on an applied batch (running → ok / issues / failed). */
    class Review(val model: String) : ChatItem() {
        enum class ReviewStatus { RUNNING, OK, ISSUES, FAILED }
        var status by mutableStateOf(ReviewStatus.RUNNING)
        var summary by mutableStateOf("")
        val issues = mutableStateListOf<Reviewer.Issue>()

        fun finish(report: Reviewer.Report?) {
            if (report == null) {
                status = ReviewStatus.FAILED; summary = ""
            } else {
                status = if (report.ok) ReviewStatus.OK else ReviewStatus.ISSUES
                summary = report.summary
                issues.clear(); issues.addAll(report.issues)
            }
        }
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
    /** v0.6: where this project's chat sessions are saved; null = in-memory only. */
    val store: SessionStore? = null,
    /** v0.6: the background runtime (foreground service, notifications). */
    private val hooks: RunHooks = RunHooks.NONE,
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

    // ---- v0.7 team review / loop guard / compaction
    /** Approved file changes waiting for the reviewer pass (team mode). */
    private var pendingReview: List<FileChange>? = null
    private var reviewRounds = 0
    private val loopPrints = ArrayList<String>()
    private var loopNudged = false

    // ---- v0.6 sessions
    var sessionId by mutableStateOf(SessionStore.newId()); private set
    var sessionTitle by mutableStateOf(""); private set
    private var titleAuto = true
    private var sessionCreated = System.currentTimeMillis()
    /** The saved sessions of this project, newest first (refreshed after saves). */
    val sessions = mutableStateListOf<SessionMeta>()
    /** Bytes used by saved sessions + images (for the 50 MB warning). */
    var storageBytes by mutableLongStateOf(0L); private set
    /** The last session ended inside a turn (app killed, phone off): offer to resume it. */
    var interrupted by mutableStateOf(false); private set

    private val history = ArrayList<Msg>()
    private val touched = LinkedHashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var job: Job? = null
    private var saveJob: Job? = null
    private val pendingAsk = PendingAskStore(pendingAskFile)

    init {
        val restored = store?.let { st -> runCatching { st.last()?.let { st.load(it) } }.getOrNull() }
        if (restored != null) applySession(restored)
        else {
            // v0.5 kept only an open question on disk: show it; its answer goes back as a message.
            pendingAsk.load()?.let { items += ChatItem.AskUser(it, "", restored = true) }
        }
        pendingAsk.clear()
        refreshSessions()
    }

    // ------------------------------------------------------------------ sessions

    private fun applySession(d: SessionData) {
        sessionId = d.meta.id; sessionTitle = d.meta.title; titleAuto = d.meta.autoTitle; sessionCreated = d.meta.createdAt
        items.clear(); items.addAll(d.items)
        plan.clear(); plan.addAll(d.plan)
        touched.clear(); touched.addAll(d.touched)
        sessionUsage.clear(); sessionUsage.putAll(d.usage)
        step = 0; contextTokens = 0; currentUser = null; pendingNote = null; usageVersion++
        pendingReview = null; reviewRounds = 0; loopPrints.clear(); loopNudged = false
        // checkpoints that no longer exist lose their "Restore to here"
        val cps = runCatching { checkpoints.list().map { it.id }.toSet() }.getOrDefault(emptySet())
        items.filterIsInstance<ChatItem.User>().forEach { if (it.checkpointId != null && it.checkpointId !in cps) it.checkpointId = null }
        SessionRepair.repair(items, keepAskPending = true)
        history.clear(); history.addAll(HistoryRebuilder.rebuild(items))
        interrupted = d.active && SessionRepair.pendingAsk(items) == null
        if (interrupted && items.lastOrNull().let { it !is ChatItem.Notice || it.kind != ChatItem.Notice.Kind.INTERRUPTED }) {
            items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.INTERRUPTED)
        }
    }

    private fun snapshot(active: Boolean = running): SessionData {
        val msgs = items.count { it is ChatItem.User || (it is ChatItem.Assistant && it.text.isNotBlank()) }
        val preview = (items.lastOrNull { it is ChatItem.Assistant && it.text.isNotBlank() } as? ChatItem.Assistant)?.text
            ?.let { SessionTitles.auto(it.lineSequence().firstOrNull { l -> l.isNotBlank() }.orEmpty()) }.orEmpty()
        val meta = SessionMeta(sessionId, sessionTitle, titleAuto, sessionCreated, System.currentTimeMillis(), msgs, preview)
        return SessionData(meta, items.toList(), plan.toList(), touched.toList(), sessionUsage.toMap(), active)
    }

    /** Queue a save ~1 s after the last change. */
    fun markDirty() {
        if (store == null) return
        saveJob?.cancel()
        saveJob = scope.launch { delay(SAVE_DEBOUNCE_MS); saveNow() }
    }

    /** Save now. [blocking] waits for the write (onPause, before the process may die). */
    fun saveNow(blocking: Boolean = false) {
        val st = store ?: return
        saveJob?.cancel(); saveJob = null
        if (items.isEmpty()) return
        val json = runCatching { SessionCodec.encode(snapshot(), st.images) }.getOrNull() ?: return
        if (blocking) runCatching { st.save(json) }
        else scope.launch {
            withContext(io) { runCatching { st.save(json) } }
            refreshSessions()
        }
    }

    fun refreshSessions() {
        val st = store ?: return
        scope.launch {
            val (list, size) = withContext(io) { runCatching { st.list() to st.sizeBytes() }.getOrDefault(emptyList<SessionMeta>() to 0L) }
            sessions.clear(); sessions.addAll(list); storageBytes = size
        }
    }

    /** Start an empty session (the current one stays in the list). */
    fun newSession() {
        if (running) stop()
        saveNow()
        resetState()
        sessionId = SessionStore.newId(); sessionTitle = ""; titleAuto = true; sessionCreated = System.currentTimeMillis()
        scope.launch { withContext(io) { runCatching { store?.setLast(null) } } }
    }

    private fun resetState() {
        pendingAsk.clear(); items.clear(); history.clear(); touched.clear(); plan.clear(); step = 0; contextTokens = 0
        sessionUsage.clear(); currentUser = null; pendingNote = null; usageVersion++; interrupted = false
        pendingReview = null; reviewRounds = 0; loopPrints.clear(); loopNudged = false
    }

    /** Switch to a saved session. Refused while a turn runs. */
    fun openSession(id: String): Boolean {
        val st = store ?: return false
        if (running) return false
        if (id == sessionId) return true
        saveNow(blocking = true)
        val d = st.load(id) ?: return false
        applySession(d)
        runCatching { st.setLast(id) }
        refreshSessions()
        return true
    }

    fun renameSession(id: String, title: String) {
        val st = store ?: return
        if (id == sessionId) {
            sessionTitle = title.replace(Regex("\\s+"), " ").trim().take(120)
            titleAuto = sessionTitle.isEmpty()
            if (titleAuto) sessionTitle = items.firstOrNull { it is ChatItem.User }?.let { SessionTitles.auto((it as ChatItem.User).text) }.orEmpty()
            saveNow()
        } else scope.launch { withContext(io) { runCatching { st.rename(id, title) } }; refreshSessions() }
    }

    fun deleteSession(id: String) {
        val st = store ?: return
        if (id == sessionId) {
            if (running) return
            saveJob?.cancel()
            resetState()
            sessionId = SessionStore.newId(); sessionTitle = ""; titleAuto = true; sessionCreated = System.currentTimeMillis()
        }
        scope.launch { withContext(io) { runCatching { st.delete(id) } }; refreshSessions() }
    }

    /** Session search runs on disk (titles and message text). */
    suspend fun searchSessions(query: String): List<SessionMeta> =
        store?.let { st -> withContext(io) { runCatching { st.search(query) }.getOrDefault(emptyList()) } } ?: emptyList()

    // ------------------------------------------------------------------ turns

    /** Send [text] to the agent; the chat bubble shows [display] (e.g. the slash command as typed). */
    fun send(text: String, display: String = text, images: List<ImagePart> = emptyList()) {
        if (running || (text.isBlank() && images.isEmpty())) return
        // typing instead of answering a card restored after a restart dismisses it
        items.filterIsInstance<ChatItem.AskUser>().filter { it.restored && it.status == ChatItem.AskUser.Status.PENDING }.forEach {
            it.status = ChatItem.AskUser.Status.CANCELLED; pendingAsk.clear()
            HistoryRebuilder.owner(items, it.callId)?.results?.set(it.callId!!, HistoryRebuilder.ASK_DISMISSED)
        }
        items.filterIsInstance<ChatItem.Notice>().filter { it.kind == ChatItem.Notice.Kind.INTERRUPTED }.forEach { items.remove(it) }
        interrupted = false
        val note = pendingNote?.let { "[$it]\n\n" }.orEmpty()
        pendingNote = null
        val shown = display.trim().ifEmpty { text.trim() }
        val user = ChatItem.User(shown, images, prompt = note + text.trim())
        if (titleAuto && sessionTitle.isBlank()) sessionTitle = SessionTitles.auto(shown.ifBlank { text })
        // the model's view of the conversation always comes from the items (also after a restore)
        history.clear(); history.addAll(HistoryRebuilder.rebuild(items))
        items += user
        currentUser = user
        history += Msg.User(user.prompt, images)
        startTurn()
    }

    /** Continue the turn that a restart cut off (from the saved items). */
    fun resumeInterrupted(): Boolean {
        if (running || !interrupted) return false
        items.filterIsInstance<ChatItem.Notice>().filter { it.kind == ChatItem.Notice.Kind.INTERRUPTED }.forEach { items.remove(it) }
        interrupted = false
        items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.RESUMED)
        currentUser = items.lastOrNull { it is ChatItem.User } as ChatItem.User?
        history.clear(); history.addAll(HistoryRebuilder.rebuild(items))
        if (history.isEmpty()) return false
        startTurn()
        return true
    }

    private fun startTurn() {
        running = true
        step = 0
        hooks.onTurnStart(projectName)
        saveNow(blocking = true) // "active" is on disk before any network call
        var outcome = RunOutcome(RunOutcome.Kind.DONE)
        job = scope.launch {
            try { outcome = loop() }
            catch (e: CancellationException) { items += ChatItem.Notice("⏹", kind = ChatItem.Notice.Kind.STOPPED); outcome = RunOutcome(RunOutcome.Kind.STOPPED) }
            catch (e: Exception) {
                val msg = e.message ?: e.toString()
                items += ChatItem.Notice(msg, isError = true); outcome = RunOutcome(RunOutcome.Kind.ERROR, msg)
            }
            finally {
                items.filterIsInstance<ChatItem.Approval>().filter { it.decision == null }.forEach { it.deferred.cancel(); it.decision = false }
                items.filterIsInstance<ChatItem.AskUser>().filter { it.status == ChatItem.AskUser.Status.PENDING && !it.restored }.forEach {
                    it.status = ChatItem.AskUser.Status.CANCELLED; it.deferred.cancel(); pendingAsk.clear()
                }
                items.filterIsInstance<ChatItem.Assistant>().forEach { it.streaming = false }
                items.filterIsInstance<ChatItem.Tool>().filter { it.result == null }.forEach {
                    it.ok = false; it.result = "(stopped)"; it.durationMs = System.currentTimeMillis() - it.createdAt
                }
                running = false
                pendingReview = null
                saveNow()
                hooks.onTurnEnd(projectName, outcome)
            }
        }
    }

    fun stop() { job?.cancel() }

    /** "/clear" and "New chat": keep the old conversation in the Sessions list and start fresh. */
    fun clear() = newSession()

    /** Revert the project to just before [point]'s turn (and drop it and every newer checkpoint). */
    fun restoreCheckpoint(point: Checkpoints.Point): Checkpoints.Plan {
        val plan = checkpoints.restore(point)
        val remaining = checkpoints.list().map { it.id }.toSet()
        items.filterIsInstance<ChatItem.User>().forEach { if (it.checkpointId != null && it.checkpointId !in remaining) it.checkpointId = null }
        items += ChatItem.Notice(point.prompt, kind = ChatItem.Notice.Kind.RESTORED, count = plan.changedCount)
        val files = (plan.restored + plan.deleted).take(30).joinToString(", ")
        pendingNote = "Note: the user restored the project to the checkpoint taken before their message \"${point.prompt}\". " +
            "Files reverted: ${files.ifEmpty { "none" }}. Re-read files before editing them."
        markDirty()
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
        hooks.onResumed(projectName)
        markDirty()
    }

    /** Submit ([skip] = false) or skip an `ask_user` card. */
    fun answer(item: ChatItem.AskUser, skip: Boolean) {
        if (item.status != ChatItem.AskUser.Status.PENDING) return
        if (!skip && !item.canSubmit) return
        item.status = if (skip) ChatItem.AskUser.Status.SKIPPED else ChatItem.AskUser.Status.ANSWERED
        item.durationMs = System.currentTimeMillis() - item.createdAt
        pendingAsk.clear()
        val result = if (skip) AskUserTool.skipped() else AskUserTool.serialize(item.request, item.answers)
        if (item.restored) {
            val owner = HistoryRebuilder.owner(items, item.callId)
            if (owner != null && !running) {
                // v0.6: the conversation is on disk, so the real turn continues with the answer as the tool result
                owner.results[item.callId!!] = result
                currentUser = items.lastOrNull { it is ChatItem.User } as ChatItem.User?
                interrupted = false
                history.clear(); history.addAll(HistoryRebuilder.rebuild(items))
                startTurn()
            } else send(AskUserTool.asUserMessage(item.request, if (skip) null else item.answers))
        } else {
            item.deferred.complete(result)
            hooks.onResumed(projectName)
            markDirty()
        }
    }

    /** Answer the open single-choice question from a notification button. */
    fun answerFromNotification(questionId: String, optionId: String): Boolean {
        val card = SessionRepair.pendingAsk(items) ?: return false
        val q = card.request.questions.firstOrNull { it.id == questionId } ?: return false
        if (q.options.none { it.id == optionId }) return false
        card.update(q, AskUserTool.toggle(q, card.answers[q.id] ?: AskAnswer(), optionId))
        if (!card.canSubmit) return false
        answer(card, skip = false)
        return true
    }

    private suspend fun askUser(a: JSONObject?, callId: String): String {
        val req = AskUserTool.parse(a)
            ?: return "Error: ask_user needs a non-empty `questions` array; each question needs `id`, `prompt` and `kind`."
        val card = ChatItem.AskUser(req, a.toString(), callId = callId)
        items += card
        saveNow() // the open question survives the process
        hooks.onWaiting(projectName, RunWait.forAsk(req))
        return card.deferred.await()
    }

    /** Wait on an approval card, telling the runtime (notification) while it is open. */
    private suspend fun awaitApproval(card: ChatItem.Approval, kind: RunWait.Kind, detail: String): Boolean {
        saveNow()
        hooks.onWaiting(projectName, RunWait(kind, detail.take(200)))
        return card.deferred.await().also { card.durationMs = System.currentTimeMillis() - card.createdAt }
    }

    private fun client(): Pair<LlmClient, String> {
        val id = settings.activeProvider
        val def = Providers.byId(id)
        val cfg = settings.config(id)
        require(cfg.model.isNotBlank()) { "No model selected for ${def.displayName}" }
        return LlmClient(def, cfg.baseUrl, cfg.model, settings.apiKey(id)) to cfg.model
    }

    private suspend fun loop(): RunOutcome {
        val (llm, model) = client()
        var lastText = ""
        for (step in 0 until MAX_STEPS) {
            trimHistory()
            this.step = step + 1
            hooks.onActivity(projectName, step + 1, RunActivity.THINKING)
            val bubble = ChatItem.Assistant()
            items += bubble
            val sys = withContext(Dispatchers.IO) { SystemPrompt.build(projectDir, touched, linux.ready, settings.agentMode == "team") }
            val inputChars = sys.length + history.sumOf { size(it) }
            contextTokens = (inputChars / 3.6).toInt()
            val streamed = StringBuffer()
            // only the newest image turns carry their pixels; older ones say "[image omitted]"
            val turn = llm.complete(sys, ImagePruner.prune(history), Tools.specs, object : StreamListener {
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
            bubble.calls = turn.toolCalls
            bubble.streaming = false
            if (turn.text.isNotBlank()) lastText = turn.text
            history += Msg.Assistant(turn.text, turn.toolCalls)
            markDirty()
            if (turn.toolCalls.isEmpty()) return RunOutcome(RunOutcome.Kind.DONE, NotifText.summary(lastText))
            executeTools(turn.toolCalls, bubble)

            // v0.7: loop guard — nudge once, then stop before burning tokens in circles
            loopPrints += LoopDetector.fingerprint(turn.toolCalls)
            if (loopPrints.size > LoopDetector.WINDOW) loopPrints.removeAt(0)
            if (LoopDetector.tripleRepeat(loopPrints)) {
                if (!loopNudged) {
                    loopNudged = true
                    history += Msg.User(
                        "[System] You are repeating the same tool calls with the same arguments. " +
                            "Re-read the affected file, change your approach, or summarise what is blocking you and ask the user.",
                    )
                } else {
                    items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.LOOP)
                    return RunOutcome(RunOutcome.Kind.STOPPED, NotifText.summary(lastText))
                }
            }

            // v0.7: team mode — a reviewer pass over the applied batch
            if (settings.agentMode == "team") runReview()
            // v0.7: summarise old context when the history grows past the threshold
            maybeCompact(llm, model)
        }
        items += ChatItem.Notice("Step limit reached ($MAX_STEPS). Send a message to continue.", kind = ChatItem.Notice.Kind.STEP_LIMIT)
        return RunOutcome(RunOutcome.Kind.STEP_LIMIT, NotifText.summary(lastText))
    }

    private suspend fun executeTools(calls: List<ToolCall>, bubble: ChatItem.Assistant) {
        val parsed = calls.map { c -> c to (runCatching { JSONObject(c.argsJson) }.getOrNull()) }
        // results also land on the bubble right away, so a restart can rebuild what already ran
        val results = object : HashMap<String, String>() {
            override fun put(key: String, value: String): String? { bubble.results[key] = value; return super.put(key, value) }
        }

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
            val first = changes.values.first()
            hooks.onActivity(projectName, step, RunActivity(when { first.isDelete -> RunActivity.Kind.DELETE; first.isNew -> RunActivity.Kind.WRITE; else -> RunActivity.Kind.EDIT },
                first.path.substringAfterLast('/')))
            val approved = if (needApproval) {
                val card = ChatItem.Approval(changes.values.toList())
                items += card
                awaitApproval(card, RunWait.Kind.FILES, changes.keys.joinToString(", "))
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
                pendingReview = changes.values.toList() // v0.7 team mode: the reviewer sees this batch
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
            if (c.name == Tools.ASK) { results[c.id] = askUser(a, c.id); continue }
            val args = a ?: JSONObject()
            val item = ChatItem.Tool(c.name, Tools.summarizeArgs(c.name, args), c.id)
            items += item
            hooks.onActivity(projectName, step, RunActivity.forTool(c.name, item.summary))
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
            markDirty()
        }

        for (c in calls) history += Msg.ToolResult(c.id, c.name, results[c.id] ?: "Error: not executed")
    }

    /** v0.7 team mode: one reviewer call over the last applied batch (max [MAX_REVIEW_ROUNDS] per turn). */
    private suspend fun runReview() {
        val changes = pendingReview ?: return
        pendingReview = null
        if (reviewRounds >= MAX_REVIEW_ROUNDS) return
        reviewRounds++
        hooks.onActivity(projectName, step, RunActivity.forTool("review", "diff"))
        val def = Providers.byId(settings.activeProvider)
        val cfg = settings.config(def.id)
        val model = settings.reviewModel.ifBlank { cfg.model }
        val card = ChatItem.Review(model)
        items += card
        markDirty()
        val diff = Reviewer.diffOf(changes)
        val req = Reviewer.buildRequest(currentUser?.text ?: "", diff)
        val out = runCatching {
            val rl = LlmClient(def, cfg.baseUrl, model, settings.apiKey(def.id))
            rl.complete(Reviewer.SYSTEM, listOf(Msg.User(req)), emptyList(), object : StreamListener {
                override fun onText(delta: String) {}
            })
        }
        out.onSuccess { turn ->
            recordUsage(model, turn.usage ?: UsageParser.estimate(req.length, turn.text.length))
            val report = Reviewer.parse(turn.text)
            card.finish(report)
            history += Msg.User(
                when {
                    report == null -> "[Reviewer] The review returned an unreadable answer; continue with your best judgement."
                    report.ok -> "[Reviewer] OK — ${report.summary.ifBlank { "changes look correct" }}"
                    else -> Reviewer.feedbackMessage(report)
                },
            )
        }
        out.onFailure { e ->
            card.finish(null)
            history += Msg.User("[Reviewer] The review call failed (${(e.message ?: "error").take(120)}); continue with your best judgement.")
        }
        markDirty()
    }

    /** v0.7: summarise the oldest context into one note once it passes the threshold. */
    private suspend fun maybeCompact(llm: LlmClient, model: String) {
        if (!settings.autoCompact) return
        val chars = history.sumOf { size(it) }.toLong()
        if (!ContextCompactor.needsCompact(chars, history.size)) return
        val cut = ContextCompactor.cutIndex(history) ?: return
        hooks.onActivity(projectName, step, RunActivity.forTool("compact", ""))
        val rendered = ContextCompactor.renderForSummary(history.subList(0, cut))
        val out = runCatching {
            llm.complete(ContextCompactor.SUMMARY_SYSTEM, listOf(Msg.User(rendered)), emptyList(), object : StreamListener {
                override fun onText(delta: String) {}
            })
        }.getOrNull() ?: return
        recordUsage(model, out.usage ?: UsageParser.estimate(rendered.length, out.text.length))
        val summary = out.text.trim()
        if (summary.length < 60) return
        val compacted = ContextCompactor.apply(history, cut, summary)
        history.clear(); history.addAll(compacted)
        items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.COMPACTED)
        markDirty()
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
        markDirty()
        return "Plan updated (${next.count { it.done }}/${next.size} done)."
    }

    /** `git_push` always needs an explicit yes from the user, whatever the approval mode. */
    private suspend fun gitPush(a: JSONObject): String {
        val summary = withContext(Dispatchers.IO) { GitTools.describePush(a, projectDir, git) }
        val card = ChatItem.Approval(emptyList(), action = summary)
        items += card
        val ok = awaitApproval(card, RunWait.Kind.PUSH, summary.lineSequence().firstOrNull().orEmpty())
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
        val ok = awaitApproval(card, RunWait.Kind.COMMIT, msg.lineSequence().first())
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
            Tools.WEB_SEARCH -> WebTools.search(a.optString("query"), a.optInt("max_results", 6))
            Tools.WEB_FETCH -> WebTools.fetch(a.optString("url"), a.optInt("max_chars", 8_000))
            Tools.LINT -> lintProject()
            Tools.TESTS -> runProjectTests()
            else -> "Error: unknown tool $name"
        }
    }

    /** v0.7: terminal lint checks + pure JSON/HTML checks, formatted as one report. */
    private suspend fun lintProject(): String {
        val cmds = ProjectChecks.lintCommands(projectDir)
        val outputs = ArrayList<Pair<String, String>>()
        for ((kind, cmd) in cmds) {
            val r = linux.exec(projectDir, cmd, 240)
            var out = r.output
            if (Regex("""(not found|command not found)""").containsMatchIn(out)) {
                out += "\nHint: install the tool first (e.g. `apk add nodejs` or `apk add python3`)."
            }
            outputs += kind to out
        }
        val pure = ProjectChecks.jsonIssues(projectDir) + ProjectChecks.htmlIssues(projectDir)
        return ProjectChecks.formatLint(outputs, pure)
    }

    /** v0.7: detect and run the project's tests (npm test / pytest / unittest). */
    private suspend fun runProjectTests(): String {
        val runners = ProjectChecks.detectTests(projectDir)
        if (runners.isEmpty()) {
            return "No test runner detected (no package.json test script, no pytest layout, no *_test.py files). Offer to write tests for this project."
        }
        val r = runners.first()
        val res = linux.exec(projectDir, r.command, 300)
        return buildString {
            appendLine("Runner: ${r.label} (${r.command})")
            append(if (res.timedOut) "[timed out]\n" else "[exit ${res.exitCode}]\n")
            append(res.output.ifBlank { "(no output)" })
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
        is Msg.User -> m.text.length + m.images.size * IMAGE_CHARS
        is Msg.Assistant -> m.text.length + m.toolCalls.sumOf { it.argsJson.length }
        is Msg.ToolResult -> m.content.length
    }

    companion object {
        const val MAX_STEPS = 40
        const val MAX_CONTEXT_CHARS = 180_000
        /** v0.7: reviewer passes per turn (team mode). */
        const val MAX_REVIEW_ROUNDS = 2
        /** v0.7: compaction threshold (chars), from [ContextCompactor.THRESHOLD_CHARS]. */
        const val COMPACT_THRESHOLD = ContextCompactor.THRESHOLD_CHARS
        /** Rough context cost of one attached image (~1.5k tokens) for the meter and trimming. */
        const val IMAGE_CHARS = 5_400
        const val SAVE_DEBOUNCE_MS = 1_000L
    }
}

object SystemPrompt {
    fun build(projectDir: File, touched: Collection<String>, linuxReady: Boolean, teamMode: Boolean = false): String {
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
            appendLine("# Web & quality tools (v0.7)")
            appendLine("- web_search / web_fetch reach the public internet only (private addresses are blocked). Use them for docs, API references and unfamiliar errors.")
            appendLine("- lint_project gives quick JS/Python/JSON/HTML checks; run_tests detects and runs the project's tests. Use them after meaningful changes and fix what they report.")
            if (teamMode) appendLine("- Team mode: a reviewer agent checks your applied changes after each batch. Reviewer messages start with \"[Reviewer]\"; fix reported issues immediately without re-asking the user.")
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
