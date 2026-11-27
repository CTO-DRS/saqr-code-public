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
import com.ctodrs.saqrcode.agent.memory.Lessons
import com.ctodrs.saqrcode.agent.memory.MemoryEngine
import com.ctodrs.saqrcode.agent.memory.MemorySafety
import com.ctodrs.saqrcode.agent.memory.SessionMemory
import com.ctodrs.saqrcode.agent.memory.SessionSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** A proposed file change awaiting (or past) approval. */
data class FileChange(val path: String, val before: String?, val after: String?) {
    val isDelete get() = after == null
    val isNew get() = before == null
}

/** Majlis: pure helpers for regenerate / edit-and-resend. */
object Rewind {
    const val NOTE_REGENERATE = "Note: the user asked for this reply to be regenerated. Files changed by the discarded attempt are still on disk. Re-read files before editing them."
    const val NOTE_EDIT = "Note: the user edited their earlier message and the conversation continues from it. Files changed after that message are still on disk. Re-read files before editing them."
    fun lastUserIndex(items: List<ChatItem>): Int = items.indexOfLast { it is ChatItem.User }
    fun indexOfUser(items: List<ChatItem>, target: ChatItem.User): Int = items.indexOfFirst { it === target }
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
        /** Majlis: shared by every branch of this message (edit / regenerate keep the old answer). */
        var branches: Branches? = null
    }
    /** One model turn: its text, plus the tool calls it made and their results (so history can be rebuilt from items). */
    class Assistant : ChatItem() {
        var text by mutableStateOf("")
        var streaming by mutableStateOf(true)
        /** Majlis: 0 none, 1 good, -1 bad. In memory for this session. */
        var feedback by mutableIntStateOf(0)
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
        /** v0.15: COMMAND = a HIGH-risk terminal command (CommandPolicy). */
        enum class ActionKind { PUSH, COMMIT, COMMAND }
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
    /** v0.1.9 plan mode: the agent's plan, shown for the user to edit before any work starts. The loop waits on [deferred] (true = run the edited plan). */
    class PlanReview(initial: List<String>) : ChatItem() {
        enum class Status { PENDING, APPROVED, CANCELLED }
        val tasks = mutableStateListOf<String>().also { it.addAll(initial) }
        var status by mutableStateOf(Status.PENDING)
        val deferred = CompletableDeferred<Boolean>()
    }
    class Notice(val text: String, val isError: Boolean = false, val kind: Kind = if (isError) Kind.ERROR else Kind.INFO, val count: Int = -1) : ChatItem() {
        /** INTERRUPTED: a turn was cut off (app killed / phone off) and can be resumed; RESUMED: it was. COMPACTED: v0.7 context compaction; LOOP: v0.7 loop detector stopped the turn. PLANNED: v0.8 planner produced a task plan; MCP: v0.8 MCP server connection problem; DESERT: v0.13.5 offline gate refused a send. */
        /** v0.15: VERIFY = completion gate verdict ([count] = CompletionGate.Status ordinal, or [VERIFY_RETRY]); BUDGET = a run budget stopped the turn ([text] = time/cost); FALLBACK = a step moved to the fallback model ([text] = its label). */
        /** v0.16: TEAM = a Chief-mode event; [count] is one of the TEAM_* subtypes and [text] its data (rendered localized by the chat). */
        /** v0.17: ROUTER = a smart-router decision ([text] = "label|provider · model|reason"); LESSON = a lesson was captured ([text] = its error signature, [count] 0 new / 1 updated). BUDGET [text] may also be "daily_cap" / "monthly_cap". */
        /** v0.18: SKILL = an installed skill was attached to the run ([text] = "name|version|reason", [count] 1 = named by the user, 0 = auto-attached). */
        enum class Kind { INFO, WARNING, ERROR, STOPPED, STEP_LIMIT, RESTORED, INTERRUPTED, RESUMED, COMPACTED, LOOP, PLANNED, MCP, DESERT, VERIFY, BUDGET, FALLBACK, TEAM, ROUTER, LESSON, SKILL }
        companion object {
            const val VERIFY_RETRY = 9
            /** TEAM subtypes. SELECTED: text = role ids joined by ',' (empty = answered without a team). */
            const val TEAM_SELECTED = 0
            /** text = estimated USD ("" = the price of a role's model is unknown). */
            const val TEAM_ESTIMATE = 1
            /** text = [TeamCost.encode]. */
            const val TEAM_COST = 2
            /** text = "role|title". */
            const val TEAM_REASSIGNED = 3
            /** text = the conflict detail. */
            const val TEAM_CONFLICT = 4
            /** text = "role|title|reason". */
            const val TEAM_FAILED = 5
            /** The planner's graph was unusable (cycle / no worker): the Chief's default graph runs instead. */
            const val TEAM_GRAPH_FALLBACK = 6
            /** text = "role|title". */
            const val TEAM_STARTED = 7
            /** text = "role|title" — the author reworks after a rejected review. */
            const val TEAM_REWORK = 8
        }
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
    /** v0.13.2 Falcon Router: daily routed-call savings (actual, baseline) next to the usage ledger. */
    val savings: SavingsLedger = SavingsLedger(File(checkpointRoot.parentFile ?: checkpointRoot, "usage/falcon.json")),
    /** v0.6: where this project's chat sessions are saved; null = in-memory only. */
    val store: SessionStore? = null,
    /** v0.6: the background runtime (foreground service, notifications). */
    private val hooks: RunHooks = RunHooks.NONE,
) {
    val items = mutableStateListOf<ChatItem>()
    var running by mutableStateOf(false); private set
    /** Model round-trips used by the current/last turn (bounded by [MAX_STEPS]). */
    var step by mutableIntStateOf(0); private set
    /** v0.15: the step budget in force (Settings → Autonomous core; default 40 = [MAX_STEPS]). */
    val maxSteps: Int get() = Budgets.clampSteps(settings.maxSteps)
    /** Rough context size in tokens (chars / 3.6) of system prompt + history. */
    var contextTokens by mutableIntStateOf(0); private set
    val plan = mutableStateListOf<PlanItem>()
    val checkpoints = Checkpoints(projectDir, File(checkpointRoot, projectName))
    /** v0.13: this project's memory bank — auto-recalled each turn, edited by the model and the user. */
    private val memory = MemoryBank(File(projectDir, ".saqr"))
    val memoryBank: MemoryBank get() = memory
    /** v0.13: lightweight symbol index feeding project_search (files, classes, functions; <200ms). */
    private val projectIndex = ProjectIndex(projectDir)
    /** v0.17 long-term layer: app-private, cross-project user preferences (used only while `globalMemory` is on). */
    val globalBank: MemoryBank = MemoryBank.shared(File(checkpointRoot.parentFile ?: checkpointRoot, "memory"), MemoryBank.GLOBAL_FILE, MemoryBank.Layer.GLOBAL)
    /** v0.17 session layer: the rolling summary of this session's old turns (with `sessionSummary` on). */
    var sessionSummary by mutableStateOf<SessionSummary?>(null); private set
    /** v0.17 smart router: model id → provider id of the targets it picked this run (clients are built on that provider). */
    private val routeProviders = HashMap<String, String>()
    private var lastRouteNotice = ""
    /** v0.17 lesson capture: the failing checks (lint, tests) of this run's first failed gate, and the files changed since. */
    private var gateFail: Pair<String?, String?>? = null
    private val fixFiles = LinkedHashSet<String>()
    /** v0.17 spend caps: priced spend of today / this month before this run (from the ledger), and which cap stopped it. */
    private var capDayBase: Double? = null
    private var capMonthBase: Double? = null
    private var capHit = SpendCap.Hit.NONE
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

    // ---- v0.15 autonomous core
    /** Identical failing calls of this run (LoopDetector v2). */
    private val failPrints = ArrayList<String>()
    /** Per step of this run: did any tool succeed (LoopDetector v2 no-progress). */
    private val stepOk = ArrayList<Boolean>()
    /** Files changed by this run (the completion gate only checks runs that changed something). */
    private val turnTouched = LinkedHashSet<String>()
    private var gateRounds = 0
    private var verification: CompletionGate.Status? = null
    /** Model usage of this run, for the cost budget and the task file. */
    private val turnUsage = HashMap<String, Usage>()
    private var costWarned = false
    private val tasks = TaskStore(File(projectDir, ".saqr/tasks"))
    private val auditFile = File(projectDir, ".saqr/audit.jsonl")
    private var task: TaskState? = null
    /** Wall time this run segment started, time spent waiting on the user, and active time from before a resume. */
    private var runStartedAt = 0L
    private var waitMs = 0L
    private var activeBefore = 0L
    private var stepBase = 0
    private var tokensBefore = Usage.ZERO
    private var costBefore: Double? = null

    // ---- v0.16 agent team (Chief mode)
    /** The role whose loop is running (null = single agent / classic team). */
    private var currentRole: AgentRole? = null
    /** File changes applied while the current role ran (handed to the Chief). */
    private var roleChanges: MutableList<FileChange>? = null
    /** Tokens per role and model of this run, for the per-role cost line and [RunOutcome.roles]. */
    private val roleUsage = HashMap<AgentRole, HashMap<String, Usage>>()
    /** Model steps of the whole run (all roles) — the step budget applies to the team as a whole. */
    private var runSteps = 0
    private var runCommands = 0 // v0.1.10: commands this run actually executed (for the completion report)
    /** The team's task graph of this run (persisted in the task file; restored on resume). */
    private var teamGraph: TaskGraph? = null
    /** Chief mode decided "no team" (a question): the classic single loop answers. */
    private var chiefSolo = false
    /** v0.18: installed skills attached to this run (null = not chosen yet this run). */
    private var runSkills: List<com.ctodrs.saqrcode.agent.skills.SkillRuntime.Attached>? = null

    // ---- v0.8 planner / MCP / review cost
    /** MCP tools discovered for the current turn (empty in single mode or when no server). */
    private var mcpSpecs: List<ToolSpec> = emptyList()
    private var mcpClients: List<Pair<Mcp.Server, McpClient>> = emptyList()

    /** v0.11: stdio MCP servers, one child process each (created per turn, closed on reset). */
    private var stdioClients: List<Pair<McpStdio.StdioServer, McpStdioClient>> = emptyList()
    /** v0.8: the reviewer's own token spend, per model — shown separately in the meter. */
    val reviewUsage = mutableStateMapOf<String, Usage>()
    /** v0.8: the planner's own token spend, per model. */
    val plannerUsage = mutableStateMapOf<String, Usage>()

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
        // v0.17: whenever the project index is rebuilt (files changed), memories re-check their file refs
        projectIndex.onRebuilt = { memory.refreshStale { File(projectDir, it).exists() } }
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
        sessionSummary = d.summary
        step = 0; contextTokens = 0; currentUser = null; pendingNote = null; usageVersion++
        pendingReview = null; reviewRounds = 0; loopPrints.clear(); loopNudged = false
        reviewUsage.clear(); plannerUsage.clear(); mcpSpecs = emptyList(); mcpClients = emptyList()
        stdioClients.forEach { runCatching { it.second.close() } }; stdioClients = emptyList()
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
        return SessionData(meta, items.toList(), plan.toList(), touched.toList(), sessionUsage.toMap(), active, sessionSummary)
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
        sessionSummary = null
        pendingReview = null; reviewRounds = 0; loopPrints.clear(); loopNudged = false
        reviewUsage.clear(); plannerUsage.clear(); mcpSpecs = emptyList(); mcpClients = emptyList()
        stdioClients.forEach { runCatching { it.second.close() } }; stdioClients = emptyList()
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
        // v0.13.5 desert mode: no cloud turn starts offline — one clear notice instead of a hanging socket error.
        if (DesertState.active) { desertNotice(); return }
        // v0.13.3 Saqr Coach: the first real command completes the «message» step.
        if (settings.coachFirstMessageAt == 0L) settings.coachFirstMessageAt = System.currentTimeMillis()
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
        // a new request invalidates the previous plan pill (v0.8 planner replaces it)
        plan.clear()
        // the model's view of the conversation always comes from the items (also after a restore)
        history.clear(); history.addAll(rebuildHistory())
        items += user
        currentUser = user
        history += Msg.User(user.prompt, images)
        startTurn()
    }

    /**
     * Majlis: run the last user message again. Everything after it (the reply, tool rows, approvals) is dropped
     * from the conversation; files already changed on disk stay, and the model is told so.
     */
    fun regenerate(): Boolean {
        if (running) return false
        val idx = Rewind.lastUserIndex(items)
        if (idx < 0) return false
        val user = items[idx] as ChatItem.User
        return rewindAndSend(idx, user.prompt, user.text, user.images, Rewind.NOTE_REGENERATE)
    }

    /** Majlis: replace an earlier user message with [newText] and continue from it; the old branch is dropped from the conversation. */
    fun editAndResend(target: ChatItem.User, newText: String): Boolean {
        if (running || newText.isBlank()) return false
        val idx = Rewind.indexOfUser(items, target)
        if (idx < 0) return false
        return rewindAndSend(idx, newText.trim(), newText.trim(), target.images, Rewind.NOTE_EDIT)
    }

    private fun rewindAndSend(idx: Int, prompt: String, display: String, images: List<ImagePart>, note: String): Boolean {
        val b = BranchOps.fork(items, idx)
        pendingAsk.clear(); plan.clear()
        pendingNote = note
        markDirty()
        send(prompt, display, images)
        if (!BranchOps.attach(items, idx, b)) { BranchOps.undo(items, b); return false }
        return true
    }

    /** Majlis: flip the user message [target] to its next (+1) or previous (-1) branch. */
    fun switchBranch(target: ChatItem.User, delta: Int): Boolean {
        if (running) return false
        val idx = Rewind.indexOfUser(items, target)
        if (idx < 0) return false
        pendingAsk.clear(); plan.clear()
        val ok = BranchOps.switch(items, idx, delta)
        if (ok) { currentUser = items.lastOrNull { it is ChatItem.User } as ChatItem.User?; markDirty() }
        return ok
    }

    /** Continue the turn that a restart cut off (from the saved items). */
    fun resumeInterrupted(): Boolean {
        if (running || !interrupted) return false
        items.filterIsInstance<ChatItem.Notice>().filter { it.kind == ChatItem.Notice.Kind.INTERRUPTED }.forEach { items.remove(it) }
        interrupted = false
        items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.RESUMED)
        currentUser = items.lastOrNull { it is ChatItem.User } as ChatItem.User?
        history.clear(); history.addAll(rebuildHistory())
        if (history.isEmpty()) return false
        startTurn(resume = true)
        return true
    }

    private fun startTurn(resume: Boolean = false) {
        running = true
        step = 0
        beginTask(resume)
        hooks.onTurnStart(projectName)
        saveNow(blocking = true) // "active" is on disk before any network call
        var outcome = RunOutcome(RunOutcome.Kind.DONE)
        job = scope.launch {
            try {
                outcome = loop()
                // v0.17: the turn is finished — a stop or failure while summarising must not relabel it
                try { rollSessionSummary() } catch (_: CancellationException) {} catch (_: Exception) {}
            }
            catch (e: CancellationException) { items += ChatItem.Notice("⏹", kind = ChatItem.Notice.Kind.STOPPED); outcome = RunOutcome(RunOutcome.Kind.STOPPED) }
            catch (e: Exception) {
                val msg = e.message ?: e.toString()
                items += ChatItem.Notice(msg, isError = true); outcome = RunOutcome(RunOutcome.Kind.ERROR, msg)
            }
            finally {
                endTask(outcome)
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
                hooks.onTurnEnd(projectName, outcome.withStats(runStats()))
            }
        }
    }

    fun stop() { job?.cancel() }

    /**
     * v0.13.5 desert mode: a send refused offline lands as one DESERT notice
     * (localized in the chat UI); repeated sends do not stack duplicate cards.
     */
    private fun desertNotice() {
        val last = items.lastOrNull() as? ChatItem.Notice
        if (last?.kind == ChatItem.Notice.Kind.DESERT) return
        items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.DESERT)
        markDirty()
    }

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

    // ------------------------------------------------------------------ v0.15 task state, budgets, audit

    /** A new task for this run — or, on resume, the cut-off task with its plan, step and spend. */
    private fun beginTask(resume: Boolean) {
        failPrints.clear(); stepOk.clear(); turnTouched.clear(); gateRounds = 0; verification = null
        turnUsage.clear(); costWarned = false; waitMs = 0L; runCommands = 0
        roleUsage.clear(); teamGraph = null; chiefSolo = false; currentRole = null; roleChanges = null; runSkills = null
        gateFail = null; fixFiles.clear(); capHit = SpendCap.Hit.NONE; routeProviders.clear(); lastRouteNotice = ""
        val now = System.currentTimeMillis()
        runStartedAt = now
        val restored = if (resume && settings.taskJournal) runCatching { tasks.latestResumable(sessionId) }.getOrNull() else null
        if (restored != null) {
            task = restored.copy(status = TaskState.Status.RUNNING, updatedAt = now)
            if (plan.isEmpty() && restored.plan.isNotEmpty()) plan.addAll(restored.plan)
            activeBefore = restored.activeMs
            stepBase = restored.step.coerceAtLeast(0)
            tokensBefore = Usage(restored.tokensIn, restored.tokensOut)
            costBefore = restored.costUsd
            teamGraph = restored.team
        } else {
            task = TaskState(TaskStateCodec.newId(now), sessionId, goal = currentUser?.text.orEmpty().take(2000), startedAt = now)
            activeBefore = 0L; stepBase = 0; tokensBefore = Usage.ZERO; costBefore = null
        }
        runSteps = stepBase
        persistTask(TaskState.Status.RUNNING)
    }

    /** Active run time: this segment minus the time spent waiting on the user, plus earlier segments. */
    private fun activeMs(): Long = activeBefore + (System.currentTimeMillis() - runStartedAt - waitMs).coerceAtLeast(0)

    /** v0.1.10: the completion-report numbers of this run (earlier resume segments included). */
    private fun runStats(): RunStats {
        val u = runTokens()
        return RunStats(activeMs(), runSteps, turnTouched.size, runCommands, u.total, u.estimated, runCost())
    }

    /** v0.1.15: live stats of the current (or last finished) run, for the usage sheet's per-task row. */
    fun currentRunStats(): RunStats = runStats()

    /** Priced spend of this run (earlier segments included); null when no price is known. */
    private fun runCost(): Double? {
        val now: Double? = if (turnUsage.isEmpty()) 0.0 else PriceTable.cost(turnUsage.toMap(), settings.prices())
        return if (now == null) costBefore else (costBefore ?: 0.0) + now
    }

    private fun runTokens(): Usage = turnUsage.values.fold(tokensBefore) { a, u -> a + u }

    /** Writes the task file (off the main thread, in order). Silent on failure — it never blocks a run. */
    private fun persistTask(status: TaskState.Status, note: String? = null) {
        if (!settings.taskJournal) return
        val cur = task ?: return
        val tok = runTokens()
        val next = cur.copy(
            plan = plan.toList(), step = step.coerceAtLeast(cur.step), updatedAt = System.currentTimeMillis(),
            activeMs = activeMs(), tokensIn = tok.input, tokensOut = tok.output, costUsd = runCost(),
            status = status, note = note ?: cur.note, team = teamGraph ?: cur.team,
        )
        task = next
        scope.launch { withContext(io) { runCatching { tasks.save(next) } } }
    }

    private fun endTask(outcome: RunOutcome) {
        val status = when (outcome.kind) {
            RunOutcome.Kind.DONE -> TaskState.Status.DONE
            RunOutcome.Kind.ERROR -> TaskState.Status.FAILED
            RunOutcome.Kind.STOPPED, RunOutcome.Kind.STEP_LIMIT, RunOutcome.Kind.BUDGET -> TaskState.Status.STOPPED
        }
        val note = listOfNotNull(
            outcome.kind.name.lowercase(),
            outcome.verification?.let { CompletionGate.summaryLine(it) },
        ).joinToString(" · ")
        persistTask(status, note)
    }

    /** One audit line per tool call (redacted; rotated at 1 MB). */
    private fun audit(tool: String, risk: Risk, decision: AuditEntry.Decision, ok: Boolean, ms: Long, summary: String, role: String? = currentRole?.id) {
        if (!settings.auditLog) return
        val e = AuditEntry(System.currentTimeMillis(), tool, risk, decision, ok, ms, summary, role)
        val secrets = arrayOf(settings.githubToken, settings.apiKey(settings.activeProvider))
        scope.launch { withContext(io) { runCatching { AuditLog.append(auditFile, e, *secrets) } } }
    }

    /** Record one model call's usage in this session and in the persistent ledger. */
    private suspend fun recordUsage(model: String, u: Usage, role: AgentRole? = currentRole) {
        turnUsage[model] = (turnUsage[model] ?: Usage.ZERO) + u
        // v0.16: per-role spend in Chief mode
        if (role != null) roleUsage.getOrPut(role) { HashMap() }.let { it[model] = (it[model] ?: Usage.ZERO) + u }
        sessionUsage[model] = (sessionUsage[model] ?: Usage.ZERO) + u
        withContext(Dispatchers.IO) { runCatching { ledger.record(model, u) } }
        usageVersion++
    }

    /**
     * v0.13.2 Falcon Router: the model for a light stage. The user's explicit pick wins;
     * otherwise the router picks the cheapest capable model when enabled (and provably
     * cheaper); otherwise the coder's model. Returns (model, routed).
     */
    private fun stageModel(stage: FalconRouter.Stage, explicit: String): Pair<String, Boolean> {
        if (explicit.isNotBlank()) return explicit to false
        if (settings.smartRouter) {
            val tier = if (stage == FalconRouter.Stage.REVIEWER) ModelTier.INDEPENDENT_REVIEW else ModelTier.CHEAP
            val d = smartRoute(tier, stage.name.lowercase(), needsTools = false)
            return d.target.model to d.routed
        }
        if (!settings.falconRouter) {
            val def = Providers.byId(settings.activeProvider)
            return settings.config(def.id).model to false
        }
        val def = Providers.byId(settings.activeProvider)
        val cfg = settings.config(def.id)
        val picked = FalconRouter.pick(stage, cfg.model, (def.suggestedModels + cfg.model).distinct(), settings.prices())
        return if (picked != null) picked to true else cfg.model to false
    }

    /** v0.13.2: price one routed call at its model (actual) and at the coder's price (baseline). */
    private suspend fun recordSavings(model: String, u: Usage) {
        val def = Providers.byId(settings.activeProvider)
        val coderModel = settings.config(def.id).model
        val m = FalconRouter.meter(listOf(FalconRouter.RoutedCall(model, u)), coderModel, settings.prices()) ?: return
        withContext(Dispatchers.IO) { runCatching { savings.record(m.first, m.second) } }
    }

    fun decide(item: ChatItem.Approval, approve: Boolean) {
        if (item.decision != null) return
        item.decision = approve
        item.deferred.complete(approve)
        hooks.onResumed(projectName)
        markDirty()
    }

    /** v0.1.9 plan mode: run the (edited) plan, or cancel the turn. */
    fun decidePlan(item: ChatItem.PlanReview, approve: Boolean) {
        if (item.status != ChatItem.PlanReview.Status.PENDING) return
        if (approve && Planner.fromEdited(item.tasks.toList()) == null) return
        item.status = if (approve) ChatItem.PlanReview.Status.APPROVED else ChatItem.PlanReview.Status.CANCELLED
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
                history.clear(); history.addAll(rebuildHistory())
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
        persistTask(TaskState.Status.WAITING_APPROVAL)
        val t0 = System.currentTimeMillis()
        return card.deferred.await().also { waitMs += System.currentTimeMillis() - t0; persistTask(TaskState.Status.RUNNING) }
    }

    /** Wait on an approval card, telling the runtime (notification) while it is open. */
    private suspend fun awaitApproval(card: ChatItem.Approval, kind: RunWait.Kind, detail: String): Boolean {
        saveNow()
        hooks.onWaiting(projectName, RunWait(kind, detail.take(200)))
        persistTask(TaskState.Status.WAITING_APPROVAL)
        val t0 = System.currentTimeMillis()
        return card.deferred.await().also {
            card.durationMs = System.currentTimeMillis() - card.createdAt
            waitMs += System.currentTimeMillis() - t0
            persistTask(TaskState.Status.RUNNING)
        }
    }

    private fun client(): Pair<LlmClient, String> {
        val id = settings.activeProvider
        val def = Providers.byId(id)
        val cfg = settings.config(id)
        require(cfg.model.isNotBlank()) { "No model selected for ${def.displayName}" }
        return LlmClient(def, cfg.baseUrl, cfg.model, settings.apiKey(id)) to cfg.model
    }

    private suspend fun loop(): RunOutcome {
        var (llm, model) = client()
        // v0.17 spend caps: a run never starts (or continues) past the user's daily/monthly cap
        loadCapBase()
        if (spendCapHit() != SpendCap.Hit.NONE) return capStop(NotifText.summary(""))
        // v0.17 smart router: the coder stays the user's model unless it lacks what this turn needs (images, context size)
        if (settings.smartRouter) {
            val needsVision = (history.lastOrNull { it is Msg.User } as Msg.User?)?.images?.isNotEmpty() == true
            val ctx = (history.sumOf { size(it) } / 3.6).toInt() + SYSTEM_PROMPT_TOKENS
            val d = smartRoute(ModelTier.STRONG, "coder", needsVision, ctx)
            if (d.routed) { llm = clientFor(d.target.model); model = d.target.model }
        }
        // v0.18: installed skills named by the user (or, opt-in, the most relevant ones) join this run
        attachSkills(currentUser?.text.orEmpty())
        // v0.1.9 plan mode: show the plan and wait for the user's edit/approval before any work
        if (settings.planFirst && stepBase == 0 && !planReview(model)) {
            items += ChatItem.Notice("⏹", kind = ChatItem.Notice.Kind.STOPPED)
            return RunOutcome(RunOutcome.Kind.STOPPED)
        }
        // v0.16 Chief mode: the Chief staffs the request with the minimum set of roles
        if (settings.agentMode == "team" && settings.chiefMode && !chiefSolo) return chiefLoop(llm, model)
        val team = settings.agentMode == "team" && !chiefSolo
        var lastText = ""
        // v0.8 team mode: plan first (cheap model), then code; MCP tools connect once per turn
        if (team && stepBase == 0) runPlanner()
        refreshMcpTools()
        // v0.15 budgets: steps (default 40 = the old MAX_STEPS), optional active minutes and priced cost
        val limits = settings.budgets()
        val maxSteps = Budgets.clampSteps(limits.maxSteps)
        var stepIdx = stepBase
        while (true) {
            if (spendCapHit() != SpendCap.Hit.NONE) return capStop(NotifText.summary(lastText))
            val hit = Budgets.check(limits, stepIdx, activeMs(), runCost())
            when (hit) {
                Budgets.Hit.STEPS -> {
                    items += ChatItem.Notice("Step limit reached ($maxSteps). Send a message to continue.", kind = ChatItem.Notice.Kind.STEP_LIMIT)
                    return RunOutcome(RunOutcome.Kind.STEP_LIMIT, NotifText.summary(lastText), verification)
                }
                Budgets.Hit.TIME, Budgets.Hit.COST -> {
                    val which = if (hit == Budgets.Hit.TIME) "time" else "cost"
                    items += ChatItem.Notice(which, kind = ChatItem.Notice.Kind.BUDGET)
                    return RunOutcome(RunOutcome.Kind.BUDGET, NotifText.summary(lastText), verification)
                }
                Budgets.Hit.NONE -> {}
            }
            if (!costWarned && Budgets.costUnenforceable(limits, runCost(), runTokens().total)) {
                costWarned = true
                items += ChatItem.Notice("The cost limit can't be enforced: no price is known for ${turnUsage.keys.joinToString(", ")}. Add one in Usage & budget.",
                    kind = ChatItem.Notice.Kind.WARNING)
            }
            trimHistory()
            this.step = stepIdx + 1
            stepIdx++
            runSteps = stepIdx
            persistTask(TaskState.Status.RUNNING)
            hooks.onActivity(projectName, step, RunActivity.THINKING)
            val bubble = ChatItem.Assistant()
            items += bubble
            val sys = withContext(Dispatchers.IO) {
                SystemPrompt.build(projectDir, touched, linux.ready, team, mcpSpecs.map { it.name },
                    memory = memoryBlock(), untrusted = settings.untrustedMarking, riskyApproval = settings.confirmRiskyCommands,
                    skills = skillSection(null), learning = settings.learningMode)
            }
            val inputChars = sys.length + history.sumOf { size(it) }
            contextTokens = (inputChars / 3.6).toInt()
            // only the newest image turns carry their pixels; older ones say "[image omitted]"
            val (turn, usedModel) = completeStep(llm, model, sys, ImagePruner.prune(history), specs(), bubble)
            val used = turn.usage ?: UsageParser.estimate(inputChars, turn.text.length + turn.toolCalls.sumOf { it.argsJson.length + it.name.length })
            if (!used.estimated && used.input > 0) contextTokens = used.input.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            recordUsage(usedModel, used)
            // make sure the bubble shows the final text even if a delta launch is pending
            bubble.text = turn.text
            bubble.calls = turn.toolCalls
            bubble.streaming = false
            if (turn.text.isNotBlank()) lastText = turn.text
            history += Msg.Assistant(turn.text, turn.toolCalls)
            markDirty()
            if (turn.toolCalls.isEmpty()) {
                // v0.15 completion gate: a run that changed files is checked before it may finish
                if (completionGate()) continue
                return RunOutcome(RunOutcome.Kind.DONE, NotifText.summary(lastText), verification)
            }
            executeTools(turn.toolCalls, bubble)

            // v0.7: loop guard — nudge once, then stop before burning tokens in circles
            // v0.15: + cycles, repeated identical failures and no-progress streaks (smartLoopGuard)
            loopPrints += LoopDetector.fingerprint(turn.toolCalls)
            if (loopPrints.size > LoopDetector.WINDOW) loopPrints.removeAt(0)
            val loop = LoopDetector.detect(loopPrints, failPrints, stepOk, settings.smartLoopGuard)
            if (loop != null) {
                if (!loopNudged) {
                    loopNudged = true
                    history += Msg.User(LoopDetector.nudge(loop))
                    // the nudge gets a fair chance: v2 evidence starts over
                    if (loop == LoopDetector.Kind.REPEATED_FAILURE || loop == LoopDetector.Kind.NO_PROGRESS) { failPrints.clear(); stepOk.clear() }
                } else {
                    items += ChatItem.Notice(loop.name, kind = ChatItem.Notice.Kind.LOOP)
                    return RunOutcome(RunOutcome.Kind.STOPPED, NotifText.summary(lastText), verification)
                }
            }

            // v0.7: team mode — a reviewer pass over the applied batch
            if (team) runReview()
            // v0.7: summarise old context when the history grows past the threshold —
            // v0.13.2: on the router's cheaper model when one is provably capable
            // (v0.17: the compaction model is only picked — and the router decision logged — when compaction is due)
            if (settings.autoCompact && ContextCompactor.needsCompact(history.sumOf { size(it) }.toLong(), history.size)) {
                val (cl, cm, cr) = compactClient(llm, model)
                maybeCompact(cl, cm, cr)
            }
        }
    }

    /**
     * v0.15: one model step with resilience. A transient failure (429/5xx/timeout/network) is
     * retried once after a short backoff (`retryLlm`); if it fails again and the user picked a
     * fallback model on an already-configured provider, the step runs there with a notice.
     * Anything else — a bad key, a 400, a cancelled turn — surfaces exactly as before.
     */
    private suspend fun completeStep(
        llm: LlmClient, model: String, sys: String, msgs: List<Msg>, tools: List<ToolSpec>, bubble: ChatItem.Assistant,
    ): Pair<Turn, String> {
        val streamed = StringBuffer()
        val listener = object : StreamListener {
            override fun onText(delta: String) {
                streamed.append(delta)
                scope.launch { bubble.text = streamed.toString() }
            }
        }
        fun reset() { streamed.setLength(0); bubble.text = "" }
        var last: Exception
        try {
            return llm.complete(sys, msgs, tools, listener) to model
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (!LlmRetry.isRetryable(e)) throw e
            last = e
        }
        if (settings.retryLlm) {
            delay(LlmRetry.backoffMs(0))
            reset()
            try {
                return llm.complete(sys, msgs, tools, listener) to model
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (!LlmRetry.isRetryable(e)) throw e
                last = e
            }
        }
        // v0.17 smart router: walk a failover chain (the user's fallback first, then capable alternatives on other providers)
        if (settings.smartRouter) {
            val failing = RouteTarget(routeProviders[model] ?: settings.activeProvider, model)
            val needsVision = (msgs.lastOrNull { it is Msg.User } as Msg.User?)?.images?.isNotEmpty() == true
            val req = RouteRequest(ModelTier.STRONG, failing, needsVision, tools.isNotEmpty(), ((sys.length + msgs.sumOf { size(it) }) / 3.6).toInt())
            val available = settings.routableProviders()
            val chain = SmartRouter.failoverChain(failing, req, SmartRouter.candidates(available, settings.prices(), settings.visionOverrides),
                settings.fallbackModel, available)
            if (chain.isEmpty()) throw last
            items += ChatItem.Notice(chain.first().label, kind = ChatItem.Notice.Kind.FALLBACK)
            audit("router", Risk.SAFE, AuditEntry.Decision.AUTO, true, 0, "failover ${failing.label} → ${chain.first().label} (${(last.message ?: "error").take(80)})")
            reset()
            val (turn, used) = Failover.run(chain, onSwitch = { from, to, e ->
                items += ChatItem.Notice(to.label, kind = ChatItem.Notice.Kind.FALLBACK)
                audit("router", Risk.SAFE, AuditEntry.Decision.AUTO, true, 0, "failover ${from.label} → ${to.label} (${(e.message ?: "error").take(80)})")
                reset()
            }) { t ->
                routeProviders[t.model] = t.provider
                clientFor(t.model).complete(sys, msgs, tools, listener)
            }
            return turn to used.model
        }
        val primary = settings.activeProvider
        val fb = LlmRetry.usable(settings.fallbackModel, primary, model) { settings.providerConfigured(it) } ?: throw last
        val fdef = Providers.all.firstOrNull { it.id == fb.provider } ?: throw last
        val fcfg = settings.config(fdef.id)
        items += ChatItem.Notice(fb.label, kind = ChatItem.Notice.Kind.FALLBACK)
        reset()
        val fl = LlmClient(fdef, fcfg.baseUrl, fb.model, settings.apiKey(fdef.id))
        return fl.complete(sys, msgs, tools, listener) to fb.model
    }

    /**
     * v0.15 completion gate. Returns true when the coder must continue (checks failed and
     * rounds remain); false when the run may finish — with [verification] set honestly.
     */
    private suspend fun completionGate(feed: (String) -> Unit = { history += Msg.User(it) }): Boolean {
        if (!CompletionGate.shouldVerify(settings.verifyBeforeDone, settings.agentMode == "team", turnTouched.isNotEmpty())) return false
        hooks.onActivity(projectName, step, RunActivity.forTool("verify", ""))
        var lint: String? = null
        var tests: String? = null
        if (linux.ready) {
            lint = runCatching { lintProject() }.getOrNull()?.takeIf { it.isNotBlank() }
            tests = runCatching { runProjectTests(timeoutSec = 300) }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains("No test runner detected") }
        }
        val status = CompletionGate.status(lint, tests)
        verification = status
        // v0.17 lessons: remember the first failure of this run; a later PASS turns it into an error → solution lesson
        if (status == CompletionGate.Status.FAILED && gateFail == null) { gateFail = lint to tests; fixFiles.clear() }
        if (status == CompletionGate.Status.PASSED && gateFail != null) { captureLesson(); gateFail = null }
        return when (CompletionGate.next(status, gateRounds)) {
            CompletionGate.Next.RETRY -> {
                gateRounds++
                items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.VERIFY, count = ChatItem.Notice.VERIFY_RETRY)
                val lessons = if (settings.memoryLessons) Lessons.section(Lessons.matching(memory, listOfNotNull(tests, lint).joinToString("\n"))) else ""
                feed(CompletionGate.feedback(lint, tests, gateRounds) + lessons)
                markDirty()
                true
            }
            CompletionGate.Next.FINISH -> {
                items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.VERIFY, count = status.ordinal)
                markDirty()
                false
            }
        }
    }

    private suspend fun executeTools(calls: List<ToolCall>, bubble: ChatItem.Assistant) {
        // results also land on the bubble right away, so a restart can rebuild what already ran
        val results = object : HashMap<String, String>() {
            override fun put(key: String, value: String): String? { bubble.results[key] = value; return super.put(key, value) }
        }
        // v0.16 Chief mode: a role only runs the tools of its subset; anything else is refused (and audited)
        val role = currentRole
        val refused = if (role == null) emptySet() else calls.filter { !AgentRoles.allows(role, it.name, mcpSpecs) }.map { it.id }.toSet()
        if (role != null) for (c in calls) if (c.id in refused) {
            results[c.id] = AgentRoles.refusal(role, c.name)
            audit(c.name, ToolRegistry.riskOf(c.name, mcpSpecs) ?: Risk.HIGH, AuditEntry.Decision.BLOCKED, false, 0, "not allowed for role ${role.id}")
        }
        val parsed = calls.filter { it.id !in refused }.map { c -> c to (runCatching { JSONObject(c.argsJson) }.getOrNull()) }

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

        var fileDecision = AuditEntry.Decision.AUTO
        if (changes.isNotEmpty()) {
            val needApproval = settings.approveAll || changes.size >= 2
            if (needApproval) fileDecision = AuditEntry.Decision.APPROVED
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
                turnTouched += changes.keys // v0.15: the completion gate checks runs that changed files
                if (gateFail != null) fixFiles += changes.keys // v0.17: the fix that turns the gate green becomes a lesson
                projectIndex.invalidate() // v0.17: files changed → next index build re-checks memory file refs
                pendingReview = changes.values.toList() // v0.7 team mode: the reviewer sees this batch
                roleChanges?.addAll(changes.values) // v0.16: the Chief hands these to the role's reviewers
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
                fileDecision = AuditEntry.Decision.REJECTED
                changeCallIds.keys.forEach { results[it] = "The user REJECTED these changes. Nothing was written. Ask what they would like instead." }
            }
        }
        // v0.15 audit: one line per file call
        for ((c, a) in parsed) {
            if (c.name !in Tools.MUTATING) continue
            val r = results[c.id].orEmpty()
            val d = if (r.startsWith("The user REJECTED")) AuditEntry.Decision.REJECTED else fileDecision.takeIf { c.id in changeCallIds } ?: AuditEntry.Decision.AUTO
            audit(c.name, ToolRegistry.riskOf(c.name) ?: Risk.MEDIUM, d, !LoopDetector.isFailure(r) && d != AuditEntry.Decision.REJECTED, 0,
                a?.optString("path").orEmpty())
        }

        // 2) Run the rest in order — v0.15: consecutive read-only SAFE calls run side by side (max 3),
        // each with its own timeout; results always land in the original call order.
        val rest = parsed.filter { it.first.name !in Tools.MUTATING }
        val batches = if (settings.parallelTools) ParallelPlan.batches(rest.map { it.first.name }) { ToolRegistry.isParallelSafe(it) }
            else rest.indices.map { listOf(it) }
        for (batch in batches) {
            if (batch.size == 1) runOne(rest[batch[0]].first, rest[batch[0]].second, results)
            else runParallel(batch.map { rest[it] }, results)
        }

        // v0.15 LoopDetector v2 evidence: did anything work this step, and which calls failed identically
        if (calls.isNotEmpty()) {
            stepOk += calls.any { c -> results[c.id]?.let { !LoopDetector.isFailure(it) } == true }
            while (stepOk.size > LoopDetector.FAILURE_WINDOW) stepOk.removeAt(0)
            for (c in calls) {
                val r = results[c.id] ?: continue
                if (LoopDetector.isFailure(r)) failPrints += LoopDetector.failurePrint(c, r)
            }
            while (failPrints.size > LoopDetector.FAILURE_WINDOW) failPrints.removeAt(0)
        }

        for (c in calls) history += Msg.ToolResult(c.id, c.name, results[c.id] ?: "Error: not executed")
    }

    /** One non-file tool call, exactly as before plus the v0.15 registry check, risky-command gate, audit and envelope. */
    private suspend fun runOne(c: ToolCall, a: JSONObject?, results: MutableMap<String, String>) {
        if (c.name == Tools.PLAN) {
            results[c.id] = updatePlan(a)
            audit(c.name, Risk.SAFE, AuditEntry.Decision.AUTO, true, 0, "plan")
            return
        }
        if (c.name == Tools.ASK) {
            val t0 = System.currentTimeMillis()
            results[c.id] = askUser(a, c.id)
            audit(c.name, Risk.SAFE, AuditEntry.Decision.AUTO, true, System.currentTimeMillis() - t0, "ask_user")
            return
        }
        val args = a ?: JSONObject()
        val item = ChatItem.Tool(c.name, Tools.summarizeArgs(c.name, args), c.id)
        items += item
        hooks.onActivity(projectName, step, RunActivity.forTool(c.name, item.summary))
        val def = ToolRegistry.resolve(c.name, mcpSpecs)
        var decision = AuditEntry.Decision.AUTO
        val out = try {
            when {
                // v0.15: a name outside the registry (and this turn's MCP list) is refused
                def == null -> { decision = AuditEntry.Decision.BLOCKED; ToolRegistry.unknownToolMessage(c.name) }
                c.name == Tools.GIT_PUSH -> gitPush(args).also { decision = approvalDecision(it) }
                c.name == Tools.GIT_COMMIT -> gitCommit(args).also { if (!it.startsWith("Error")) decision = approvalDecision(it) }
                c.name == Tools.RUN -> {
                    val (o, d) = runCommandGated(args)
                    if (d != AuditEntry.Decision.REJECTED && d != AuditEntry.Decision.BLOCKED) runCommands++
                    decision = d; o
                }
                settings.parallelTools && def.parallelSafe -> timedReadOnly(c.name, args, def)
                else -> runTool(c.name, args)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { item.ok = false; "Error: " + Redact.text(e.message ?: e.toString(), settings.githubToken) }
        finishRow(item, c, args, out, def?.risk ?: Risk.HIGH, decision, results)
    }

    /** A batch of parallel-safe calls: rows appear in order, work runs concurrently, results keep the order. */
    private suspend fun runParallel(batch: List<Pair<ToolCall, JSONObject?>>, results: MutableMap<String, String>) {
        val rows = batch.map { (c, a) ->
            ChatItem.Tool(c.name, Tools.summarizeArgs(c.name, a ?: JSONObject()), c.id).also { items += it }
        }
        hooks.onActivity(projectName, step, RunActivity.forTool(batch.first().first.name, rows.first().summary))
        val outs = ParallelPlan.mapBounded(batch) { (c, a) ->
            val def = ToolRegistry.get(c.name)!!
            try { timedReadOnly(c.name, a ?: JSONObject(), def) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { "Error: " + Redact.text(e.message ?: e.toString(), settings.githubToken) }
        }
        batch.forEachIndexed { i, (c, a) ->
            finishRow(rows[i], c, a ?: JSONObject(), outs[i], Risk.SAFE, AuditEntry.Decision.AUTO, results)
        }
    }

    private fun approvalDecision(out: String) =
        if (out.startsWith("The user REJECTED")) AuditEntry.Decision.REJECTED else AuditEntry.Decision.APPROVED

    /** Shared tail of every non-file call: row state, untrusted envelope, result, audit. */
    private fun finishRow(item: ChatItem.Tool, c: ToolCall, args: JSONObject, out: String, risk: Risk, decision: AuditEntry.Decision, results: MutableMap<String, String>,
                          role: String? = currentRole?.id) {
        val failed = LoopDetector.isFailure(out)
        if (failed) item.ok = false
        item.durationMs = System.currentTimeMillis() - item.createdAt
        item.result = out
        // v0.15: web and MCP output reaches the model inside an UNTRUSTED CONTENT envelope
        results[c.id] = if (settings.untrustedMarking) Untrusted.maybeWrap(c.name, untrustedSource(c.name, args), out) else out
        val d = if (decision == AuditEntry.Decision.AUTO && out.startsWith("Blocked")) AuditEntry.Decision.BLOCKED else decision
        audit(c.name, risk, d, item.ok && !failed && d != AuditEntry.Decision.REJECTED, item.durationMs, item.summary, role)
        markDirty()
    }

    private fun untrustedSource(name: String, a: JSONObject): String = when (name) {
        Tools.WEB_FETCH -> a.optString("url")
        Tools.WEB_SEARCH -> "web search \"" + a.optString("query").take(80) + "\""
        else -> "MCP tool $name"
    }

    /**
     * v0.15: a read-only SAFE tool under its registry timeout (retried on timeout when the
     * registry allows). Local file/search/git reads are interruptible; web tools also keep
     * their own network timeouts.
     */
    private suspend fun timedReadOnly(name: String, a: JSONObject, def: ToolDef): String {
        var attempt = 0
        while (true) {
            val out = withTimeoutOrNull(def.timeoutSec * 1000L) { readOnlyTool(name, a) }
            if (out != null) return out
            if (attempt >= def.maxRetries) return "Error: $name timed out after ${def.timeoutSec}s."
            attempt++
        }
    }

    private suspend fun readOnlyTool(name: String, a: JSONObject): String {
        DesertMode.cloudTool(name)?.let { f -> if (DesertState.active) return DesertMode.toolMessage(f) }
        return when (name) {
            Tools.WEB_SEARCH -> WebTools.search(a.optString("query"), a.optInt("max_results", 6))
            Tools.WEB_FETCH -> WebTools.fetch(a.optString("url"), a.optInt("max_chars", 8_000))
            else -> runInterruptible(Dispatchers.IO) { readLocal(name, a) }
        }
    }

    /** The blocking local read-only tools (files, search, symbol index, git status/diff/log). */
    private fun readLocal(name: String, a: JSONObject): String = when (name) {
        Tools.LIST -> Tools.listFiles(projectDir, a.optString("path", "."), a.optBoolean("recursive", true))
        Tools.READ -> Tools.readFile(projectDir, a.optString("path"),
            a.optInt("start_line", 0).takeIf { it > 0 }, a.optInt("end_line", 0).takeIf { it > 0 })
        Tools.SEARCH -> Tools.search(projectDir, a.optString("query"), a.optBoolean("regex"), a.optString("path", "."))
        Tools.PROJECT_SEARCH -> projectIndex.render(a.optString("query"))
        in Tools.GIT -> GitTools.run(name, a, projectDir, git, settings.gitName, settings.gitEmail)
        else -> ToolRegistry.unknownToolMessage(name)
    }

    /**
     * v0.15 tiered command policy: the SafetyPolicy blocklist still refuses outright (inside
     * [runTool]); a HIGH-risk command waits for the user's yes on an approval card when
     * `confirmRiskyCommands` is on. Only the user's tap completes that card — the model has
     * no tool that can approve anything.
     */
    private suspend fun runCommandGated(a: JSONObject): Pair<String, AuditEntry.Decision> {
        val cmd = a.optString("command")
        if (settings.confirmRiskyCommands) {
            val risk = CommandPolicy.assess(cmd)
            if (risk.needsApproval) {
                val card = ChatItem.Approval(emptyList(), action = CommandPolicy.describe(cmd, risk), actionKind = ChatItem.Approval.ActionKind.COMMAND)
                items += card
                val ok = awaitApproval(card, RunWait.Kind.COMMAND, cmd.lineSequence().firstOrNull().orEmpty())
                if (!ok) return ("The user REJECTED this high-risk command (" + risk.reasons.joinToString("; ") + "). Nothing was run. " +
                    "Do not retry it or an equivalent command; explain what you need or ask the user.") to AuditEntry.Decision.REJECTED
                return runTool(Tools.RUN, a) to AuditEntry.Decision.APPROVED
            }
        }
        return runTool(Tools.RUN, a) to AuditEntry.Decision.AUTO
    }

    /** v0.7 team mode: one reviewer call over the last applied batch (max [MAX_REVIEW_ROUNDS] per turn). */
    private suspend fun runReview() {
        val changes = pendingReview ?: return
        pendingReview = null
        if (reviewRounds >= MAX_REVIEW_ROUNDS) return
        reviewRounds++
        hooks.onActivity(projectName, step, RunActivity.forTool("review", "diff"))
        val (model, routed) = stageModel(FalconRouter.Stage.REVIEWER, settings.reviewModel)
        val card = ChatItem.Review(model)
        items += card
        markDirty()
        val diff = Reviewer.diffOf(changes)
        // v0.8: automatic checks (lint/tests) run before the reviewer and join its context
        val checks = if (settings.reviewAutoTests) collectChecks() else null
        val req = Reviewer.buildRequest(currentUser?.text ?: "", diff, checks)
        val out = runCatching {
            val rl = clientFor(model)
            rl.complete(Reviewer.SYSTEM, listOf(Msg.User(req)), emptyList(), object : StreamListener {
                override fun onText(delta: String) {}
            })
        }
        out.onSuccess { turn ->
            val used = turn.usage ?: UsageParser.estimate(req.length, turn.text.length)
            recordUsage(model, used)
            reviewUsage[model] = (reviewUsage[model] ?: Usage.ZERO) + used // v0.8: shown separately
            if (routed) recordSavings(model, used)
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

    /**
     * v0.8: quick automatic checks for the reviewer — lint always (cheap), tests only when a
     * runner exists. Failures of the helper functions never block the review itself.
     */
    private suspend fun collectChecks(): String? {
        if (!linux.ready) return null
        val results = ArrayList<Pair<String, String>>()
        runCatching { lintProject().takeIf { it.isNotBlank() } }?.getOrNull()?.let { results += "lint" to it }
        runCatching { runProjectTests(timeoutSec = 120) }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.contains("No test runner detected") }?.let { results += "tests" to it }
        return Reviewer.checksSection(results)
    }

    /**
     * v0.13.2: the compaction client — the coder's own unless Falcon Router found a cheaper
     * capable model for summarising (compaction has no explicit per-stage pick).
     */
    private fun compactClient(coderLlm: LlmClient, coderModel: String): Triple<LlmClient, String, Boolean> {
        val (model, routed) = stageModel(FalconRouter.Stage.COMPACTOR, "")
        if (model == coderModel) return Triple(coderLlm, coderModel, false)
        return Triple(clientFor(model), model, routed)
    }

    /** v0.7: summarise the oldest context into one note once it passes the threshold. */
    private suspend fun maybeCompact(llm: LlmClient, model: String, routed: Boolean = false) {
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
        val used = out.usage ?: UsageParser.estimate(rendered.length, out.text.length)
        recordUsage(model, used)
        if (routed) recordSavings(model, used)
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
        // v0.13.5 desert mode: cloud-bound tools refuse clearly mid-turn (the turn may have started online);
        // local tools — files, terminal, memory, index, local git, lint/tests — keep working untouched.
        DesertMode.cloudTool(name)?.let { f -> if (DesertState.active) return@withContext DesertMode.toolMessage(f) }
        // v0.15: only registered tools (built-ins + this turn's MCP tools) ever run
        if (ToolRegistry.resolve(name, mcpSpecs) == null) return@withContext ToolRegistry.unknownToolMessage(name)
        when {
            name.startsWith(Mcp.PREFIX) -> runMcpTool(name, a)
            name in Tools.GIT -> GitTools.run(name, a, projectDir, git, settings.gitName, settings.gitEmail)
            name == Tools.LIST -> Tools.listFiles(projectDir, a.optString("path", "."), a.optBoolean("recursive", true))
            name == Tools.READ -> Tools.readFile(projectDir, a.optString("path"),
                a.optInt("start_line", 0).takeIf { it > 0 }, a.optInt("end_line", 0).takeIf { it > 0 })
            name == Tools.SEARCH -> Tools.search(projectDir, a.optString("query"), a.optBoolean("regex"), a.optString("path", "."))
            name == Tools.PROJECT_SEARCH -> projectIndex.render(a.optString("query"))
            name == Tools.RUN -> {
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
            name == Tools.WEB_SEARCH -> WebTools.search(a.optString("query"), a.optInt("max_results", 6))
            name == Tools.WEB_FETCH -> WebTools.fetch(a.optString("url"), a.optInt("max_chars", 8_000))
            name == Tools.LINT -> lintProject()
            name == Tools.TESTS -> runProjectTests()
            // v0.17: typed entries, file refs and the (opt-in) global layer; every layer refuses secrets
            name == Tools.MEMORY_SAVE -> MemoryEngine.save(a, memory, globalBank, settings.globalMemory, *memorySecrets())
            name == Tools.MEMORY_FORGET -> MemoryEngine.forget(a, memory, globalBank, settings.globalMemory)
            else -> "Error: unknown tool $name"
        }
    }

    /** The tool list the model sees: built-ins plus the MCP tools discovered this turn (v0.15: via the registry, same content). */
    private fun specs(): List<ToolSpec> = ToolRegistry.specs(mcpSpecs)

    /** v0.8: connects to the user's MCP servers once per turn and collects their tools.
     *  v0.11: HTTP servers first, then stdio servers, sharing the same [MAX_MCP_SERVERS] budget. */
    private suspend fun refreshMcpTools() {
        mcpSpecs = emptyList(); mcpClients = emptyList()
        stdioClients.forEach { runCatching { it.second.close() } }; stdioClients = emptyList()
        val servers = settings.mcpServers.take(MAX_MCP_SERVERS)
        var budget = MAX_MCP_SERVERS - servers.size
        if (servers.isEmpty() && settings.mcpStdioServers.isEmpty()) return
        var anyOk = false
        for (s in servers) {
            // v0.13.5 desert mode: remote servers are skipped (with a notice) instead of timing out one by one.
            if (DesertState.active) {
                items += ChatItem.Notice(
                    "MCP \u201c${s.name}\u201d: desert mode is active (no network) — its tools are skipped this turn.",
                    isError = false, kind = ChatItem.Notice.Kind.MCP,
                )
                continue
            }
            // v0.14.1 OAuth: refresh a token that is about to expire (best effort) before connecting.
            if (settings.mcpOAuthConfig(s.name) != null) McpOAuthRuntime.maybeRefresh(settings, s.name)
            val cl = McpClient(Mcp.Server(s.name, s.url, settings.mcpAuthHeader(s.name)))
            val tools = withContext(Dispatchers.IO) { runCatching { cl.listTools() }.getOrNull() }
            if (tools == null || tools.isEmpty()) {
                items += ChatItem.Notice(
                    // v0.14.1: an unauthorized server names the way out instead of a bare HTTP error.
                    if (cl.unauthorized) "MCP “${s.name}”: ${cl.endpointError} — sign in again from Settings → MCP servers."
                    else "MCP “${s.name}”: ${cl.endpointError ?: "no tools found"} — its tools are skipped this turn.",
                    isError = false, kind = ChatItem.Notice.Kind.MCP,
                )
                continue
            }
            // v0.10: the user may hide specific tools of this server from the agent.
            val visible = Mcp.filterTools(tools, settings.mcpDisabledTools[s.slug].orEmpty())
            val hidden = tools.size - visible.size
            if (hidden > 0) {
                items += ChatItem.Notice(
                    "MCP “${s.name}”: $hidden tool(s) hidden by you (Settings → MCP).",
                    isError = false, kind = ChatItem.Notice.Kind.MCP,
                )
            }
            if (visible.isEmpty()) continue
            mcpClients += s to cl
            mcpSpecs = mcpSpecs + visible.map { ToolSpec(it.name, it.description, it.inputSchema) }
            anyOk = true
        }
        // v0.11: stdio servers through the terminal env (proot Linux when installed)
        for (s in settings.mcpStdioServers.take(budget)) {
            val cl = McpStdioClient(s, linux, projectDir)
            val started = withContext(Dispatchers.IO) { runCatching { cl.start() }.getOrDefault(false) }
            val tools = if (started) withContext(Dispatchers.IO) { runCatching { cl.listTools() }.getOrNull() } else null
            if (tools == null || tools.isEmpty()) {
                runCatching { cl.close() }
                items += ChatItem.Notice(
                    "MCP “${s.name}” (stdio): ${cl.endpointError ?: "no tools found"} — its tools are skipped this turn.",
                    isError = false, kind = ChatItem.Notice.Kind.MCP,
                )
                continue
            }
            val visible = Mcp.filterTools(tools, settings.mcpDisabledTools[s.slug].orEmpty())
            if (visible.isEmpty()) { runCatching { cl.close() }; continue }
            stdioClients += s to cl
            mcpSpecs = mcpSpecs + visible.map { ToolSpec(it.name, it.description, it.inputSchema) }
            anyOk = true
        }
        if (anyOk) markDirty()
    }

    /** Executes one `mcp_<server>_<tool>` call against the matching client (HTTP or stdio). */
    private suspend fun runMcpTool(full: String, a: JSONObject): String = withContext(Dispatchers.IO) {
        val (slug, tool) = Mcp.splitToolName(full)
            ?: return@withContext "Error: malformed MCP tool name '$full' (expected mcp_<server>_<tool>)"
        val http = mcpClients.firstOrNull { it.first.slug == slug }?.second
        val stdio = if (http == null) stdioClients.firstOrNull { it.first.slug == slug }?.second else null
        // v0.13.5 desert mode: remote (HTTP) servers are cloud-bound; local stdio servers keep working.
        if (http != null && DesertState.active) return@withContext DesertMode.toolMessage(DesertMode.Feature.MCP_REMOTE)
        if (http == null && stdio == null) {
            return@withContext "Error: MCP server '$slug' is not connected this turn. Tell the user to check Settings → MCP servers."
        }
        // v0.10 defense in depth: a tool hidden by the user is refused even if the model calls it.
        if (tool in settings.mcpDisabledTools[slug].orEmpty()) {
            return@withContext "Error: tool '$tool' is disabled by the user for this MCP server. Do not retry it."
        }
        return@withContext if (http != null) http.callTool(tool, a) else stdio!!.callTool(tool, a)
    }

    /**
     * v0.8 team mode: an explicit planning pass before the coder loop. A cheap model (or the
     * coder's own model) turns the request into a task list that fills the progress pill and
     * is handed to the coder. Silent on failure — the coder can work without a plan.
     */
    /** v0.1.9 plan mode. Returns false when the user cancelled. A planner failure lets the run continue unplanned. */
    private suspend fun planReview(coderModel: String): Boolean {
        val user = currentUser ?: return true
        if (!Planner.worthPlanning(user.text)) return true
        val model = stageModel(FalconRouter.Stage.PLANNER, settings.plannerModel).first.ifBlank { coderModel }
        if (model.isBlank()) return true
        hooks.onActivity(projectName, 0, RunActivity.forTool("planner", ""))
        val req = Planner.buildRequest(user.text)
        val turn = runCatching {
            clientFor(model).complete(Planner.SYSTEM, listOf(Msg.User(req)), emptyList(), object : StreamListener {
                override fun onText(delta: String) {}
            })
        }.getOrNull() ?: return true
        recordUsage(model, turn.usage ?: UsageParser.estimate(req.length, turn.text.length))
        val parsed = Planner.parse(turn.text) ?: return true
        val card = ChatItem.PlanReview(parsed.tasks)
        items += card
        saveNow()
        hooks.onWaiting(projectName, RunWait(RunWait.Kind.ASK, parsed.tasks.joinToString(" · ").take(200)))
        persistTask(TaskState.Status.WAITING_APPROVAL)
        val t0 = System.currentTimeMillis()
        val ok = card.deferred.await()
        waitMs += System.currentTimeMillis() - t0
        persistTask(TaskState.Status.RUNNING)
        if (!ok) return false
        val edited = Planner.fromEdited(card.tasks.toList()) ?: return true
        plan.clear(); plan.addAll(Planner.toPlanItems(edited))
        items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.PLANNED, count = edited.tasks.size)
        history += Msg.User(Planner.planMessage(edited).replace("Suggested task plan", "Task plan approved by the user"))
        markDirty()
        return true
    }

    private suspend fun runPlanner() {
        val user = currentUser ?: return
        if (!Planner.worthPlanning(user.text)) return
        val (model, routed) = stageModel(FalconRouter.Stage.PLANNER, settings.plannerModel)
        if (model.isBlank()) return
        hooks.onActivity(projectName, 0, RunActivity.forTool("planner", ""))
        val req = Planner.buildRequest(user.text)
        val out = runCatching {
            val pl = clientFor(model)
            pl.complete(Planner.SYSTEM, listOf(Msg.User(req)), emptyList(), object : StreamListener {
                override fun onText(delta: String) {}
            })
        }
        out.onSuccess { turn ->
            val used = turn.usage ?: UsageParser.estimate(req.length, turn.text.length)
            recordUsage(model, used)
            plannerUsage[model] = (plannerUsage[model] ?: Usage.ZERO) + used
            if (routed) recordSavings(model, used)
            val parsed = Planner.parse(turn.text) ?: return
            plan.clear(); plan.addAll(Planner.toPlanItems(parsed))
            items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.PLANNED, count = parsed.tasks.size)
            history += Msg.User(Planner.planMessage(parsed))
            markDirty()
        }
    }

    // ------------------------------------------------------------------ v0.16 agent team (Chief mode)

    /** The client the Chief's roles fall back to (the coder's own), set at the start of a Chief run. */
    private var chiefCoderLlm: LlmClient? = null
    private var chiefCoderModel: String = ""

    /** (model, routed) for [role]: the tier's explicit pick, else Falcon Router, else the coder's model. */
    private fun roleModel(role: AgentRole, log: Boolean = true): Pair<String, Boolean> {
        if (settings.smartRouter) {
            val explicit = when {
                role.tier == ModelTier.INDEPENDENT_REVIEW -> settings.reviewModel
                role == AgentRole.PLANNER || role == AgentRole.CHIEF -> settings.plannerModel
                else -> ""
            }
            if (explicit.isNotBlank()) return explicit.trim() to false
            val d = smartRoute(role.tier, role.id, needsTools = role.kind == AgentRole.Kind.WORKER || role.kind == AgentRole.Kind.READER, log = log)
            return d.target.model to d.routed
        }
        val def = Providers.byId(settings.activeProvider)
        val cfg = settings.config(def.id)
        return AgentRoles.modelFor(role, cfg.model, settings.reviewModel, settings.plannerModel, settings.falconRouter,
            (def.suggestedModels + cfg.model).distinct(), settings.prices())
    }

    private fun roleClient(role: AgentRole): Triple<LlmClient, String, Boolean> {
        val (m, routed) = roleModel(role)
        val coder = chiefCoderLlm
        if (coder != null && m == chiefCoderModel) return Triple(coder, chiefCoderModel, false)
        return Triple(clientFor(m), m, routed)
    }

    /** The whole run's budget (all roles share it). */
    private fun budgetHit(): Budgets.Hit =
        if (spendCapHit() != SpendCap.Hit.NONE) Budgets.Hit.COST // v0.17: the spend cap stops the team like a cost budget (its own notice)
        else Budgets.check(settings.budgets(), runSteps, activeMs(), runCost())

    private fun showGraph(g: TaskGraph, arabic: Boolean) {
        plan.clear(); plan.addAll(g.toPlanItems(arabic))
        markDirty()
    }

    private fun teamNotice(subtype: Int, text: String) {
        items += ChatItem.Notice(text, kind = ChatItem.Notice.Kind.TEAM, count = subtype)
        markDirty()
    }

    private fun roleCosts(): List<TeamCost.RoleCost> = TeamCost.aggregate(roleUsage, settings.prices())

    /**
     * v0.16 Chief mode. The Chief classifies the request (rules; the cheap model only when they
     * are unsure), picks the minimum roles, builds the task graph (the Planner's JSON when the
     * Planner was picked) and runs it through [ChiefRunner]. Each role gets only the context it
     * needs; budgets, the completion gate, the audit log and the loop guard apply as in a
     * classic run. A restart resumes the saved graph instead of starting over.
     */
    private suspend fun chiefLoop(llm: LlmClient, model: String): RunOutcome {
        chiefCoderLlm = llm; chiefCoderModel = model
        loadCapBase()
        if (spendCapHit() != SpendCap.Hit.NONE) return capStop("")
        val goal = currentUser?.text.orEmpty()
        val arabic = TeamLang.isArabic(goal)
        val enabled = settings.teamEnabledRoles()
        var graph = teamGraph?.resetRunning()?.takeIf { !it.isEmpty }
        if (graph == null) {
            hooks.onActivity(projectName, 0, RunActivity.forTool("chief", ""))
            var cls = Chief.classify(goal)
            if (cls.ambiguous) cls = classifyWithModel(goal) ?: cls
            val sel = Chief.select(cls.intent, goal, enabled)
            teamNotice(ChatItem.Notice.TEAM_SELECTED, sel.roles.joinToString(",") { it.id })
            if (sel.noTeam) {
                // a question: no team — the classic single loop answers it
                chiefSolo = true
                return loop()
            }
            graph = (if (AgentRole.PLANNER in sel.roles) planTeam(goal, sel, arabic) else null) ?: Chief.defaultGraph(sel, arabic)
            if (settings.teamCostEstimate) {
                if (settings.smartRouter) {
                    // v0.17: real per-model prices from the current context size, as a low–high range
                    val ctx = (history.sumOf { size(it) } / 3.6).toLong() + SYSTEM_PROMPT_TOKENS
                    val r = CostEstimate.range(graph.tasks.map { roleModel(it.role, log = false).first to TeamCost.expectedSteps(it.role) }, ctx, settings.prices())
                    teamNotice(ChatItem.Notice.TEAM_ESTIMATE, r?.let { "${TeamCost.usd(it.low)}–${TeamCost.usd(it.high)}" } ?: "")
                } else {
                    val est = TeamCost.estimate(graph.tasks.map { it.role }, { roleModel(it).first }, settings.prices())
                    teamNotice(ChatItem.Notice.TEAM_ESTIMATE, est?.let { TeamCost.usd(it) } ?: "")
                }
            }
        }
        teamGraph = graph
        showGraph(graph, arabic)
        persistTask(TaskState.Status.RUNNING)
        // MCP tools connect once per run, only once a team is needed (a question goes to the classic loop, which connects them itself)
        refreshMcpTools()
        val port = object : TeamPort {
            override suspend fun work(task: Subtask, context: String): WorkResult {
                teamNotice(ChatItem.Notice.TEAM_STARTED, "${task.role.id}|${task.title}")
                return if (task.role.kind == AgentRole.Kind.READER) readerLoop(task, context, arabic) else workerLoop(task, context, arabic)
            }
            override suspend fun review(task: Subtask, context: String, diff: String): Reviewer.Report? = verdictCall(task, context, diff)
            override suspend fun ask(conflict: TeamConflict): Boolean =
                Chief.askContinue(askUser(Chief.askArgs(conflict, arabic), "chief-" + System.currentTimeMillis().toString(36)))
            override fun budgetStop(): Boolean = budgetHit() != Budgets.Hit.NONE
            override fun onGraph(graph: TaskGraph) {
                teamGraph = graph
                showGraph(graph, arabic)
                persistTask(TaskState.Status.RUNNING)
            }
            override fun onEvent(event: TeamEvent) = when (event) {
                is TeamEvent.Reassigned -> teamNotice(ChatItem.Notice.TEAM_REASSIGNED, "${event.to.id}|${event.task.title}")
                is TeamEvent.Conflict -> teamNotice(ChatItem.Notice.TEAM_CONFLICT, event.conflict.detail)
                is TeamEvent.Failed -> teamNotice(ChatItem.Notice.TEAM_FAILED, "${event.task.role.id}|${event.task.title}|${event.task.note.take(160)}")
                is TeamEvent.Rework -> teamNotice(ChatItem.Notice.TEAM_REWORK, "${event.task.role.id}|${event.task.title}")
            }
        }
        val runner = ChiefRunner(goal, graph, port, enabled, settings.teamMaxConcurrent, touched.toList().takeLast(20))
        var g = runner.run()
        teamGraph = g
        showGraph(g, arabic)

        // budgets: the whole team run stops cleanly, exactly like a classic run
        val hit = budgetHit()
        if (runner.stopped && hit != Budgets.Hit.NONE) return teamEnd(g, arabic, stopKind(hit))
        if (runner.stopped) return teamEnd(g, arabic, RunOutcome.Kind.STOPPED)

        // v0.15 completion gate over everything the team changed; failures go to the Debugger
        var feedback: String? = null
        while (completionGate { feedback = it }) {
            val h = budgetHit()
            if (h != Budgets.Hit.NONE) return teamEnd(g, arabic, stopKind(h))
            val fixer = if (AgentRole.DEBUGGER in enabled) AgentRole.DEBUGGER else AgentRole.DEVELOPER
            val fix = Subtask("verify$gateRounds", if (arabic) "أصلح الفحوص الفاشلة" else "Fix the failing checks", fixer, status = SubtaskStatus.RUNNING)
            g = g.add(fix); teamGraph = g; showGraph(g, arabic)
            val r = port.work(fix, RoleContext.build(goal, fix, emptyList(), turnTouched.toList(), feedback))
            g = g.update(fix.id) { it.copy(status = if (r.ok) SubtaskStatus.DONE else SubtaskStatus.FAILED, output = r.output.take(TaskGraphCodec.OUTPUT_KEEP),
                files = r.changes.map { c -> c.path }.distinct(), note = r.failure.orEmpty()) }
            teamGraph = g; showGraph(g, arabic)
            if (r.stop) {
                val h2 = budgetHit()
                return teamEnd(g, arabic, if (h2 != Budgets.Hit.NONE) stopKind(h2) else RunOutcome.Kind.STOPPED)
            }
        }
        return teamEnd(g, arabic, RunOutcome.Kind.DONE)
    }

    private fun stopKind(hit: Budgets.Hit): RunOutcome.Kind = when (hit) {
        Budgets.Hit.STEPS -> {
            items += ChatItem.Notice("Step limit reached ($maxSteps). Send a message to continue.", kind = ChatItem.Notice.Kind.STEP_LIMIT)
            RunOutcome.Kind.STEP_LIMIT
        }
        else -> {
            val text = if (capHit != SpendCap.Hit.NONE) SpendCap.noticeText(capHit) else if (hit == Budgets.Hit.TIME) "time" else "cost"
            items += ChatItem.Notice(text, kind = ChatItem.Notice.Kind.BUDGET)
            RunOutcome.Kind.BUDGET
        }
    }

    /** The team's merged result as the final answer, the per-role cost line, and the outcome. */
    private fun teamEnd(g: TaskGraph, arabic: Boolean, kind: RunOutcome.Kind): RunOutcome {
        val text = TeamResult.merge(g, arabic)
        val bubble = ChatItem.Assistant()
        bubble.text = text; bubble.streaming = false
        items += bubble
        history += Msg.Assistant(text, emptyList())
        val costs = roleCosts()
        if (costs.isNotEmpty()) teamNotice(ChatItem.Notice.TEAM_COST, TeamCost.encode(costs))
        markDirty()
        return RunOutcome(kind, NotifText.summary(text), verification, costs)
    }

    /** The cheap model's one-word intent, only when the rules were unsure. Null on any failure. */
    private suspend fun classifyWithModel(goal: String): Chief.Classification? {
        val (cl, m, routed) = roleClient(AgentRole.CHIEF)
        val req = goal.take(1_500)
        val out = runCatching {
            cl.complete(Chief.CLASSIFY_SYSTEM, listOf(Msg.User(req)), emptyList(), object : StreamListener { override fun onText(delta: String) {} })
        }.getOrNull() ?: return null
        val used = out.usage ?: UsageParser.estimate(req.length + Chief.CLASSIFY_SYSTEM.length, out.text.length)
        recordUsage(m, used, AgentRole.CHIEF)
        if (routed) recordSavings(m, used)
        val intent = Chief.parseIntent(out.text) ?: return null
        return Chief.Classification(intent, false, "model")
    }

    /** The Planner role: the team JSON plan (roles + dependencies) turned into a safe graph; null = use the default graph. */
    private suspend fun planTeam(goal: String, sel: Chief.Selection, arabic: Boolean): TaskGraph? {
        val (pl, m, routed) = roleClient(AgentRole.PLANNER)
        hooks.onActivity(projectName, 0, RunActivity.forTool("planner", ""))
        val req = Planner.buildRequest(goal)
        val sys = Planner.teamSystem(sel.roles)
        val out = runCatching {
            pl.complete(sys, listOf(Msg.User(req)), emptyList(), object : StreamListener { override fun onText(delta: String) {} })
        }.getOrNull() ?: return null
        val used = out.usage ?: UsageParser.estimate(req.length + sys.length, out.text.length)
        recordUsage(m, used, AgentRole.PLANNER)
        plannerUsage[m] = (plannerUsage[m] ?: Usage.ZERO) + used
        if (routed) recordSavings(m, used)
        val parsed = Planner.parse(out.text) ?: return null
        val g = Chief.graphFromPlan(parsed, sel, arabic)
        if (g == null) { teamNotice(ChatItem.Notice.TEAM_GRAPH_FALLBACK, ""); return null }
        items += ChatItem.Notice("", kind = ChatItem.Notice.Kind.PLANNED, count = g.tasks.size)
        return g
    }

    /**
     * A WORKER role: the regular agent loop on a scoped history (goal + subtask + dependency
     * reports), with the role's tool subset, its step cap and its own loop guard. Approvals,
     * checkpoints, the command policy and the audit log work exactly as in a classic run.
     */
    private suspend fun workerLoop(t: Subtask, context: String, arabic: Boolean): WorkResult {
        val role = t.role
        val (rl, rm, routed) = roleClient(role)
        val saved = ArrayList(history)
        // v0.17: the Debugger sees lessons from earlier fixes that match the error it was handed
        val lessons = if (role == AgentRole.DEBUGGER && settings.memoryLessons) Lessons.section(Lessons.matching(memory, context)) else ""
        history.clear(); history += Msg.User(context + lessons)
        val prevRole = currentRole
        currentRole = role
        val changes = ArrayList<FileChange>()
        roleChanges = changes
        // LoopDetector per role: every role starts with fresh evidence
        loopPrints.clear(); failPrints.clear(); stepOk.clear(); loopNudged = false
        var last = ""
        var steps = 0
        try {
            while (true) {
                if (budgetHit() != Budgets.Hit.NONE) return WorkResult(false, last, changes.toList(), "budget", stop = true)
                if (steps >= role.maxSteps) return WorkResult(false, last, changes.toList(), "reached the ${role.nameEn} step cap (${role.maxSteps})")
                trimHistory()
                steps++; runSteps++
                this.step = runSteps
                persistTask(TaskState.Status.RUNNING)
                hooks.onActivity(projectName, step, RunActivity.THINKING)
                val bubble = ChatItem.Assistant()
                items += bubble
                val roleMcp = if (role.mcp) mcpSpecs else emptyList()
                val sys = withContext(Dispatchers.IO) {
                    SystemPrompt.build(projectDir, touched, linux.ready, false, roleMcp.map { it.name },
                        memory = memoryBlock(context), untrusted = settings.untrustedMarking, riskyApproval = settings.confirmRiskyCommands,
                        skills = skillSection(role), learning = settings.learningMode) +
                        AgentRoles.systemSection(role, arabic)
                }
                val inputChars = sys.length + history.sumOf { size(it) }
                contextTokens = (inputChars / 3.6).toInt()
                val (turn, usedModel) = completeStep(rl, rm, sys, ImagePruner.prune(history), AgentRoles.specs(role, mcpSpecs), bubble)
                val used = turn.usage ?: UsageParser.estimate(inputChars, turn.text.length + turn.toolCalls.sumOf { it.argsJson.length + it.name.length })
                recordUsage(usedModel, used, role)
                if (routed && usedModel == rm) recordSavings(usedModel, used)
                bubble.text = turn.text
                bubble.calls = turn.toolCalls
                bubble.streaming = false
                if (turn.text.isNotBlank()) last = turn.text
                history += Msg.Assistant(turn.text, turn.toolCalls)
                markDirty()
                if (turn.toolCalls.isEmpty()) return WorkResult(true, last.ifBlank { "(no report)" }, changes.toList())
                executeTools(turn.toolCalls, bubble)
                loopPrints += LoopDetector.fingerprint(turn.toolCalls)
                if (loopPrints.size > LoopDetector.WINDOW) loopPrints.removeAt(0)
                val loop = LoopDetector.detect(loopPrints, failPrints, stepOk, settings.smartLoopGuard)
                if (loop != null) {
                    if (!loopNudged) {
                        loopNudged = true
                        history += Msg.User(LoopDetector.nudge(loop))
                        if (loop == LoopDetector.Kind.REPEATED_FAILURE || loop == LoopDetector.Kind.NO_PROGRESS) { failPrints.clear(); stepOk.clear() }
                    } else {
                        items += ChatItem.Notice(loop.name, kind = ChatItem.Notice.Kind.LOOP)
                        return WorkResult(false, last, changes.toList(), "loop guard stopped the ${role.nameEn} (${loop.name.lowercase()})")
                    }
                }
            }
        } finally {
            history.clear(); history.addAll(saved)
            currentRole = prevRole
            roleChanges = null
        }
    }

    /**
     * A READER role (Research, Architect): an isolated read-only loop on its own small history.
     * Only read-only SAFE tools ever run, so two readers may share the phone side by side
     * (teamMaxConcurrent = 2). Its own loop guard; at its step cap it is asked for its report.
     */
    private suspend fun readerLoop(t: Subtask, context: String, arabic: Boolean): WorkResult {
        val role = t.role
        val (rl, rm, routed) = roleClient(role)
        val h = arrayListOf<Msg>(Msg.User(context))
        val specs = ToolRegistry.specs(emptyList()).filter { AgentRoles.readerAllows(role, it.name) }
        val prints = ArrayList<String>(); val fails = ArrayList<String>(); val oks = ArrayList<Boolean>()
        var nudged = false
        var last = ""
        var steps = 0
        while (true) {
            if (budgetHit() != Budgets.Hit.NONE) return WorkResult(false, last, failure = "budget", stop = true)
            val finalCall = steps >= role.maxSteps
            if (finalCall) h += Msg.User("Step limit reached: write your report to the Chief now, without tools.")
            steps++; runSteps++
            this.step = runSteps
            persistTask(TaskState.Status.RUNNING)
            hooks.onActivity(projectName, step, RunActivity.THINKING)
            val bubble = ChatItem.Assistant()
            items += bubble
            val sys = withContext(Dispatchers.IO) {
                SystemPrompt.build(projectDir, touched, linux.ready, false, emptyList(), memory = memoryBlock(context),
                    untrusted = settings.untrustedMarking, riskyApproval = false, skills = skillSection(role)) + AgentRoles.systemSection(role, arabic)
            }
            val (turn, usedModel) = completeStep(rl, rm, sys, h, if (finalCall) emptyList() else specs, bubble)
            val used = turn.usage ?: UsageParser.estimate(sys.length + h.sumOf { size(it) }, turn.text.length + turn.toolCalls.sumOf { it.argsJson.length })
            recordUsage(usedModel, used, role)
            if (routed && usedModel == rm) recordSavings(usedModel, used)
            bubble.text = turn.text; bubble.calls = turn.toolCalls; bubble.streaming = false
            if (turn.text.isNotBlank()) last = turn.text
            h += Msg.Assistant(turn.text, turn.toolCalls)
            markDirty()
            if (turn.toolCalls.isEmpty() || finalCall) {
                return if (last.isNotBlank()) WorkResult(true, last) else WorkResult(false, last, failure = "no report from the ${role.nameEn}")
            }
            val results = object : HashMap<String, String>() {
                override fun put(key: String, value: String): String? { bubble.results[key] = value; return super.put(key, value) }
            }
            val allowed = turn.toolCalls.filter { AgentRoles.readerAllows(role, it.name) }
            turn.toolCalls.filter { it !in allowed }.forEach { c ->
                results[c.id] = AgentRoles.refusal(role, c.name)
                audit(c.name, ToolRegistry.riskOf(c.name, mcpSpecs) ?: Risk.HIGH, AuditEntry.Decision.BLOCKED, false, 0, "not allowed for role ${role.id}", role.id)
            }
            if (allowed.isNotEmpty()) {
                val parsed = allowed.map { c -> c to (runCatching { JSONObject(c.argsJson) }.getOrNull() ?: JSONObject()) }
                val rows = parsed.map { (c, a) -> ChatItem.Tool(c.name, Tools.summarizeArgs(c.name, a), c.id).also { items += it } }
                val outs = ParallelPlan.mapBounded(parsed) { (c, a) ->
                    try { timedReadOnly(c.name, a, ToolRegistry.get(c.name)!!) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { "Error: " + Redact.text(e.message ?: e.toString(), settings.githubToken) }
                }
                parsed.forEachIndexed { i, (c, a) -> finishRow(rows[i], c, a, outs[i], Risk.SAFE, AuditEntry.Decision.AUTO, results, role.id) }
            }
            for (c in turn.toolCalls) h += Msg.ToolResult(c.id, c.name, results[c.id] ?: "Error: not executed")
            // the reader's own loop guard
            oks += turn.toolCalls.any { c -> results[c.id]?.let { !LoopDetector.isFailure(it) } == true }
            while (oks.size > LoopDetector.FAILURE_WINDOW) oks.removeAt(0)
            turn.toolCalls.forEach { c -> results[c.id]?.takeIf { LoopDetector.isFailure(it) }?.let { fails += LoopDetector.failurePrint(c, it) } }
            while (fails.size > LoopDetector.FAILURE_WINDOW) fails.removeAt(0)
            prints += LoopDetector.fingerprint(turn.toolCalls)
            if (prints.size > LoopDetector.WINDOW) prints.removeAt(0)
            val loop = LoopDetector.detect(prints, fails, oks, settings.smartLoopGuard)
            if (loop != null) {
                if (!nudged) { nudged = true; h += Msg.User(LoopDetector.nudge(loop)); fails.clear(); oks.clear() }
                else {
                    items += ChatItem.Notice(loop.name, kind = ChatItem.Notice.Kind.LOOP)
                    return WorkResult(false, last, failure = "loop guard stopped the ${role.nameEn} (${loop.name.lowercase()})")
                }
            }
        }
    }

    /** A VERDICT role (Reviewer, Security): one strict-JSON review of the team's diff, on the review tier's model. */
    private suspend fun verdictCall(t: Subtask, context: String, diff: String): Reviewer.Report? {
        val role = t.role
        val (rl, m, routed) = roleClient(role)
        hooks.onActivity(projectName, step, RunActivity.forTool("review", role.id))
        val card = ChatItem.Review(m)
        items += card
        markDirty()
        val checks = if (role == AgentRole.REVIEWER && settings.reviewAutoTests) collectChecks() else null
        val sys = Reviewer.SYSTEM + "\n\n# Your focus as the ${role.nameEn} (${role.nameAr})\n" + role.prompt + skillSection(role)
        val req = Reviewer.buildRequest(currentUser?.text ?: "", diff, checks) + "\n\n## Team context\n" + context.take(3_000)
        val out = runCatching {
            rl.complete(sys, listOf(Msg.User(req)), emptyList(), object : StreamListener { override fun onText(delta: String) {} })
        }
        val turn = out.getOrNull()
        if (turn == null) {
            val e = out.exceptionOrNull()
            if (e is CancellationException) throw e
            card.finish(null); markDirty()
            return null
        }
        val used = turn.usage ?: UsageParser.estimate(req.length + sys.length, turn.text.length)
        recordUsage(m, used, role)
        reviewUsage[m] = (reviewUsage[m] ?: Usage.ZERO) + used
        if (routed) recordSavings(m, used)
        val report = Reviewer.parse(turn.text)
        card.finish(report)
        markDirty()
        return report
    }

    /** v0.7: terminal lint checks + pure JSON/HTML checks, formatted as one report. */
    // ------------------------------------------------------------------ v0.17 memory engine & smart router

    /** The model's history from the items: with `sessionSummary` on, the rolling summary replaces the old turns. */
    private fun rebuildHistory(): List<Msg> =
        if (settings.sessionSummary) SessionMemory.rebuild(items, sessionSummary) else HistoryRebuilder.rebuild(items)

    /** Clears this session's rolling summary (Memory sheet); the next turn sends the full history again. */
    fun clearSessionSummary() { sessionSummary = null; markDirty() }

    /**
     * After a finished turn: when the verbatim part of a long session is over the threshold,
     * fold its old turns (plus the previous summary) into a new rolling summary on the cheap
     * compaction model. Any failure leaves the session exactly as it was.
     */
    private suspend fun rollSessionSummary() {
        if (!settings.sessionSummary) return
        val roll = SessionMemory.planRoll(items.toList(), sessionSummary) ?: return
        val (model, routed) = stageModel(FalconRouter.Stage.COMPACTOR, "")
        if (model.isBlank()) return
        val out = runCatching {
            clientFor(model).complete(ContextCompactor.SUMMARY_SYSTEM, listOf(Msg.User(roll.rendered)), emptyList(), object : StreamListener {
                override fun onText(delta: String) {}
            })
        }.getOrNull() ?: return
        val used = out.usage ?: UsageParser.estimate(roll.rendered.length, out.text.length)
        recordUsage(model, used)
        if (routed) recordSavings(model, used)
        val text = MemorySafety.scrub(out.text, *memorySecrets())
        val next = SessionMemory.apply(roll, text) ?: return
        sessionSummary = next
        markDirty()
    }

    /** The user's configured secrets the memory guard checks every layer against (never stored, never logged). */
    private fun memorySecrets(): Array<String?> =
        (listOf(settings.githubToken) + Providers.all.map { settings.apiKey(it.id) }).toTypedArray()

    /** What the relevance retrieval searches with: the request, plus recent error output, plus a role's subtask. */
    private fun recallQuery(extra: String): String = buildString {
        append(currentUser?.text.orEmpty().take(2_000))
        history.asReversed().asSequence().filterIsInstance<Msg.ToolResult>().take(6)
            .filter { LoopDetector.isFailure(it.content) }.forEach { append('\n').append(it.content.take(600)) }
        if (extra.isNotBlank()) append('\n').append(extra.take(2_000))
    }

    /** The `# MEMORY` block: the whole bank (relevance off, as before) or pinned preferences + top-K; the global layer only when on. */
    private fun memoryBlock(extra: String = ""): String {
        val cfg = MemoryEngine.Config(settings.memoryRelevance, settings.memoryTopK, settings.globalMemory)
        return MemoryEngine.block(memory, globalBank, cfg, if (cfg.relevance) recallQuery(extra) else "")
    }

    /** v0.17: the gate went FAILED → PASSED in this run — store (or reinforce) the error → solution lesson. */
    private fun captureLesson() {
        if (!settings.memoryLessons) return
        val (lint, tests) = gateFail ?: return
        val secrets = memorySecrets()
        val sig = Lessons.signature(lint?.let { MemorySafety.scrub(it, *secrets) }, tests?.let { MemorySafety.scrub(it, *secrets) }) ?: return
        val last = (items.lastOrNull { it is ChatItem.Assistant && it.text.isNotBlank() } as ChatItem.Assistant?)?.text.orEmpty()
        val solution = MemorySafety.scrub(Lessons.solution(fixFiles, last), *secrets)
        if (solution.isBlank()) return
        val (res, dedup) = memory.saveLesson(sig, solution, fixFiles.toList())
        if (res.startsWith("Remembered") || res.startsWith("Lesson")) {
            items += ChatItem.Notice(sig, kind = ChatItem.Notice.Kind.LESSON, count = if (dedup) 1 else 0)
            markDirty()
        }
    }

    // ------------------------------------------------------------------ v0.18 agent skills

    /**
     * Chooses this run's skills once: the enabled skills the user named as `${'$'}id`, plus — with
     * autoAttachSkills on — the top-N relevant ones. Each attachment is audited with its reason,
     * shown as a quiet pill and counted in the skill's usage. Never throws.
     */
    private fun attachSkills(request: String) {
        if (runSkills != null) return
        val installed = runCatching { settings.installedSkills }.getOrDefault(emptyList())
        val picked = com.ctodrs.saqrcode.agent.skills.SkillRuntime.select(installed, request, settings.skillsEnabled, settings.autoAttachSkills, settings.skillsTopN)
        runSkills = picked
        if (picked.isEmpty()) return
        for (a in picked) {
            audit("skills", Risk.SAFE, AuditEntry.Decision.AUTO, true, 0, com.ctodrs.saqrcode.agent.skills.SkillRuntime.auditLine(a), null)
            items += ChatItem.Notice("${a.skill.name}|${a.skill.version}|${a.reason}", kind = ChatItem.Notice.Kind.SKILL, count = if (a.explicit) 1 else 0)
        }
        runCatching { settings.installedSkills = com.ctodrs.saqrcode.agent.skills.SkillManager.recordUse(installed, picked.map { it.skill.id }) }
        markDirty()
    }

    /** The installed-skills section for [role] (null = the single agent): enveloped, permission-bounded, empty when none apply. */
    private fun skillSection(role: AgentRole?): String =
        com.ctodrs.saqrcode.agent.skills.SkillRuntime.section(runSkills.orEmpty(), role)

    /** Flags memories whose files disappeared (the Memory sheet calls this when it opens). */
    fun refreshMemoryStale(): Int = memory.refreshStale { File(projectDir, it).exists() }

    /** Smart router on and a configured vision model exists — an image turn can be routed instead of refused. */
    fun visionRouteAvailable(): Boolean {
        if (!settings.smartRouter) return false
        return SmartRouter.candidates(settings.routableProviders(), settings.prices(), settings.visionOverrides).any { it.second.vision }
    }

    private fun smartRoute(tier: ModelTier, label: String, needsVision: Boolean = false, contextTokens: Int = 0,
                           needsTools: Boolean = true, log: Boolean = true): RouteDecision {
        val primary = RouteTarget(settings.activeProvider, settings.config(settings.activeProvider).model)
        val req = RouteRequest(tier, primary, needsVision, needsTools, contextTokens,
            avoid = if (tier == ModelTier.INDEPENDENT_REVIEW) primary else null)
        val d = SmartRouter.route(req, settings.routableProviders(), settings.prices(), settings.visionOverrides, settings.fallbackModel)
        routeProviders[d.target.model] = d.target.provider
        if (log) logRoute(label, d)
        return d
    }

    /** Every decision goes to the audit log; a routed one (not the user's coder model) also gets one compact chat notice. */
    private fun logRoute(label: String, d: RouteDecision) {
        val line = SmartRouter.logLine(label, d)
        audit("router", Risk.SAFE, AuditEntry.Decision.AUTO, true, 0, line)
        if (d.routed && line != lastRouteNotice) {
            lastRouteNotice = line
            items += ChatItem.Notice("$label|${d.target.label}|${d.reason}", kind = ChatItem.Notice.Kind.ROUTER)
            markDirty()
        }
    }

    /** A client for [model] on the provider the router picked it from (the active provider otherwise). */
    private fun clientFor(model: String): LlmClient {
        val def = Providers.byId(routeProviders[model] ?: settings.activeProvider)
        val cfg = settings.config(def.id)
        return LlmClient(def, cfg.baseUrl, model, settings.apiKey(def.id))
    }

    private suspend fun loadCapBase() {
        if (settings.dailySpendCap <= 0.0 && settings.monthlySpendCap <= 0.0) { capDayBase = null; capMonthBase = null; return }
        val prices = settings.prices()
        // the ledger already holds this run's earlier calls; subtract them so they are not counted twice
        val run = if (turnUsage.isEmpty()) 0.0 else PriceTable.cost(turnUsage.toMap(), prices) ?: 0.0
        val (d, m) = withContext(Dispatchers.IO) {
            runCatching { PriceTable.cost(ledger.day(), prices) to PriceTable.cost(ledger.month(), prices) }.getOrDefault(null to null)
        }
        capDayBase = d?.minus(run)?.coerceAtLeast(0.0); capMonthBase = m?.minus(run)?.coerceAtLeast(0.0)
    }

    /** Today's / this month's priced spend including this run, checked against the caps. */
    private fun spendCapHit(): SpendCap.Hit {
        if (settings.dailySpendCap <= 0.0 && settings.monthlySpendCap <= 0.0) return SpendCap.Hit.NONE
        val run = if (turnUsage.isEmpty()) null else PriceTable.cost(turnUsage.toMap(), settings.prices())
        fun plus(a: Double?, b: Double?) = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
        val hit = SpendCap.check(plus(capDayBase, run), plus(capMonthBase, run), settings.dailySpendCap, settings.monthlySpendCap)
        if (hit != SpendCap.Hit.NONE) capHit = hit
        return hit
    }

    private fun capStop(summary: String): RunOutcome {
        items += ChatItem.Notice(SpendCap.noticeText(capHit), kind = ChatItem.Notice.Kind.BUDGET)
        markDirty()
        return RunOutcome(RunOutcome.Kind.BUDGET, summary, verification)
    }

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
    private suspend fun runProjectTests(timeoutSec: Long = 300): String {
        val runners = ProjectChecks.detectTests(projectDir)
        if (runners.isEmpty()) {
            return "No test runner detected (no package.json test script, no pytest layout, no *_test.py files). Offer to write tests for this project."
        }
        val r = runners.first()
        val res = linux.exec(projectDir, r.command, timeoutSec)
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
        /** v0.8: MCP servers contacted per turn (the first ones configured). */
        const val MAX_MCP_SERVERS = 4
        /** v0.17: rough system-prompt size (tokens) the router adds to the history when sizing a turn. */
        const val SYSTEM_PROMPT_TOKENS = 4_000

        /** v0.7: compaction threshold (chars), from [ContextCompactor.THRESHOLD_CHARS]. */
        const val COMPACT_THRESHOLD = ContextCompactor.THRESHOLD_CHARS
        /** Rough context cost of one attached image (~1.5k tokens) for the meter and trimming. */
        const val IMAGE_CHARS = 5_400
        const val SAVE_DEBOUNCE_MS = 1_000L
    }
}

object SystemPrompt {
    fun build(
        projectDir: File, touched: Collection<String>, linuxReady: Boolean, teamMode: Boolean = false, mcpToolNames: List<String> = emptyList(), memory: String = "",
        /** v0.15: web/MCP output arrives in an UNTRUSTED CONTENT envelope. */
        untrusted: Boolean = false,
        /** v0.15: HIGH-risk commands wait for the user's approval card. */
        riskyApproval: Boolean = false,
        /** v0.18: the installed-skills section ([com.ctodrs.saqrcode.agent.skills.SkillRuntime.section]); empty = none. */
        skills: String = "",
        /** v0.1.17: learning mode adds the line-by-line teaching section. */
        learning: Boolean = false,
        learningArabic: Boolean = true,
    ): String {
        val rules = ProjectRules.collect(projectDir, touched).takeIf { it.isNotBlank() }
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
- MEMORY: the "# MEMORY" section holds durable decisions, preferences and facts remembered from earlier sessions. Honor them without re-asking (e.g. don't suggest a stack the user already rejected). When the user states something durable — a decision, a preference, a project convention — call memory_save once with a concise key; when they correct or retract one, call memory_forget. Never store secrets there.
- Be concise. When done, give a short summary of what changed and how to run it. Do not paste whole files back to the user.

# Git
- Use the git_* tools (git_status, git_diff, git_log, git_commit, git_branch, git_pull, git_push) rather than running `git` in the terminal; they use the user's linked account and commit identity.
- Only commit or push when the user asks. git_push always shows the user an approval card; never force-push unless asked.

# Terminal
""")
            if (linuxReady) appendLine("- run_command executes in Alpine Linux (proot, no root on the phone). Install packages with `apk add` (e.g. `apk add nodejs npm python3 git`). Servers must bind 127.0.0.1. Commands are non-interactive; pass -y flags.")
            else appendLine("- The Linux environment is NOT installed yet; run_command uses Android's limited /system/bin/sh (toybox). If the task needs node/python/etc., tell the user to install Linux from the Terminal tab.")
            appendLine("- Dangerous commands (rm -rf /, curl|sh, eval, sudo, etc.) are blocked by policy.")
            if (riskyApproval) appendLine("- High-risk commands (rm -r, piping downloads or text into a shell, writes outside /workspace, git push/force or reset --hard via the shell, recursive chmod/chown, obfuscated inline scripts) wait for the user's approval card. Prefer safer equivalents (git_* tools, edit_file, delete_file) and never try to disguise a command.")
            appendLine()
            appendLine("# Web & quality tools (v0.7)")
            appendLine("- web_search / web_fetch reach the public internet only (private addresses are blocked). Use them for docs, API references and unfamiliar errors.")
            appendLine("- lint_project gives quick JS/Python/JSON/HTML checks; run_tests detects and runs the project's tests. Use them after meaningful changes and fix what they report.")
            if (untrusted) appendLine(Untrusted.RULE)
            if (teamMode) appendLine("- Team mode: a planner outlined the tasks for this request ([Planner] message); follow it and keep the plan pill updated. A reviewer agent checks your applied changes after each batch. Reviewer messages start with \"[Reviewer]\"; fix reported issues immediately without re-asking the user.")
            if (mcpToolNames.isNotEmpty()) {
                appendLine("- MCP tools (names starting with mcp_) call the user's configured external servers. Available now: ${mcpToolNames.take(12).joinToString(", ")}. Their arguments follow the schema in each tool spec; never call an MCP tool that is not listed.")
            }
            appendLine()
            if (memory.isNotBlank()) {
                appendLine("# MEMORY (remembered from earlier sessions — honor it, keep it fresh with memory_save/memory_forget)")
                appendLine(memory)
                appendLine()
            }
            if (skills.isNotBlank()) {
                appendLine(skills.trim())
                appendLine()
            }
            appendLine("# PROJECT MAP")
            appendLine(Tools.projectMap(projectDir))
            if (touched.isNotEmpty()) {
                appendLine()
                appendLine("# FILES CREATED / MODIFIED IN THIS SESSION")
                touched.forEach { appendLine("- $it") }
            }
            if (learning) {
                appendLine()
                appendLine(LearningMode.section(learningArabic))
            }
            if (rules != null) {
                appendLine()
                appendLine("# PROJECT-SPECIFIC RULES (AGENTS.md — honor them; when the user sets a durable project convention or asks to remember one, update the right AGENTS.md with edit_file)")
                appendLine(rules)
            }
        }
    }
}
