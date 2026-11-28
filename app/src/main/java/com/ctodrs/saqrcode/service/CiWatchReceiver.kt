package com.ctodrs.saqrcode.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.watch.CiWatch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** v0.1.77: checks one repo's newest CI run about every 30 minutes; quiet unless something failed or got fixed. */
class CiWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SaqrApp ?: return
        val repo = app.settings.ciWatchRepo
        if (!CiWatch.validRepo(repo)) return
        schedule(context, true)
        val pending = goAsync()
        thread(name = "ciwatch") {
            try {
                val c = URL("https://api.github.com/repos/${repo.trim()}/actions/runs?per_page=1").openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 15000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                app.settings.githubToken?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                if (c.responseCode != 200) return@thread
                val run = CiWatch.parseLatest(c.inputStream.bufferedReader().readText())
                val ar = AgentRuntime.lang() == "ar"
                val (msg, state) = CiWatch.decide(repo.trim(), app.settings.ciWatchState, run, ar)
                app.settings.ciWatchState = state
                msg?.let { Notifs.digest(context, it.title, it.body, AgentRuntime.lang()) }
            } catch (_: Exception) {
            } finally { pending.finish() }
        }
    }

    companion object {
        private fun intent(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 7082, Intent(ctx, CiWatchReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun schedule(ctx: Context, enabled: Boolean) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            if (!enabled) { am.cancel(intent(ctx)); return }
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + 30 * 60_000L, intent(ctx)) }
        }
    }
}
