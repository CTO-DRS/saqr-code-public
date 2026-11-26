package com.ctodrs.saqrcode.data

import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.agent.skills.SemVer
import com.ctodrs.saqrcode.agent.skills.Skill
import com.ctodrs.saqrcode.agent.skills.SkillCodec
import com.ctodrs.saqrcode.agent.skills.SkillSafety
import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.14 «أجنحة مفتوحة» — Skill packs (.saqrpack): a JSON bundle that installs, in one tap,
 * a project template, ready Arabic slash commands, AGENTS.md rules and preferred tools.
 *
 * حزم المهارات (.saqrpack): حزمة JSON واحدة تنصب بنقرة قالب مشروع وأوامر عربية جاهزة
 * وقواعد AGENTS.md وأدوات مفضلة — القالب يظهر في الشاشة الرئيسية، والقواعد تُحقن في
 * AGENTS.md ليعرضها موجّه النظام تلقائيًا، والأوامر تُدمج مع أوامر الشرطة المائلة.
 */
data class PackCommand(val name: String, val template: String)

data class SaqrPack(
    val id: String,
    val name: String,
    val nameAr: String = "",
    val description: String = "",
    /** A [Template] enum name; blank when [files] carries everything. */
    val templateRef: String = "",
    /** Extra/override files layered on the template (or standalone when no template). */
    val files: Map<String, String> = emptyMap(),
    val commands: List<PackCommand> = emptyList(),
    /** AGENTS.md rules snippet injected into every project created from this pack. */
    val rules: String = "",
    /** Preferred tools, rendered as one AGENTS.md line. */
    val tools: List<String> = emptyList(),
    val installedAt: Long = 0L,
    /** v0.18 (optional): the pack's semantic version — compared against the gallery for «update available». */
    val version: String = "",
    /** v0.18 (optional): the pack's author. */
    val author: String = "",
    /** v0.18 (optional): agent skills carried by the pack (schema `saqrpack: 2`). */
    val skills: List<Skill> = emptyList(),
)

object SaqrPackCodec {

    const val VERSION = 1
    /** v0.18: a pack with a `skills` section. Additive — a pack without skills is still written as v1. */
    const val VERSION_SKILLS = 2
    const val MAX_SKILLS = 12
    const val MAX_VERSION = 32
    const val MAX_AUTHOR = 40

    // Hard limits keep a hostile pack from ballooning the editor, the prompt or the disk.
    const val MAX_FILES = 40
    const val MAX_FILE_CHARS = 128_000
    const val MAX_TOTAL_CHARS = 512_000
    const val MAX_COMMANDS = 12
    const val MAX_COMMAND_TEMPLATE = 800
    const val MAX_RULES = 2000
    const val MAX_TOOLS = 8
    const val MAX_TOOL_LEN = 40
    const val MAX_NAME = 40
    const val MAX_ID = 40
    const val MAX_DESC = 140
    const val MAX_PATH = 120

    private val CMD_NAME = Regex("""^[\p{L}\p{N}_-]{1,32}$""")
    private val BUILTIN_KEYS = SlashCommands.builtins
        .flatMap { listOf(it.name) + it.aliases }
        .map { SlashCommands.normalize(it) }
        .toSet()

    // ------------------------------------------------------------ codec

    fun toJson(p: SaqrPack): String = JSONObject()
        .put("saqrpack", if (p.skills.isEmpty()) VERSION else VERSION_SKILLS)
        .put("id", p.id)
        .put("name", p.name)
        .put("nameAr", p.nameAr)
        .put("description", p.description)
        .put("template", p.templateRef)
        .put("files", JSONObject(p.files))
        .put("commands", JSONArray().apply {
            p.commands.forEach { put(JSONObject().put("name", it.name).put("template", it.template)) }
        })
        .put("rules", p.rules)
        .put("tools", JSONArray(p.tools))
        .put("installedAt", p.installedAt)
        .apply {
            // v0.18 optional fields: only written when set, so a v0.14 pack serializes exactly as before
            if (p.version.isNotBlank()) put("version", p.version)
            if (p.author.isNotBlank()) put("author", p.author)
            if (p.skills.isNotEmpty()) put("skills", JSONArray().apply { p.skills.forEach { put(SkillCodec.toJsonObject(it)) } })
        }
        .toString()

    /** Strict parse: null on garbage, wrong version or missing id/name — never throws. */
    fun fromJson(json: String?): SaqrPack? = runCatching {
        val o = JSONObject(json ?: return@runCatching null)
        val v = o.optInt("saqrpack", 0)
        // v1 = the v0.14 pack (any "skills" key is ignored); v2 = a pack WITH a skills section
        val skills = ArrayList<Skill>()
        when (v) {
            VERSION -> Unit
            VERSION_SKILLS -> {
                val arr = o.optJSONArray("skills") ?: return@runCatching null
                for (i in 0 until minOf(arr.length(), MAX_SKILLS * 2)) {
                    // a malformed skill makes the whole pack malformed — never install half of it silently
                    skills += SkillCodec.fromJsonObject(arr.optJSONObject(i)) ?: return@runCatching null
                }
                if (skills.isEmpty()) return@runCatching null
            }
            else -> return@runCatching null
        }
        val id = o.optString("id").trim().take(MAX_ID)
        val name = o.optString("name").trim().take(MAX_NAME)
        if (id.isBlank() || name.isBlank()) return@runCatching null
        val files = LinkedHashMap<String, String>()
        o.optJSONObject("files")?.let { fo -> fo.keys().forEach { k -> files[k] = fo.optString(k) } }
        val cmds = ArrayList<PackCommand>()
        o.optJSONArray("commands")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                cmds += PackCommand(c.optString("name").trim().removePrefix("/"), c.optString("template"))
            }
        }
        val tools = ArrayList<String>()
        o.optJSONArray("tools")?.let { arr -> for (i in 0 until arr.length()) tools += arr.optString(i).trim() }
        SaqrPack(
            id = id,
            name = name,
            nameAr = o.optString("nameAr").trim().take(MAX_NAME),
            description = o.optString("description").trim().take(MAX_DESC),
            templateRef = o.optString("template").trim().take(24),
            files = files,
            commands = cmds,
            rules = o.optString("rules").take(MAX_RULES),
            tools = tools.filter { it.isNotBlank() },
            installedAt = o.optLong("installedAt", 0L),
            version = o.optString("version").trim().take(MAX_VERSION),
            author = o.optString("author").trim().take(MAX_AUTHOR),
            skills = skills,
        )
    }.getOrNull()

    fun encodePacks(packs: List<SaqrPack>): String = JSONArray().apply {
        packs.forEach { put(JSONObject(toJson(it))) }
    }.toString()

    fun decodePacks(json: String?): List<SaqrPack> = runCatching {
        val arr = JSONArray(json ?: return@runCatching emptyList())
        (0 until arr.length()).mapNotNull { i -> fromJson(arr.optString(i)) }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ validation

    /**
     * Returns an error key ("path"/"size"/"template"/"command"/"duplicate"/"empty", v0.18:
     * "version"/"skill") or null when valid. A skills-only pack (no template, no files) is
     * valid since v0.18; every skill must pass [SkillSafety.check].
     */
    fun validate(p: SaqrPack): String? {
        if (p.templateRef.isBlank() && p.files.isEmpty() && p.skills.isEmpty()) return "empty"
        if (p.templateRef.isNotBlank() && Template.entries.none { it.name == p.templateRef }) return "template"
        var total = 0
        if (p.files.size > MAX_FILES) return "size"
        for ((path, content) in p.files) {
            if (!safePath(path)) return "path"
            total += content.length
            if (content.length > MAX_FILE_CHARS) return "size"
        }
        if (total > MAX_TOTAL_CHARS) return "size"
        if (p.commands.size > MAX_COMMANDS) return "size"
        val seen = HashSet<String>()
        for (c in p.commands) {
            if (!CMD_NAME.matches(c.name) || c.template.isBlank() || c.template.length > MAX_COMMAND_TEMPLATE) return "command"
            if (SlashCommands.normalize(c.name) in BUILTIN_KEYS) return "duplicate"
            if (!seen.add(SlashCommands.normalize(c.name))) return "duplicate"
        }
        if (p.rules.length > MAX_RULES) return "size"
        if (p.tools.size > MAX_TOOLS || p.tools.any { it.length > MAX_TOOL_LEN }) return "size"
        // v0.18: optional version + skills
        if (p.version.isNotBlank() && !SemVer.isValid(p.version)) return "version"
        if (p.skills.size > MAX_SKILLS) return "size"
        val skillIds = HashSet<String>()
        for (s in p.skills) if (!skillIds.add(s.id)) return "duplicate"
        if (p.skills.any { SkillSafety.check(it).isNotEmpty() }) return "skill"
        return null
    }

    /** A pack path must stay inside the project: no absolute, no parent jumps, no backslashes. */
    private fun safePath(path: String): Boolean = when {
        path.isBlank() || path.length > MAX_PATH -> false
        path.startsWith('/') || path.contains('\\') -> false
        path.split('/').any { it == ".." } -> false
        else -> true
    }

    // ------------------------------------------------------------ project files

    /**
     * The files a project created from this pack starts with: the template base (when
     * [SaqrPack.templateRef] is set) overlaid by [SaqrPack.files], then rules and preferred
     * tools merged into AGENTS.md so the agent reads them from the system prompt.
     */
    fun packFiles(p: SaqrPack, projectName: String): Map<String, String> {
        val base = if (p.templateRef.isNotBlank() && Template.entries.any { it.name == p.templateRef }) {
            Template.valueOf(p.templateRef).files(projectName)
        } else {
            emptyMap()
        }
        val out = (base + p.files).toMutableMap()
        val agents = StringBuilder((out["AGENTS.md"] ?: Template.AGENTS_MD).trimEnd())
        if (p.rules.isNotBlank()) {
            agents.append("\n\n# Pack rules · ").append(p.name).append('\n').append(p.rules.trim())
        }
        if (p.tools.isNotEmpty()) {
            agents.append("\n\n- Preferred tools: ").append(p.tools.joinToString(", "))
        }
        out["AGENTS.md"] = agents.toString() + "\n"
        return out
    }

    // ------------------------------------------------------------ commands

    /**
     * Merge the pack's commands into the user's custom commands: builtins are untouchable,
     * a pack command replaces an existing custom command with the same Arabic-normalized
     * name (upsert), and duplicates inside the pack keep the first.
     */
    fun mergeCommands(existing: List<SlashCommand>, p: SaqrPack): List<SlashCommand> {
        var out = existing
        val seen = HashSet<String>()
        for (c in p.commands) {
            val key = SlashCommands.normalize(c.name)
            if (key.isEmpty() || !CMD_NAME.matches(c.name)) continue
            if (key in BUILTIN_KEYS || !seen.add(key)) continue
            out = out.filter { SlashCommands.normalize(it.name) != key } + SlashCommand(c.name, c.template)
        }
        return out
    }

    /** Install = upsert by id with an [SaqrPack.installedAt] stamp + merged commands. */
    fun install(
        existing: List<SlashCommand>,
        packs: List<SaqrPack>,
        p: SaqrPack,
        now: Long,
    ): Pair<List<SlashCommand>, List<SaqrPack>> =
        mergeCommands(existing, p) to (packs.filter { it.id != p.id } + p.copy(installedAt = now))

    /** Uninstall keeps the commands the user already has; only the pack (its template) goes. */
    fun remove(packs: List<SaqrPack>, id: String): List<SaqrPack> = packs.filter { it.id != id }

    // ------------------------------------------------------------ sample

    /** The built-in «Flask» sample: one tap installs a template, commands, rules and tools. */
    fun flaskSample(): SaqrPack = SaqrPack(
        id = "flask",
        name = "Flask",
        nameAr = "فلاسك",
        description = "قالب Flask جاهز بأوامر عربية للتشغيل والاختبار",
        templateRef = "PYTHON",
        files = mapOf(
            "app.py" to """from flask import Flask

app = Flask(__name__)

@app.route("/")
def home():
    return "مرحبا من صقر كود — Flask يعمل على هاتفك!"

@app.route("/about")
def about():
    return "Flask sample by Saqr Code"

if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000)
""",
            "requirements.txt" to "flask>=3.0\n",
        ),
        commands = listOf(
            PackCommand(
                "flask-run",
                "شغّل تطبيق Flask: ثبّت المتطلبات أولًا `pip3 install -r requirements.txt` ثم نفّذ `python3 app.py` في الطرفية، وأخبرني بالرابط المحلي للمعاينة. {args}",
            ),
            PackCommand(
                "flask-test",
                "اكتب اختبارات pytest لمسارات app.py باستخدام `app.test_client()` وضعها في tests/test_app.py، ثم نفّذ `python3 -m pytest -q` وأصلح ما فشل. {args}",
            ),
            PackCommand(
                "flask-route",
                "أضف مسارًا جديدًا إلى app.py باسم {args} يعيد JSON مع دالة صغيرة نظيفة، وحدّث README بمثال نداء curl له. {args}",
            ),
        ),
        rules = "- Flask بلا امتدادات ثقيلة؛ انتقل إلى Blueprints عندما يتجاوز الملف 150 سطرًا.\n" +
            "- المسارات دوال صغيرة تعيد JSON أو نصًا فقط، ولا منطق أعمال داخلها.\n" +
            "- كل مسار جديد يأتي باختبار pytest مكافئ في tests/.",
        tools = listOf("python3", "pip3", "pytest"),
    )
}
