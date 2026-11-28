package com.ctodrs.saqrcode.device

/** v0.1.80 · Installed apps from `pm list packages`. Read-only. Pure parsing. */
object AppList {
    const val COMMAND_USER = "pm list packages -3"

    fun parse(out: String): List<String> =
        out.lineSequence().map { it.trim() }.filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").substringBefore('=').trim() }
            .filter { it.isNotEmpty() }.distinct().sorted().toList()

    /** Short text: the count and the first [max] names. */
    fun describe(pkgs: List<String>, max: Int = 40): String =
        if (pkgs.isEmpty()) "No apps found (the list may be hidden without Shizuku)."
        else pkgs.size.toString() + " apps\n" + pkgs.take(max).joinToString("\n") + (if (pkgs.size > max) "\n…" else "")
}
