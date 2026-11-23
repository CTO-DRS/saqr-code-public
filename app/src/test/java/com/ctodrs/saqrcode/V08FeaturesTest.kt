package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.Mcp
import com.ctodrs.saqrcode.agent.Planner
import com.ctodrs.saqrcode.agent.Reviewer
import com.ctodrs.saqrcode.data.Template
import com.ctodrs.saqrcode.data.TemplateFiles
import com.ctodrs.saqrcode.ondevice.Downloader
import com.ctodrs.saqrcode.ondevice.Elf
import com.ctodrs.saqrcode.ondevice.Hints
import com.ctodrs.saqrcode.ondevice.LogcatStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v0.8 · planner stage, MCP client, pinned downloader, build hardening, crash monitor, lite template. */
class V08FeaturesTest {

    // ------------------------------------------------------------ planner

    @Test
    fun `planner parses a JSON plan with tasks`() {
        val p = Planner.parse("""{"tasks":[{"title":"Read the files"},{"title":"Implement"},{"title":"Verify"}]}""")
        assertNotNull(p)
        assertEquals(listOf("Read the files", "Implement", "Verify"), p!!.tasks)
        assertTrue(p.valid)
    }

    @Test
    fun `planner tolerates fences and prose around the JSON`() {
        val p = Planner.parse("Here you go:\n```json\n{\"tasks\":[{\"title\":\"اكتشف الملفات\"}]}\n```\nHope it helps.")
        assertNotNull(p)
        assertEquals(listOf("اكتشف الملفات"), p!!.tasks)
    }

    @Test
    fun `planner caps tasks at six`() {
        val titles = (1..9).joinToString(",") { """{"title":"Task $it"}""" }
        val p = Planner.parse("""{"tasks":[$titles]}""")
        assertEquals(Planner.MAX_TASKS, p!!.tasks.size)
    }

    @Test
    fun `planner rejects empty or malformed plans`() {
        assertNull(Planner.parse("no json here"))
        assertNull(Planner.parse("""{"tasks":[]}"""))
        assertNull(Planner.parse("""{"tasks":[{"title":""}]}"""))
    }

    @Test
    fun `worthPlanning skips trivial or slash requests`() {
        assertFalse(Planner.worthPlanning(""))
        assertFalse(Planner.worthPlanning("/fix the header"))
        assertFalse(Planner.worthPlanning("hi"))
        assertTrue(Planner.worthPlanning("Build a small notes app with a list screen, add/delete, and Arabic labels"))
        assertTrue(Planner.worthPlanning("سوّ لي تطبيق قائمة مهام فيه إضافة وحذف وتخزين محلي"))
    }

    @Test
    fun `plan message and plan items render`() {
        val plan = Planner.parse("""{"tasks":[{"title":"A"},{"title":"B"}]}""")!!
        val msg = Planner.planMessage(plan)
        assertTrue(msg.startsWith("[Planner]"))
        assertTrue(msg.contains("1. A"))
        assertTrue(msg.contains("2. B"))
        val items = Planner.toPlanItems(plan)
        assertEquals(2, items.size)
        assertTrue(items.all { !it.done && !it.active })
    }

    // ------------------------------------------------------------ MCP

    @Test
    fun `mcp tool names are sanitised and deterministic`() {
        assertEquals("mcp_myserver_weather_lookup", Mcp.toolName("My Server", "weather.lookup"))
        assertEquals("mcp_ctx7_resolve_library_id", Mcp.toolName("ctx7", "resolve-library-id"))
        assertEquals(Mcp.toolName("s1", "a b"), Mcp.toolName("s1", "a_b"))
        assertEquals(Mcp.toolName("s1", "x"), Mcp.toolName("S1", "x"))
        assertTrue(Mcp.toolName("s", "x").startsWith(Mcp.PREFIX))
    }

    @Test
    fun `splitToolName round-trips server and tool`() {
        val server = Mcp.Server("My Server", "https://mcp.example.com/mcp")
        val full = Mcp.toolName(server.slug, "weather_lookup")
        val (slug, tool) = Mcp.splitToolName(full)!!
        assertEquals(server.slug, slug)
        assertEquals("weather_lookup", tool)
        assertNull(Mcp.splitToolName("read_file"))
        assertNull(Mcp.splitToolName("mcp_nounderscore"))
    }

    @Test
    fun `mcp parseToolsList maps server tools with the prefix`() {
        val result = JSONObject("""{"tools":[
            {"name":"get_weather","description":"Weather lookup","inputSchema":{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}},
            {"name":""},
            {"name":"docs","description":""}
        ]}""")
        val tools = Mcp.parseToolsList("srv", result)
        assertEquals(2, tools.size)
        assertEquals("mcp_srv_get_weather", tools[0].name)
        assertTrue(tools[0].description.contains("Weather lookup"))
        assertEquals("mcp_srv_docs", tools[1].name)
        assertTrue(tools[1].description.isNotBlank()) // fallback description
        assertEquals("object", tools[0].inputSchema.optString("type"))
    }

    @Test
    fun `mcp sse and json responses are both understood`() {
        val json = Mcp.parseResponse("application/json", """{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}""")
        assertNotNull(json)
        val sse = Mcp.parseResponse("text/event-stream",
            "event: message\r\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}\r\n\r\nevent: ping\r\ndata: {}\r\n")
        assertNotNull(sse)
        assertTrue(sse!!.has("result"))
        assertNull(Mcp.parseResponse("text/html", "<html>not json</html>"))
    }

    @Test
    fun `mcp call results render text content and errors`() {
        val ok = Mcp.renderCallResult(JSONObject("""{"content":[{"type":"text","text":"hello"},{"type":"text","text":"world"}]}"""))
        assertEquals("hello\nworld", ok)
        val err = Mcp.renderCallResult(JSONObject("""{"isError":true,"content":[{"type":"text","text":"boom"}]}"""))
        assertTrue(err.startsWith("Error: boom"))
        val empty = Mcp.renderCallResult(JSONObject("""{"content":[]}"""))
        assertEquals("(the tool returned no content)", empty)
    }

    @Test
    fun `mcp jsonrpc errors are surfaced`() {
        assertEquals("MCP error -32001: denied", Mcp.errorOf(JSONObject("""{"error":{"code":-32001,"message":"denied"}}""")))
        assertNull(Mcp.errorOf(JSONObject("""{"result":{}}""")))
    }

    @Test
    fun `mcp config encode-decode round trip`() {
        val servers = listOf(Mcp.Server("Context 7", "https://mcp.example.com/mcp"), Mcp.Server("x", "https://x.example/"))
        val back = Mcp.decodeConfig(Mcp.encodeConfig(servers))
        assertEquals(2, back.size)
        assertEquals("Context 7", back[0].name)
        assertEquals("https://mcp.example.com/mcp", back[0].url)
        assertEquals(emptyList<Mcp.Server>(), Mcp.decodeConfig(null))
        assertEquals(emptyList<Mcp.Server>(), Mcp.decodeConfig("not json"))
    }

    @Test
    fun `mcp describeSchema lists required and optional args`() {
        val d = Mcp.describeSchema(JSONObject("""{"type":"object","properties":{"city":{"type":"string","description":"City name"},"n":{"type":"integer"}},"required":["city"]}"""))
        assertTrue(d.contains("city (string, required)"))
        assertTrue(d.contains("n (integer, optional)"))
        assertEquals("no arguments", Mcp.describeSchema(JSONObject()))
    }

    @Test
    fun `initialize and tools_call bodies follow jsonrpc`() {
        val init = Mcp.initializeRequest()
        assertEquals("2.0", init.optString("jsonrpc"))
        assertEquals("initialize", init.optString("method"))
        assertTrue(init.getJSONObject("params").has("protocolVersion"))
        val call = Mcp.toolsCallRequest("t", JSONObject("""{"a":1}"""))
        assertEquals("tools/call", call.optString("method"))
        assertEquals("t", call.getJSONObject("params").optString("name"))
    }

    // ------------------------------------------------------------ downloader / hardening

    @Test
    fun `artifact validity requires hex pin and urls`() {
        assertTrue(Downloader.Artifact("a", "0".repeat(64), listOf("https://x/y")).valid())
        assertFalse(Downloader.Artifact("a", "zz".repeat(32), listOf("https://x/y")).valid())
        assertFalse(Downloader.Artifact("a", "0".repeat(63), listOf("https://x/y")).valid())
        assertFalse(Downloader.Artifact("a", "0".repeat(64), emptyList()).valid())
    }

    @Test
    fun `sha256Hex matches known vectors`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Downloader.sha256Hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Downloader.sha256Hex("abc"))
        val f = File.createTempFile("sha", ".bin").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Downloader.sha256Hex(f))
        f.delete()
    }

    @Test
    fun `rangeStart parses content-range headers`() {
        assertEquals(12345L, Downloader.rangeStart("bytes 12345-99999/200000"))
        assertEquals(0L, Downloader.rangeStart("bytes 0-99/100"))
        assertNull(Downloader.rangeStart(null))
        assertNull(Downloader.rangeStart("nonsense"))
    }

    @Test
    fun `built-in artifacts carry valid pins`() {
        listOf(
            com.ctodrs.saqrcode.ondevice.OnDeviceBuild.ART_ROOTFS,
            com.ctodrs.saqrcode.ondevice.OnDeviceBuild.ART_CMDTOOLS,
            com.ctodrs.saqrcode.ondevice.OnDeviceBuild.ART_GRADLE,
        ).forEach {
            assertTrue("pin of ${it.name}", it.valid())
            assertTrue("mirror list of ${it.name}", it.urls.isNotEmpty())
        }
    }

    @Test
    fun `elf check accepts aarch64 and rejects others`() {
        val arm64 = ByteArray(20).also {
            it[0] = 0x7F.toByte(); it[1] = 'E'.code.toByte(); it[2] = 'L'.code.toByte(); it[3] = 'F'.code.toByte()
            it[4] = 2 // ELFCLASS64
            it[18] = 0xB7.toByte() // EM_AARCH64 little-endian
        }
        assertTrue(Elf.isArm64(arm64))
        val x86 = arm64.copyOf().also { it[18] = 0x3E.toByte() } // EM_X86_64
        assertFalse(Elf.isArm64(x86))
        assertFalse(Elf.isArm64(ByteArray(10)))
        assertFalse(Elf.isArm64("#!/bin/sh\n".toByteArray()))
        assertFalse(Elf.isArm64(File("/nonexistent-elf"))) // missing file
    }

    @Test
    fun `hints map failure classes to actionable text`() {
        assertTrue(Hints.forMessage("java.lang.OutOfMemoryError: Cannot allocate memory")!!.contains("Lite Web"))
        assertTrue(Hints.forMessage("Unable to resolve host cdimage.ubuntu.com")!!.contains("Network"))
        assertTrue(Hints.forMessage("checksum mismatch for gradle.zip")!!.contains("Network"))
        assertTrue(Hints.forMessage("proot tracee died unexpectedly")!!.contains("proot"))
        assertTrue(Hints.forMessage("No space left on device")!!.contains("Storage"))
        assertTrue(Hints.forMessage("gradle exited 1")!!.contains("project code"))
        assertTrue(Hints.forMessage("aapt2 fetched but not a valid arm64 ELF")!!.contains("mirror"))
        assertNull(Hints.forMessage("mysterious failure"))
        assertNull(Hints.forMessage(null))
    }

    // ------------------------------------------------------------ crash monitor

    @Test
    fun `crashes parses fatal blocks of the built package`() {
        val log = """
            10-1 12:00:00.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main
            10-1 12:00:00.000  1234  1234 E AndroidRuntime: Process: com.saqr.demo, PID 1234
            10-1 12:00:00.000  1234  1234 E AndroidRuntime: java.lang.NullPointerException: config is null
            10-1 12:00:00.000  1234  1234 E AndroidRuntime:     at com.saqr.demo.MainActivity.onCreate(MainActivity.kt:21)
            10-1 12:00:00.000  9999  9999 E AndroidRuntime: FATAL EXCEPTION: other
            10-1 12:00:00.000  9999  9999 E AndroidRuntime: Process: com.other.app, PID 9999
            10-1 12:00:00.000  9999  9999 E AndroidRuntime: java.lang.IllegalStateException: boom
        """.trimIndent()
        val crashes = LogcatStream.crashes(log, "com.saqr.demo")
        assertEquals(1, crashes.size)
        assertEquals("com.saqr.demo", crashes[0].packageName)
        assertEquals("java.lang.NullPointerException", crashes[0].exception)
        assertEquals("config is null", crashes[0].message)
        assertTrue(crashes[0].firstFrame.contains("MainActivity.kt:21"))
        val lines = LogcatStream.summarize(crashes)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("NullPointerException"))
    }

    @Test
    fun `crashes returns everything when no package filter`() {
        val log = """
            E AndroidRuntime: FATAL EXCEPTION: main
            E AndroidRuntime: Process: com.x.y, PID 1
            E AndroidRuntime: java.lang.RuntimeException: a
            E AndroidRuntime: FATAL EXCEPTION: main
            E AndroidRuntime: Process: com.a.b, PID 2
            E AndroidRuntime: java.lang.IllegalStateException: b
        """.trimIndent()
        assertEquals(2, LogcatStream.crashes(log, "").size)
    }

    @Test
    fun `guessPackage reads applicationId from gradle`() {
        val g = """
            android {
                defaultConfig {
                    applicationId = "com.saqr.notes"
                }
            }
        """.trimIndent()
        assertEquals("com.saqr.notes", LogcatStream.guessPackage(g))
        assertEquals("", LogcatStream.guessPackage("nothing here"))
    }

    // ------------------------------------------------------------ lite template

    @Test
    fun `lite web template is zero-dependency and complete`() {
        val files = TemplateFiles.liteWeb("ملاحظاتي")
        // buildable set: gradle files, manifest, one activity, the page
        listOf(
            "settings.gradle.kts", "build.gradle.kts", "gradle.properties",
            "app/build.gradle.kts", "app/src/main/AndroidManifest.xml", "app/src/main/assets/index.html",
        ).forEach { p -> assertTrue("missing $p", files.containsKey(p)) }
        assertTrue(files.keys.any { it.endsWith("MainActivity.kt") })
        val app = files["app/build.gradle.kts"]!!
        assertFalse("no compose plugin", app.contains("kotlin.plugin.compose"))
        assertFalse("no androidx deps", app.contains("androidx."))
        assertTrue(files["gradle.properties"]!!.contains("android.useAndroidX=false"))
        val main = files.entries.first { it.key.endsWith("MainActivity.kt") }.value
        assertTrue(main.contains("android.app.Activity")) // plain Activity, no AndroidX
        assertTrue(main.contains("WebView"))
        val page = files["app/src/main/assets/index.html"]!!
        assertTrue(page.contains("dir=\"rtl\""))
        assertTrue(page.contains("ملاحظاتي"))
        assertTrue(files["app/src/main/AndroidManifest.xml"]!!.contains("android.intent.action.MAIN"))
    }

    @Test
    fun `lite template fills the package name`() {
        val files = TemplateFiles.liteWeb("My Notes")
        val app = files["app/build.gradle.kts"]!!
        assertTrue(app.contains("com.saqr.mynotes"))
    }

    @Test
    fun `all templates expose a files map`() {
        Template.entries.forEach { t ->
            val files = t.files("demo")
            assertTrue("template $t empty", files.isNotEmpty())
        }
    }

    // ------------------------------------------------------------ reviewer checks section

    @Test
    fun `checksSection skips empty and no-runner outputs`() {
        assertNull(Reviewer.checksSection(emptyList()))
        assertNull(Reviewer.checksSection(listOf("lint" to "", "tests" to "No test runner detected (no package.json test script)")))
        val s = Reviewer.checksSection(listOf("lint" to "app.js:3: missing semicolon", "tests" to "2 passed"))
        assertNotNull(s)
        assertTrue(s!!.contains("### lint"))
        assertTrue(s.contains("app.js:3"))
        assertTrue(s.contains("### tests"))
    }

    @Test
    fun `buildRequest embeds the checks section`() {
        val req = Reviewer.buildRequest("add a button", "--- a/Main.kt", "### lint\nok")
        assertTrue(req.contains("## Automatic checks"))
        assertTrue(req.contains("### lint"))
        val plain = Reviewer.buildRequest("add a button", "--- a/Main.kt", null)
        assertFalse(plain.contains("Automatic checks"))
    }
}
