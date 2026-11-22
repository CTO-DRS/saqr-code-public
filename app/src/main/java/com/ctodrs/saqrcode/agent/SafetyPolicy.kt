package com.ctodrs.saqrcode.agent

/**
 * Blocklist for commands the agent may run. Deliberately conservative: the agent runs
 * inside proot with the project bound at /workspace, but a phone is a personal device.
 */
object SafetyPolicy {
    data class Verdict(val allowed: Boolean, val reason: String = "")

    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""\brm\s+(-[a-zA-Z]*\s+)*(-[a-zA-Z]*[rR][a-zA-Z]*\s+)(-[a-zA-Z]*\s+)*(/|/\*|~|~/|${'$'}HOME|/root|/etc|/usr|/bin|/sdcard|/storage|/data)(\s|/?$|/\*)""") to "recursive delete outside the project",
        Regex("""\brm\s+.*--no-preserve-root""") to "rm --no-preserve-root",
        Regex("""\bmkfs(\.\w+)?\b""") to "formatting filesystems",
        Regex("""\bdd\s+.*\bof=/dev/""") to "writing raw devices",
        Regex(""">\s*/dev/(sd|block|mmc|nvme)""") to "writing raw devices",
        Regex(""":\s*\(\s*\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:""") to "fork bomb",
        Regex("""\b(shutdown|reboot|halt|poweroff)\b""") to "power control",
        Regex("""\bchmod\s+(-R\s+)?[0-7]*777\s+/(\s|$)""") to "chmod 777 on /",
        Regex("""\bchown\s+-R\s+\S+\s+/(\s|$)""") to "chown -R on /",
        Regex("""\b(curl|wget)\b[^|;&]*\|\s*(sudo\s+)?(ba|z|da)?sh\b""") to "piping a download into a shell",
        Regex("""\beval\b""") to "eval",
        Regex("""\$\{?IFS""") to "\$IFS obfuscation",
        Regex("""\b(su|sudo)\b""") to "privilege escalation",
        Regex("""169\.254\.169\.254|metadata\.google\.internal""") to "cloud metadata endpoint",
        Regex("""\bbase64\s+(-d|--decode)\b[^|]*\|\s*(ba)?sh\b""") to "decoding into a shell",
        Regex("""\b(nc|ncat|netcat)\b.*\s-e\s""") to "reverse shell",
        Regex("""/dev/tcp/""") to "raw /dev/tcp socket",
        Regex("""\bhistory\s+-c\b""") to "clearing history",
        Regex("""\bcrontab\b""") to "persistent scheduling",
    )

    fun check(command: String): Verdict {
        val c = command.trim()
        if (c.isEmpty()) return Verdict(false, "empty command")
        if (c.length > 4000) return Verdict(false, "command too long")
        for ((re, why) in rules) if (re.containsMatchIn(c)) return Verdict(false, why)
        return Verdict(true)
    }
}
