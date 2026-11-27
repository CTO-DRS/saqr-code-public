package com.ctodrs.saqrcode.agent

/**
 * v0.15 «النواة المستقلة» · Tiered command policy for `run_command`.
 *
 * Sits ON TOP of [SafetyPolicy]: the blocklist still refuses the catastrophic commands
 * outright. Everything it lets through is classified here as MEDIUM (runs as before) or HIGH
 * (needs an explicit yes from the user on an approval card — the agent can never approve its
 * own command). The checks run on the raw command AND on a de-obfuscated copy (quotes and
 * backslash escapes removed: `r''m`, `"r"m`, `r\m` all read as `rm`), so simple spelling
 * tricks do not slip past.
 *
 * Pure logic, unit-tested in V015FeaturesTest.
 */
object CommandPolicy {

    data class Assessment(val risk: Risk, val reasons: List<String>, val blocked: Boolean = false) {
        val needsApproval get() = !blocked && risk == Risk.HIGH
    }

    /** Paths a command may write to without counting as "outside the project". */
    private val SAFE_WRITE_PREFIXES = listOf("/workspace", "/tmp", "/dev/null", "/dev/stdout", "/dev/stderr", "/proc/self/fd")

    private val INTERPRETER_INLINE = Regex("""\b(python[0-9.]*\s+(-[a-zA-Z]*\s+)*-c|(ba|z|da|k)?sh\s+(-[a-zA-Z]*\s+)*-c|node\s+(-[a-zA-Z]*\s+)*(-e|--eval|-p|--print)|perl\s+(-[a-zA-Z]*\s+)*-e|ruby\s+(-[a-zA-Z]*\s+)*-e|php\s+(-[a-zA-Z]*\s+)*-r)\b""")

    private val OBFUSCATION = Regex(
        """(\\x[0-9a-fA-F]{2}|\\[0-7]{3}|\$'\\|base64|b64decode|atob\s*\(|fromCharCode|chr\s*\(|\bexec\s*\(|\beval\s*\(|__import__|compile\s*\(|os\.system|subprocess|child_process|codecs\.decode|bytes\.fromhex|\bxxd\b|rot13|\$\(|`)""",
    )

    private val rules: List<Pair<Regex, String>> = listOf(
        // network installers and remote scripts
        Regex("""\b(curl|wget|fetch|aria2c)\b[^;&]*\|\s*(sudo\s+)?(\S*/)?((ba|z|da|k|fi)?sh|python[0-9.]*|node|perl|ruby|php)\b""") to "downloads piped into an interpreter",
        Regex("""<\(\s*(curl|wget)\b""") to "running a downloaded script (process substitution)",
        Regex("""\$\(\s*(curl|wget)\b|`\s*(curl|wget)\b""") to "running downloaded content (command substitution)",
        Regex("""\|\s*(sudo\s+)?(\S*/)?(ba|z|da|k)?sh\b(\s|$)""") to "piping text into a shell",
        Regex("""\b(base64\s+(-d|--decode|-D)|xxd\s+-r)""") to "decoding hidden content",
        // v0.1.31: uploading local files to a server (the way an injected page would exfiltrate secrets)
        Regex("""\b(curl|wget)\b[^;&|]*\s(-d|-F|--data\S*|--form\S*|--json)[=\s]+\S*@""") to "uploading a local file to a server",
        Regex("""\bcurl\b[^;&|]*\s(-T|--upload-file)\b""") to "uploading a local file to a server",
        Regex("""\bwget\b[^;&|]*--post-(file|data)\b""") to "uploading local data to a server",
        Regex("""\b(nc|ncat|netcat)\b[^;&|]*<\s*\S""") to "sending a file over a raw socket",
        // deletion
        Regex("""\brm\s+(-[a-zA-Z]*\s+)*(-[a-zA-Z]*[rR][a-zA-Z]*|--recursive)\b""") to "recursive delete (rm -r)",
        Regex("""\bfind\b.*\s-(delete|exec\s+rm)\b""") to "bulk delete with find",
        // git via the shell (git_push has its own approval card)
        Regex("""\bgit\b(\s+-\S+(\s+\S+)?)*\s+push\b""") to "git push from the shell",
        Regex("""\bgit\b[^;&|]*\s(--force(-with-lease)?|-f)\b""") to "forced git operation",
        Regex("""\bgit\b[^;&|]*\breset\s+--hard\b""") to "git reset --hard",
        Regex("""\bgit\b[^;&|]*\bclean\s+-[a-zA-Z]*f""") to "git clean -f",
        // permissions
        Regex("""\bch(mod|own|grp)\s+(-[a-zA-Z]*R[a-zA-Z]*|--recursive)\b""") to "recursive permission change",
        // commands assembled from variables: a=r; b=m; ${'$'}a${'$'}b -rf x
        Regex("""(^|[;&|(]\s*)\$\{?[A-Za-z_][A-Za-z0-9_]*\}?\s*\$\{?[A-Za-z_]""") to "command assembled from variables",
        Regex("""(^|[;&|(]\s*)\$\{?[A-Za-z_][A-Za-z0-9_]*\}?\s+-""") to "command name hidden in a variable",
    )

    /** Removes quoting tricks so `r''m -r`, `"r"m` and `r\m` read as `rm`. */
    fun deobfuscate(cmd: String): String =
        cmd.replace(Regex("""\\(?=[A-Za-z/.-])"""), "")
            .replace("'", "")
            .replace("\"", "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun assess(command: String): Assessment {
        val safety = SafetyPolicy.check(command)
        if (!safety.allowed) return Assessment(Risk.HIGH, listOf(safety.reason), blocked = true)
        val raw = command.trim()
        val plain = deobfuscate(raw)
        // the blocklist again on the de-obfuscated copy: `r''m -rf /` must not pass as a different spelling
        if (plain != raw) {
            val again = SafetyPolicy.check(plain)
            if (!again.allowed) return Assessment(Risk.HIGH, listOf(again.reason), blocked = true)
        }
        val reasons = LinkedHashSet<String>()
        for (text in setOf(raw, plain)) {
            for ((re, why) in rules) if (re.containsMatchIn(text)) reasons += why
            if (INTERPRETER_INLINE.containsMatchIn(text) && OBFUSCATION.containsMatchIn(text)) reasons += "inline interpreter code with obfuscation"
            outsideWrite(text)?.let { reasons += "writes outside /workspace ($it)" }
        }
        return if (reasons.isEmpty()) Assessment(Risk.MEDIUM, emptyList()) else Assessment(Risk.HIGH, reasons.toList())
    }

    private val REDIRECT = Regex("""(?:^|[^0-9&<])>{1,2}\s*([~/$][^\s;|&)]*)""")
    private val TEE = Regex("""\btee\s+(-[a-zA-Z]+\s+)*([~/$][^\s;|&)]*)""")
    private val COPY_LIKE = Regex("""\b(cp|mv|install|ln|rsync|dd|touch|mkdir|unzip|tar)\b([^;&|]*)""")

    /** The first absolute target written outside the project, or null. */
    fun outsideWrite(cmd: String): String? {
        REDIRECT.findAll(cmd).forEach { m -> m.groupValues[1].takeIf { !safeTarget(it) }?.let { return it } }
        TEE.findAll(cmd).forEach { m -> m.groupValues[2].takeIf { !safeTarget(it) }?.let { return it } }
        COPY_LIKE.findAll(cmd).forEach { m ->
            val tool = m.groupValues[1]
            val args = m.groupValues[2].trim().split(' ').filter { it.isNotBlank() }
            val targets = when (tool) {
                "dd" -> args.filter { it.startsWith("of=") }.map { it.removePrefix("of=") }
                "tar", "unzip" -> args.withIndex().filter { (i, a) -> i > 0 && args[i - 1] in setOf("-C", "-d", "--directory") }.map { it.value }
                "touch", "mkdir" -> args.filterNot { it.startsWith("-") }
                else -> listOfNotNull(args.lastOrNull { !it.startsWith("-") })
            }
            targets.firstOrNull { (it.startsWith("/") || it.startsWith("~") || it.startsWith("$")) && !safeTarget(it) }?.let { return it }
        }
        return null
    }

    private fun safeTarget(path: String): Boolean {
        if (path.startsWith("~") || path.startsWith("$")) return false
        val p = path.replace(Regex("/+"), "/")
        if (p.split('/').any { it == ".." }) return false
        return SAFE_WRITE_PREFIXES.any { p == it || p.startsWith("$it/") }
    }

    /** The approval card text: the command, then why it needs a yes. */
    fun describe(command: String, a: Assessment): String = buildString {
        append("$ ").append(command.trim().take(1200))
        if (a.reasons.isNotEmpty()) append("\n\n⚠ ").append(a.reasons.joinToString(" · "))
    }
}
