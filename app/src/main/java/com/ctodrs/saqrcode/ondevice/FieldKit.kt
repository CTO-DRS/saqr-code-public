package com.ctodrs.saqrcode.ondevice

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * v0.12.1 «ريش مكمّلة» · عدّة الميدان · Field kit.
 *
 * Closes the 0.12 "field numbers" item the practical way: every on-device build step
 * (rootfs, apt JDK, cmdline-tools, sdkmanager, aapt2, gradle) is timed automatically and
 * stored per run together with the device identity (model, SoC family, RAM, battery). The
 * records export as a README-ready Markdown table or a shareable CSV, so users running on
 * Snapdragon and MediaTek hardware can produce the field table in one tap.
 *
 * Pure Kotlin + org.json so [encode]/[decode]/[markdown]/[csv] are unit-testable on the JVM.
 */
object FieldKit {

    /** One measured step of one run. */
    data class Record(
        val run: String,       // "bootstrap" | "build-debug" | "build-release"
        val step: String,      // "rootfs" | "jdk17" | "cmdline-tools" | "android35" | "aapt2" | "gradle" | "gradle-download"
        val ms: Long,
        val device: String,    // Build.MODEL
        val soc: String,       // [socFamily]
        val ramMB: Int,        // device total RAM
        val batteryPct: Int,   // 0..100, or -1 when unknown
        val apkMB: Int,        // build result size (0 for setup steps)
        val at: Long,          // epoch millis when the step finished
    )

    /** SoC family from the raw identifiers Android reports (HARDWARE / SOC_MODEL / BOARD). */
    fun socFamily(hardware: String?, socModel: String?, board: String? = null): String {
        val s = listOfNotNull(hardware, socModel, board).joinToString(" ").lowercase(Locale.US)
        return when {
            s.isBlank() -> "Unknown"
            listOf("qcom", "qsd", "snapdragon", "sm4", "sm6", "sm7", "sm8", "sdm", "apq").any { s.contains(it) } -> "Snapdragon"
            listOf("mt6", "mt8", "mtk", "dimensity", "mediatek").any { s.contains(it) } -> "MediaTek"
            s.contains("exynos") || s.contains("universal") -> "Exynos"
            s.contains("tensor") || s.contains("gs10") || s.contains("gs20") || s.contains("gs25") -> "Tensor"
            s.contains("unisoc") || s.contains("umsm") || s.contains("spd") -> "Unisoc"
            s.contains("kirin") -> "Kirin"
            else -> "Other"
        }
    }

    /** "800ms" / "45s" / "12m 34s" / "1h 05m" — the field-table time format. */
    fun formatMs(ms: Long): String {
        if (ms < 0) return "?"
        val s = ms / 1000
        return when {
            ms < 1000 -> "${ms}ms"
            s < 60 -> "${s}s"
            s < 3600 -> "%dm %02ds".format(Locale.US, s / 60, s % 60)
            else -> "%dh %02dm".format(Locale.US, s / 3600, (s % 3600) / 60)
        }
    }

    /** Markdown table (README-ready, LTR headers so it pastes cleanly into English/Arabic docs). */
    fun markdown(records: List<Record>): String {
        if (records.isEmpty()) return ""
        val b = StringBuilder()
        b.append("| Device | SoC | RAM | Step | Time | APK | Run |\n")
        b.append("|---|---|---|---|---|---|---|\n")
        records.forEach { r ->
            b.append("| ${r.device} | ${r.soc} | ${ramLabel(r.ramMB)} | ${r.step} | ${formatMs(r.ms)} | ${apkLabel(r.apkMB)} | ${r.run} |\n")
        }
        return b.toString().trimEnd('\n')
    }

    /** RFC-ish CSV (quoted values, "" escapes), one row per record. */
    fun csv(records: List<Record>): String {
        val b = StringBuilder("device,soc,ram_mb,run,step,time_ms,apk_mb,battery_pct,at\n")
        records.forEach { r ->
            b.append(q(r.device)).append(',').append(q(r.soc)).append(',').append(r.ramMB).append(',')
                .append(q(r.run)).append(',').append(q(r.step)).append(',').append(r.ms).append(',')
                .append(r.apkMB).append(',').append(r.batteryPct).append(',').append(r.at).append('\n')
        }
        return b.toString()
    }

    fun encode(records: List<Record>): String = JSONArray().apply {
        records.forEach { r ->
            put(JSONObject()
                .put("run", r.run).put("step", r.step).put("ms", r.ms)
                .put("device", r.device).put("soc", r.soc).put("ramMB", r.ramMB)
                .put("batteryPct", r.batteryPct).put("apkMB", r.apkMB).put("at", r.at))
        }
    }.toString()

    fun decode(json: String?): List<Record> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val run = o.optString("run").trim()
            val step = o.optString("step").trim()
            if (run.isEmpty() || step.isEmpty()) return@mapNotNull null
            Record(run, step, o.optLong("ms").coerceAtLeast(0), o.optString("device").trim().ifBlank { "?" },
                o.optString("soc").trim().ifBlank { "Unknown" }, o.optInt("ramMB"), o.optInt("batteryPct", -1),
                o.optInt("apkMB"), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun q(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""

    private fun ramLabel(mb: Int): String {
        if (mb <= 0) return "?"
        val gb = mb / 1024f
        return if (gb >= 1f) String.format(Locale.US, "%.0f GB", gb) else "$mb MB"
    }

    private fun apkLabel(mb: Int): String = if (mb > 0) "$mb MB" else "—"
}
