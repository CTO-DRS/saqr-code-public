package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.data.BuildHistory
import com.ctodrs.saqrcode.data.BuildHistoryCodec
import com.ctodrs.saqrcode.data.BuildRecord
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.ui.PaletteItem
import com.ctodrs.saqrcode.ui.PaletteKind
import com.ctodrs.saqrcode.ui.PaletteLogic
import com.ctodrs.saqrcode.ui.branchOf
import com.ctodrs.saqrcode.ui.theme.ColorFx
import com.ctodrs.saqrcode.ui.theme.PackColors
import com.ctodrs.saqrcode.ui.theme.ThemePack
import com.ctodrs.saqrcode.ui.theme.ThemeStudio
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** v0.12 «ريش جديدة» · theme packs, Theme Studio parsing, command palette, build history, editor settings. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V012FeaturesTest {

    // ------------------------------------------------------------ ColorFx

    @Test
    fun `parseHex accepts 6 3 and 8 digit forms and rejects junk`() {
        assertEquals(0xFFE9B44C.toInt(), ColorFx.parseHex("#E9B44C"))
        assertEquals(0xFFAABBCC.toInt(), ColorFx.parseHex("#abc"))
        assertEquals(0x80112233.toInt(), ColorFx.parseHex("#80112233"))
        assertNull(ColorFx.parseHex("#12345"))
        assertNull(ColorFx.parseHex("#zzzzzz"))
        assertNull(ColorFx.parseHex(""))
        assertNull(ColorFx.parseHex("not-a-color"))
    }

    @Test
    fun `hex drops alpha and round trips`() {
        assertEquals("#E9B44C", ColorFx.hex(0xFFE9B44C.toInt()))
        assertEquals("#E9B44C", ColorFx.hex(ColorFx.parseHex("#80E9B44C")!!))
    }

    @Test
    fun `mix lerps toward the target and clamps`() {
        val m = ColorFx.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        assertEquals(0x7F, (m shr 16) and 0xFF)
        assertEquals(0x7F, (m shr 8) and 0xFF)
        assertEquals(0x7F, m and 0xFF)
        assertEquals(0xFF101010.toInt(), ColorFx.mix(0xFF101010.toInt(), 0xFFFFFFFF.toInt(), -1f))
        assertEquals(0xFFFFFFFF.toInt(), ColorFx.mix(0xFF101010.toInt(), 0xFFFFFFFF.toInt(), 2f))
    }

    @Test
    fun `onColor picks dark text on gold and light text on charcoal`() {
        val gold = ColorFx.parseHex("#E9B44C")!!
        val charcoal = ColorFx.parseHex("#22252A")!!
        assertTrue(ColorFx.luminance(gold) > 0.5f)
        assertEquals(0xFF14100A.toInt(), ColorFx.onColor(gold))
        assertEquals(0xFFFFF6E8.toInt(), ColorFx.onColor(charcoal))
    }

    // ------------------------------------------------------------ ThemePack

    @Test
    fun `theme pack json round trip`() {
        val pack = ThemePack("x", "حزمة", "Pack", "مزاج", true, PackColors(0xFF0A0B0D.toInt(), 0xFF111317.toInt(), 0xFF171A1F.toInt(), 0xFFEDEEF0.toInt(), 0xFFA4AAB3.toInt(), 0xFFE9B44C.toInt()))
        val back = ThemePack.fromJson(pack.toJson())
        assertNotNull(back)
        assertEquals(pack, back)
    }

    @Test
    fun `theme pack json is strict about colors`() {
        assertNull(ThemePack.fromJson("not json"))
        assertNull(ThemePack.fromJson("{}"))
        assertNull(ThemePack.fromJson(JSONObject().put("bg", "#0A0B0D").toString()))
        assertNull(ThemePack.fromJson(JSONObject().put("bg", "#zzzzzz").put("surface1", "#111317").put("surface2", "#171A1F")
            .put("text1", "#EDEEF0").put("text2", "#A4AAB3").put("accent", "#E9B44C").toString()))
    }

    @Test
    fun `builtins are six unique coherent packs`() {
        val b = ThemeStudio.builtins
        assertEquals(6, b.size)
        assertEquals(6, b.map { it.id }.toSet().size)
        assertTrue(b.any { !it.dark } && b.any { it.dark }) // فجر/الأندلس فاتحان والبقية داكنة
        b.forEach { p ->
            listOf(p.colors.bg, p.colors.surface1, p.colors.surface2, p.colors.text1, p.colors.text2, p.colors.accent).forEach {
                assertEquals(0xFF, (it ushr 24))
            }
            assertTrue(p.name.isNotBlank())
        }
    }

    @Test
    fun `packs encode and decode as a list`() {
        val encoded = ThemeStudio.encodePacks(ThemeStudio.builtins.take(2))
        val decoded = ThemeStudio.decodePacks(encoded)
        assertEquals(2, decoded.size)
        assertEquals(ThemeStudio.builtins[0], decoded[0])
        assertEquals(emptyList<ThemePack>(), ThemeStudio.decodePacks(null))
        assertEquals(emptyList<ThemePack>(), ThemeStudio.decodePacks("[]"))
    }

    // ------------------------------------------------------------ Theme Studio parsing

    @Test
    fun `parseModelOutput tolerates fences and prose`() {
        val json = """{"name":"ليل الصحراء","nameEn":"Desert Night","dark":true,"bg":"#0C0A08","surface1":"#141210","surface2":"#1C1916","text1":"#F1E9DC","text2":"#B5A693","accent":"#E8823C"}"""
        val fenced = "```json\n$json\n```"
        val prose = "Here is your theme:\n$json\nHope you like it!"
        assertEquals(ThemePack.fromJson(json), ThemeStudio.parseModelOutput(fenced))
        assertEquals(ThemePack.fromJson(json), ThemeStudio.parseModelOutput(prose))
        assertNull(ThemeStudio.parseModelOutput("no json here"))
        assertNull(ThemeStudio.parseModelOutput("""{"name":"ناقص","dark":true}"""))
    }

    @Test
    fun `buildRequest keeps the mood short and provides a default`() {
        assertTrue(ThemeStudio.buildRequest("غروب على الكورنيش").contains("غروب على الكورنيش"))
        assertTrue(ThemeStudio.buildRequest("   ").isNotBlank())
        assertEquals(300, ThemeStudio.buildRequest("x".repeat(400)).takeLast(350).count { it == 'x' }.coerceAtMost(300))
    }

    // ------------------------------------------------------------ Command palette

    @Test
    fun `palette build includes commands files settings and sessions`() {
        val items = PaletteLogic.build(listOf("app/main.py", "README.md"), SlashCommands.builtins)
        assertTrue(items.any { it.kind == PaletteKind.FILE && it.title == "app/main.py" })
        assertTrue(items.any { it.kind == PaletteKind.COMMAND && it.key == SlashCommands.EXPLAIN })
        assertTrue(items.any { it.kind == PaletteKind.SETTING && it.title == "الثيم والاستوديو" })
        assertTrue(items.any { it.kind == PaletteKind.SESSION })
    }

    @Test
    fun `palette search understands Arabic aliases and forms`() {
        val items = PaletteLogic.build(listOf("app/main.py", "style.css"), SlashCommands.builtins)
        val top = PaletteLogic.search("اشرح", items)
        assertEquals(PaletteKind.COMMAND, top.first().kind)
        assertEquals(SlashCommands.EXPLAIN, top.first().key)
        // alef forms unify: أشرح finds /explain through its Arabic alias too
        assertEquals(SlashCommands.EXPLAIN, PaletteLogic.search("أشرح", items).first().key)
        // settings match on Arabic titles
        val theme = PaletteLogic.search("ثيم", items)
        assertEquals(PaletteKind.SETTING, theme.first().kind)
        assertTrue(theme.first().title.contains("الثيم"))
    }

    @Test
    fun `palette search matches files and requires all tokens`() {
        val items = PaletteLogic.build(listOf("app/main.py", "style.css", "docs/readme.md"), emptyList())
        val hit = PaletteLogic.search("main", items)
        assertEquals(PaletteKind.FILE, hit.first().kind)
        assertEquals("app/main.py", hit.first().title)
        assertTrue(PaletteLogic.search("app main", items).first().title == "app/main.py")
        assertTrue(PaletteLogic.search("app nonexistent", items).isEmpty())
        // empty query falls back to the default order, capped at the limit
        val all = PaletteLogic.search("", items, limit = 5)
        assertEquals(5, all.size)
    }

    // ------------------------------------------------------------ Build history

    @Test
    fun `build history codec round trip`() {
        val map = mapOf(
            "alpha" to BuildRecord(true, 1_700_000_000_000, "app-release.apk"),
            "beta" to BuildRecord(false, 1_700_000_001_000, ""),
        )
        val decoded = BuildHistoryCodec.decode(BuildHistoryCodec.encode(map))
        assertEquals(map, decoded)
        assertEquals(emptyMap<String, BuildRecord>(), BuildHistoryCodec.decode(null))
        assertEquals(emptyMap<String, BuildRecord>(), BuildHistoryCodec.decode("{bad"))
    }

    @Test
    fun `build history records clears and reads the last result`() {
        val history = BuildHistory(RuntimeEnvironment.getApplication())
        assertNull(history.last("proj"))
        history.record("proj", true, "app-debug.apk")
        val last = history.last("proj")
        assertNotNull(last)
        assertTrue(last!!.ok)
        assertEquals("app-debug.apk", last.apk)
        assertTrue(last.time > 0)
        history.record("proj", false, "")
        assertFalse(history.last("proj")!!.ok)
        history.clear("proj")
        assertNull(history.last("proj"))
        history.clear("never-existed") // no-op
    }

    // ------------------------------------------------------------ Editor + theme settings

    @Test
    fun `editor settings coerce and persist`() {
        val settings = SettingsStore(RuntimeEnvironment.getApplication(), SecureStore(RuntimeEnvironment.getApplication()))
        assertEquals(13, settings.editorFontSize)
        settings.editorFontSize = 99
        assertEquals(20, settings.editorFontSize)
        settings.editorFontSize = 3
        assertEquals(12, settings.editorFontSize)
        assertFalse(settings.editorWrap)
        settings.editorWrap = true
        assertTrue(settings.editorWrap)
    }

    @Test
    fun `theme mode accepts oled and custom with a pack`() {
        val settings = SettingsStore(RuntimeEnvironment.getApplication(), SecureStore(RuntimeEnvironment.getApplication()))
        settings.themeMode = "oled"
        assertEquals("oled", settings.themeMode)
        val pack = ThemeStudio.builtins.first()
        settings.customThemeJson = pack.toJson()
        settings.themeMode = "custom"
        assertEquals(pack, ThemePack.fromJson(settings.customThemeJson))
        assertEquals(pack.id, ThemePack.fromJson(settings.customThemeJson)?.id)
    }

    // ------------------------------------------------------------ Branch helper

    @Test
    fun `branchOf reads git HEAD`() {
        val dir = createTempDir("proj")
        try {
            assertNull(branchOf(dir))
            val git = java.io.File(dir, ".git").apply { mkdirs() }
            java.io.File(git, "HEAD").writeText("ref: refs/heads/feature/theme-studio\n")
            assertEquals("feature/theme-studio", branchOf(dir))
            java.io.File(git, "HEAD").writeText("e4d2b19c0ffee4d2b19c0ffee4d2b19c0ffee4d2\n")
            assertEquals("e4d2b19", branchOf(dir))
        } finally { dir.deleteRecursively() }
    }
}
