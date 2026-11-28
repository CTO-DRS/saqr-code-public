package com.ctodrs.saqrcode.device

/** v0.1.71 · ADB Control Center, phase 1. How dangerous a device command is (the owner's three classes). */
enum class AdbRisk { SAFE, SENSITIVE, DANGEROUS }

/** One finished command: what ran, on which engine, and how it ended. */
data class CommandResult(
    val command: String,
    val engine: String,
    val output: String,
    val error: String,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean = false,
    val refused: String? = null,
) {
    val success get() = refused == null && !timedOut && exitCode == 0
}

/** Classifies a device shell command before any engine runs it. Pure logic. */
object AdbRiskClassifier {
    data class Verdict(val risk: AdbRisk, val reasons: List<String>)

    private val DANGEROUS = listOf(
        Regex("""\brm\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)+""") to "deletes files recursively or by force",
        Regex("""\bpm\s+(uninstall|clear|disable|disable-user|hide|suspend)\b""") to "removes or disables an app or its data",
        Regex("""\bcmd\s+package\s+(uninstall|clear)\b""") to "removes an app or its data",
        Regex("""\b(reboot|poweroff|shutdown)\b""") to "restarts or powers off the device",
        Regex("""\bsvc\s+power\b""") to "changes power state",
        Regex("""\bsettings\s+put\s+(secure|global)\b""") to "edits system settings",
        Regex("""\b(setprop|resetprop)\b""") to "changes a system property",
        Regex("""\bmount\b.*\b(remount|-o\s+remount)\b|\bmount\s+-o\s+remount""") to "remounts a partition",
        Regex("""\b(dd|mkfs[.\w]*|fastboot|flash_image|wipe)\b""") to "writes raw storage",
        Regex("""\bformat\b|\bwipe\s+data\b|\bfactory""") to "erases data",
        Regex("""\bsu\b|\bmagisk\b""") to "escalates privileges",
        Regex("""\bchmod\s+(-R\s+)?[0-7]*7[0-7]{0,2}\s+/(system|vendor|data)\b|\bchown\b""") to "changes ownership or permissions on system paths",
        Regex(""">\s*/(system|vendor|proc|sys|dev/block)\b""") to "writes into a system path",
        Regex("""\bam\s+(force-stop|kill|kill-all)\b.*\bandroid\b""") to "stops a system component",
    )

    private val SENSITIVE = listOf(
        Regex("""\bpm\s+(grant|revoke|install|install-existing)\b""") to "changes app permissions or installs an app",
        Regex("""\bcmd\s+package\s+(install|compile)\b""") to "installs or recompiles an app",
        Regex("""\bam\s+(start|startservice|broadcast|force-stop|kill|instrument|stopservice)\b""") to "starts or stops app components",
        Regex("""\bsettings\s+put\b""") to "changes a setting",
        Regex("""\bappops\s+set\b""") to "changes an app operation",
        Regex("""\bcmd\s+(appops|deviceidle|netpolicy|jobscheduler)\b""") to "changes system policy",
        Regex("""\binput\s+(tap|swipe|text|keyevent|draganddrop)\b""") to "sends input to the screen",
        Regex("""\b(mv|cp|mkdir|touch|chmod|ln)\b""") to "changes files",
        Regex("""\bwm\s+(size|density)\b""") to "changes the display",
        Regex("""\bsvc\s+(wifi|data|bluetooth|nfc)\b""") to "toggles a radio",
        Regex("""\b(kill|killall|pkill)\b""") to "ends processes",
        Regex("""(^|[^>])>>?\s*\S""") to "writes output to a file",
    )

    fun classify(command: String): Verdict {
        val c = command.trim()
        if (c.isEmpty()) return Verdict(AdbRisk.SAFE, emptyList())
        val dangerous = DANGEROUS.filter { it.first.containsMatchIn(c) }.map { it.second }
        if (dangerous.isNotEmpty()) return Verdict(AdbRisk.DANGEROUS, dangerous.distinct())
        val sensitive = SENSITIVE.filter { it.first.containsMatchIn(c) }.map { it.second }
        if (sensitive.isNotEmpty()) return Verdict(AdbRisk.SENSITIVE, sensitive.distinct())
        // Several commands chained, or command substitution: cannot be judged line by line, so treat it as sensitive.
        if (Regex("""[;&|]|\$\(|`""").containsMatchIn(c)) return Verdict(AdbRisk.SENSITIVE, listOf("chains several commands"))
        return Verdict(AdbRisk.SAFE, emptyList())
    }
}
