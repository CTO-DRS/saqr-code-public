package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.AnthropicFormat
import com.ctodrs.saqrcode.agent.LlmException
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.OpenAiFormat
import com.ctodrs.saqrcode.agent.StreamListener
import com.ctodrs.saqrcode.agent.ToolCall
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave D: scripted-model scenarios for the wire layer the agent loop stands on. Each test replays the
 * exact stream a provider sends (split chunks, parallel calls, errors) and checks the Turn the loop receives,
 * then checks that the history the loop sends back keeps every tool call paired with its result.
 */
class V0135AgentScenarioTest {
    private class L : StreamListener {
        val text = StringBuilder(); val tools = ArrayList<String>()
        override fun onText(delta: String) { text.append(delta) }
        override fun onToolStart(name: String) { tools += name }
    }

    private fun openAi(l: L, vararg chunks: String) = OpenAiFormat.StreamAccumulator(l).also { a -> chunks.forEach(a::onData) }.result()
    private fun anthropic(l: L, vararg events: String) = AnthropicFormat.StreamAccumulator(l).also { a -> events.forEach { a.onEvent(null, it) } }.result()

    // ---- OpenAI-compatible stream

    @Test fun `plain answer streams text and stops`() {
        val l = L()
        val t = openAi(l,
            """{"choices":[{"delta":{"content":"Hello "}}]}""",
            """{"choices":[{"delta":{"content":"world"},"finish_reason":"stop"}]}""",
            "[DONE]")
        assertEquals("Hello world", t.text); assertEquals("Hello world", l.text.toString())
        assertTrue(t.toolCalls.isEmpty()); assertEquals("stop", t.stopReason)
    }

    @Test fun `tool arguments split across chunks are joined into one call`() {
        val l = L()
        val t = openAi(l,
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"write_file","arguments":"{\"path\":\"a."}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"txt\",\"content\""}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":":\"hi\"}"}}]},"finish_reason":"tool_calls"}]}""")
        assertEquals(1, t.toolCalls.size)
        assertEquals("c1", t.toolCalls[0].id); assertEquals("write_file", t.toolCalls[0].name)
        val args = JSONObject(t.toolCalls[0].argsJson)
        assertEquals("a.txt", args.getString("path")); assertEquals("hi", args.getString("content"))
        assertEquals(listOf("write_file"), l.tools)
        assertEquals("tool_calls", t.stopReason)
    }

    @Test fun `parallel tool calls interleaved by index stay separate and ordered`() {
        val l = L()
        val t = openAi(l,
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a","function":{"name":"read_file","arguments":"{\"path\":"}},{"index":1,"id":"b","function":{"name":"list_files","arguments":"{\"dir\":"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\"src\"}"}},{"index":0,"function":{"arguments":"\"x.kt\"}"}}]}}]}""")
        assertEquals(listOf("read_file", "list_files"), t.toolCalls.map { it.name })
        assertEquals("x.kt", JSONObject(t.toolCalls[0].argsJson).getString("path"))
        assertEquals("src", JSONObject(t.toolCalls[1].argsJson).getString("dir"))
        assertEquals(listOf("read_file", "list_files"), l.tools)
    }

    @Test fun `text before a tool call is kept and a missing id or arguments get safe defaults`() {
        val l = L()
        val t = openAi(l,
            """{"choices":[{"delta":{"content":"Checking."}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"git_status"}}]}}]}""")
        assertEquals("Checking.", t.text)
        assertEquals("{}", t.toolCalls.single().argsJson)
        assertTrue(t.toolCalls.single().id.startsWith("call_"))
    }

    @Test fun `junk chunks are ignored and a provider error aborts with its message`() {
        val l = L()
        val t = openAi(l, "not json at all", """{"choices":[]}""", """{"choices":[{"delta":{"content":"ok"}}]}""")
        assertEquals("ok", t.text)
        try { openAi(L(), """{"error":{"message":"rate limited"}}"""); fail("expected LlmException") }
        catch (e: LlmException) { assertEquals("rate limited", e.message) }
    }

    // ---- Anthropic stream

    @Test fun `anthropic text then tool use with split json`() {
        val l = L()
        val t = anthropic(l,
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"On it. "}}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"tu1","name":"run_command"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"cmd\":\"ls"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":" -la\"}"}}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""")
        assertEquals("On it. ", t.text)
        assertEquals("tu1", t.toolCalls.single().id)
        assertEquals("ls -la", JSONObject(t.toolCalls.single().argsJson).getString("cmd"))
        assertEquals("tool_use", t.stopReason); assertEquals(listOf("run_command"), l.tools)
    }

    @Test fun `anthropic tool with no input defaults to empty object and errors abort`() {
        val t = anthropic(L(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tu9","name":"git_status"}}""")
        assertEquals("{}", t.toolCalls.single().argsJson)
        try { anthropic(L(), """{"type":"error","error":{"message":"overloaded"}}"""); fail("expected LlmException") }
        catch (e: LlmException) { assertEquals("overloaded", e.message) }
    }

    // ---- what the loop sends back

    private val history = listOf(
        Msg.User("fix the build"),
        Msg.Assistant("", listOf(ToolCall("c1", "read_file", """{"path":"a"}"""), ToolCall("c2", "run_command", """{"cmd":"ls"}"""))),
        Msg.ToolResult("c1", "read_file", "contents"),
        Msg.ToolResult("c2", "run_command", "DENIED by user"),
        Msg.Assistant("Understood, I will not run it.", emptyList()),
        Msg.User("ok continue"),
    )

    @Test fun `openai history pairs every tool call with its result in order`() {
        val msgs = OpenAiFormat.buildRequest("m", "sys", history, emptyList()).getJSONArray("messages")
        assertEquals(listOf("system", "user", "assistant", "tool", "tool", "assistant", "user"), roles(msgs))
        val asst = msgs.getJSONObject(2)
        assertTrue(asst.isNull("content"))
        val ids = asst.getJSONArray("tool_calls").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } }
        assertEquals(ids, listOf(msgs.getJSONObject(3).getString("tool_call_id"), msgs.getJSONObject(4).getString("tool_call_id")))
        assertEquals("DENIED by user", msgs.getJSONObject(4).getString("content"))
    }

    @Test fun `anthropic history merges results into one user turn and keeps ids paired`() {
        val msgs = AnthropicFormat.buildRequest("m", "sys", history, emptyList()).getJSONArray("messages")
        assertEquals(listOf("user", "assistant", "user", "assistant", "user"), roles(msgs))
        val uses = msgs.getJSONObject(1).getJSONArray("content")
        val results = msgs.getJSONObject(2).getJSONArray("content")
        assertEquals(2, uses.length()); assertEquals(2, results.length())
        for (i in 0 until 2) {
            assertEquals("tool_use", uses.getJSONObject(i).getString("type"))
            assertEquals(uses.getJSONObject(i).getString("id"), results.getJSONObject(i).getString("tool_use_id"))
        }
        assertNotNull(msgs.getJSONObject(4))
    }

    @Test fun `anthropic merges a trailing user text into the same turn as the tool results`() {
        val h = listOf(
            Msg.User("go"),
            Msg.Assistant("", listOf(ToolCall("c1", "read_file", "{}"))),
            Msg.ToolResult("c1", "read_file", "x"),
            Msg.User("and also this"),
        )
        val msgs = AnthropicFormat.buildRequest("m", "sys", h, emptyList()).getJSONArray("messages")
        assertEquals(listOf("user", "assistant", "user"), roles(msgs))
        assertEquals(2, msgs.getJSONObject(2).getJSONArray("content").length())
    }

    private fun roles(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it).getString("role") }
}
