package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.LlmClient
import com.ctodrs.saqrcode.agent.LlmException
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.StreamListener
import com.ctodrs.saqrcode.agent.Tools
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end streaming against a local fake server speaking each provider's wire format. */
class LlmClientTest {
    private val server = MockWebServer()
    private var lastBody = ""
    private var lastHeaders = mapOf<String, String>()
    private val base get() = server.url("/").toString().trimEnd('/')

    @Suppress("UNUSED_PARAMETER")
    private fun route(path: String, status: Int = 200, body: String) {
        server.enqueue(MockResponse().setResponseCode(status)
            .setHeader("Content-Type", if (status == 200) "text/event-stream" else "application/json").setBody(body))
    }

    private fun captured() {
        val r = server.takeRequest()
        lastBody = r.body.readUtf8()
        lastHeaders = r.headers.associate { it.first.lowercase() to it.second }
    }

    @After fun tearDown() = server.shutdown()

    private class L : StreamListener { val sb = StringBuilder(); override fun onText(delta: String) { sb.append(delta) } }

    @Test fun openAiCompatible() = runBlocking {
        route("/v1/chat/completions", body = listOf(
            """{"choices":[{"delta":{"content":"تمام، "}}]}""",
            """{"choices":[{"delta":{"content":"سأقرأ الملف"}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","type":"function","function":{"name":"read_file","arguments":"{\"path\":\"index.html\"}"}}]},"finish_reason":"tool_calls"}]}""",
            "[DONE]",
        ).joinToString("") { "data: $it\n\n" })
        val l = L()
        val turn = LlmClient(Providers.byId("deepseek"), "$base/v1", "deepseek-chat", "sk-test")
            .complete("sys", listOf(Msg.User("اقرأ index.html")), Tools.specs, l)
        assertEquals("تمام، سأقرأ الملف", l.sb.toString())
        assertEquals("read_file", turn.toolCalls.single().name)
        captured()
        assertEquals("Bearer sk-test", lastHeaders["authorization"])
        val req = JSONObject(lastBody)
        assertEquals("deepseek-chat", req.getString("model"))
        assertTrue(req.getBoolean("stream"))
        assertEquals("system", req.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test fun anthropic() = runBlocking {
        route("/v1/messages", body = """
event: message_start
data: {"type":"message_start","message":{"id":"m"}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

event: content_block_start
data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"t1","name":"run_command","input":{}}}

event: content_block_delta
data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"command\":\"ls\"}"}}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"tool_use"}}

event: message_stop
data: {"type":"message_stop"}

""".trimStart())
        val turn = LlmClient(Providers.byId("anthropic"), base, "claude-x", "ak").complete("sys", listOf(Msg.User("hi")), Tools.specs, L())
        assertEquals("Hello", turn.text)
        assertEquals("{\"command\":\"ls\"}", turn.toolCalls.single().argsJson)
        captured()
        assertEquals("ak", lastHeaders["x-api-key"])
        assertEquals("2023-06-01", lastHeaders["anthropic-version"])
        assertEquals("sys", JSONObject(lastBody).getString("system"))
    }

    @Test fun httpErrorSurfacesMessage() = runBlocking {
        route("/v1/chat/completions", status = 401, body = """{"error":{"message":"Invalid API key"}}""")
        val e = runCatching {
            LlmClient(Providers.byId("openai"), "$base/v1", "m", "bad").complete("s", listOf(Msg.User("x")), emptyList(), L())
        }.exceptionOrNull()
        assertTrue(e is LlmException)
        assertTrue(e!!.message!!.contains("401") && e.message!!.contains("Invalid API key"))
    }

    @Test fun errorTextNeverEchoesTheKey() = runBlocking {
        val key = "zz-secret-key-1234567890"
        route("/v1/chat/completions", status = 401, body = """{"error":{"message":"Incorrect API key provided: $key. Check it."}}""")
        val e = runCatching {
            LlmClient(Providers.byId("openai"), "$base/v1", "m", key).complete("s", listOf(Msg.User("x")), emptyList(), L())
        }.exceptionOrNull()
        assertTrue(e is LlmException)
        assertTrue(e!!.message!!.contains("401") && e.message!!.contains("Incorrect API key"))
        assertTrue(!e.message!!.contains(key))
    }

    @Test fun safeErrorKeepsOrdinaryProviderText() {
        val m = "HTTP 400: This model's maximum context length is 8192 tokens, however you requested 9000 tokens"
        assertEquals(m, LlmClient.safeError(m, "sk-abcdef123456"))
        assertEquals("bad ***", LlmClient.safeError("bad sk-abcdef123456", "sk-abcdef123456"))
    }
}
