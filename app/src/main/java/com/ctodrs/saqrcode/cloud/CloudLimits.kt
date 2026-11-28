package com.ctodrs.saqrcode.cloud

import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * v0.1.65 · Guard rails for the cloud workspace: a daily minutes cap, a per-command timeout,
 * and a plain cost estimate the user can always see. The estimate uses the provider's list
 * price for a 2 vCPU / 4 GiB box; the free credits cover it, but the number is shown anyway.
 */
data class CloudLimits(
    val dailyMinutes: Int = 30,
    val maxCommandSec: Int = 600,
    val idleKillSec: Int = 300,
) {
    fun commandTimeout(requested: Int?): Int = (requested ?: 300).coerceIn(5, maxCommandSec)

    companion object {
        const val USD_PER_HOUR = 0.1656
        fun costUsd(seconds: Long): Double = seconds / 3600.0 * USD_PER_HOUR
    }
}

/** Seconds used per calendar day, persisted as a tiny JSON file. */
class CloudLedger(private val file: File, private val today: () -> LocalDate = { LocalDate.now() }) {
    private fun read(): JSONObject = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())

    fun usedSecondsToday(): Long = read().optLong(today().toString(), 0L)

    fun add(seconds: Long) {
        val j = read()
        // keep only the last 14 days so the file never grows
        val keep = JSONObject()
        j.keys().forEach { k -> if (k >= today().minusDays(14).toString()) keep.put(k, j.getLong(k)) }
        keep.put(today().toString(), usedSecondsToday() + seconds.coerceAtLeast(0))
        file.parentFile?.mkdirs()
        file.writeText(keep.toString())
    }

    fun remainingSeconds(limits: CloudLimits): Long = (limits.dailyMinutes * 60L - usedSecondsToday()).coerceAtLeast(0)

    /** The meter line shown in Settings and in result cards. */
    fun meter(limits: CloudLimits): String {
        val used = usedSecondsToday()
        return "${used / 60}/${limits.dailyMinutes} min today · ~$" + String.format(java.util.Locale.US, "%.2f", CloudLimits.costUsd(used))
    }
}
