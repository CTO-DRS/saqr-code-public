package com.ctodrs.saqrcode.agent

import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * v0.1.22 Sentinel (morning digest): a once-a-day local notification that tells the user where
 * things stand: projects with uncommitted work and what the model calls cost. Everything is computed
 * on the phone. Pure functions here; scheduling and posting live in service/SentinelReceiver.
 */
object Sentinel {
    const val DIGEST_HOUR = 8

    /** The next local [hour]:00 strictly after [now]. */
    fun nextFire(now: ZonedDateTime, hour: Int = DIGEST_HOUR): ZonedDateTime {
        val today = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun nextFireMillis(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), hour: Int = DIGEST_HOUR): Long =
        nextFire(java.time.Instant.ofEpochMilli(nowMillis).atZone(zone), hour).toInstant().toEpochMilli()

    /** [dirty] = project name → number of changed files. Costs are USD, null when unpriced. */
    data class Input(
        val dirty: List<Pair<String, Int>> = emptyList(),
        val yesterdayCost: Double? = null,
        val monthCost: Double? = null,
        val monthBudget: Double? = null,
    )

    data class Message(val title: String, val body: String)

    private fun usd(v: Double) = "$" + String.format(java.util.Locale.US, "%.2f", v)

    /** Null when there is nothing worth interrupting the user for. */
    fun message(i: Input, arabic: Boolean): Message? {
        val dirty = i.dirty.filter { it.second > 0 }.sortedByDescending { it.second }
        val spent = (i.yesterdayCost ?: 0.0) > 0.0
        if (dirty.isEmpty() && !spent) return null
        val lines = ArrayList<String>()
        if (dirty.isNotEmpty()) {
            val shown = dirty.take(3).joinToString(if (arabic) "، " else ", ") { "${it.first} (${it.second})" }
            val more = dirty.size - 3
            lines += if (arabic) "تغييرات غير محفوظة في ${dirty.size} مشروع: $shown" + (if (more > 0) " و$more أخرى" else "")
            else "Uncommitted changes in ${dirty.size} project${if (dirty.size == 1) "" else "s"}: $shown" + (if (more > 0) " and $more more" else "")
        }
        if (spent) {
            val y = usd(i.yesterdayCost!!)
            val m = i.monthCost?.takeIf { it > 0.0 }?.let { usd(it) }
            val b = i.monthBudget?.takeIf { it > 0.0 && m != null }?.let { " / " + usd(it) }
            lines += if (arabic) "صرف أمس $y" + (m?.let { "، وهذا الشهر $it${b.orEmpty()}" } ?: "")
            else "Yesterday's model spend $y" + (m?.let { "; this month $it${b.orEmpty()}" } ?: "")
        }
        return Message(if (arabic) "ملخص الصباح من صقر" else "Saqr morning digest", lines.joinToString("\n"))
    }
}
