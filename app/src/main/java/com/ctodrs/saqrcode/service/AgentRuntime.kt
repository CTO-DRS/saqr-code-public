package com.ctodrs.saqrcode.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.ActiveRuns
import com.ctodrs.saqrcode.agent.AgentRuns
import com.ctodrs.saqrcode.agent.RunActivity
import com.ctodrs.saqrcode.agent.RunEffect
import com.ctodrs.saqrcode.agent.RunHooks
import com.ctodrs.saqrcode.agent.RunInfo
import com.ctodrs.saqrcode.agent.RunOutcome
import com.ctodrs.saqrcode.agent.RunWait
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Locale

/**
 * The process-wide owner of running agent turns. Agent sessions report to it ([RunHooks]); it runs
 * the [AgentRuns] state machine, starts/stops [AgentService] (which keeps the process alive with a
 * foreground notification and wake/Wi-Fi locks) and posts the attention / finished notifications.
 * The UI observes [state]; since sessions live in the process (not in an Activity), reopening the
 * app simply re-attaches to them.
 */
object AgentRuntime : RunHooks {
    private val machine = AgentRuns()
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<List<RunInfo>>(emptyList())
    /** Every project with a live turn (running or waiting for the user). */
    val state: StateFlow<List<RunInfo>> = _state

    /** Started activities; > 0 means the user is looking at the app. */
    @Volatile var visibleActivities = 0
    val appVisible get() = visibleActivities > 0

    private val app: SaqrApp get() = SaqrApp.instance
    val active: ActiveRuns by lazy { ActiveRuns(File(app.filesDir, "agent/active.json")) }

    fun lang(): String = AppCompatDelegate.getApplicationLocales()[0]?.language ?: Locale.getDefault().language

    fun runs(): List<RunInfo> = machine.all
    fun headline(): RunInfo? = machine.headline()
    val locksNeeded get() = machine.locksNeeded

    private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }

    override fun onTurnStart(project: String) = onMain { apply(machine.start(project, System.currentTimeMillis())) }
    override fun onActivity(project: String, step: Int, activity: RunActivity) = onMain { apply(machine.activity(project, step, activity)) }
    override fun onWaiting(project: String, wait: RunWait) = onMain { apply(machine.waiting(project, wait)) }
    override fun onResumed(project: String) = onMain { apply(machine.resumed(project)) }
    override fun onTurnEnd(project: String, outcome: RunOutcome) = onMain { apply(machine.finish(project, outcome)) }

    private fun apply(effects: List<RunEffect>) {
        _state.value = machine.all
        val ctx: Context = app
        for (e in effects) when (e) {
            RunEffect.StartService -> startService(ctx)
            RunEffect.StopService -> AgentService.instance?.syncWithRuntime()
            RunEffect.AcquireLocks, RunEffect.ReleaseLocks, RunEffect.UpdateOngoing -> AgentService.instance?.syncWithRuntime()
            RunEffect.RenewLocks -> AgentService.instance?.renewLocks()
            is RunEffect.PersistActive -> active.write(e.projects)
            is RunEffect.PostAttention -> Notifs.attention(ctx, e.project, e.wait, lang())
            is RunEffect.ClearAttention -> Notifs.cancelAttention(ctx, e.project)
            is RunEffect.PostFinished -> if (!appVisible) Notifs.finished(ctx, e.project, e.outcome, lang())
        }
    }

    private fun startService(ctx: Context) {
        if (!app.settings.keepInBackground) return
        runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, AgentService::class.java).setAction(AgentService.ACTION_RUN))
        }.onFailure {
            // Android 12+ refuses to start a foreground service from the background; the turn still
            // runs while the process lives, and the next turn started from the app gets the service.
        }
    }
}
