package com.ctodrs.saqrcode.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ctodrs.saqrcode.agent.ActiveRuns
import java.io.File

/**
 * After a reboot: if a turn was running when the phone shut down, offer to resume it with a
 * notification. Nothing runs (and no network call is made) until the user taps it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        (context.applicationContext as? com.ctodrs.saqrcode.SaqrApp)?.let { if (it.settings.sentinelDigest) SentinelReceiver.schedule(context, true); if (com.ctodrs.saqrcode.watch.CiWatch.validRepo(it.settings.ciWatchRepo)) CiWatchReceiver.schedule(context, true) }
        val projects = ActiveRuns(File(context.filesDir, "agent/active.json")).read()
        if (projects.isNotEmpty()) Notifs.resumeOffer(context, projects, AgentRuntime.lang())
    }
}
