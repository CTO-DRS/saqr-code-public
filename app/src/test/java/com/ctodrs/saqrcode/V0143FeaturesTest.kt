package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.data.GalleryClient
import com.ctodrs.saqrcode.data.GalleryEntry
import com.ctodrs.saqrcode.data.GalleryKit
import com.ctodrs.saqrcode.data.SaqrPackCodec
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.git.GhException
import com.ctodrs.saqrcode.agent.SlashCommand
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * v0.14.3 «أجنحة مفتوحة · معرض المجتمع»: the strict index codec (entries validated through
 * the pack pipeline, one broken entry never breaks the gallery, id dedupe, the 60-entry cap),
 * the Arabic-aware search across every field, the raw-URL builder shapes, the offline cache in
 * the settings store, the anonymous fetch over the wire (MockWebServer — no real sockets beyond
 * localhost), and the one-tap install flowing through the existing .saqrpack pipeline.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0143FeaturesTest {

    private fun settings() = SettingsStore(
        RuntimeEnvironment.getApplication(), SecureStore(RuntimeEnvironment.getApplication()))

    /** A valid pack JSON for index entries (the built-in Flask sample round-tripped). */
    private fun flaskJson(): String = SaqrPackCodec.toJson(SaqrPackCodec.flaskSample())

    private fun entry(id: String, name: String, packJson: String, extra: String = ""): String =
        """{"id":"$id","name":"$name","nameAr":"$name عربي","description":"وصف $name","author":"مجتمع صقر","tags":["قوالب","$name"]$extra,"pack":$packJson}"""

    private fun index(vararg entries: String, updated: Long = 1000L): String =
        """{"saqrgallery":1,"updated":$updated,"entries":[${entries.joinToString(",")}]}"""

    // ------------------------------------------------- index codec

    @Test
    fun `index parses entries with their packs intact`() {
        val idx = GalleryKit.parseIndex(index(entry("flask", "Flask", flaskJson())))!!
        assertEquals(1, idx.entries.size)
        assertEquals(1000L, idx.updated)
        val e = idx.entries[0]
        assertEquals("flask", e.id)
        assertEquals("Flask", e.name)
        assertEquals("Flask عربي", e.nameAr)
        assertEquals("مجتمع صقر", e.author)
        assertEquals(listOf("قوالب", "Flask"), e.tags)
        assertNotNull(e.pack)
        assertEquals("flask", e.pack!!.id)
        assertEquals(3, e.pack!!.commands.size)
        // The pack JSON round-trips for the one-tap install callback.
        assertEquals("flask", SaqrPackCodec.fromJson(e.packJson)!!.id)
    }

    @Test
    fun `index is strict about garbage versions and missing arrays`() {
        assertNull(GalleryKit.parseIndex(null))
        assertNull(GalleryKit.parseIndex(""))
        assertNull(GalleryKit.parseIndex("not json at all"))
        assertNull(GalleryKit.parseIndex("""{"saqrgallery":2,"entries":[]}"""))
        assertNull(GalleryKit.parseIndex("""{"saqrgallery":1}"""))
        assertNull(GalleryKit.parseIndex("""{"saqrgallery":1,"entries":"nope"}"""))
        // An empty entries array is a valid (if boring) gallery.
        assertEquals(0, GalleryKit.parseIndex("""{"saqrgallery":1,"entries":[]}""")!!.entries.size)
    }

    @Test
    fun `entries with broken packs are skipped and the rest survive`() {
        val good = entry("flask", "Flask", flaskJson())
        val badJson = """{"id":"bad","name":"Broken","pack":{"saqrpack":9,"id":"x"}}"""
        val hostilePack = SaqrPackCodec.toJson(
            SaqrPackCodec.flaskSample().copy(
                id = "hostile",
                files = mapOf("../escape.txt" to "nope"),
            )
        )
        val hostile = """{"id":"hostile","name":"Hostile","pack":$hostilePack}"""
        val idx = GalleryKit.parseIndex(index(good, badJson, hostile))!!
        assertEquals(listOf("flask"), idx.entries.map { it.id })
    }

    @Test
    fun `duplicate ids keep the first entry`() {
        val a = entry("flask", "First", flaskJson())
        val b = entry("flask", "Second", flaskJson())
        val idx = GalleryKit.parseIndex(index(a, b))!!
        assertEquals(1, idx.entries.size)
        assertEquals("First", idx.entries[0].name)
        // Blank ids never pass either.
        val blank = """{"id":"  ","name":"Blank","pack":${flaskJson()}}"""
        assertEquals(0, GalleryKit.parseIndex(index(blank))!!.entries.size)
    }

    @Test
    fun `entries cap at sixty`() {
        val many = (1..70).joinToString(",") {
            entry("p$it", "Pack$it", SaqrPackCodec.toJson(SaqrPackCodec.flaskSample().copy(id = "p$it")))
        }
        val idx = GalleryKit.parseIndex("""{"saqrgallery":1,"entries":[$many]}""")!!
        assertEquals(GalleryKit.MAX_ENTRIES, idx.entries.size)
        assertEquals("p1", idx.entries.first().id)
    }

    @Test
    fun `long fields are trimmed to their caps`() {
        val longName = "ا".repeat(200)
        val e = """{"id":"x","name":"$longName","author":"$longName","description":"$longName","pack":${flaskJson()}}"""
        val parsed = GalleryKit.parseIndex(index(e))!!.entries[0]
        assertEquals(GalleryKit.MAX_NAME, parsed.name.length)
        assertEquals(GalleryKit.MAX_AUTHOR, parsed.author.length)
        assertEquals(GalleryKit.MAX_DESC, parsed.description.length)
    }

    // ------------------------------------------------- search

    @Test
    fun `search matches arabic-insensitively across every field`() {
        // A deliberately Arabic entry: hamza forms, diacritics and the search across every field.
        val ar = """{"id":"ar","name":"Web App","nameAr":"ويب سريع","description":"قوالب عربية جاهزة","author":"أحمد","tags":["شبكة"],"pack":${flaskJson()}}"""
        val entries = GalleryKit.parseIndex(index(ar))!!.entries
        // nameAr (diacritics dropped by the normalizer)
        assertEquals(1, GalleryKit.search(entries, "وِيْب").size)
        assertEquals(1, GalleryKit.search(entries, "سريع").size)
        // description
        assertEquals(1, GalleryKit.search(entries, "قوالب").size)
        // author — hamza unified
        assertEquals(1, GalleryKit.search(entries, "أَحْمَد").size)
        // tag
        assertEquals(1, GalleryKit.search(entries, "شَبَكة").size)
        // latin name
        assertEquals(1, GalleryKit.search(entries, "web").size)
        // Blank query returns everything; a foreign query returns nothing.
        assertEquals(1, GalleryKit.search(entries, "").size)
        assertEquals(0, GalleryKit.search(entries, "django").size)
    }

    @Test
    fun `installed ids mark what the user already has`() {
        val packs = listOf(SaqrPackCodec.flaskSample())
        assertTrue("flask" in GalleryKit.installedIds(packs))
        assertFalse("static" in GalleryKit.installedIds(packs))
        assertTrue(GalleryKit.installedIds(emptyList()).isEmpty())
    }

    // ------------------------------------------------- urls

    @Test
    fun `raw url builder handles every repo shape`() {
        assertEquals(
            "https://raw.githubusercontent.com/CTO-DRS/saqr-gallery/main/gallery.json",
            GalleryKit.rawUrl("CTO-DRS/saqr-gallery"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/CTO-DRS/saqr-gallery/main/gallery.json",
            GalleryKit.rawUrl("https://github.com/CTO-DRS/saqr-gallery"),
        )
        // A bare owner falls back to the gallery repository name.
        assertEquals(
            "https://raw.githubusercontent.com/CTO-DRS/saqr-gallery/main/gallery.json",
            GalleryKit.rawUrl("CTO-DRS"),
        )
        // Custom branch/path with percent-encoding where needed.
        assertEquals(
            "https://raw.githubusercontent.com/o/r/dev/index.json",
            GalleryKit.rawUrl("o/r", "dev", "index.json"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/o/r/main/%D8%A3%D8%B9%D8%B1%D8%A8.json",
            GalleryKit.rawUrl("o/r", "main", "أعرب.json"),
        )
    }

    @Test
    fun `install error names the broken pack`() {
        val good = GalleryKit.parseIndex(index(entry("flask", "Flask", flaskJson())))!!.entries[0]
        assertNull(GalleryKit.installError(good))
        val broken = GalleryEntry(id = "x", name = "X", pack = null, packJson = "{}")
        assertEquals("json", GalleryKit.installError(broken))
    }

    // ------------------------------------------------- cache

    @Test
    fun `gallery cache round-trips in the settings store`() {
        val s = settings()
        assertEquals("", s.galleryCache)
        assertEquals(0L, s.galleryFetchedAt)
        s.galleryCache = index(entry("flask", "Flask", flaskJson()))
        s.galleryFetchedAt = 12345L
        assertEquals(12345L, s.galleryFetchedAt)
        val parsed = GalleryKit.parseIndex(s.galleryCache)!!
        assertEquals(listOf("flask"), parsed.entries.map { it.id })
    }

    // ------------------------------------------------- one-tap install through the pack pipeline

    @Test
    fun `a gallery entry installs through the existing pack pipeline`() {
        val idx = GalleryKit.parseIndex(index(entry("flask", "Flask", flaskJson())))!!
        val p = idx.entries[0].pack!!
        assertNull(SaqrPackCodec.validate(p))
        val existing = listOf(SlashCommand("mine", "أمري الخاص"))
        val (cmds, packs) = SaqrPackCodec.install(existing, emptyList(), p, 999L)
        // The pack lands as a home-screen template…
        assertEquals(listOf("flask"), packs.map { it.id })
        assertEquals(999L, packs[0].installedAt)
        // …and its three Arabic commands merge beside the user's own.
        assertEquals(setOf("mine", "flask-run", "flask-test", "flask-route"), cmds.map { it.name }.toSet())
        // Reinstall (the gallery's «تثبيت من جديد») upserts by id without duplicating commands.
        val (_, packs2) = SaqrPackCodec.install(cmds, packs, p, 1000L)
        assertEquals(listOf("flask"), packs2.map { it.id })
    }

    // ------------------------------------------------- the fetch over the wire

    @Test
    fun `fetch pulls the index anonymously`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(index(entry("flask", "Flask", flaskJson()))))
        server.start()
        val body = GalleryClient().fetch(server.url("/gallery.json").toString())
        assertTrue(body.contains("\"flask\""))
        val req = server.takeRequest()
        assertEquals("/gallery.json", req.path)
        assertNull(req.getHeader("Authorization")) // public content — no token, ever
        server.shutdown()
    }

    @Test
    fun `fetch turns http failures into readable refusals`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
        server.start()
        var err: GhException? = null
        try {
            GalleryClient().fetch(server.url("/missing.json").toString())
        } catch (e: GhException) {
            err = e
        }
        assertNotNull(err)
        assertEquals(404, err!!.code)
        assertTrue(err.message!!.contains("404"))
        server.shutdown()
    }

    @Test
    fun `fetch refuses an empty success body`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("   "))
        server.start()
        var err: Exception? = null
        try {
            GalleryClient().fetch(server.url("/gallery.json").toString())
        } catch (e: Exception) {
            err = e
        }
        assertNotNull(err)
        assertTrue(err is GhException)
        server.shutdown()
    }
}
