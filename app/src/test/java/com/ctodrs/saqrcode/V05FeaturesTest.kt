package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.AnthropicFormat
import com.ctodrs.saqrcode.agent.ChainNode
import com.ctodrs.saqrcode.agent.ElementContext
import com.ctodrs.saqrcode.agent.GeminiFormat
import com.ctodrs.saqrcode.agent.HtmlChain
import com.ctodrs.saqrcode.agent.ImageMath
import com.ctodrs.saqrcode.agent.ImagePart
import com.ctodrs.saqrcode.agent.ImagePruner
import com.ctodrs.saqrcode.agent.LlmClient
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.OpenAiFormat
import com.ctodrs.saqrcode.agent.PickedElement
import com.ctodrs.saqrcode.agent.PickerJs
import com.ctodrs.saqrcode.agent.PickerSecurity
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.Selector
import com.ctodrs.saqrcode.agent.SourceLocator
import com.ctodrs.saqrcode.agent.Stack
import com.ctodrs.saqrcode.agent.StackDetector
import com.ctodrs.saqrcode.agent.StreamListener
import com.ctodrs.saqrcode.agent.ToolCall
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.agent.Vision
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V05FeaturesTest {

    // ------------------------------------------------------------------ selector builder

    private val sample = """<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8">
  <title>مقهى <b>سراج</b></title>
  <style>.card > p { color: red; } /* <div class="fake"> */</style>
</head>
<body>
  <header id="top"><h1 class="brand">مقهى سراج</h1><nav><a href="#">A</a><a href="#">B</a><a href="#" class="cta">C</a></nav></header>
  <main>
    <section class="cards grid">
      <div class="card"><p>One</p><img src="1.png"><button class="btn">Buy</button></div>
      <div class="card featured hot"><p>Two</p><br/><button class="btn primary" type="button">اطلب الآن</button></div>
      <div class="card"><p>Three</p><!-- <button>old</button> --><button class="btn">Buy</button></div>
    </section>
    <form><input name="q"><input name="z" id="search box"></form>
  </main>
  <script>if (a < b && "</div>") { document.write("<p>x</p>") }</script>
</body>
</html>
"""

    private fun nodeOf(root: HtmlChain.Node, pred: (HtmlChain.Node) -> Boolean) = HtmlChain.walk(root).first(pred)

    @Test fun selectorUsesIdWhenPresent() {
        assertEquals("#top", Selector.build(listOf(ChainNode("header", "top"), ChainNode("body"))))
        // an ancestor's id anchors the path
        assertEquals("#top > h1.brand", Selector.build(listOf(ChainNode("h1", classes = listOf("brand")), ChainNode("header", "top"), ChainNode("body"))))
        // ids that aren't CSS identifiers are quoted
        assertEquals("[id=\"search box\"]", Selector.idSelector("search box"))
        assertEquals("[id=\"1st\"]", Selector.idSelector("1st"))
    }

    @Test fun selectorBuildsNthOfTypePathOnSampleHtml() {
        val root = HtmlChain.parse(sample)
        val buy2 = nodeOf(root) { it.tag == "button" && "primary" in it.classes }
        assertEquals("body > main > section.cards.grid > div.card.featured:nth-of-type(2) > button.btn.primary", Selector.build(HtmlChain.chainOf(buy2)))
        val third = nodeOf(root) { it.tag == "a" && "cta" in it.classes }
        assertEquals("#top > nav > a.cta:nth-of-type(3)", Selector.build(HtmlChain.chainOf(third)))
        val input = nodeOf(root) { it.attrs["name"] == "z" }
        assertEquals("[id=\"search box\"]", Selector.build(HtmlChain.chainOf(input)))
    }

    @Test fun selectorRoundTripsForEveryElementInSample() {
        val root = HtmlChain.parse(sample)
        val body = nodeOf(root) { it.tag == "body" }
        val all = HtmlChain.walk(body).toList()
        assertTrue(all.size > 15)
        for (n in all) {
            val sel = Selector.build(HtmlChain.chainOf(n))
            assertSame("selector $sel", n, HtmlChain.select(root, sel))
        }
    }

    @Test fun selectorDepthIsCappedAtSix() {
        val chain = (1..10).map { ChainNode("div", classes = listOf("l$it")) }
        val sel = Selector.build(chain)
        assertEquals(6, sel.split(" > ").size)
        assertTrue(sel.endsWith("div.l1"))
        // internal overlay classes and invalid identifiers are skipped; at most two classes
        assertEquals("p.a.b", Selector.build(listOf(ChainNode("p", classes = listOf("__saqr_box", "a", "2x", "b", "c")))))
    }

    @Test fun miniHtmlParserHandlesRawTextVoidAndLines() {
        val root = HtmlChain.parse(sample)
        val tags = HtmlChain.walk(root).map { it.tag }.toList()
        assertEquals("comment content is not a node", 3, tags.count { it == "button" })
        assertFalse("<div class=fake> inside <style> is text", HtmlChain.walk(root).any { "fake" in it.classes })
        assertEquals(3, HtmlChain.walk(root).count { it.tag == "p" })
        val img = nodeOf(root) { it.tag == "img" }
        assertTrue("img is void", img.children.isEmpty())
        assertEquals(13, nodeOf(root) { "featured" in it.classes }.line)
        assertEquals("button", img.parent!!.children.last().tag)
    }

    // ------------------------------------------------------------------ picked element payload + security

    private fun tmpProject(): File = File(System.getProperty("java.io.tmpdir"), "saqr-v05-" + System.nanoTime()).apply { mkdirs() }

    private fun payload(url: String, html: String = "<button class=\"btn primary\" type=\"button\">اطلب الآن</button>", text: String = "اطلب الآن") = JSONObject()
        .put("chain", JSONArray().put(JSONObject().put("tag", "BUTTON").put("cls", JSONArray().put("btn").put("primary")).put("nth", 1).put("count", 1))
            .put(JSONObject().put("tag", "div").put("cls", JSONArray().put("card")).put("nth", 2).put("count", 3))
            .put(JSONObject().put("tag", "body")))
        .put("html", html).put("text", text)
        .put("styles", JSONObject().put("color", "rgb(26, 20, 6)").put("background", "rgb(233, 180, 76)").put("font-size", "16px").put("margin", "").put("display", "block").put("evil", "x"))
        .put("rect", JSONObject().put("x", 12.5).put("y", 80).put("w", 200).put("h", 44)).put("vw", 411).put("url", url).toString()

    @Test fun pickedElementParsesAndTruncates() {
        val dir = tmpProject()
        File(dir, "pages").mkdirs()
        val el = PickedElement.parse(payload("file://" + File(dir, "pages/index.html").path, html = "<div>" + "x".repeat(5000) + "</div>", text = "y".repeat(900)), dir)!!
        assertEquals("body > div.card:nth-of-type(2) > button.btn.primary", el.selector)
        assertEquals("button", el.tag)
        assertEquals(PickedElement.MAX_HTML, el.outerHtml.length)
        assertEquals(PickedElement.MAX_TEXT, el.text.length)
        assertEquals(setOf("color", "background", "font-size", "display"), el.styles.keys)
        assertEquals("pages/index.html", el.file)
        assertEquals(411.0, el.viewportWidth, 0.0)
        assertNull(PickedElement.parse("{\"chain\":[]}", dir))
        assertNull(PickedElement.parse("not json", dir))
    }

    @Test fun bridgeOnlyForProjectFilesAndLocalhost() {
        val dir = tmpProject()
        assertTrue(PickerSecurity.isLocal("file://" + File(dir, "index.html").path, dir))
        assertTrue(PickerSecurity.isLocal("file://" + File(dir, "a/b c.html").path.replace(" ", "%20"), dir))
        assertFalse(PickerSecurity.isLocal("file://" + dir.path + "/../other/index.html", dir))
        assertFalse(PickerSecurity.isLocal("file://" + dir.path + "-evil/index.html", dir))
        assertFalse(PickerSecurity.isLocal("file:///etc/passwd", dir))
        assertTrue(PickerSecurity.isLocal("http://localhost:5173/", dir))
        assertTrue(PickerSecurity.isLocal("http://127.0.0.1:8080/app", dir))
        assertTrue(PickerSecurity.isLocal("http://[::1]:3000/", dir))
        assertFalse(PickerSecurity.isLocal("https://localhost.evil.com/", dir))
        assertFalse(PickerSecurity.isLocal("https://example.com/", dir))
        assertFalse(PickerSecurity.isLocal("javascript:alert(1)", dir))
        assertFalse(PickerSecurity.isLocal("about:blank", dir))
        assertFalse(PickerSecurity.isLocal(null, dir))
        assertEquals("index.html", PickerSecurity.relativeFile("http://localhost:5173/", dir))
        assertNull(PickerSecurity.relativeFile("https://example.com/x.html", dir))
    }

    @Test fun pickerScriptCarriesSanitizedToken() {
        val js = PickerJs.toggle(true, "ab-12';alert(1);'")
        assertTrue(js.contains("enable(true, 'ab-12alert1')"))
        assertTrue(js.contains("window.SaqrPicker.onPick(tok, payload(el))"))
        assertTrue(js.contains("#E9B44C"))
    }

    // ------------------------------------------------------------------ source matching

    private fun el(selector: String, html: String, text: String = "", file: String? = "index.html") =
        PickedElement(selector, html, text, emptyMap(), 0.0, 0.0, 10.0, 10.0, 400.0, "file:///x/$file", file, "div")

    @Test fun sourceLocatorFindsByIdSnippetTextClassAndSelector() {
        val dir = tmpProject()
        File(dir, "index.html").writeText(sample)
        File(dir, "src").mkdirs()
        File(dir, "src/App.jsx").writeText("export default () => (\n  <div>\n    <section id={\"hero\"} className=\"hero big\">\n      <h2>Hello hero</h2>\n    </section>\n  </div>\n)\n")
        File(dir, "node_modules/x").mkdirs()
        File(dir, "node_modules/x/index.html").writeText("<header id=\"top\"></header>")

        assertEquals("index.html:9 (id)", SourceLocator.locate(dir, el("#top", "<header id=\"top\"><h1>…"))!!.let { "$it (${it.how})" })
        assertEquals("src/App.jsx:3", SourceLocator.locate(dir, el("#hero", "<section id=\"hero\" class=\"hero big\">…</section>"))!!.toString())
        // rendered opening tag appears verbatim in source
        val snip = SourceLocator.locate(dir, el("x", "<button class=\"btn primary\" type=\"button\">اطلب الآن</button>"))!!
        assertEquals("index.html:13" to "snippet", snip.toString() to snip.how)
        // text (the opening tag differs from the source)
        val byText = SourceLocator.locate(dir, el("x", "<h2 data-v=\"1\">Hello hero</h2>", text = "Hello hero"))!!
        assertEquals("src/App.jsx:4" to "text", byText.toString() to byText.how)
        // class list (JSX className)
        val byClass = SourceLocator.locate(dir, el("x", "<section class=\"hero big\" data-x=\"1\"></section>"))!!
        assertEquals("src/App.jsx:3" to "class", byClass.toString() to byClass.how)
        // selector walk through the page when nothing textual matches
        val bySel = SourceLocator.locate(dir, el("body > main > form > input", "<input name=\"q\" data-generated=\"1\">"))!!
        assertEquals("index.html:16" to "selector", bySel.toString() to bySel.how)
        assertNull(SourceLocator.locate(dir, el("x", "<span data-only=\"runtime\"></span>")))
    }

    @Test fun elementContextBlock() {
        val e = PickedElement.parse(payload("file:///p/index.html"), File("/p"))!!
        val ctx = ElementContext.build(e, com.ctodrs.saqrcode.agent.SourceMatch("index.html", 13, "snippet"), "اجعله أحمر")
        assertTrue(ctx.contains("source (best match): index.html:13 (by snippet)"))
        assertTrue(ctx.contains("selector: body > div.card:nth-of-type(2) > button.btn.primary"))
        assertTrue(ctx.contains("computed styles: color: rgb(26, 20, 6)"))
        assertTrue(ctx.contains("```html\n<button class=\"btn primary\""))
        assertTrue(ctx.contains("Instruction: اجعله أحمر"))
        assertTrue(ElementContext.build(e, null, "x").contains("not found"))
        assertEquals("🎯 body > div.card:nth-of-type(2) > button.btn.primary\nاجعله أحمر", ElementContext.display(e, " اجعله أحمر "))
    }

    // ------------------------------------------------------------------ multimodal requests

    private val img = ImagePart("QUJD", "image/jpeg")

    @Test fun openAiMultimodalContentParts() {
        val body = OpenAiFormat.buildRequest("gpt-4o", "sys", listOf(Msg.User("hi"), Msg.User("recreate this", listOf(img))), emptyList())
        val msgs = body.getJSONArray("messages")
        assertEquals("hi", msgs.getJSONObject(1).getString("content")) // text-only stays a string
        val parts = msgs.getJSONObject(2).getJSONArray("content")
        assertEquals("text", parts.getJSONObject(0).getString("type"))
        assertEquals("recreate this", parts.getJSONObject(0).getString("text"))
        assertEquals("image_url", parts.getJSONObject(1).getString("type"))
        assertEquals("data:image/jpeg;base64,QUJD", parts.getJSONObject(1).getJSONObject("image_url").getString("url"))
    }

    @Test fun anthropicImageBlocks() {
        val body = AnthropicFormat.buildRequest("claude-sonnet-4-5", "sys", listOf(
            Msg.User("look", listOf(img, ImagePart("WFla", "image/png"))),
        ), emptyList())
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(3, content.length())
        val src = content.getJSONObject(0).getJSONObject("source")
        assertEquals("image", content.getJSONObject(0).getString("type"))
        assertEquals("base64", src.getString("type"))
        assertEquals("image/jpeg", src.getString("media_type"))
        assertEquals("QUJD", src.getString("data"))
        assertEquals("image/png", content.getJSONObject(1).getJSONObject("source").getString("media_type"))
        assertEquals("text", content.getJSONObject(2).getString("type"))
    }

    @Test fun geminiInlineData() {
        val body = GeminiFormat.buildRequest("sys", listOf(
            Msg.User("look", listOf(img)),
            Msg.Assistant("", listOf(ToolCall("c1", "read_file", "{\"path\":\"a\"}"))),
            Msg.ToolResult("c1", "read_file", "ok"),
        ), Tools.specs)
        assertEquals("sys", body.getJSONObject("system_instruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val c = body.getJSONArray("contents")
        val parts = c.getJSONObject(0).getJSONArray("parts")
        assertEquals("user", c.getJSONObject(0).getString("role"))
        assertEquals("look", parts.getJSONObject(0).getString("text"))
        val inline = parts.getJSONObject(1).getJSONObject("inline_data")
        assertEquals("image/jpeg", inline.getString("mime_type"))
        assertEquals("QUJD", inline.getString("data"))
        assertEquals("model", c.getJSONObject(1).getString("role"))
        assertEquals("read_file", c.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getJSONObject("functionCall").getString("name"))
        assertTrue(body.getJSONArray("tools").getJSONObject(0).getJSONArray("function_declarations").length() > 3)
    }

    @Test fun openRouterRequestCarriesImageOverTheWire() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"))
        server.start()
        try {
            val client = LlmClient(Providers.byId("openrouter"), server.url("/").toString().trimEnd('/'), "openai/gpt-4o-mini", "k")
            val turn = client.complete("sys", listOf(Msg.User("x", listOf(img))), emptyList(), object : StreamListener { override fun onText(delta: String) {} })
            assertEquals("ok", turn.text)
            val sent = JSONObject(server.takeRequest().body.readUtf8())
            val parts = sent.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
            assertEquals("data:image/jpeg;base64,QUJD", parts.getJSONObject(1).getJSONObject("image_url").getString("url"))
        } finally { server.shutdown() }
    }

    // ------------------------------------------------------------------ vision heuristic

    @Test fun visionHeuristic() {
        val yes = listOf("gpt-4o", "gpt-4o-mini", "openai/gpt-4o-mini", "gpt-4.1-mini", "gpt-5", "gpt-5-mini", "o3", "o4-mini", "o1",
            "claude-3-5-sonnet-latest", "claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5", "anthropic/claude-sonnet-4.5",
            "gemini-2.5-flash", "google/gemini-2.5-pro", "meta-llama/llama-3.2-11b-vision-instruct", "llama-4-maverick",
            "qwen/qwen2.5-vl-72b-instruct", "Qwen-VL-Max", "pixtral-12b", "grok-2-vision", "openai/gpt-4o:free")
        val no = listOf("deepseek-chat", "deepseek-reasoner", "deepseek/deepseek-chat", "qwen/qwen3-coder", "o1-mini", "o3-mini",
            "gpt-3.5-turbo", "llama-3.1-70b-instruct", "mistral-large", "text-embedding-3-small", "gpt-4o-audio-preview", "", "  ")
        yes.forEach { assertTrue("should see: $it", Vision.heuristic(it)) }
        no.forEach { assertFalse("should not see: $it", Vision.heuristic(it)) }
    }

    @Test fun visionOverridesAndSuggestion() {
        val ov = mapOf("deepseek-chat" to Vision.ON, "gpt-4o" to Vision.OFF)
        assertTrue(Vision.supports("DeepSeek-Chat ", ov))
        assertFalse(Vision.supports("gpt-4o", ov))
        assertTrue(Vision.supports("gpt-4.1", ov))
        assertEquals(ov, Vision.decode(Vision.encode(ov + ("x" to Vision.AUTO))))
        assertEquals(emptyMap<String, String>(), Vision.decode("garbage"))
        assertEquals("gpt-4.1-mini", Vision.suggestSwitch(Providers.byId("openai").suggestedModels, emptyMap()))
        assertNull(Vision.suggestSwitch(Providers.byId("deepseek").suggestedModels, emptyMap()))
        assertEquals("openai/gpt-4o-mini", Vision.suggestSwitch(Providers.byId("openrouter").suggestedModels, emptyMap()))
    }

    // ------------------------------------------------------------------ pruning old images

    @Test fun pruneKeepsOnlyLastTwoImageTurns() {
        val a = ImagePart("A"); val b = ImagePart("B"); val c = ImagePart("C")
        val h = listOf(
            Msg.User("first", listOf(a, a)), Msg.Assistant("ok", emptyList()),
            Msg.User("plain"), Msg.Assistant("ok", emptyList()),
            Msg.User("second", listOf(b)), Msg.Assistant("", listOf(ToolCall("1", "read_file", "{}"))), Msg.ToolResult("1", "read_file", "x"),
            Msg.User("third", listOf(c)),
        )
        val p = ImagePruner.prune(h)
        assertEquals(h.size, p.size)
        assertEquals(Msg.User("first\n[image omitted] [image omitted]"), p[0])
        assertEquals(h[2], p[2])
        assertEquals(listOf(b), (p[4] as Msg.User).images)
        assertEquals(listOf(c), (p[7] as Msg.User).images)
        assertEquals(h.subList(5, 7), p.subList(5, 7))
        assertEquals("second\n[image omitted]", (ImagePruner.prune(h, keep = 1)[4] as Msg.User).text)
        assertEquals(h, ImagePruner.prune(h, keep = 5))
    }

    // ------------------------------------------------------------------ downscale math

    @Test fun downscaleMath() {
        assertEquals(1568 to 1176, ImageMath.targetSize(4000, 3000))
        assertEquals(1176 to 1568, ImageMath.targetSize(3000, 4000))
        assertEquals(1568 to 1568, ImageMath.targetSize(4096, 4096))
        assertEquals(800 to 600, ImageMath.targetSize(800, 600))
        assertEquals(1568 to 10, ImageMath.targetSize(1568, 10))
        assertEquals(1568 to 1, ImageMath.targetSize(10_000, 1))
        assertEquals(706 to 1568, ImageMath.targetSize(1080, 2400))
        assertEquals(1, ImageMath.inSampleSize(3000, 2000))
        assertEquals(2, ImageMath.inSampleSize(4000, 3000))
        assertEquals(4, ImageMath.inSampleSize(8000, 6000))
        // element crop: CSS px → bitmap px (2.625x), padded and clamped
        assertEquals(listOf(24, 202, 541, 131), ImageMath.cropRect(12.5, 80.0, 200.0, 44.0, 411.0, 1079, 2000)!!.toList())
        assertEquals(listOf(0, 0, 100, 50), ImageMath.cropRect(-10.0, -10.0, 1000.0, 1000.0, 100.0, 100, 50)!!.toList())
        assertNull(ImageMath.cropRect(0.0, 0.0, 0.0, 10.0, 400.0, 100, 100))
        // bubble preview keeps the aspect ratio inside its box
        assertEquals(240f to 80f, com.ctodrs.saqrcode.ui.chat.fitSize(504, 168, 240f, 180f))
        assertEquals(101.25f to 180f, com.ctodrs.saqrcode.ui.chat.fitSize(1080, 1920, 240f, 180f))
        assertEquals(64f to 180f, com.ctodrs.saqrcode.ui.chat.fitSize(10, 1000, 240f, 180f))
    }

    // ------------------------------------------------------------------ image → code

    @Test fun stackDetectionAndPrompt() {
        val web = tmpProject().also { File(it, "index.html").writeText("<p>hi</p>") }
        val compose = tmpProject().also { File(it, "app").mkdirs(); File(it, "app/build.gradle.kts").writeText("buildFeatures { compose = true }") }
        val other = tmpProject().also { File(it, "main.py").writeText("print(1)") }
        assertEquals(Stack.WEB, StackDetector.detect(web))
        assertEquals(Stack.COMPOSE, StackDetector.detect(compose))
        assertEquals(Stack.UNKNOWN, StackDetector.detect(other))
        assertTrue(StackDetector.imageToCodePrompt(Stack.UNKNOWN, "").contains("ask_user"))
        val p = StackDetector.imageToCodePrompt(Stack.WEB, "خلفية داكنة")
        assertTrue(p.startsWith("[Image → Code]"))
        assertTrue(p.contains("RTL") && p.contains("Preview") && p.endsWith("User notes: خلفية داكنة"))
        assertTrue(StackDetector.imageToCodePrompt(Stack.COMPOSE, "").contains("Jetpack Compose"))
        assertNotNull(p)
    }
}
