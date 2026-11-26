package com.ctodrs.saqrcode.agent.skills

import com.ctodrs.saqrcode.agent.AgentRole
import com.ctodrs.saqrcode.agent.AgentRoles
import com.ctodrs.saqrcode.agent.Risk
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.agent.ToolRegistry
import com.ctodrs.saqrcode.agent.Untrusted
import com.ctodrs.saqrcode.agent.WebGuard
import java.text.Normalizer

/** One honest reason a skill was refused: a stable [code] (UI label) and a short English [detail]. */
data class SkillIssue(val code: String, val detail: String) {
    override fun toString() = "$code: $detail"
}

/**
 * v0.18 · static safety checks for a skill, run before anything is installed or updated —
 * from the gallery, a file, a paste or the local editor alike (one pipeline).
 *
 * The checks are deliberately conservative and explainable: each refusal names the field and
 * the reason. They are a first line only — at runtime a skill still cannot widen a role's
 * tools, skip the command policy or an approval card, and its text arrives in a data envelope.
 *
 * The gallery's CI validator (saqr-gallery `scripts/validate_gallery.py`) mirrors these rules.
 */
object SkillSafety {

    const val MAX_ID = 40
    const val MAX_NAME = 40
    const val MAX_DESC = 200
    const val MAX_AUTHOR = 40
    const val MIN_INSTRUCTIONS = 20
    const val MAX_INSTRUCTIONS = 4_000
    const val MAX_TAGS = 6
    const val MAX_TAG = 24
    const val MAX_PERMISSIONS = 10
    const val MAX_CHANGELOG = 20
    const val MAX_CHANGE_NOTES = 300

    private val ID = Regex("^[a-z0-9][a-z0-9-]{1,39}$")
    private val CATEGORY = Regex("^[a-z0-9][a-z0-9-]{0,23}$")

    /** Risk ceilings a pack may declare. HIGH is never grantable by a pack. */
    val CEILINGS = setOf("SAFE", "MEDIUM")

    // ------------------------------------------------------------ hidden characters

    /**
     * Invisible / direction-changing code points refused anywhere in a skill: bidi embeddings,
     * overrides and isolates (the "Trojan Source" set), zero-width space, word joiner and
     * invisible operators, BOM, soft hyphen, Unicode tag characters, and control characters
     * other than tab/newline/CR. ZWNJ/ZWJ (U+200C/D) and LRM/RLM (U+200E/F) are allowed — they
     * occur in normal Arabic/Persian text and cannot reorder what the user reads.
     */
    fun isHidden(cp: Int): Boolean = when {
        cp == 0x09 || cp == 0x0A || cp == 0x0D -> false
        cp < 0x20 || cp in 0x7F..0x9F -> true
        cp == 0x00AD || cp == 0x061C || cp == 0x180E -> true
        cp == 0x200B -> true
        cp in 0x202A..0x202E -> true
        cp in 0x2060..0x2064 -> true
        cp in 0x2066..0x2069 -> true
        cp == 0xFEFF -> true
        cp in 0xFFF9..0xFFFB -> true
        cp in 0xE0000..0xE007F -> true
        else -> false
    }

    fun hiddenChars(text: String): List<Int> {
        val out = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isHidden(cp)) out += cp
            i += Character.charCount(cp)
        }
        return out
    }

    // ------------------------------------------------------------ text normalization

    /** Lowercase, NFKC (folds full-width / compatibility look-alikes), Arabic letter forms unified. */
    fun fold(text: String): String =
        SlashCommands.normalize(Normalizer.normalize(text, Normalizer.Form.NFKC)).replace('ة', 'ه')

    // ------------------------------------------------------------ injection markers

    private val INJECTION: List<Pair<Regex, String>> = listOf(
        Regex("""\b(ignore|disregard|forget|override|bypass)\b[^.\n]{0,20}\b(previous|prior|above|earlier|preceding|system|safety|your|developer)\b[^.\n]{0,20}\b(instructions?|rules?|prompts?|guidelines?|polic(y|ies)|restrictions?|guardrails?)\b""")
            to "asks to ignore or override instructions",
        Regex("""\b(ignore|disregard|forget)\s+(all|any|every)\s+(other\s+)?(instructions?|rules?|prompts?|guidelines?)\b""")
            to "asks to ignore or override instructions",
        Regex("""\b(reveal|print|show|repeat|output|leak|dump|disclose)\b[^.\n]{0,30}\b(system prompt|hidden instructions|your instructions|initial prompt)\b""")
            to "asks to reveal the system prompt",
        Regex("""<\|(im_start|im_end|system|endoftext)\|>|</?\s*system\s*>|\[/?inst]|<<sys>>""")
            to "contains chat-template role tokens",
        Regex("""\b(developer mode|jailbreak|do anything now)\b""")
            to "jailbreak phrasing",
        Regex("""\b(do not|don't|never|no need to)\b[^.\n]{0,20}\b(ask|wait|request)\b[^.\n]{0,25}\b(approval|confirmation|permission|consent)\b""")
            to "asks to skip the user's approval",
        Regex("""\b(auto[- ]?approve|without (asking|approval|confirmation|the user's (approval|consent)))\b""")
            to "asks to act without approval",
        Regex("""(تجاهل|تجاوز|انس|اهمل|الغ|اكسر)\S*\s+[^.\n]{0,20}((التعليمات|القواعد|الاوامر|التوجيهات|القيود)\s+(السابقه|اعلاه|الاصليه|كلها|جميعها)|(كل|جميع)\s+(التعليمات|القواعد|الاوامر|القيود)|(تعليمات|قواعد|قيود)\s+(النظام|الامان|الحمايه))""")
            to "asks to ignore or override instructions (Arabic)",
        Regex("""(بدون|دون|من غير)\s+(طلب\s+|انتظار\s+)?(موافقه|اذن|تاكيد|استئذان)""")
            to "asks to act without approval (Arabic)",
        Regex("""(اكشف|اعرض|اطبع|كرر)\s+[^.\n]{0,20}(موجه النظام|تعليمات النظام|التعليمات المخفيه)""")
            to "asks to reveal the system prompt (Arabic)",
    )

    /** Envelope markers a skill could try to spoof (ours and the untrusted-content ones). */
    private val SPOOF = listOf(SkillRuntime.BEGIN, SkillRuntime.END, Untrusted.BEGIN, Untrusted.END, "[installed skill", "[end installed skill", "[untrusted content")

    // ------------------------------------------------------------ secret requests

    private val NEGATION = Regex("""\b(never|don't|do not|avoid|not|no|without|nor)\b|(^|\s)(لا|ابدا|اياك|تجنب|ممنوع|دون|بدون)(\s|$)""")

    private val SECRET: List<Pair<Regex, String>> = listOf(
        Regex("""\b(send|upload|post|exfiltrate|reveal|print|output|leak|email|share|paste|include|echo|cat|display|show|transmit|forward|copy)\b[^.\n]{0,40}\b(api[ _-]?keys?|access[ _-]?tokens?|auth[ _-]?tokens?|bearer tokens?|github tokens?|secrets?|passwords?|passphrases?|credentials?|private[ _-]?keys?|ssh[ _-]?keys?|keystores?|seed phrases?)\b""")
            to "asks to output or send secrets",
        Regex("""\bask\b[^.\n]{0,25}\b(for|to (give|share|paste|send|type))\b[^.\n]{0,25}\b(api[ _-]?keys?|tokens?|passwords?|secrets?|credentials?|passphrases?)\b""")
            to "asks the user for secrets",
        Regex("""(~|\${'$'}home)/\.ssh|\bid_(rsa|ed25519|ecdsa)\b|keystore\.properties|/data/data/|\bshared_prefs\b|\bsecurestore\b|\.git-credentials|\.netrc\b""")
            to "points at secret storage",
        Regex("""(ارسل|اعرض|اطبع|اكشف|شارك|انشر|ارفع|الصق|اطلب|انسخ)\S*\s+[^.\n]{0,40}(مفتاح|المفتاح|مفاتيح|رمز الوصول|الرمز السري|التوكن|توكن|كلمه المرور|كلمات المرور|كلمه السر|الاسرار|بيانات الدخول)""")
            to "asks to output or send secrets (Arabic)",
    )

    // ------------------------------------------------------------ internal URLs

    private val URL = Regex("""\b([a-z][a-z0-9+.-]{1,15})://[^\s)\]>"'`]+""", RegexOption.IGNORE_CASE)
    private val PRIVATE_IP = Regex("""(?<![\d.])(10\.\d{1,3}\.\d{1,3}\.\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}|0\.0\.0\.0)(?![\d])""")
    private val INTERNAL_HOST = Regex("""\b(metadata\.google\.internal|metadata\.goog)\b|\b[a-z0-9-]+\.(internal|local|localhost):\d{1,5}\b""")

    /** A loopback dev server with an explicit unprivileged port (e.g. http://127.0.0.1:5000) is normal coding guidance. */
    private fun isLoopbackDevServer(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host != "127.0.0.1" && host != "localhost") return false
        return uri.port in 1024..65535 && uri.userInfo.isNullOrEmpty()
    }

    /** URLs in [text] that point outside the public web: (url, reason). */
    fun internalUrls(text: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (m in URL.findAll(text)) {
            val url = m.value.trimEnd('.', ',', ';', ':', '!', '?', '،')
            val scheme = m.groupValues[1].lowercase()
            if (scheme != "http" && scheme != "https") { out += url to "only http/https links are allowed"; continue }
            val v = WebGuard.check(url)
            if (!v.allowed && !isLoopbackDevServer(url)) out += url to v.reason
        }
        val withoutUrls = URL.replace(text, " ")
        PRIVATE_IP.findAll(withoutUrls).forEach { out += it.value to "private/internal address" }
        INTERNAL_HOST.findAll(withoutUrls.lowercase()).forEach { out += it.value to "internal host name" }
        return out
    }

    // ------------------------------------------------------------ the scans

    /** Injection / secret / URL / hidden-character findings for one text field. */
    fun scanText(field: String, text: String): List<SkillIssue> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<SkillIssue>()
        val hidden = hiddenChars(text)
        if (hidden.isNotEmpty()) {
            out += SkillIssue("hidden_unicode", "$field: invisible or direction-changing characters " +
                hidden.distinct().take(4).joinToString(" ") { "U+" + Integer.toHexString(it).uppercase().padStart(4, '0') })
        }
        val folded = fold(text)
        for (marker in SPOOF) if (folded.contains(fold(marker))) {
            out += SkillIssue("injection", "$field: imitates an envelope marker")
            break
        }
        for ((re, why) in INJECTION) {
            val m = re.find(folded) ?: continue
            out += SkillIssue("injection", "$field: $why («${m.value.take(48)}»)")
        }
        for ((re, why) in SECRET) {
            for (m in re.findAll(folded)) {
                val before = folded.substring(maxOf(0, m.range.first - 24), m.range.first)
                if (NEGATION.containsMatchIn(before)) continue
                out += SkillIssue("secret", "$field: $why («${m.value.take(48)}»)")
                break
            }
        }
        for ((url, why) in internalUrls(text)) out += SkillIssue("internal_url", "$field: ${url.take(60)} — $why")
        return out
    }

    /** Declared-permission problems: unknown tools, HIGH tools, tools above the declared ceiling, a HIGH/unknown ceiling. */
    fun permissionIssues(s: Skill): List<SkillIssue> {
        val out = ArrayList<SkillIssue>()
        val ceiling = s.risk.uppercase()
        when {
            ceiling == "HIGH" -> out += SkillIssue("permission_high", "risk: HIGH can never be granted to a pack")
            ceiling !in CEILINGS -> out += SkillIssue("risk", "risk: must be SAFE or MEDIUM, got «${s.risk.take(20)}»")
        }
        if (s.permissions.size > MAX_PERMISSIONS) out += SkillIssue("size", "permissions: at most $MAX_PERMISSIONS tools")
        val cap = if (ceiling == "MEDIUM") Risk.MEDIUM else Risk.SAFE
        for (p in s.permissions.distinct()) {
            val def = ToolRegistry.get(p)
            when {
                def == null -> out += SkillIssue("permission_unknown", "permissions: «${p.take(40)}» is not a Saqr tool")
                def.risk == Risk.HIGH -> out += SkillIssue("permission_high", "permissions: «$p» is HIGH risk and never grantable by a pack")
                def.risk.ordinal > cap.ordinal -> out += SkillIssue("permission_ceiling", "permissions: «$p» is ${def.risk} but the skill's ceiling is $ceiling")
            }
        }
        return out
    }

    /** Every reason to refuse [s]; empty = safe to install. */
    fun check(s: Skill): List<SkillIssue> {
        val out = ArrayList<SkillIssue>()
        if (!ID.matches(s.id)) out += SkillIssue("id", "id: 2–$MAX_ID chars of a-z, 0-9 and '-', starting with a letter or digit")
        if (s.name.isBlank() || s.name.length > MAX_NAME) out += SkillIssue("name", "name: 1–$MAX_NAME chars")
        if (s.nameAr.length > MAX_NAME) out += SkillIssue("name", "nameAr: at most $MAX_NAME chars")
        if (s.description.length > MAX_DESC) out += SkillIssue("size", "description: at most $MAX_DESC chars")
        if (s.author.length > MAX_AUTHOR) out += SkillIssue("size", "author: at most $MAX_AUTHOR chars")
        if (!CATEGORY.matches(s.category)) out += SkillIssue("category", "category: lowercase a-z, 0-9 and '-', at most 24 chars")
        if (s.tags.size > MAX_TAGS || s.tags.any { it.length > MAX_TAG }) out += SkillIssue("tags", "tags: at most $MAX_TAGS tags of $MAX_TAG chars")
        if (!SemVer.isValid(s.version)) out += SkillIssue("version", "version: «${s.version.take(20)}» is not semver (e.g. 1.2.0)")
        if (s.instructions.length > MAX_INSTRUCTIONS) out += SkillIssue("size", "instructions: ${s.instructions.length} chars, the cap is $MAX_INSTRUCTIONS")
        if (s.instructions.trim().length < MIN_INSTRUCTIONS) out += SkillIssue("size", "instructions: at least $MIN_INSTRUCTIONS chars")
        if (s.role.isNotBlank()) {
            val r = AgentRoles.byId(s.role)
            if (r == null || r.kind == AgentRole.Kind.PLANNING) out += SkillIssue("role", "role: «${s.role.take(20)}» is not a working team role")
        }
        out += permissionIssues(s)
        if (s.changelog.size > MAX_CHANGELOG) out += SkillIssue("changelog", "changelog: at most $MAX_CHANGELOG entries")
        for (c in s.changelog) {
            if (!SemVer.isValid(c.version)) { out += SkillIssue("changelog", "changelog: «${c.version.take(20)}» is not semver"); break }
            if (SemVer.newer(c.version, s.version)) { out += SkillIssue("changelog", "changelog: entry ${c.version} is newer than the skill's version ${s.version}"); break }
            if (c.notes.length > MAX_CHANGE_NOTES) { out += SkillIssue("changelog", "changelog: notes for ${c.version} exceed $MAX_CHANGE_NOTES chars"); break }
        }
        // content scans over every text the model or the user will read
        out += scanText("instructions", s.instructions)
        out += scanText("name", s.name)
        out += scanText("nameAr", s.nameAr)
        out += scanText("description", s.description)
        out += scanText("author", s.author)
        s.tags.forEach { out += scanText("tags", it) }
        s.changelog.forEach { out += scanText("changelog ${c(it)}", it.notes) }
        return out.distinct()
    }

    private fun c(ch: SkillChange) = ch.version.take(20)
}
