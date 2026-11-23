package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.Mcp
import com.ctodrs.saqrcode.data.TemplateFiles
import com.ctodrs.saqrcode.ondevice.Aapt2Mirrors
import com.ctodrs.saqrcode.ondevice.BuildCache
import com.ctodrs.saqrcode.ondevice.OnDeviceBuild
import com.ctodrs.saqrcode.ondevice.SigningConfig
import com.ctodrs.saqrcode.ondevice.SigningInit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** v0.9 · signed release build, aapt2 mirror presets, cache management, MCP tools-browser helpers. */
class V09FeaturesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ------------------------------------------------------------ signing config

    @Test
    fun `signing config is complete only when every field is filled`() {
        assertFalse(SigningConfig("", "p", "alias", "p").complete())
        assertFalse(SigningConfig("keystore/release.jks", "", "alias", "p").complete())
        assertFalse(SigningConfig("keystore/release.jks", "p", "", "p").complete())
        assertFalse(SigningConfig("keystore/release.jks", "p", "alias", "").complete())
        assertTrue(SigningConfig("keystore/release.jks", "p", "alias", "p").complete())
    }

    @Test
    fun `keystore paths must stay inside the project`() {
        assertTrue(SigningConfig.isSafePath("keystore/release.jks"))
        assertTrue(SigningConfig.isSafePath("release.jks"))
        assertTrue(SigningConfig.isSafePath("a\\b\\key.jks")) // windows separators normalised
        assertFalse(SigningConfig.isSafePath(""))
        assertFalse(SigningConfig.isSafePath("/etc/passwd"))
        assertFalse(SigningConfig.isSafePath("C:/keys/saqr.jks"))
        assertFalse(SigningConfig.isSafePath("../outside.jks"))
        assertFalse(SigningConfig.isSafePath("a/../../outside.jks"))
    }

    @Test
    fun `init script reads the keystore from env vars and disables shrinking`() {
        val s = SigningInit.script()
        assertTrue(s.contains("storeFile file(System.getenv('${SigningInit.ENV_KS}')"))
        assertTrue(s.contains("storePassword System.getenv('${SigningInit.ENV_KS_PASS}')"))
        assertTrue(s.contains("keyAlias System.getenv('${SigningInit.ENV_ALIAS}')"))
        assertTrue(s.contains("keyPassword System.getenv('${SigningInit.ENV_KEY_PASS}')"))
        assertTrue(s.contains("com.android.application"))
        assertTrue(s.contains("signingConfigs"))
        assertTrue(s.contains("minifyEnabled false"))
        assertFalse("no passwords inline", s.contains("storePassword \""))
    }

    // ------------------------------------------------------------ aapt2 mirrors

    @Test
    fun `aapt2 mirror resolve picks presets, urls and the auto fallback`() {
        assertEquals(Aapt2Mirrors.AUTO, Aapt2Mirrors.resolve(""))
        assertEquals(Aapt2Mirrors.AUTO, Aapt2Mirrors.resolve("auto"))
        assertEquals(Aapt2Mirrors.AUTO, Aapt2Mirrors.resolve("  "))
        assertEquals(Aapt2Mirrors.REVANCED, Aapt2Mirrors.resolve("revanced"))
        val custom = Aapt2Mirrors.resolve("https://example.com/aapt2")
        assertEquals("custom", custom.id)
        assertEquals("https://example.com/aapt2", custom.url)
        assertFalse(custom.viaApi)
        assertEquals(Aapt2Mirrors.AUTO, Aapt2Mirrors.resolve("nonsense"))
    }

    @Test
    fun `aapt2 presets point at verified sources`() {
        assertTrue(Aapt2Mirrors.AUTO.viaApi)
        assertTrue(Aapt2Mirrors.AUTO.url.startsWith("https://api.github.com/repos/"))
        assertTrue(Aapt2Mirrors.REVANCED.url.startsWith("https://github.com/ReVanced/aapt2/releases/"))
        assertTrue("stable latest URL", Aapt2Mirrors.REVANCED.url.contains("/latest/download/aapt2-arm64-v8a"))
        assertTrue(Aapt2Mirrors.PRESETS.size >= 2)
    }

    // ------------------------------------------------------------ build cache

    @Test
    fun `dirSize sums files recursively and skips missing dirs`() {
        val root = tmp.newFolder("env")
        java.io.File(root, "debian/bin").mkdirs()
        java.io.File(root, "debian/bin/java").writeText("abcdef") // 6
        java.io.File(root, "debian/etc.conf").writeText("ab") // 2
        assertEquals(8L, BuildCache.dirSize(root))
        assertEquals(0L, BuildCache.dirSize(java.io.File(root, "missing")))
        assertEquals(0L, BuildCache.dirSize(null))
    }

    @Test
    fun `formatBytes uses the right unit`() {
        assertEquals("0 B", BuildCache.formatBytes(0))
        assertEquals("0 B", BuildCache.formatBytes(-5))
        assertEquals("512 B", BuildCache.formatBytes(512))
        assertEquals("1 KB", BuildCache.formatBytes(1024))
        assertEquals("1.5 KB", BuildCache.formatBytes(1536))
        assertEquals("3.4 MB", BuildCache.formatBytes((3.4 * 1024 * 1024).toLong()))
        assertEquals("1.2 GB", BuildCache.formatBytes((1.2 * 1024 * 1024 * 1024).toLong()))
        val big = BuildCache.formatBytes(20L * 1024 * 1024 * 1024)
        assertTrue(big.endsWith(" GB"))
        assertFalse("round numbers carry no decimal: $big", big.startsWith("20.0"))
    }

    @Test
    fun `components cover rootfs, sdk, gradle and downloads`() {
        val files = tmp.newFolder("files")
        val cache = tmp.newFolder("cache")
        val entries = BuildCache.components(files, cache)
        val ids = entries.map { it.id }
        assertTrue(ids.containsAll(listOf("rootfs", "sdk", "gradle", "downloads")))
        assertEquals(java.io.File(files, "linux/debian").path, entries.first { it.id == "rootfs" }.dir.path)
        assertEquals(cache.path, entries.first { it.id == "downloads" }.dir.path)
        assertEquals(0L, BuildCache.total(entries))
    }

    // ------------------------------------------------------------ mcp tools browser helpers

    @Test
    fun `sampleArgs fills required fields with type-based values`() {
        val schema = JSONObject("""{"type":"object","properties":{
            "city":{"type":"string"},"n":{"type":"integer"},"flag":{"type":"boolean"},
            "items":{"type":"array"},"meta":{"type":"object"},"opt":{"type":"string"}},
            "required":["city","n","flag","items","meta"]}""")
        val args = Mcp.sampleArgs(schema)
        assertEquals(5, args.length())
        assertEquals("", args.optString("city"))
        assertEquals(0, args.optInt("n"))
        assertEquals(false, args.optBoolean("flag"))
        assertEquals(0, args.optJSONArray("items")?.length())
        assertTrue(args.optJSONObject("meta") != null)
        assertFalse("optional keys are omitted", args.has("opt"))
        assertEquals(0, Mcp.sampleArgs(JSONObject("""{"type":"object"}""")).length())
        assertEquals(0, Mcp.sampleArgs(JSONObject()).length())
    }

    // ------------------------------------------------------------ regression guards

    @Test
    fun `built-in artifacts keep valid pins after the v09 refactor`() {
        listOf(OnDeviceBuild.ART_ROOTFS, OnDeviceBuild.ART_CMDTOOLS, OnDeviceBuild.ART_GRADLE).forEach {
            assertTrue("pin of ${it.name}", it.valid())
        }
    }

    @Test
    fun `lite template still builds with the signing helpers in place`() {
        val files = TemplateFiles.liteWeb("demo")
        assertTrue(files.containsKey("app/build.gradle.kts"))
        val app = files["app/build.gradle.kts"]!!
        assertNotEquals("", app)
        // the generated init script only configures application modules
        assertTrue(SigningInit.script().contains("com.android.application"))
    }
}
