package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.data.ProjectRepository
import com.ctodrs.saqrcode.data.SaqrPack
import com.ctodrs.saqrcode.data.SaqrPackCodec
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.data.Template
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
import java.io.File
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
 * v0.14 «أجنحة مفتوحة · Skill packs»: the .saqrpack codec and validation, the file
 * materialization (template overlay + AGENTS.md rules + preferred tools), the Arabic-aware
 * command merge, install/remove semantics, persistence, and the ≤5s Flask acceptance.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V014FeaturesTest {

    private fun settings() = SettingsStore(
        RuntimeEnvironment.getApplication(), SecureStore(RuntimeEnvironment.getApplication()))

    // ------------------------------------------------- codec

    @Test
    fun `pack json round trip preserves every field`() {
        val p = SaqrPack(
            id = "demo", name = "Demo", nameAr = "عرض", description = "A demo pack",
            templateRef = "PYTHON",
            files = mapOf("app.py" to "print('hi')\n", "README.md" to "# demo\n"),
            commands = listOf(
                com.ctodrs.saqrcode.data.PackCommand("demo-run", "شغّل التطبيق {args}"),
                com.ctodrs.saqrcode.data.PackCommand("demo-test", "اختبر التطبيق"),
            ),
            rules = "- كود صغير فقط.",
            tools = listOf("python3", "pytest"),
            installedAt = 1234L,
        )
        val back = SaqrPackCodec.fromJson(SaqrPackCodec.toJson(p))
        assertNotNull(back)
        assertEquals(p, back)
    }

    @Test
    fun `pack from json rejects garbage wrong version and blank identity`() {
        assertNull(SaqrPackCodec.fromJson("not json at all"))
        assertNull(SaqrPackCodec.fromJson(null))
        assertNull(SaqrPackCodec.fromJson("""{"saqrpack":2,"id":"x","name":"X"}"""))
        assertNull(SaqrPackCodec.fromJson("""{"saqrpack":1,"id":"","name":"X"}"""))
        assertNull(SaqrPackCodec.fromJson("""{"saqrpack":1,"id":"x"}"""))
        assertNotNull(SaqrPackCodec.fromJson("""{"saqrpack":1,"id":"x","name":"X"}"""))
    }

    @Test
    fun `encode packs and decode packs round trip`() {
        val packs = listOf(SaqrPackCodec.flaskSample(), SaqrPack(id = "other", name = "Other"))
        val json = SaqrPackCodec.encodePacks(packs)
        val back = SaqrPackCodec.decodePacks(json)
        assertEquals(2, back.size)
        assertEquals(packs, back)
        assertEquals(emptyList<SaqrPack>(), SaqrPackCodec.decodePacks(""))
        assertEquals(emptyList<SaqrPack>(), SaqrPackCodec.decodePacks("junk"))
    }

    // ------------------------------------------------- validation

    @Test
    fun `validate accepts the flask sample`() = assertNull(SaqrPackCodec.validate(SaqrPackCodec.flaskSample()))

    @Test
    fun `validate rejects an unknown template ref without files`() {
        assertEquals("empty", SaqrPackCodec.validate(SaqrPack(id = "x", name = "X")))
        assertEquals(
            "template",
            SaqrPackCodec.validate(SaqrPack(id = "x", name = "X", templateRef = "SPRING", files = mapOf("a.txt" to "a"))),
        )
        assertNull(SaqrPackCodec.validate(SaqrPack(id = "x", name = "X", templateRef = "PYTHON")))
    }

    @Test
    fun `validate rejects unsafe paths oversize rules and too many commands`() {
        assertEquals(
            "path",
            SaqrPackCodec.validate(SaqrPack(id = "x", name = "X", files = mapOf("../evil.txt" to "x"))),
        )
        assertEquals(
            "path",
            SaqrPackCodec.validate(SaqrPack(id = "x", name = "X", files = mapOf("/etc/passwd" to "x"))),
        )
        assertEquals(
            "size",
            SaqrPackCodec.validate(SaqrPack(id = "x", name = "X", templateRef = "EMPTY", rules = "x".repeat(2001))),
        )
        val many = (1..13).map { com.ctodrs.saqrcode.data.PackCommand("cmd-$it", "t$it") }
        assertEquals(
            "size",
            SaqrPackCodec.validate(SaqrPack(id = "x", name = "X", templateRef = "EMPTY", commands = many)),
        )
        assertEquals(
            "command",
            SaqrPackCodec.validate(
                SaqrPack(id = "x", name = "X", templateRef = "EMPTY", commands = listOf(com.ctodrs.saqrcode.data.PackCommand("bad name!", "t"))),
            ),
        )
    }

    @Test
    fun `validate rejects builtin collisions and duplicates inside the pack`() {
        assertEquals(
            "duplicate",
            SaqrPackCodec.validate(
                SaqrPack(id = "x", name = "X", templateRef = "EMPTY", commands = listOf(com.ctodrs.saqrcode.data.PackCommand("fix", "my fix"))),
            ),
        )
        // the Arabic alias of /fix is protected too
        assertEquals(
            "duplicate",
            SaqrPackCodec.validate(
                SaqrPack(id = "x", name = "X", templateRef = "EMPTY", commands = listOf(com.ctodrs.saqrcode.data.PackCommand("اصلح", "my fix"))),
            ),
        )
        assertEquals(
            "duplicate",
            SaqrPackCodec.validate(
                SaqrPack(
                    id = "x", name = "X", templateRef = "EMPTY",
                    commands = listOf(
                        com.ctodrs.saqrcode.data.PackCommand("run", "a"),
                        com.ctodrs.saqrcode.data.PackCommand("run", "b"),
                    ),
                ),
            ),
        )
    }

    // ------------------------------------------------- project files

    @Test
    fun `pack files resolve the template overlay and merge rules and tools into agents md`() {
        val p = SaqrPackCodec.flaskSample()
        val files = SaqrPackCodec.packFiles(p, "Flask demo")
        // PYTHON template base + the pack's own files
        assertTrue(files.keys.containsAll(listOf("main.py", "app.py", "requirements.txt", "README.md", "AGENTS.md")))
        assertTrue(files["app.py"]!!.contains("from flask import Flask"))
        val agents = files["AGENTS.md"]!!
        assertTrue(agents.contains(Template.AGENTS_MD.trim()))
        assertTrue(agents.contains("# Pack rules · Flask"))
        assertTrue(agents.contains("Blueprints"))
        assertTrue(agents.contains("- Preferred tools: python3, pip3, pytest"))
    }

    @Test
    fun `pack files write an agents md when custom files lack one`() {
        val p = SaqrPack(id = "solo", name = "Solo", files = mapOf("app.py" to "print(1)\n"), rules = "- rule one")
        val files = SaqrPackCodec.packFiles(p, "Solo")
        assertEquals(setOf("app.py", "AGENTS.md"), files.keys)
        assertTrue(files["AGENTS.md"]!!.contains(Template.AGENTS_MD.trim()))
        assertTrue(files["AGENTS.md"]!!.contains("# Pack rules · Solo"))
    }

    @Test
    fun `a pack without a template ref builds from its own files only`() {
        val p = SaqrPack(id = "solo", name = "Solo", files = mapOf("index.html" to "<p>hi</p>"))
        val files = SaqrPackCodec.packFiles(p, "Solo")
        assertEquals(setOf("index.html", "AGENTS.md"), files.keys)
        assertFalse(files["AGENTS.md"]!!.contains("# Pack rules"))
    }

    // ------------------------------------------------- commands

    @Test
    fun `merge commands adds new upserts normalized names and never touches builtins`() {
        val existing = listOf(
            SlashCommand("deploy", "ارفع المشروع"),
            SlashCommand("تخصيص", "استخدم GetX دائمًا"),
        )
        val pack = SaqrPack(
            id = "p", name = "P",
            commands = listOf(
                com.ctodrs.saqrcode.data.PackCommand("deploy", "ارفع على السيرفر الجديد"),
                com.ctodrs.saqrcode.data.PackCommand("تخصيصات", "استخدم Riverpod"),
                com.ctodrs.saqrcode.data.PackCommand("brand-new", "أمر جديد"),
                com.ctodrs.saqrcode.data.PackCommand("fix", "تعديل مدمج"), // builtin — ignored
                com.ctodrs.saqrcode.data.PackCommand("run", "first"),
                com.ctodrs.saqrcode.data.PackCommand("run", "second"), // inside-pack dup — first wins
            ),
        )
        val merged = SaqrPackCodec.mergeCommands(existing, pack)
        val byKey = merged.associateBy { SlashCommands.normalize(it.name) }
        // upserted by Arabic-normalized key (تخصيص vs تخصيصات differ — both stay, deploy replaced)
        assertEquals("ارفع على السيرفر الجديد", byKey["deploy"]!!.template)
        assertEquals("استخدم GetX دائمًا", byKey[SlashCommands.normalize("تخصيص")]!!.template)
        assertEquals("أمر جديد", byKey["brand-new"]!!.template)
        assertEquals("first", byKey["run"]!!.template)
        assertFalse(merged.any { SlashCommands.normalize(it.name) == "fix" })
        assertEquals(5, merged.size) // deploy (replaced), تخصيص (kept), تخصيصات, brand-new, run
    }

    // ------------------------------------------------- install / remove

    @Test
    fun `install stamps and upserts by id and remove keeps commands`() {
        val pack = SaqrPackCodec.flaskSample()
        val (cmds1, packs1) = SaqrPackCodec.install(emptyList(), emptyList(), pack, 100L)
        assertEquals(3, cmds1.size)
        assertEquals(1, packs1.size)
        assertEquals(100L, packs1.first().installedAt)
        // reinstalling the same id replaces, never duplicates
        val (cmds2, packs2) = SaqrPackCodec.install(cmds1, packs1, pack.copy(description = "updated"), 200L)
        assertEquals(1, packs2.size)
        assertEquals("updated", packs2.first().description)
        assertEquals(200L, packs2.first().installedAt)
        assertEquals(3, cmds2.size) // commands upsert, not duplicate
        // uninstall keeps the commands the user already has
        val packs3 = SaqrPackCodec.remove(packs2, "flask")
        assertTrue(packs3.isEmpty())
    }

    // ------------------------------------------------- persistence

    @Test
    fun `packs persist through the settings store`() {
        val s = settings()
        assertEquals("", s.packsJson)
        assertEquals(emptyList<SaqrPack>(), SaqrPackCodec.decodePacks(s.packsJson))
        val (cmds, packs) = SaqrPackCodec.install(emptyList(), emptyList(), SaqrPackCodec.flaskSample(), 5L)
        s.customCommands = cmds
        s.packsJson = SaqrPackCodec.encodePacks(packs)
        val back = SaqrPackCodec.decodePacks(s.packsJson)
        assertEquals(listOf(SaqrPackCodec.flaskSample().copy(installedAt = 5L)), back)
        assertEquals(3, s.customCommands.size)
    }

    // ------------------------------------------------- the Flask acceptance: ≤ 5 seconds

    @Test
    fun `flask sample installs and creates a full project in under 5 seconds`() {
        val s = settings()
        val repo = ProjectRepository(RuntimeEnvironment.getApplication())
        val start = System.currentTimeMillis()
        val (cmds, packs) = SaqrPackCodec.install(s.customCommands, SaqrPackCodec.decodePacks(s.packsJson), SaqrPackCodec.flaskSample(), start)
        s.customCommands = cmds
        s.packsJson = SaqrPackCodec.encodePacks(packs)
        val project = repo.create("Flask demo", SaqrPackCodec.packFiles(packs.first(), "Flask demo")).getOrThrow()
        val elapsed = System.currentTimeMillis() - start
        assertTrue(File(project.dir, "app.py").isFile)
        assertTrue(File(project.dir, "requirements.txt").readText().contains("flask"))
        assertTrue(File(project.dir, "AGENTS.md").readText().contains("# Pack rules · Flask"))
        // ready commands are real slash commands the composer can parse and expand
        val inv = com.ctodrs.saqrcode.agent.SlashCommands.parse("/flask-run الآن", s.customCommands)
        assertNotNull(inv)
        assertTrue(inv!!.command.template.contains("pip3 install"))
        assertTrue("one-tap install must stay under 5s (took ${elapsed}ms)", elapsed < 5000)
    }

    // ------------------------------------------------- repository

    @Test
    fun `project repository creates from a files map and refuses duplicates`() {
        val repo = ProjectRepository(RuntimeEnvironment.getApplication())
        val p = repo.create("Pack project", mapOf("README.md" to "# hello\n", "src/App.kt" to "class App\n")).getOrThrow()
        assertEquals("Pack-project", p.name)
        assertTrue(File(p.dir, "src/App.kt").isFile)
        val again = repo.create("Pack project", mapOf("README.md" to "# hello\n"))
        assertTrue(again.isFailure)
    }
}
