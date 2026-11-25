package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import java.io.File
import java.util.Locale

/*
 * v0.6 background execution, the pure part: what the agent is doing (per project), what the
 * foreground service / wake locks / notifications should do about it, and the notification texts.
 * The Android side (AgentService, AgentRuntime) only applies the [RunEffect]s.
 */

/** What a running turn is doing right now (shown in the ongoing notification). */
data class RunActivity(val kind: Kind, val target: String = "") {
    enum class Kind { THINKING, READ, WRITE, EDIT, DELETE, RUN, SEARCH, LIST, GIT, PLAN, OTHER }

    companion object {
        val THINKING = RunActivity(Kind.THINKING)

        fun forTool(name: String, summary: String): RunActivity = when (name) {
            Tools.READ -> RunActivity(Kind.READ, summary.substringAfterLast('/'))
            Tools.WRITE -> RunActivity(Kind.WRITE, summary.substringAfterLast('/'))
            Tools.EDIT -> RunActivity(Kind.EDIT, summary.substringAfterLast('/'))
            Tools.DELETE -> RunActivity(Kind.DELETE, summary.substringAfterLast('/'))
            Tools.RUN -> RunActivity(Kind.RUN, summary)
            Tools.SEARCH -> RunActivity(Kind.SEARCH, summary)
            Tools.LIST -> RunActivity(Kind.LIST, summary)
            Tools.PLAN -> RunActivity(Kind.PLAN)
            in Tools.GIT -> RunActivity(Kind.GIT, name.removePrefix("git_"))
            else -> RunActivity(Kind.OTHER, name)
        }
    }
}

/** Why a turn is paused for the user. [options] are offered as notification buttons (single choice, ≤ 3). */
data class RunWait(
    val kind: Kind,
    val detail: String = "",
    val questionId: String? = null,
    val options: List<Pair<String, String>> = emptyList(),
) {
    /** v0.15: COMMAND = a HIGH-risk terminal command waits for the user's yes. */
    enum class Kind { ASK, PUSH, COMMIT, FILES, COMMAND }

    companion object {
        const val MAX_INLINE = 3

        /** Inline answers only for exactly one single-choice question with at most three options. */
        fun forAsk(req: AskRequest): RunWait {
            val q = req.questions.singleOrNull()
            val inline = q != null && q.kind == AskKind.SINGLE && q.options.size in 1..MAX_INLINE && !q.allowOther
            return RunWait(
                Kind.ASK, detail = (q?.prompt ?: req.title ?: req.questions.first().prompt).take(200),
                questionId = if (inline) q!!.id else null,
                options = if (inline) q!!.options.map { it.id to it.label } else emptyList(),
            )
        }
    }
}

/** How a turn ended. */
data class RunOutcome(
    val kind: Kind,
    val summary: String = "",
    /** v0.15 completion gate: the final verification status (null = the gate did not run). */
    val verification: CompletionGate.Status? = null,
    /** v0.16 Chief mode: tokens and priced spend per team role (empty outside Chief mode). */
    val roles: List<TeamCost.RoleCost> = emptyList(),
) {
    /** v0.15: BUDGET = stopped cleanly at the run's time or cost limit. */
    enum class Kind { DONE, ERROR, STOPPED, STEP_LIMIT, BUDGET }
}

/** Callbacks from [AgentSession] into the background runtime. All default to no-ops (tests, previews). */
interface RunHooks {
    fun onTurnStart(project: String) {}
    fun onActivity(project: String, step: Int, activity: RunActivity) {}
    fun onWaiting(project: String, wait: RunWait) {}
    fun onResumed(project: String) {}
    fun onTurnEnd(project: String, outcome: RunOutcome) {}

    companion object { val NONE = object : RunHooks {} }
}

/** One project's live run. */
data class RunInfo(
    val project: String,
    val startedAt: Long,
    val step: Int = 0,
    val activity: RunActivity = RunActivity.THINKING,
    val wait: RunWait? = null,
) {
    val waiting get() = wait != null
}

sealed class RunEffect {
    object StartService : RunEffect() { override fun toString() = "StartService" }
    object StopService : RunEffect() { override fun toString() = "StopService" }
    object AcquireLocks : RunEffect() { override fun toString() = "AcquireLocks" }
    object ReleaseLocks : RunEffect() { override fun toString() = "ReleaseLocks" }
    /** Re-arm the wake lock timeout (once per step). */
    object RenewLocks : RunEffect() { override fun toString() = "RenewLocks" }
    object UpdateOngoing : RunEffect() { override fun toString() = "UpdateOngoing" }
    data class PersistActive(val projects: Set<String>) : RunEffect()
    data class PostAttention(val project: String, val wait: RunWait) : RunEffect()
    data class ClearAttention(val project: String) : RunEffect()
    data class PostFinished(val project: String, val outcome: RunOutcome) : RunEffect()
}

/**
 * The service state machine. The service runs while any project has a turn (running or waiting
 * for the user); wake + Wi-Fi locks are held only while at least one turn is actually running.
 */
class AgentRuns {
    private val runs = LinkedHashMap<String, RunInfo>()

    val all: List<RunInfo> get() = runs.values.toList()
    fun get(project: String) = runs[project]
    val serviceNeeded get() = runs.isNotEmpty()
    val locksNeeded get() = runs.values.any { !it.waiting }

    private inline fun transition(block: () -> Unit): List<RunEffect> {
        val hadService = serviceNeeded
        val hadLocks = locksNeeded
        val before = runs.keys.toSet()
        block()
        val out = ArrayList<RunEffect>()
        if (!hadService && serviceNeeded) out += RunEffect.StartService
        if (!hadLocks && locksNeeded) out += RunEffect.AcquireLocks
        if (hadLocks && !locksNeeded) out += RunEffect.ReleaseLocks
        if (runs.keys != before) out += RunEffect.PersistActive(runs.keys.toSet())
        return out
    }

    fun start(project: String, now: Long): List<RunEffect> {
        val fresh = project !in runs
        val effects = transition {
            runs[project] = runs[project]?.copy(wait = null, activity = RunActivity.THINKING) ?: RunInfo(project, now)
        }
        return effects + listOfNotNull(if (fresh) null else RunEffect.ClearAttention(project)) + RunEffect.UpdateOngoing
    }

    fun activity(project: String, step: Int, a: RunActivity): List<RunEffect> {
        val r = runs[project] ?: return emptyList()
        val renew = step != r.step
        val effects = transition { runs[project] = r.copy(step = step, activity = a, wait = null) }
        return effects + listOfNotNull(if (renew && r.wait == null) RunEffect.RenewLocks else null,
            if (r.wait != null) RunEffect.ClearAttention(project) else null) + RunEffect.UpdateOngoing
    }

    fun waiting(project: String, w: RunWait): List<RunEffect> {
        val r = runs[project] ?: return emptyList()
        val effects = transition { runs[project] = r.copy(wait = w) }
        return effects + RunEffect.PostAttention(project, w) + RunEffect.UpdateOngoing
    }

    fun resumed(project: String): List<RunEffect> {
        val r = runs[project] ?: return emptyList()
        if (r.wait == null) return emptyList()
        val effects = transition { runs[project] = r.copy(wait = null) }
        return effects + RunEffect.ClearAttention(project) + RunEffect.UpdateOngoing
    }

    fun finish(project: String, outcome: RunOutcome): List<RunEffect> {
        if (project !in runs) return emptyList()
        val effects = transition { runs.remove(project) }
        val out = ArrayList(effects)
        out += RunEffect.ClearAttention(project)
        if (outcome.kind != RunOutcome.Kind.STOPPED) out += RunEffect.PostFinished(project, outcome)
        out += if (serviceNeeded) RunEffect.UpdateOngoing else RunEffect.StopService
        return out
    }

    /** Opens the service-wide notification: the run it shows (most recent active, else most recent). */
    fun headline(): RunInfo? = runs.values.lastOrNull { !it.waiting } ?: runs.values.lastOrNull()
}

/** Projects whose turn was running, kept on disk so a killed process or a reboot can offer to resume. */
class ActiveRuns(private val file: File) {
    fun read(): Set<String> = runCatching {
        if (!file.isFile) emptySet() else JSONArray(file.readText()).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
    }.getOrDefault(emptySet())

    fun write(projects: Set<String>) {
        runCatching {
            if (projects.isEmpty()) file.delete()
            else SessionStore.atomicWrite(file, JSONArray(projects.toList()).toString())
        }
    }

    fun add(p: String) = write(read() + p)
    fun remove(p: String) = write(read() - p)
}

/** Notification texts in Arabic or English (kept in code so they are unit-tested and work from the service). */
object NotifText {
    fun isArabic(lang: String) = lang.lowercase(Locale.ROOT).startsWith("ar")

    /** Western digits even in Arabic: matches the rest of the app (code, steps, tokens). */
    fun elapsed(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    fun activity(a: RunActivity, lang: String): String {
        val ar = isArabic(lang)
        val t = a.target.let { if (it.length > 48) it.take(47) + "…" else it }
        return when (a.kind) {
            RunActivity.Kind.THINKING -> if (ar) "يفكّر…" else "Thinking…"
            RunActivity.Kind.READ -> if (ar) "يقرأ $t…" else "Reading $t…"
            RunActivity.Kind.WRITE -> if (ar) "يكتب $t…" else "Writing $t…"
            RunActivity.Kind.EDIT -> if (ar) "يعدّل $t…" else "Editing $t…"
            RunActivity.Kind.DELETE -> if (ar) "يحذف $t…" else "Deleting $t…"
            RunActivity.Kind.RUN -> if (ar) "يشغّل: $t" else "Running: $t"
            RunActivity.Kind.SEARCH -> if (ar) "يبحث عن $t…" else "Searching $t…"
            RunActivity.Kind.LIST -> if (ar) "يستعرض الملفات…" else "Listing files…"
            RunActivity.Kind.GIT -> if (ar) "git $t…" else "git $t…"
            RunActivity.Kind.PLAN -> if (ar) "يحدّث الخطة…" else "Updating the plan…"
            RunActivity.Kind.OTHER -> if (ar) "ينفّذ $t…" else "Running $t…"
        }
    }

    fun step(n: Int, lang: String) = if (isArabic(lang)) "الخطوة $n" else "Step $n"

    /** Ongoing notification body: "Step 4 · Writing app.kt…" / "الخطوة 4 · يكتب app.kt…". */
    fun ongoing(r: RunInfo, lang: String): String {
        if (r.wait != null) return waitingShort(lang)
        val act = activity(r.activity, lang)
        return if (r.step > 0) step(r.step, lang) + " · " + act else act
    }

    /** Title of the ongoing notification; mentions the other projects when several run at once. */
    fun ongoingTitle(r: RunInfo, others: Int, lang: String): String = when {
        others <= 0 -> r.project
        isArabic(lang) -> "${r.project} (+$others)"
        else -> "${r.project} (+$others)"
    }

    fun waitingShort(lang: String) = if (isArabic(lang)) "بانتظار ردّك" else "Waiting for you"

    fun attentionTitle(lang: String) = if (isArabic(lang)) "صقر يحتاج ردّك" else "Saqr needs your answer"

    fun attentionBody(project: String, w: RunWait, lang: String): String {
        val ar = isArabic(lang)
        val what = when (w.kind) {
            RunWait.Kind.ASK -> w.detail
            RunWait.Kind.PUSH -> if (ar) "الموافقة على git push" else "Approve git push"
            RunWait.Kind.COMMIT -> if (ar) "الموافقة على git commit" else "Approve git commit"
            RunWait.Kind.FILES -> if (ar) "مراجعة تغييرات الملفات" else "Review file changes"
            RunWait.Kind.COMMAND -> if (ar) "الموافقة على أمر عالي الخطورة" else "Approve a high-risk command"
        }
        return "$project · $what"
    }

    fun finishedTitle(o: RunOutcome, lang: String): String {
        val ar = isArabic(lang)
        return when (o.kind) {
            RunOutcome.Kind.DONE -> if (ar) "انتهت المهمة" else "Task finished"
            RunOutcome.Kind.ERROR -> if (ar) "تعثّرت المهمة" else "Task failed"
            RunOutcome.Kind.STEP_LIMIT -> if (ar) "توقفت عند حد الخطوات" else "Stopped at the step limit"
            RunOutcome.Kind.STOPPED -> if (ar) "أُوقفت المهمة" else "Task stopped"
            RunOutcome.Kind.BUDGET -> if (ar) "توقفت عند حد الميزانية" else "Stopped at the run budget"
        }
    }

    fun finishedBody(project: String, o: RunOutcome, lang: String): String {
        val s = o.summary.replace(Regex("\\s+"), " ").trim()
        // v0.15: the completion gate's honest verdict leads the body when it ran
        val verdict = o.verification?.let { verificationLine(it, lang) + " " }.orEmpty()
        val body = (verdict + s).trim().ifEmpty { if (isArabic(lang)) "اضغط للعرض" else "Tap to view" }
        return "$project · " + (if (body.length > 160) body.take(159) + "…" else body)
    }

    /** v0.15: one short line for the completion gate's verdict. */
    fun verificationLine(v: CompletionGate.Status, lang: String): String {
        val ar = isArabic(lang)
        return when (v) {
            CompletionGate.Status.PASSED -> if (ar) "✓ تحقّق: الفحوص نجحت." else "✓ Verified: checks passed."
            CompletionGate.Status.FAILED -> if (ar) "✗ لم يُتحقق: الفحوص ما زالت تفشل." else "✗ Not verified: checks still fail."
            CompletionGate.Status.UNVERIFIED -> if (ar) "• لم يُتحقق: لا فحوص متاحة." else "• Not verified: no checks could run."
        }
    }

    fun resumeTitle(lang: String) = if (isArabic(lang)) "استئناف المهمة؟" else "Resume task?"

    fun resumeBody(projects: Collection<String>, lang: String): String =
        if (isArabic(lang)) "توقّف عمل الوكيل عند إيقاف الهاتف: " + projects.joinToString("، ")
        else "The agent was interrupted when the phone shut down: " + projects.joinToString(", ")

    fun stop(lang: String) = if (isArabic(lang)) "إيقاف" else "Stop"
    fun open(lang: String) = if (isArabic(lang)) "فتح" else "Open"
    fun resume(lang: String) = if (isArabic(lang)) "استئناف" else "Resume"
    fun channelRun(lang: String) = if (isArabic(lang)) "الوكيل يعمل" else "Agent running"
    fun channelAttention(lang: String) = if (isArabic(lang)) "يحتاج ردّك" else "Needs your answer"
    fun channelDone(lang: String) = if (isArabic(lang)) "انتهاء المهام" else "Task results"

    /** v0.13.4: the ongoing hands-free dictation notification. */
    fun dictationTitle(lang: String) = if (isArabic(lang)) "الإملاء بلا يدين يعمل" else "Hands-free dictation is on"
    fun dictationBody(lang: String) =
        if (isArabic(lang)) "قل «يا صقر» ثم أمرك — أوقفه من الإشعار متى شئت"
        else "Say «Ya Saqr» then your command — stop any time from here"

    /** Short plain summary of a reply for the "finished" notification (markdown and code removed). */
    fun summary(text: String): String = SpeechText.clean(text).replace(Regex("\\s+"), " ").trim().take(300)
}
