package com.ctodrs.saqrcode.ui.theme

import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.12 «ريش جديدة» · استوديو الثيمات · Theme Studio.
 *
 * A theme pack is six ARGB colors (background, two surfaces, two text tiers and the accent)
 * from which a full [SaqrColors] palette is derived. Packs come from three sources: the six
 * built-in community packs (نجد، تهامة، البحر، الوادي، عُمان، الأندلس), packs the model
 * generates from a mood phrase, and packs the user saved or imported as JSON.
 *
 * Everything here is pure Kotlin (ARGB ints + org.json) so it is unit-testable on the JVM;
 * the Compose-side derivation lives in PackColors.kt.
 */

/** Small ARGB color helpers used by the theme pipeline. */
object ColorFx {

    /** Parses "#RGB", "#RRGGBB" or "#AARRGGBB" into an opaque ARGB int (null when malformed). */
    fun parseHex(raw: String): Int? {
        val s = raw.trim().removePrefix("#")
        if (s.isEmpty() || !s.all(::valid)) return null
        val expanded = when (s.length) {
            3 -> s.map { "$it$it" }.joinToString("")
            6, 8 -> s
            else -> return null
        }
        val v = expanded.toLongOrNull(16) ?: return null
        return if (expanded.length == 8) v.toInt() else (0xFF000000L or v).toInt()
    }

    /** "#RRGGBB" (alpha dropped). */
    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Replaces the alpha byte. */
    fun alpha(argb: Int, a: Int): Int = (a shl 24) or (argb and 0xFFFFFF)

    /** Per-channel lerp from [a] to [b] at t (keeps [a]'s alpha). */
    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(aV: Int, bV: Int) = (aV + (bV - aV) * f).toInt().coerceIn(0, 255)
        val r = ch((a shr 16) and 0xFF, (b shr 16) and 0xFF)
        val g = ch((a shr 8) and 0xFF, (b shr 8) and 0xFF)
        val bl = ch(a and 0xFF, b and 0xFF)
        return (a and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
    }

    /** Weighted sRGB luminance, 0..1. */
    fun luminance(argb: Int): Float =
        (0.2126f * ((argb shr 16) and 0xFF) + 0.7152f * ((argb shr 8) and 0xFF) + 0.0722f * (argb and 0xFF)) / 255f

    fun lighten(argb: Int, t: Float): Int = mix(argb, 0xFFFFFF, t)
    fun darken(argb: Int, t: Float): Int = mix(argb, 0x000000, t)

    /** Readable on-color: near-black on light accents, warm-white on dark ones. */
    fun onColor(argb: Int): Int = if (luminance(argb) > 0.5f) 0xFF14100A.toInt() else 0xFFFFF6E8.toInt()

    private fun valid(ch: Char) = ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
}

/** The six pack colors a theme is built from. */
data class PackColors(
    val bg: Int,
    val surface1: Int,
    val surface2: Int,
    val text1: Int,
    val text2: Int,
    val accent: Int,
)

/** A named, shareable color theme. */
data class ThemePack(
    val id: String,
    val name: String,
    val nameEn: String,
    val mood: String,
    val dark: Boolean,
    val colors: PackColors,
) {
    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("nameEn", nameEn)
        .put("mood", mood)
        .put("dark", dark)
        .put("bg", ColorFx.hex(colors.bg))
        .put("surface1", ColorFx.hex(colors.surface1))
        .put("surface2", ColorFx.hex(colors.surface2))
        .put("text1", ColorFx.hex(colors.text1))
        .put("text2", ColorFx.hex(colors.text2))
        .put("accent", ColorFx.hex(colors.accent))
        .toString()

    companion object {
        /** Strict parse: all six colors must be valid hex, or null. */
        fun fromJson(json: String?): ThemePack? {
            val o = runCatching { JSONObject(json ?: return null) }.getOrNull() ?: return null
            val c = colors(o) ?: return null
            val id = o.optString("id").trim().ifBlank { "pack-" + (c.accent and 0xFFFFFF).toString(16) }
            val name = o.optString("name").trim().ifBlank { o.optString("nameEn").trim().ifBlank { id } }
            return ThemePack(id, name.take(40), o.optString("nameEn").trim().take(40), o.optString("mood").trim().take(120),
                o.optBoolean("dark", true), c)
        }

        private fun colors(o: JSONObject): PackColors? {
            val bg = ColorFx.parseHex(o.optString("bg")) ?: return null
            val s1 = ColorFx.parseHex(o.optString("surface1")) ?: return null
            val s2 = ColorFx.parseHex(o.optString("surface2")) ?: return null
            val t1 = ColorFx.parseHex(o.optString("text1")) ?: return null
            val t2 = ColorFx.parseHex(o.optString("text2")) ?: return null
            val ac = ColorFx.parseHex(o.optString("accent")) ?: return null
            return PackColors(bg, s1, s2, t1, t2, ac)
        }
    }
}

/** Theme Studio: model prompting, tolerant parsing, pack lists and the built-in packs. */
object ThemeStudio {

    const val SYSTEM_PROMPT = """You are the theme designer of Saqr Code, an Arabic-first coding app. The user gives a mood, scene or vibe; you output ONLY one JSON object — no markdown, no prose — with a complete color theme:
{"name":"<اسم قصير بالعربية>","nameEn":"<short English name>","dark":true,"bg":"#0A0B0D","surface1":"#111317","surface2":"#171A1F","text1":"#EDEEF0","text2":"#A4AAB3","accent":"#E9B44C"}
Rules:
- dark=true: near-black background and surfaces, light text; dark=false: warm paper tones, dark text.
- text1 on bg must stay highly readable; accent must pop against bg (it colors buttons, links and highlights).
- One coherent accent hue; six-digit hex values only; no gradients and no keys beyond the ones shown."""

    /** The one-shot user prompt for the generator. */
    fun buildRequest(mood: String): String =
        "## Mood\n" + mood.take(300).trim().ifBlank { "ليل صحراوي هادئ مع لمسة ذهبية" } + "\n\nOutput the JSON theme only."

    /** Extracts a ThemePack from model output (tolerates fences and prose). */
    fun parseModelOutput(text: String): ThemePack? {
        val s = text.trim().replace(Regex("""^```[a-zA-Z]*"""), "").removeSuffix("```").trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return ThemePack.fromJson(s.substring(start, end + 1))
    }

    fun encodePacks(packs: List<ThemePack>): String = JSONArray().apply { packs.forEach { put(JSONObject(it.toJson())) } }.toString()

    fun decodePacks(json: String?): List<ThemePack> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i -> ThemePack.fromJson(arr.optString(i)) }
    }.getOrDefault(emptyList())

    /** Built-in community packs: najd, tihama, bahar, wadi, oman, andalus. */
    val builtins: List<ThemePack> = listOf(
        ThemePack("najd", "نجد", "Najd", "رملي فاتح دافئ بأثر ذهبي", false,
            PackColors(0xFFF5EFE3.toInt(), 0xFFFBF7EE.toInt(), 0xFFEDE4D2.toInt(), 0xFF221D14.toInt(), 0xFF6E6350.toInt(), 0xFFB07B24.toInt())),
        ThemePack("tihama", "تهامة", "Tihama", "ليل ساحلي حار بلمسة طين برتقالية", true,
            PackColors(0xFF0F0C09.toInt(), 0xFF171310.toInt(), 0xFF1F1A14.toInt(), 0xFFF0E7D8.toInt(), 0xFFB3A691.toInt(), 0xFFE8823C.toInt())),
        ThemePack("bahar", "البحر", "Bahar", "أعماق بحرية بفيروز صافٍ", true,
            PackColors(0xFF071019.toInt(), 0xFF0C1826.toInt(), 0xFF122233.toInt(), 0xFFE3EEF7.toInt(), 0xFF9FB6C9.toInt(), 0xFF43B0D5.toInt())),
        ThemePack("wadi", "الوادي", "Wadi", "وادٍ أخضر تحت قمر", true,
            PackColors(0xFF0A100C.toInt(), 0xFF101813.toInt(), 0xFF16211A.toInt(), 0xFFE7F0E7.toInt(), 0xFF9FB4A3.toInt(), 0xFF5FBF7A.toInt())),
        ThemePack("oman", "عُمان", "Oman", "طين عُماني بأفق مغربي", true,
            PackColors(0xFF120C0A.toInt(), 0xFF1B1310.toInt(), 0xFF241916.toInt(), 0xFFF2E8E0.toInt(), 0xFFB9A697.toInt(), 0xFFD96C4F.toInt())),
        ThemePack("andalus", "الأندلس", "Andalus", "عاجي أندلسي بقرميد نحاسي", false,
            PackColors(0xFFF7F1E8.toInt(), 0xFFFDFBF6.toInt(), 0xFFEFE5D6.toInt(), 0xFF2A2018.toInt(), 0xFF71604E.toInt(), 0xFFC25E42.toInt())),
    )
}
