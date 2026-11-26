package com.ctodrs.saqrcode.agent.skills

import com.ctodrs.saqrcode.agent.MemoryBank
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.agent.memory.MemoryRecall
import com.ctodrs.saqrcode.data.SaqrPack
import com.ctodrs.saqrcode.data.SaqrPackCodec
import org.json.JSONArray
import org.json.JSONObject

/** Where an installed skill came from. */
enum class SkillSource(val id: String) {
    GALLERY("gallery"), FILE("file"), PASTE("paste"), LOCAL("local");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: PASTE }
}

/** An installed skill plus its local-only state (never exported). */
data class InstalledSkill(
    val skill: Skill,
    /** The pack it came from ("local" for skills written on this device). */
    val packId: String,
    val source: SkillSource,
    val enabled: Boolean = true,
    /** Local-only: never included in a bundle export, and a single export needs an explicit confirm. */
    val private: Boolean = false,
    /** The user's own 1–5 rating (0 = not rated). Local only. */
    val rating: Int = 0,
    /** How many runs attached this skill. Local only. */
    val uses: Int = 0,
    /** SHA-256 of the skill's canonical JSON, recorded at install. */
    val sha256: String = "",
    /** SHA-256 of the whole source pack (canonical), recorded at install. */
    val packSha256: String = "",
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** One step of an install: a new skill, an update (with the changelog entries it brings), or an identical re-install. */
sealed class SkillAction {
    abstract val skill: Skill
    data class New(override val skill: Skill) : SkillAction()
    data class Update(val old: InstalledSkill, override val skill: Skill, val changes: List<SkillChange>) : SkillAction()
    data class Same(val old: InstalledSkill) : SkillAction() { override val skill get() = old.skill }
}

sealed class InstallPlan {
    data class Refused(val reasons: List<SkillIssue>) : InstallPlan()
    data class Ready(
        val pack: SaqrPack,
        val packSha256: String,
        val actions: List<SkillAction>,
        val source: SkillSource,
    ) : InstallPlan() {
        val updates get() = actions.filterIsInstance<SkillAction.Update>()
        val news get() = actions.filterIsInstance<SkillAction.New>()
        /** Template/file packs keep the v0.14 path (home-screen template + commands). */
        val hasProjectPart get() = pack.templateRef.isNotBlank() || pack.files.isNotEmpty()
    }
}

/**
 * v0.18 · the skill manager: install / update / uninstall / enable / rate / private / export /
 * search — pure functions over the installed list, so the UI and the tests share one pipeline.
 *
 * Every way a skill can arrive (gallery, file, paste) goes through [plan]: the same .saqrpack
 * codec and validation, [SkillSafety] for each skill, the gallery's SHA-256 when it has one,
 * version comparison against what is installed, and an integrity check of the installed copy.
 */
object SkillManager {

    const val LOCAL_PACK = "local"
    /** Hard cap on installed skills (low-memory phones; the auto-attach index stays tiny). */
    const val MAX_INSTALLED = 60
    /** Raw pack text larger than this is refused before parsing. */
    const val MAX_RAW_CHARS = 600_000

    // ------------------------------------------------------------ store codec

    fun encode(list: List<InstalledSkill>): String = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject()
                .put("skill", SkillCodec.toJsonObject(s.skill))
                .put("packId", s.packId)
                .put("source", s.source.id)
                .put("enabled", s.enabled)
                .put("private", s.private)
                .put("rating", s.rating)
                .put("uses", s.uses)
                .put("sha256", s.sha256)
                .put("packSha256", s.packSha256)
                .put("installedAt", s.installedAt)
                .put("updatedAt", s.updatedAt))
        }
    }.toString()

    fun decode(json: String?): List<InstalledSkill> = runCatching {
        if (json.isNullOrBlank()) return@runCatching emptyList()
        val arr = JSONArray(json)
        val seen = HashSet<String>()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val sk = SkillCodec.fromJsonObject(o.optJSONObject("skill")) ?: return@mapNotNull null
            if (!seen.add(sk.id)) return@mapNotNull null
            InstalledSkill(
                skill = sk,
                packId = o.optString("packId").ifBlank { LOCAL_PACK },
                source = SkillSource.of(o.optString("source")),
                enabled = o.optBoolean("enabled", true),
                private = o.optBoolean("private", false),
                rating = o.optInt("rating", 0).coerceIn(0, 5),
                uses = o.optInt("uses", 0).coerceAtLeast(0),
                sha256 = o.optString("sha256"),
                packSha256 = o.optString("packSha256"),
                installedAt = o.optLong("installedAt", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ install / update

    /** Issues for every skill of [pack], prefixed with the skill id. */
    fun skillIssues(pack: SaqrPack): List<SkillIssue> = pack.skills.flatMap { s ->
        SkillSafety.check(s).map { it.copy(detail = "${s.id.take(40)} · ${it.detail}") }
    }

    /**
     * Plans installing [raw] (a .saqrpack document) against [installed]: refused with every
     * honest reason, or ready with one action per skill. [expectedSha256] is the gallery's
     * checksum for the pack — when present it must match the content exactly.
     */
    fun plan(raw: String?, source: SkillSource, installed: List<InstalledSkill>, expectedSha256: String? = null): InstallPlan {
        if (raw == null || raw.isBlank()) return InstallPlan.Refused(listOf(SkillIssue("json", "empty document")))
        if (raw.length > MAX_RAW_CHARS) return InstallPlan.Refused(listOf(SkillIssue("size", "pack is ${raw.length} chars, the cap is $MAX_RAW_CHARS")))
        val pack = SaqrPackCodec.fromJson(raw)
            ?: return InstallPlan.Refused(listOf(SkillIssue("json", "not a .saqrpack document (saqrpack 1, or 2 with a skills section)")))
        when (val key = SaqrPackCodec.validate(pack)) {
            null -> Unit
            "skill" -> return InstallPlan.Refused(skillIssues(pack).ifEmpty { listOf(SkillIssue("skill", "a skill failed validation")) })
            else -> return InstallPlan.Refused(listOf(SkillIssue(key, packReason(key))))
        }
        // A v0.14 pack without skills or a gallery checksum installs exactly as before, even if it is not canonical.
        val sha = CanonicalJson.packSha256(raw)
            ?: if (pack.skills.isEmpty() && expectedSha256.isNullOrBlank()) ""
            else return InstallPlan.Refused(listOf(SkillIssue("checksum", "pack contains non-integer numbers, so it has no canonical checksum")))
        if (!expectedSha256.isNullOrBlank() && !expectedSha256.equals(sha, ignoreCase = true)) {
            return InstallPlan.Refused(listOf(SkillIssue("checksum", "SHA-256 mismatch: the gallery lists ${expectedSha256.take(12)}…, the content is ${sha.take(12)}…")))
        }
        val byId = installed.associateBy { it.skill.id }
        val actions = ArrayList<SkillAction>()
        val issues = ArrayList<SkillIssue>()
        for (s in pack.skills) {
            val old = byId[s.id]
            if (old == null) { actions += SkillAction.New(s); continue }
            if (old.packId != pack.id) {
                issues += SkillIssue("conflict", "${s.id}: already installed from «${old.packId}» — uninstall it first")
                continue
            }
            if (old.sha256.isNotBlank() && SkillCodec.sha256(old.skill) != old.sha256) {
                issues += SkillIssue("tampered", "${s.id}: the installed copy no longer matches its recorded SHA-256 — uninstall and reinstall it")
                continue
            }
            val cmp = SemVer.compare(s.version, old.skill.version) ?: 1
            when {
                cmp > 0 -> actions += SkillAction.Update(old, s, changelogDiff(s, old.skill.version))
                cmp == 0 && SkillCodec.sha256(s) == old.sha256.ifBlank { SkillCodec.sha256(old.skill) } -> actions += SkillAction.Same(old)
                cmp == 0 -> issues += SkillIssue("same_version_changed", "${s.id}: content changed but the version is still ${s.version} — the author must bump the version")
                else -> issues += SkillIssue("downgrade", "${s.id}: ${s.version} is older than the installed ${old.skill.version}")
            }
        }
        if (issues.isNotEmpty()) return InstallPlan.Refused(issues)
        val newCount = actions.count { it is SkillAction.New }
        if (installed.size + newCount > MAX_INSTALLED) {
            return InstallPlan.Refused(listOf(SkillIssue("size", "at most $MAX_INSTALLED installed skills")))
        }
        return InstallPlan.Ready(pack, sha, actions, source)
    }

    /** Changelog entries newer than [installedVersion] (newest first) — what an update brings. */
    fun changelogDiff(s: Skill, installedVersion: String): List<SkillChange> =
        s.changelog.filter { SemVer.newer(it.version, installedVersion) && !SemVer.newer(it.version, s.version) }
            .sortedWith { a, b -> -(SemVer.compare(a.version, b.version) ?: 0) }

    /** Applies a ready plan: new skills enabled, updates keep the user's enabled/private/rating/uses. */
    fun apply(plan: InstallPlan.Ready, installed: List<InstalledSkill>, now: Long): List<InstalledSkill> {
        val out = installed.toMutableList()
        for (a in plan.actions) when (a) {
            is SkillAction.New -> out += InstalledSkill(a.skill, plan.pack.id, plan.source, sha256 = SkillCodec.sha256(a.skill),
                packSha256 = plan.packSha256, installedAt = now, updatedAt = now)
            is SkillAction.Update -> {
                val i = out.indexOfFirst { it.skill.id == a.skill.id }
                val next = a.old.copy(skill = a.skill, sha256 = SkillCodec.sha256(a.skill), packSha256 = plan.packSha256, updatedAt = now, source = plan.source)
                if (i >= 0) out[i] = next else out += next
            }
            is SkillAction.Same -> Unit
        }
        return out
    }

    /**
     * The v0.14 half of an install, unchanged for template/file packs (commands merged, pack
     * upserted for the home screen — stored without its skills); a skills-only pack only merges
     * its commands, if any. Returns (commands, packs).
     */
    fun applyPackPart(plan: InstallPlan.Ready, commands: List<SlashCommand>, packs: List<SaqrPack>, now: Long): Pair<List<SlashCommand>, List<SaqrPack>> =
        if (plan.hasProjectPart) SaqrPackCodec.install(commands, packs, plan.pack.copy(skills = emptyList()), now)
        else SaqrPackCodec.mergeCommands(commands, plan.pack) to packs

    private fun packReason(key: String) = when (key) {
        "empty" -> "the pack has no template, files or skills"
        "template" -> "unknown template"
        "path" -> "a file path leaves the project"
        "size" -> "a size limit was exceeded"
        "command" -> "a command is malformed"
        "duplicate" -> "a duplicate or builtin-shadowing command or skill id"
        "version" -> "the pack version is not semver"
        else -> key
    }

    // ------------------------------------------------------------ state changes

    fun uninstall(list: List<InstalledSkill>, id: String) = list.filter { it.skill.id != id }

    fun setEnabled(list: List<InstalledSkill>, id: String, on: Boolean) = list.map { if (it.skill.id == id) it.copy(enabled = on) else it }

    fun setPrivate(list: List<InstalledSkill>, id: String, on: Boolean) = list.map { if (it.skill.id == id) it.copy(private = on) else it }

    /** 1–5; 0 clears the rating. */
    fun rate(list: List<InstalledSkill>, id: String, stars: Int) = list.map { if (it.skill.id == id) it.copy(rating = stars.coerceIn(0, 5)) else it }

    fun recordUse(list: List<InstalledSkill>, ids: Collection<String>) =
        if (ids.isEmpty()) list else list.map { if (it.skill.id in ids) it.copy(uses = it.uses + 1) else it }

    /** Is a newer version of [s] in [candidates] (e.g. a gallery pack's skills)? Returns that skill. */
    fun newerIn(s: InstalledSkill, candidates: List<Skill>): Skill? =
        candidates.firstOrNull { it.id == s.skill.id && SemVer.newer(it.version, s.skill.version) }

    // ------------------------------------------------------------ local (private) skills

    private val SLUG_DROP = Regex("[^a-z0-9]+")

    /** A free id for a skill named [name]: a latin slug when possible, else my-skill-N. */
    fun freeId(name: String, taken: Set<String>): String {
        val base = SLUG_DROP.replace(name.lowercase(), "-").trim('-').take(32).ifBlank { "my-skill" }.let { if (it.length < 2) "my-skill" else it }
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    /**
     * Creates a private, local-only skill written on this device — through the same safety
     * checks as an imported one. Returns (new list, issues); the list is unchanged on issues.
     */
    fun createLocal(
        installed: List<InstalledSkill>, name: String, instructions: String, category: String, tags: List<String>, now: Long,
        description: String = "", role: String = "",
    ): Pair<List<InstalledSkill>, List<SkillIssue>> {
        val id = freeId(name, installed.map { it.skill.id }.toSet())
        val s = Skill(
            id = id, name = name.trim(), description = description.trim(), category = category.trim().lowercase().ifBlank { SkillCodec.DEFAULT_CATEGORY },
            tags = tags.map { it.trim() }.filter { it.isNotBlank() }, version = "1.0.0", instructions = instructions.trim(), role = role,
        )
        val issues = SkillSafety.check(s)
        if (issues.isNotEmpty()) return installed to issues
        if (installed.size >= MAX_INSTALLED) return installed to listOf(SkillIssue("size", "at most $MAX_INSTALLED installed skills"))
        return (installed + InstalledSkill(s, LOCAL_PACK, SkillSource.LOCAL, private = true, sha256 = SkillCodec.sha256(s),
            installedAt = now, updatedAt = now)) to emptyList()
    }

    // ------------------------------------------------------------ export

    data class Export(val json: String, val exported: Int, val skippedPrivate: Int)

    /**
     * One skill as a .saqrpack (saqrpack 2). A private skill exports only with
     * [includePrivate] — the UI asks the user first. Null when refused.
     */
    fun exportOne(s: InstalledSkill, includePrivate: Boolean = false): Export? {
        if (s.private && !includePrivate) return null
        val pack = SaqrPack(
            id = ("skill-" + s.skill.id).take(SaqrPackCodec.MAX_ID), name = s.skill.name.take(SaqrPackCodec.MAX_NAME),
            nameAr = s.skill.nameAr.take(SaqrPackCodec.MAX_NAME), description = s.skill.description.take(SaqrPackCodec.MAX_DESC),
            version = s.skill.version, author = s.skill.author, skills = listOf(s.skill),
        )
        return Export(SaqrPackCodec.toJson(pack), 1, 0)
    }

    /** A bundle of skills as one .saqrpack; private ones are left out unless [includePrivate]. Null when nothing to export. */
    fun exportBundle(list: List<InstalledSkill>, includePrivate: Boolean = false, id: String = "skills-bundle", name: String = "Saqr skills"): Export? {
        val chosen = list.filter { includePrivate || !it.private }.take(SaqrPackCodec.MAX_SKILLS)
        if (chosen.isEmpty()) return null
        val pack = SaqrPack(id = id, name = name, nameAr = "مهارات صقر", description = "Exported skills", skills = chosen.map { it.skill })
        return Export(SaqrPackCodec.toJson(pack), chosen.size, list.count { it.private && !includePrivate })
    }

    // ------------------------------------------------------------ search

    /** Categories present in [list] (known ones first, in their order). */
    fun categories(list: List<InstalledSkill>): List<String> {
        val present = list.map { it.skill.category }.toSet()
        return SkillCodec.CATEGORIES.filter { it in present } + present.filter { it !in SkillCodec.CATEGORIES }.sorted()
    }

    private fun norm(s: String) = MemoryBank.normalize(SlashCommands.normalize(s))

    /**
     * Arabic-aware search over name, nameAr, description, tags, category and author: a match is a
     * normalized substring, or every query term (stemmed) appearing among the skill's terms.
     * [category] null = all. Results: enabled first, then most used, then name.
     */
    fun search(list: List<InstalledSkill>, query: String, category: String? = null): List<InstalledSkill> {
        val inCat = if (category.isNullOrBlank()) list else list.filter { it.skill.category == category }
        val order = compareByDescending<InstalledSkill> { it.enabled }.thenByDescending { it.uses }.thenBy { it.skill.name.lowercase() }
        val q = query.trim()
        if (q.isEmpty()) return inCat.sortedWith(order)
        val nq = norm(q)
        val qTerms = MemoryRecall.tokens(q).toSet()
        return inCat.filter { s ->
            val k = s.skill
            val fields = listOf(k.name, k.nameAr, k.description, k.tags.joinToString(" "), k.category.replace('-', ' '), k.author, k.id.replace('-', ' '))
            val hay = norm(fields.joinToString(" "))
            if (nq.isNotEmpty() && nq in hay) return@filter true
            if (qTerms.isEmpty()) return@filter false
            val terms = MemoryRecall.tokens(fields.joinToString(" ")).toSet()
            qTerms.all { t -> terms.any { it == t || it.startsWith(t) || t.startsWith(it) && it.length >= 3 } }
        }.sortedWith(order)
    }
}
