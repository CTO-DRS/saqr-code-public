package com.ctodrs.saqrcode.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.Sentinel
import com.ctodrs.saqrcode.agent.UsageLedger
import com.ctodrs.saqrcode.git.GitService
import java.io.File
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * v0.1.22 Sentinel: fires once a day (inexact alarm, no special permission), posts the morning digest
 * when there is something to say, and books the next one. Re-armed on boot and whenever the switch changes.
 */
class SentinelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SaqrApp ?: return
        if (!app.settings.sentinelDigest) return
        schedule(context, true)
        val pending = goAsync()
        thread(name = "sentinel") {
            try {
                val git = GitService(app.filesDir, app.settings.accounts)
                val dirty = app.projects.list().mapNotNull { p ->
                    runCatching { git.status(p.dir, withCounts = false).takeIf { it.isRepo }?.files?.size }.getOrNull()?.let { p.name to it }
                }
                val ledger = UsageLedger(File(app.filesDir, "usage/usage.json"))
                val prices = app.settings.prices()
                val yesterday = PriceTable.cost(ledger.day(LocalDate.now().minusDays(1)), prices)
                val month = PriceTable.cost(ledger.month(), prices)
                val lang = AgentRuntime.lang()
                Sentinel.message(Sentinel.Input(dirty, yesterday, month, app.settings.monthlyBudget), lang == "ar")?.let {
                    Notifs.digest(context, it.title, it.body, lang)
                }
            } finally { pending.finish() }
        }
    }

    companion object {
        private fun intent(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 7081, Intent(ctx, SentinelReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Arms (or cancels) the next 08:00 digest. */
        fun schedule(ctx: Context, enabled: Boolean) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            if (!enabled) { am.cancel(intent(ctx)); return }
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC, Sentinel.nextFireMillis(System.currentTimeMillis()), intent(ctx)) }
        }
    }
}
