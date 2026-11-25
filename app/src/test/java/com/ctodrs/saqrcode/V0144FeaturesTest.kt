package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.data.SaqrPackCodec
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.data.SyncClient
import com.ctodrs.saqrcode.data.SyncException
import com.ctodrs.saqrcode.data.SyncKit
import com.ctodrs.saqrcode.data.SyncPayload
import com.ctodrs.saqrcode.git.GhException
import com.ctodrs.saqrcode.ui.theme.ThemeStudio
import com.ctodrs.saqrcode.ui.theme.ThemePack
import com.ctodrs.saqrcode.agent.SlashCommand
import android.content.Context
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * v0.14.4 «أجنحة مفتوحة · المزامنة عبر المستودع»: the explicit non-secret whitelist (secrets
 * excluded by construction), the strict payload codec, replace-semantics apply with honest
 * counts, the AES-GCM vault (PBKDF2 passphrase — wrong phrase and tampered blob refused),
 * the repo-slug shapes, and the push/pull over the real wire (MockWebServer — localhost only).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0144FeaturesTest {

    private fun settings(): SettingsStore {
        val app = RuntimeEnvironment.getApplication()
        // Robolectric shares one Application per class — start every store from a clean slate.
        app.getSharedPreferences("saqr_settings", Context.MODE_PRIVATE).edit().clear().commit()
        return SettingsStore(app, SecureStore(app))
    }

    private val PASS = "عبارة المرور ١٢٣"

    /** A fresh store carrying every syncable kind of data. */
    private fun populated(): SettingsStore {
        val s = settings()
        s.editorFontSize = 17
        s.editorWrap = true
        s.agentMode = "team"
        s.approveAll = true
        s.monthlyBudget = 12.5
        s.voiceLang = "ar-SA"
        s.desert = "auto"
        s.gitName = "صقر"
        s.gitEmail = "saqr@example.com"
        s.customCommands = listOf(
            SlashCommand("نشر", "ابنِ المشروع وارفعه"),
            SlashCommand("اختبار", "شغّل الاختبارات"),
        )
        val flask = SaqrPackCodec.flaskSample()
        val (cmds, packs) = SaqrPackCodec.install(s.customCommands, emptyList(), flask, 999L)
        s.customCommands = cmds
        s.packsJson = SaqrPackCodec.encodePacks(packs)
        val pack = ThemePack("najd", "نجد", "Najd", "صحراء", true, com.ctodrs.saqrcode.ui.theme.PackColors(0xFF0A0B0D.toInt(), 0xFF111317.toInt(), 0xFF171A1F.toInt(), 0xFFEDEEF0.toInt(), 0xFFA4AAB3.toInt(), 0xFFE9B44C.toInt()))
        s.savedThemesJson = ThemeStudio.encodePacks(listOf(pack))
        s.customThemeJson = pack.toJson()
        s.themeMode = "custom"
        return s
    }

    // ------------------------------------------------- snapshot & whitelist

    @Test
    fun `snapshot captures settings themes commands and packs`() {
        val s = populated()
        val p = SyncKit.snapshot(s, "Pixel 9", "0.14.4", 1700000000000L)
        assertEquals(1, p.version)
        assertEquals("0.14.4", p.app)
        assertEquals("Pixel 9", p.device)
        assertEquals(1700000000000L, p.createdAt)
        assertEquals("17", p.settings["editor_font"])
        assertEquals("1", p.settings["editor_wrap"])
        assertEquals("team", p.settings["agent_mode"])
        assertEquals("12.5", p.settings["monthly_budget"])
        assertEquals("ar-SA", p.settings["voice_lang"])
        assertEquals("صقر", p.settings["git_name"])
        assertEquals("custom", p.themes["mode"])
        assertTrue(p.themes["saved"].orEmpty().contains("نجد"))
        assertEquals(5, com.ctodrs.saqrcode.agent.SlashCommands.decode(p.commandsJson).size) // 2 custom + 3 flask
        assertEquals(listOf("flask"), SaqrPackCodec.decodePacks(p.packsJson).map { it.id })
    }

    @Test
    fun `secrets never enter the snapshot or the container`() {
        val s = populated()
        s.saveProvider("openai", "https://api.openai.com/v1", "gpt-x", "sk-super-secret-123")
        s.setMcpAuth("خادم", "Bearer the-auth-header")
        s.setMoyasarSecret("sk_live_moyasar")
        s.accounts.saveGitHub("ghp_another_secret_token_1234567890", com.ctodrs.saqrcode.data.GitHubAccount(
            "ctodrs", null, null, 1L, "repo", false))
        val p = SyncKit.snapshot(s, "dev", "0.14.4", 1L)
        val json = SyncKit.toJson(p)
        val container = SyncKit.seal(p, PASS)
        for (secret in listOf("sk-super-secret-123", "the-auth-header", "sk_live_moyasar", "ghp_another_secret_token_1234567890")) {
            assertNull("leaked: $secret", json.indexOf(secret).takeIf { it >= 0 })
            assertNull("leaked in container: $secret", container.indexOf(secret).takeIf { it >= 0 })
        }
    }

    // ------------------------------------------------- payload codec

    @Test
    fun `payload round-trips through json`() {
        val p = SyncKit.snapshot(populated(), "هاتف", "0.14.4", 42L)
        val back = SyncKit.parse(SyncKit.toJson(p))!!
        assertEquals(p.version, back.version)
        assertEquals(p.device, back.device)
        assertEquals(p.createdAt, back.createdAt)
        assertEquals(p.settings, back.settings)
        assertEquals(p.themes, back.themes)
        assertEquals(p.commandsJson, back.commandsJson)
        assertEquals(p.packsJson, back.packsJson)
    }

    @Test
    fun `payload parse is strict`() {
        assertNull(SyncKit.parse(null))
        assertNull(SyncKit.parse(""))
        assertNull(SyncKit.parse("garbage {"))
        assertNull(SyncKit.parse("""{"saqrsync":2,"data":{"settings":{},"themes":{}}}"""))
        assertNull(SyncKit.parse("""{"saqrsync":1}"""))
        assertNull(SyncKit.parse("""{"saqrsync":1,"data":{"themes":{}}}"""))
        // Non-string values are dropped, not crashes.
        val p = SyncKit.parse("""{"saqrsync":1,"created":5,"data":{"settings":{"editor_font":17,"approve_all":"1"},"themes":{"mode":"dark"}}}""")!!
        assertEquals("1", p.settings["approve_all"])
        assertNull(p.settings["editor_font"])
    }

    // ------------------------------------------------- apply

    @Test
    fun `apply restores everything into a fresh store`() {
        val payload = SyncKit.snapshot(populated(), "الجهاز الأول", "0.14.4", 7L)
        val fresh = settings()
        val a = SyncKit.apply(payload, fresh)
        assertEquals(17, fresh.editorFontSize)
        assertEquals(true, fresh.editorWrap)
        assertEquals("team", fresh.agentMode)
        assertEquals(true, fresh.approveAll)
        assertEquals(12.5, fresh.monthlyBudget!!, 0.001)
        assertEquals("صقر", fresh.gitName)
        assertEquals("custom", fresh.themeMode)
        assertEquals(listOf("نجد"), ThemeStudio.decodePacks(fresh.savedThemesJson).map { it.name })
        assertEquals(5, fresh.customCommands.size)
        assertEquals(listOf("flask"), SaqrPackCodec.decodePacks(fresh.packsJson).map { it.id })
        // Honest counts.
        assertTrue(a.settings >= 12)
        assertEquals(1, a.themes)
        assertEquals(5, a.commands)
        assertEquals(1, a.packs)
    }

    @Test
    fun `apply is replace semantics`() {
        val payload = SyncKit.snapshot(populated(), "d", "0.14.4", 7L)
        val other = settings()
        other.customCommands = listOf(SlashCommand("قديم", "أمر قديم"))
        val (_, oldPacks) = SaqrPackCodec.install(
            other.customCommands, emptyList(),
            SaqrPackCodec.flaskSample().copy(id = "قديم-pak"), 5L)
        other.packsJson = SaqrPackCodec.encodePacks(oldPacks)
        SyncKit.apply(payload, other)
        // Only the snapshot's data survives — replace, not merge.
        assertEquals(
            setOf("نشر", "اختبار", "flask-run", "flask-test", "flask-route"),
            other.customCommands.map { it.name }.toSet(),
        )
        assertEquals(listOf("flask"), SaqrPackCodec.decodePacks(other.packsJson).map { it.id })
    }

    @Test
    fun `apply survives broken section values without dying`() {
        val json = SyncKit.toJson(SyncKit.snapshot(populated(), "d", "0.14.4", 7L))
        val o = JSONObject(json)
        o.getJSONObject("data").getJSONObject("settings").put("editor_font", "not-a-number")
        val fresh = settings()
        val a = SyncKit.apply(SyncKit.parse(o.toString())!!, fresh)
        // The broken key is skipped, the rest land.
        assertEquals(13, fresh.editorFontSize) // default untouched
        assertEquals("team", fresh.agentMode)
        assertTrue(a.settings >= 12)
    }

    // ------------------------------------------------- vault

    @Test
    fun `vault round-trips the same passphrase`() {
        val plain = SyncKit.toJson(SyncKit.snapshot(populated(), "جهاز", "0.14.4", 1L))
        val container = SyncKit.encrypt(plain, PASS)
        assertTrue(container.startsWith("SAQRSYNC1."))
        assertEquals(plain, SyncKit.decrypt(container, PASS))
        assertEquals(plain, SyncKit.decrypt(container + "\n", PASS)) // tolerate a trailing newline
        assertNotNull(SyncKit.open(container, PASS))
    }

    @Test
    fun `wrong passphrase is refused honestly`() {
        val container = SyncKit.encrypt("hello", "correct-horse-battery")
        var err: SyncException? = null
        try { SyncKit.decrypt(container, "wrong-passphrase!") } catch (e: SyncException) { err = e }
        assertNotNull(err)
        assertEquals("passphrase", err!!.reason)
    }

    @Test
    fun `tampered container is refused`() {
        val container = SyncKit.encrypt("hello world", "correct-horse-battery")
        // Flip one character in the MIDDLE of the blob (valid base64 — the GCM tag is what fails).
        val idx = container.length / 2
        val repl = if (container[idx] == 'A') 'B' else 'A'
        val flipped = container.substring(0, idx) + repl + container.substring(idx + 1)
        var err: SyncException? = null
        try { SyncKit.decrypt(flipped, "correct-horse-battery") } catch (e: SyncException) { err = e }
        assertNotNull(err)
    }

    @Test
    fun `container format is strict and the passphrase is checked first`() {
        for (bad in listOf("", "hello", "WRONG1.aGVsbG8", "SAQRSYNC1", "SAQRSYNC1.!!!", "SAQRSYNC1.aGVsbG8")) {
            var err: SyncException? = null
            try { SyncKit.decrypt(bad, "long-enough-pass") } catch (e: SyncException) { err = e }
            assertEquals("container", err?.reason)
        }
        var short: SyncException? = null
        try { SyncKit.encrypt("x", "1234567") } catch (e: SyncException) { short = e }
        assertEquals("short", short!!.reason)
        assertNull(SyncKit.passphraseProblem("12345678"))
    }

    // ------------------------------------------------- repo slug & paths

    @Test
    fun `repo slug handles every shape the user might type`() {
        assertEquals("CTO-DRS" to "saqr-sync", SyncKit.repoSlug("CTO-DRS/saqr-sync", "me"))
        assertEquals("CTO-DRS" to "saqr-sync", SyncKit.repoSlug("https://github.com/CTO-DRS/saqr-sync.git", "me"))
        assertEquals("CTO-DRS" to "saqr-sync", SyncKit.repoSlug("git@github.com:CTO-DRS/saqr-sync", "me"))
        assertEquals("ahmed" to "my-sync", SyncKit.repoSlug("my-sync", "ahmed"))
        assertEquals("ahmed" to "saqr-sync", SyncKit.repoSlug("", "ahmed")) // the default repo
        assertNull(SyncKit.repoSlug("https://gitlab.com/o/r", "me")) // sync is GitHub-only
        assertNull(SyncKit.repoSlug("two words", "me"))
        assertNull(SyncKit.repoSlug("my-sync", "")) // no account to own it
    }

    @Test
    fun `contents path targets the sync file`() {
        val path = SyncKit.contentsPath("CTO-DRS", "saqr-sync")
        assertTrue(path.startsWith("/repos/CTO-DRS/saqr-sync/contents/"))
        assertTrue(path.contains("saqr-sync.enc"))
    }

    // ------------------------------------------------- the wire

    @Test
    fun `push and pull travel the real wire`() {
        val server = MockWebServer()
        // headSha → 404 (first push, no sha yet)
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        // put → created
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"commit":{"sha":"abc123","html_url":"https://github.com/o/r/commit/abc123"}}"""))
        // get (pull) → the same container, base64 with GitHub's line breaks
        val container = SyncKit.seal(SyncKit.snapshot(populated(), "d1", "0.14.4", 11L), PASS)
        val lineBroken = java.util.Base64.getMimeEncoder(76, byteArrayOf('\n'.code.toByte()))
            .encodeToString(container.toByteArray(Charsets.UTF_8))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"sha":"blob1","content":${JSONObject.quote(lineBroken)}}"""))
        server.start()
        val client = SyncClient("ghp_wire_token_000000000000", baseUrl = server.url("/").toString())

        assertNull(client.headSha("o", "r"))
        val url = client.put("o", "r", container, "sync: encrypted Saqr snapshot", null)
        assertEquals("https://github.com/o/r/commit/abc123", url)
        assertEquals(container, client.get("o", "r"))

        // The token only ever went into the Authorization header.
        val headReq = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals("GET", headReq!!.method) // headSha
        val putReq = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals("Bearer ghp_wire_token_000000000000", putReq!!.getHeader("Authorization"))
        assertEquals("PUT", putReq.method)
        val body = putReq.body.readUtf8()
        assertTrue(body.contains("\"message\":\"sync: encrypted Saqr snapshot\""))
        assertTrue(body.contains("content"))
        val getReq = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals("Bearer ghp_wire_token_000000000000", getReq!!.getHeader("Authorization"))
        assertTrue(getReq.path!!.contains("/contents/"))
        server.shutdown()
    }

    @Test
    fun `pull names a missing repository with 404`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        server.start()
        val client = SyncClient("ghp_wire_token_000000000000", baseUrl = server.url("/").toString())
        var err: GhException? = null
        try { client.get("o", "missing") } catch (e: GhException) { err = e }
        assertNotNull(err)
        assertEquals(404, err!!.code)
        server.shutdown()
    }
}
