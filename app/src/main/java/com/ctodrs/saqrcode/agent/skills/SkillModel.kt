package com.ctodrs.saqrcode.agent.skills

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest

/**
 * v0.18 «مهارات صقر» · Agent Skills — the data model.
 *
 * A skill is a small, reviewable prompt fragment ("how to do this kind of task well") that an
 * agent can attach to a run: an Arabic code-review checklist, a migration recipe, a testing
 * routine. It travels inside the existing `.saqrpack` format as an optional `skills` section,
 * so the same validation, install, gallery and export paths carry it.
 *
 * A skill never grants anything: its declared [Skill.permissions] are a ceiling that is
 * intersected with the role's tool subset at runtime, HIGH-risk tools can never be declared,
 * and its text reaches the model inside an envelope that marks it as data which cannot
 * override the system rules, the command policy or the user's approvals.
 *
 * Pure Kotlin + org.json, unit-tested in V018FeaturesTest.
 */
data class SkillChange(val version: String, val notes: String)

data class Skill(
    val id: String,
    val name: String,
    val nameAr: String = "",
    val description: String = "",
    val category: String = SkillCodec.DEFAULT_CATEGORY,
    val tags: List<String> = emptyList(),
    /** Semantic version (MAJOR.MINOR.PATCH[-pre][+build]). */
    val version: String = "1.0.0",
    val author: String = "",
    /** The prompt fragment itself (size-capped, statically checked). */
    val instructions: String = "",
    /** Optional [com.ctodrs.saqrcode.agent.AgentRole] id this skill is meant for; blank = any role. */
    val role: String = "",
    /** Built-in tool names this skill may use — a ceiling, never a grant. */
    val permissions: List<String> = emptyList(),
    /** The highest tool risk the skill may declare: SAFE or MEDIUM (HIGH is never grantable). */
    val risk: String = "SAFE",
    val changelog: List<SkillChange> = emptyList(),
) {
    fun displayName(arabic: Boolean) = if (arabic && nameAr.isNotBlank()) nameAr else name
}

object SkillCodec {

    const val DEFAULT_CATEGORY = "general"

    // Parse-time hard limits (anti-balloon). Validation ([SkillSafety]) applies the real caps
    // and reports them honestly; the parser only refuses absurd documents.
    private const val PARSE_MAX_TEXT = 64_000
    private const val PARSE_MAX_LIST = 64

    /** The categories the UI labels; any other `[a-z0-9-]` category is accepted and shown as-is. */
    val CATEGORIES = listOf(
        "general", "code-review", "testing", "debugging", "refactoring", "docs",
        "security", "performance", "web", "mobile", "data", "devops", "learning",
    )

    fun toJsonObject(s: Skill): JSONObject = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("nameAr", s.nameAr)
        .put("description", s.description)
        .put("category", s.category)
        .put("tags", JSONArray(s.tags))
        .put("version", s.version)
        .put("author", s.author)
        .put("instructions", s.instructions)
        .put("role", s.role)
        .put("permissions", JSONArray(s.permissions))
        .put("risk", s.risk)
        .put("changelog", JSONArray().apply {
            s.changelog.forEach { put(JSONObject().put("version", it.version).put("notes", it.notes)) }
        })

    fun toJson(s: Skill): String = toJsonObject(s).toString()

    /** Lenient-on-optional, strict-on-identity parse: null without id/name/instructions; never throws. */
    fun fromJsonObject(o: JSONObject?): Skill? = runCatching {
        if (o == null) return@runCatching null
        val id = o.optString("id").trim()
        val name = o.optString("name").trim()
        val instructions = o.optString("instructions")
        if (id.isBlank() || name.isBlank() || instructions.isBlank()) return@runCatching null
        if (id.length > PARSE_MAX_TEXT || name.length > PARSE_MAX_TEXT || instructions.length > PARSE_MAX_TEXT) return@runCatching null
        Skill(
            id = id,
            name = name,
            nameAr = o.optString("nameAr").trim().take(PARSE_MAX_TEXT),
            description = o.optString("description").trim().take(PARSE_MAX_TEXT),
            category = o.optString("category").trim().lowercase().ifBlank { DEFAULT_CATEGORY }.take(PARSE_MAX_TEXT),
            tags = strings(o.optJSONArray("tags")),
            version = o.optString("version").trim().ifBlank { "1.0.0" },
            author = o.optString("author").trim().take(PARSE_MAX_TEXT),
            instructions = instructions,
            role = o.optString("role").trim().lowercase(),
            permissions = strings(o.optJSONArray("permissions")),
            risk = o.optString("risk").trim().uppercase().ifBlank { "SAFE" },
            changelog = o.optJSONArray("changelog")?.let { arr ->
                (0 until minOf(arr.length(), PARSE_MAX_LIST)).mapNotNull { i ->
                    val c = arr.optJSONObject(i) ?: return@mapNotNull null
                    val v = c.optString("version").trim()
                    if (v.isBlank()) null else SkillChange(v, c.optString("notes").trim().take(PARSE_MAX_TEXT))
                }
            } ?: emptyList(),
        )
    }.getOrNull()

    fun fromJson(json: String?): Skill? = runCatching { fromJsonObject(JSONObject(json ?: return@runCatching null)) }.getOrNull()

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until minOf(arr.length(), PARSE_MAX_LIST)) {
            val v = arr.optString(i).trim()
            if (v.isNotBlank()) out += v.take(PARSE_MAX_TEXT)
        }
        return out
    }

    /** SHA-256 of the skill's canonical JSON — recorded at install, re-checked before every update. */
    fun sha256(s: Skill): String = CanonicalJson.sha256(toJsonObject(s))!!
}

/**
 * Semantic versioning 2.0 precedence (major.minor.patch, pre-release lower than release,
 * pre-release identifiers compared numerically or lexically, build metadata ignored).
 */
object SemVer {
    private val RE = Regex("""^(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$""")

    data class Version(val major: Int, val minor: Int, val patch: Int, val pre: List<String>)

    fun parse(v: String?): Version? {
        val m = RE.matchEntire(v?.trim() ?: return null) ?: return null
        val pre = m.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
        // numeric pre-release identifiers must not have leading zeros
        if (pre.any { it.length > 1 && it.all(Char::isDigit) && it.startsWith('0') }) return null
        return Version(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), pre)
    }

    fun isValid(v: String?) = parse(v) != null

    /** <0, 0, >0 like compareTo; null when either side is not a valid version. */
    fun compare(a: String?, b: String?): Int? {
        val x = parse(a) ?: return null
        val y = parse(b) ?: return null
        if (x.major != y.major) return x.major.compareTo(y.major)
        if (x.minor != y.minor) return x.minor.compareTo(y.minor)
        if (x.patch != y.patch) return x.patch.compareTo(y.patch)
        if (x.pre.isEmpty() || y.pre.isEmpty()) return y.pre.size.coerceAtMost(1) - x.pre.size.coerceAtMost(1)
        for (i in 0 until minOf(x.pre.size, y.pre.size)) {
            val p = x.pre[i]; val q = y.pre[i]
            val pn = p.all(Char::isDigit); val qn = q.all(Char::isDigit)
            val c = when {
                pn && qn -> BigInteger(p).compareTo(BigInteger(q))
                pn -> -1
                qn -> 1
                else -> p.compareTo(q)
            }
            if (c != 0) return c
        }
        return x.pre.size.compareTo(y.pre.size)
    }

    fun newer(candidate: String?, installed: String?): Boolean = (compare(candidate, installed) ?: 0) > 0
}

/**
 * Canonical JSON for checksums — the same bytes the gallery validator (Python
 * `json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False)`) produces:
 * object keys sorted by code point, no whitespace, UTF-8, only `"`, `\`, and control
 * characters escaped (`\b \f \n \r \t`, others as lowercase `\u00xx`). Only integer numbers
 * are canonical; a non-integer number makes the document non-canonical (null).
 */
object CanonicalJson {

    /** Top-level keys that are install-local stamps, never part of the content hash. */
    val LOCAL_KEYS = setOf("installedAt", "sha256")

    fun encode(value: Any?): String? {
        val sb = StringBuilder()
        return if (write(sb, value)) sb.toString() else null
    }

    private fun write(sb: StringBuilder, v: Any?): Boolean {
        when (v) {
            null, JSONObject.NULL -> sb.append("null")
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte, is BigInteger -> sb.append(v.toString())
            is Number -> {
                // Double/Float/BigDecimal: canonical only when it is a whole number written as one
                return false
            }
            is String -> quote(sb, v)
            is JSONObject -> {
                val keys = ArrayList<String>()
                v.keys().forEach { keys += it }
                keys.sortWith { a, b -> compareCodePoints(a, b) }
                sb.append('{')
                keys.forEachIndexed { i, k ->
                    if (i > 0) sb.append(',')
                    quote(sb, k); sb.append(':')
                    if (!write(sb, v.opt(k))) return false
                }
                sb.append('}')
            }
            is JSONArray -> {
                sb.append('[')
                for (i in 0 until v.length()) {
                    if (i > 0) sb.append(',')
                    if (!write(sb, v.opt(i))) return false
                }
                sb.append(']')
            }
            else -> return false
        }
        return true
    }

    private fun compareCodePoints(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            val ca = a.codePointAt(i); val cb = b.codePointAt(j)
            if (ca != cb) return ca.compareTo(cb)
            i += Character.charCount(ca); j += Character.charCount(cb)
        }
        return (a.length - i).compareTo(b.length - j)
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }

    /** SHA-256 of a JSON value's canonical form; null when it is not canonical (non-integer numbers). */
    fun sha256(value: Any?): String? = encode(value)?.let { sha256Hex(it) }

    /** The content hash of a pack document: canonical JSON minus the install-local top-level keys. */
    fun packSha256(packJson: String?): String? = runCatching {
        val o = JSONObject(packJson ?: return@runCatching null)
        LOCAL_KEYS.forEach { o.remove(it) }
        sha256(o)
    }.getOrNull()

    fun isSha256(s: String?) = s != null && Regex("^[0-9a-f]{64}$").matches(s)
}
