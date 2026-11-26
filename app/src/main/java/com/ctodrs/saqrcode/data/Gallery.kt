package com.ctodrs.saqrcode.data

import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.agent.WebGuard
import com.ctodrs.saqrcode.agent.skills.CanonicalJson
import com.ctodrs.saqrcode.agent.skills.InstalledSkill
import com.ctodrs.saqrcode.agent.skills.SemVer
import com.ctodrs.saqrcode.git.GitHubApi
import com.ctodrs.saqrcode.git.GhException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * v0.14.3 «أجنحة مفتوحة» — Community gallery (معرض المجتمع): a public GitHub repository
 * holds a JSON index of Arabic .saqrpack bundles; the app browses it, searches it with an
 * Arabic-aware matcher, and installs an entry in one tap through the existing pack pipeline.
 * Contribution is a pull request — no Saqr-side server, ever.
 *
 * معرض المجتمع: مستودع GitHub عام يحمل فهرس JSON لحزم .saqrpack عربية؛ يتصفحها التطبيق
 * ويبحث فيها بمطابقة عربية ويثبّت مدخلاً بنقرة عبر خط أنابيب الحزم الموجود. والمساهمة
 * طلب دمج (PR) — بلا خادم منا إطلاقًا.
 */
data class GalleryEntry(
    val id: String,
    val name: String,
    val nameAr: String = "",
    val description: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    /** The raw .saqrpack JSON of the entry — parsed and validated before listing. */
    val packJson: String = "",
    /** The validated pack (ready to install). */
    val pack: SaqrPack? = null,
    // ---- v0.18 optional fields (an old index without them parses exactly as before)
    /** The entry's semantic version (falls back to the pack's own version). */
    val version: String = "",
    /** The gallery's SHA-256 of the canonical pack JSON; blank = the index gives none. */
    val sha256: String = "",
    /** Short permissions summary shown before install (tool names). */
    val permissions: List<String> = emptyList(),
    /** Gallery-provided aggregate rating 0–5 (display only; never affects install). */
    val rating: Double? = null,
    val ratingCount: Int = 0,
    /** Public https link to the entry's changelog. */
    val changelogUrl: String = "",
    /** How many skills the pack carries (counted from the pack itself). */
    val skillCount: Int = 0,
    val integrity: Integrity = Integrity.NONE,
) {
    /** NONE = no checksum given; OK = the content matches it; MISMATCH = refuse to install. */
    enum class Integrity { NONE, OK, MISMATCH }
}

data class GalleryIndex(
    val entries: List<GalleryEntry> = emptyList(),
    /** The index's own "updated" stamp (ms), for the «updated …» line. */
    val updated: Long = 0L,
)

object GalleryKit {

    const val VERSION = 1

    /** The community gallery repository (contribution = a pull request to it). */
    const val REPO_URL = "https://github.com/CTO-DRS/saqr-gallery"
    const val DEFAULT_RAW_URL = "https://raw.githubusercontent.com/CTO-DRS/saqr-gallery/main/gallery.json"

    // Hard caps keep a hostile index from ballooning memory or the UI list.
    const val MAX_ENTRIES = 60
    const val MAX_ID = 40
    const val MAX_NAME = 60
    const val MAX_AUTHOR = 40
    const val MAX_DESC = 160
    const val MAX_TAGS = 4
    const val MAX_TAG = 24
    // v0.18 optional entry fields
    const val MAX_PERMISSIONS = 10
    const val MAX_URL = 200

    // ------------------------------------------------------------ codec

    /**
     * Strict index parse: null on garbage or a wrong version. Entries are individually
     * validated — one broken pack is skipped (it never breaks the whole gallery), ids are
     * deduped keeping the first, and the list is capped at [MAX_ENTRIES].
     */
    fun parseIndex(json: String?): GalleryIndex? = runCatching {
        val o = JSONObject(json ?: return@runCatching null)
        if (o.optInt("saqrgallery", 0) != VERSION) return@runCatching null
        val arr = o.optJSONArray("entries") ?: return@runCatching null
        val out = ArrayList<GalleryEntry>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            if (out.size >= MAX_ENTRIES) break
            val e = arr.optJSONObject(i) ?: continue
            val packJson = e.optString("pack", "")
            val pack = SaqrPackCodec.fromJson(packJson) ?: continue
            if (SaqrPackCodec.validate(pack) != null) continue
            val id = e.optString("id").trim().take(MAX_ID)
            if (id.isBlank() || !seen.add(id)) continue
            val tags = ArrayList<String>()
            e.optJSONArray("tags")?.let { ta ->
                for (j in 0 until ta.length()) {
                    if (tags.size >= MAX_TAGS) break
                    val t = ta.optString(j).trim().take(MAX_TAG)
                    if (t.isNotBlank()) tags += t
                }
            }
            // v0.18 optional fields — each one ignored (not fatal) when malformed
            val sha = e.optString("sha256").trim().lowercase().takeIf { CanonicalJson.isSha256(it) } ?: ""
            val integrity = when {
                sha.isEmpty() -> GalleryEntry.Integrity.NONE
                CanonicalJson.packSha256(packJson) == sha -> GalleryEntry.Integrity.OK
                else -> GalleryEntry.Integrity.MISMATCH
            }
            val perms = ArrayList<String>()
            e.optJSONArray("permissions")?.let { pa ->
                for (j in 0 until pa.length()) {
                    if (perms.size >= MAX_PERMISSIONS) break
                    val t = pa.optString(j).trim().take(MAX_TAG)
                    if (t.isNotBlank()) perms += t
                }
            }
            if (perms.isEmpty()) pack.skills.flatMap { it.permissions }.distinct().take(MAX_PERMISSIONS).let { perms += it }
            val rating = if (e.has("rating")) e.optDouble("rating", Double.NaN).takeIf { !it.isNaN() && it in 0.0..5.0 } else null
            val changelog = e.optString("changelogUrl").trim().take(MAX_URL)
                .takeIf { it.startsWith("https://") && WebGuard.check(it).allowed } ?: ""
            val version = e.optString("version").trim().takeIf { SemVer.isValid(it) } ?: pack.version.takeIf { SemVer.isValid(it) } ?: ""
            out += GalleryEntry(
                id = id,
                name = e.optString("name").trim().take(MAX_NAME),
                nameAr = e.optString("nameAr").trim().take(MAX_NAME),
                description = e.optString("description").trim().take(MAX_DESC),
                author = e.optString("author").trim().take(MAX_AUTHOR),
                tags = tags,
                packJson = packJson,
                pack = pack,
                version = version,
                sha256 = sha,
                permissions = perms,
                rating = rating,
                ratingCount = e.optInt("ratingCount", 0).coerceAtLeast(0),
                changelogUrl = changelog,
                skillCount = pack.skills.size,
                integrity = integrity,
            )
        }
        GalleryIndex(entries = out, updated = o.optLong("updated", 0L))
    }.getOrNull()

    // ------------------------------------------------------------ search

    /** Arabic-aware filter over name/nameAr/description/author/tags; blank query returns all. */
    fun search(entries: List<GalleryEntry>, query: String): List<GalleryEntry> {
        val q = SlashCommands.normalize(query.trim())
        if (q.isEmpty()) return entries
        return entries.filter { e ->
            val hay = SlashCommands.normalize(
                listOf(e.name, e.nameAr, e.description, e.author, *e.tags.toTypedArray()).joinToString(" ")
            )
            q in hay
        }
    }

    /** Ids already installed — the gallery marks them «مثبّت» instead of offering a second copy. */
    fun installedIds(packs: List<SaqrPack>): Set<String> = packs.map { it.id }.toSet()

    // ------------------------------------------------------------ urls

    /** raw.githubusercontent URL for an index path — every owner/repo shape and segment encoded. */
    fun rawUrl(repo: String, branch: String = "main", path: String = "gallery.json"): String {
        val clean = repo.trim().trimEnd('/')
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("git@github.com:")
            .trimEnd('/')
        val slash = clean.indexOf('/')
        val owner = if (slash > 0) clean.substring(0, slash) else clean
        val name = if (slash > 0) clean.substring(slash + 1) else "saqr-gallery"
        return "https://raw.githubusercontent.com/$owner/$name/${seg(branch)}/${seg(path.trimStart('/'))}"
    }

    /** Minimal percent-encode for a URL path segment (UTF-8, keeps unreserved characters and '/'). */
    private fun seg(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c in 0x61..0x7A || c in 0x41..0x5A || c in 0x30..0x39 ||
                c == 0x2D || c == 0x2E || c == 0x5F || c == 0x7E || c == 0x2F
            ) sb.append(c.toChar()) else sb.append('%').append(String.format("%02X", c))
        }
        return sb.toString()
    }

    /** The install error key from the pack pipeline ("json"/"path"/"size"/…, v0.18 "checksum") — null on success. */
    fun installError(entry: GalleryEntry): String? {
        val p = entry.pack ?: return "json"
        if (entry.integrity == GalleryEntry.Integrity.MISMATCH) return "checksum"
        return SaqrPackCodec.validate(p)
    }

    /**
     * v0.18 «update available»: the entry is newer than what is installed — the pack's version
     * (when both sides have one) or any of its skills' versions against the installed skill.
     */
    fun updateAvailable(entry: GalleryEntry, packs: List<SaqrPack>, skills: List<InstalledSkill>): Boolean {
        val installedPack = packs.firstOrNull { it.id == entry.id }
        if (installedPack != null && entry.version.isNotBlank() && installedPack.version.isNotBlank() &&
            SemVer.newer(entry.version, installedPack.version)
        ) return true
        val pk = entry.pack ?: return false
        return pk.skills.any { s -> skills.any { it.skill.id == s.id && it.packId == pk.id && SemVer.newer(s.version, it.skill.version) } }
    }

    /** Ids installed as packs or as skill packs — the gallery marks them «مثبّت». */
    fun installedIds(packs: List<SaqrPack>, skills: List<InstalledSkill>): Set<String> =
        packs.map { it.id }.toSet() + skills.map { it.packId }.toSet()
}

/**
 * The network half — a single anonymous GET of the public raw index. No token, no GitHub
 * API: the gallery is plain static content, so nothing user-owned ever touches the call.
 */
class GalleryClient(
    private val http: OkHttpClient = GitHubApi.defaultClient,
) {

    /** Fetches the index JSON; throws [GhException] with a readable message on non-200. */
    fun fetch(url: String = GalleryKit.DEFAULT_RAW_URL): String {
        val b = Request.Builder().url(url)
            .header("User-Agent", "SaqrCode-Android")
            .get()
        http.newCall(b.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (r.code == 200 && body.isNotBlank()) return body
            throw GhException(r.code, "gallery: HTTP ${r.code}")
        }
    }
}
