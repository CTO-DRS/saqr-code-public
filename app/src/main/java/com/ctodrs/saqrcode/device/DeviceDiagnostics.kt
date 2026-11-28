package com.ctodrs.saqrcode.device

/** v0.1.72 · The read-only first diagnostics set (all SAFE, so no confirmation is ever asked). */
class DeviceDiagnostics(private val engine: CommandEngine) {
    data class Item(val label: String, val command: String)
    data class Line(val label: String, val value: String, val ok: Boolean)

    val items = listOf(
        Item("Android", "getprop ro.build.version.release"),
        Item("SDK", "getprop ro.build.version.sdk"),
        Item("Model", "getprop ro.product.model"),
        Item("Maker", "getprop ro.product.manufacturer"),
        Item("Battery", "dumpsys battery"),
        Item("Memory", "cat /proc/meminfo"),
        Item("Uptime", "cat /proc/uptime"),
    )

    suspend fun run(prefer: String? = null): List<Line> = items.map { i ->
        val r = engine.run(i.command, prefer, 10_000)
        Line(i.label, summarize(i.label, r), r.success)
    }

    companion object {
        /** Keep each line short: battery level and memory totals instead of the whole dump. */
        fun summarize(label: String, r: CommandResult): String {
            if (!r.success) return r.refused ?: r.error.ifBlank { "exit ${r.exitCode}" }
            val out = r.output
            return when (label) {
                "Battery" -> Regex("""level:\s*(\d+)""").find(out)?.let { "${it.groupValues[1]}%" } ?: "?"
                "Memory" -> {
                    val total = Regex("""MemTotal:\s*(\d+)""").find(out)?.groupValues?.get(1)?.toLongOrNull()
                    val avail = Regex("""MemAvailable:\s*(\d+)""").find(out)?.groupValues?.get(1)?.toLongOrNull()
                    if (total != null && avail != null) "${avail / 1024} MB free of ${total / 1024} MB" else "?"
                }
                "Uptime" -> out.substringBefore(' ').toDoubleOrNull()?.let { "%.1f h".format(java.util.Locale.US, it / 3600) } ?: "?"
                else -> out.lineSequence().firstOrNull().orEmpty().ifBlank { "?" }
            }
        }
    }
}
