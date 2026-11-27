package com.ctodrs.saqrcode.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Canonical conversation model, converted per provider. */
data class ToolCall(val id: String, val name: String, val argsJson: String)

sealed class Msg {
    /** [images] are sent as multimodal parts (vision models only); see [ImagePruner] for old turns. */
    data class User(val text: String, val images: List<ImagePart> = emptyList()) : Msg()
    data class Assistant(val text: String, val toolCalls: List<ToolCall>) : Msg()
    data class ToolResult(val callId: String, val name: String, val content: String) : Msg()
}

data class ToolSpec(val name: String, val description: String, val parameters: JSONObject)

/** [usage] is what the provider reported, or null when it sent no usage fields. */
data class Turn(val text: String, val toolCalls: List<ToolCall>, val stopReason: String?, val usage: Usage? = null)

class LlmException(message: String) : Exception(message)

interface StreamListener {
    fun onText(delta: String)
    fun onToolStart(name: String) {}
}

/** v0.1.42: the one call the agent loop makes to a model. Lets tests drive the loop with a scripted fake. */
interface ModelClient {
    suspend fun complete(system: String, messages: List<Msg>, tools: List<ToolSpec>, l: StreamListener): Turn
}

/** Builds the client for a provider and model. The default builds the real [LlmClient]. */
fun interface ModelFactory {
    fun create(def: ProviderDef, baseUrl: String, model: String, apiKey: String?): ModelClient

    companion object {
        val DEFAULT = ModelFactory { def, baseUrl, model, apiKey -> LlmClient(def, baseUrl, model, apiKey) }
    }
}

class LlmClient(
    private val def: ProviderDef,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
) : ModelClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(system: String, messages: List<Msg>, tools: List<ToolSpec>, l: StreamListener): Turn =
        withContext(Dispatchers.IO) {
            when (def.kind) {
                ProviderKind.OPENAI_COMPAT -> openAi(system, messages, tools, l)
                ProviderKind.ANTHROPIC -> anthropic(system, messages, tools, l)
            }
        }

    // ---------------------------------------------------------------- OpenAI-compatible
    private suspend fun openAi(system: String, messages: List<Msg>, tools: List<ToolSpec>, l: StreamListener): Turn {
        val body = OpenAiFormat.buildRequest(model, system, messages, tools, includeUsage = def.id in USAGE_STREAM_OPTION)
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .post(body.toString().toRequestBody(JSON))
            .header("Accept", "text/event-stream")
            .apply {
                if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey")
                if (def.id == "openrouter") {
                    header("HTTP-Referer", "https://github.com/cto-drs/saqr-code")
                    header("X-Title", "Saqr Code")
                }
            }
            .build()
        val acc = OpenAiFormat.StreamAccumulator(l)
        stream(req) { _, data -> acc.onData(data) }
        return acc.result()
    }

    // ---------------------------------------------------------------- Anthropic
    private suspend fun anthropic(system: String, messages: List<Msg>, tools: List<ToolSpec>, l: StreamListener): Turn {
        val body = AnthropicFormat.buildRequest(model, system, messages, tools)
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/messages")
            .post(body.toString().toRequestBody(JSON))
            .header("x-api-key", apiKey ?: "")
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .build()
        val acc = AnthropicFormat.StreamAccumulator(l)
        stream(req) { event, data -> acc.onEvent(event, data) }
        return acc.result()
    }

    private suspend fun stream(req: Request, onEvent: (String?, String) -> Unit) {
        val call = http.newCall(req)
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string()?.take(800) ?: ""
                    throw LlmException("HTTP ${resp.code}: ${extractError(err)}")
                }
                val source = resp.body?.source() ?: throw LlmException("Empty response")
                val parser = SseParser(onEvent)
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    parser.feed(line)
                }
                parser.finish()
            }
        } finally {
            handle?.dispose()
        }
    }

    /** Lists models from the provider's official models endpoint. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val req = when (def.kind) {
            ProviderKind.OPENAI_COMPAT -> Request.Builder().url(baseUrl.trimEnd('/') + "/models")
                .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }.build()
            ProviderKind.ANTHROPIC -> Request.Builder().url(baseUrl.trimEnd('/') + "/v1/models?limit=100")
                .header("x-api-key", apiKey ?: "").header("anthropic-version", "2023-06-01").build()
        }
        http.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw LlmException("HTTP ${resp.code}: ${extractError(txt)}")
            val arr = JSONObject(txt).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id") }
                .map { it.removePrefix("models/") }.sorted()
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Providers known to accept `stream_options.include_usage`; others may reject unknown fields. */
        val USAGE_STREAM_OPTION = setOf("openai", "openrouter", "deepseek")

        fun extractError(body: String): String = try {
            val o = JSONObject(body)
            val e = o.opt("error")
            when (e) {
                is JSONObject -> e.optString("message", body)
                is String -> e
                else -> o.optString("message", body)
            }
        } catch (_: Exception) { body }
    }
}

object OpenAiFormat {
    fun buildRequest(model: String, system: String, messages: List<Msg>, tools: List<ToolSpec>, includeUsage: Boolean = false): JSONObject {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        for (m in messages) when (m) {
            is Msg.User -> msgs.put(JSONObject().put("role", "user").put("content", userContent(m)))
            is Msg.Assistant -> {
                val o = JSONObject().put("role", "assistant")
                o.put("content", if (m.text.isEmpty() && m.toolCalls.isNotEmpty()) JSONObject.NULL else m.text)
                if (m.toolCalls.isNotEmpty()) {
                    val tc = JSONArray()
                    m.toolCalls.forEach {
                        tc.put(JSONObject().put("id", it.id).put("type", "function")
                            .put("function", JSONObject().put("name", it.name).put("arguments", it.argsJson)))
                    }
                    o.put("tool_calls", tc)
                }
                msgs.put(o)
            }
            is Msg.ToolResult -> msgs.put(JSONObject().put("role", "tool").put("tool_call_id", m.callId).put("content", m.content))
        }
        val body = JSONObject().put("model", model).put("messages", msgs).put("stream", true)
        if (includeUsage) body.put("stream_options", JSONObject().put("include_usage", true))
        if (tools.isNotEmpty()) {
            val t = JSONArray()
            tools.forEach {
                t.put(JSONObject().put("type", "function").put("function",
                    JSONObject().put("name", it.name).put("description", it.description).put("parameters", it.parameters)))
            }
            body.put("tools", t)
        }
        return body
    }

    /** Plain string for text-only turns; `[{type:text},{type:image_url}]` parts when images are attached. */
    fun userContent(m: Msg.User): Any {
        if (m.images.isEmpty()) return m.text
        val parts = JSONArray()
        parts.put(JSONObject().put("type", "text").put("text", m.text.ifEmpty { " " }))
        m.images.forEach { parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", it.dataUrl))) }
        return parts
    }

    class StreamAccumulator(private val l: StreamListener) {
        private val text = StringBuilder()
        private class Partial(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())
        private val calls = sortedMapOf<Int, Partial>()
        private var finish: String? = null
        private var usage: Usage? = null

        fun onData(data: String) {
            if (data.trim() == "[DONE]") return
            val o = try { JSONObject(data) } catch (_: Exception) { return }
            o.optJSONObject("error")?.let { throw LlmException(it.optString("message", data)) }
            UsageParser.any(o)?.let { usage = it }
            val choices = o.optJSONArray("choices") ?: return
            for (i in 0 until choices.length()) {
                val c = choices.getJSONObject(i)
                if (!c.isNull("finish_reason")) finish = c.optString("finish_reason")
                val d = c.optJSONObject("delta") ?: continue
                if (d.has("content") && !d.isNull("content")) {
                    val s = d.optString("content")
                    if (s.isNotEmpty()) { text.append(s); l.onText(s) }
                }
                val tcs = d.optJSONArray("tool_calls") ?: continue
                for (j in 0 until tcs.length()) {
                    val tc = tcs.getJSONObject(j)
                    val idx = tc.optInt("index", calls.size)
                    val p = calls.getOrPut(idx) { Partial() }
                    tc.optString("id").takeIf { it.isNotEmpty() && it != "null" }?.let { p.id = it }
                    tc.optJSONObject("function")?.let { f ->
                        f.optString("name").takeIf { it.isNotEmpty() && it != "null" }?.let {
                            if (p.name.isEmpty()) { p.name = it; l.onToolStart(it) }
                        }
                        if (f.has("arguments") && !f.isNull("arguments")) p.args.append(f.optString("arguments"))
                    }
                }
            }
        }

        fun result(): Turn = Turn(
            text.toString(),
            calls.values.filter { it.name.isNotEmpty() }.map {
                ToolCall(it.id.ifEmpty { "call_" + UUID.randomUUID().toString().take(8) }, it.name,
                    it.args.toString().ifBlank { "{}" })
            },
            finish,
            usage,
        )
    }
}

object AnthropicFormat {
    fun buildRequest(model: String, system: String, messages: List<Msg>, tools: List<ToolSpec>): JSONObject {
        val out = JSONArray()
        var pendingRole: String? = null
        var pending = JSONArray()
        fun flush() {
            if (pendingRole != null && pending.length() > 0) out.put(JSONObject().put("role", pendingRole).put("content", pending))
            pending = JSONArray(); pendingRole = null
        }
        fun add(role: String, block: JSONObject) {
            if (pendingRole != role) { flush(); pendingRole = role }
            pending.put(block)
        }
        for (m in messages) when (m) {
            is Msg.User -> {
                // images first, then the text that refers to them (Anthropic's recommended order)
                m.images.forEach {
                    add("user", JSONObject().put("type", "image").put("source",
                        JSONObject().put("type", "base64").put("media_type", it.mediaType).put("data", it.base64)))
                }
                add("user", JSONObject().put("type", "text").put("text", m.text.ifEmpty { " " }))
            }
            is Msg.Assistant -> {
                if (m.text.isNotBlank()) add("assistant", JSONObject().put("type", "text").put("text", m.text))
                m.toolCalls.forEach {
                    val input = try { JSONObject(it.argsJson) } catch (_: Exception) { JSONObject() }
                    add("assistant", JSONObject().put("type", "tool_use").put("id", it.id).put("name", it.name).put("input", input))
                }
                if (m.text.isBlank() && m.toolCalls.isEmpty()) add("assistant", JSONObject().put("type", "text").put("text", "…"))
            }
            is Msg.ToolResult -> add("user", JSONObject().put("type", "tool_result").put("tool_use_id", m.callId).put("content", m.content))
        }
        flush()
        val body = JSONObject().put("model", model).put("max_tokens", 8192).put("system", system)
            .put("messages", out).put("stream", true)
        if (tools.isNotEmpty()) {
            val t = JSONArray()
            tools.forEach { t.put(JSONObject().put("name", it.name).put("description", it.description).put("input_schema", it.parameters)) }
            body.put("tools", t)
        }
        return body
    }

    class StreamAccumulator(private val l: StreamListener) {
        private val text = StringBuilder()
        private class Block(val type: String, val id: String = "", val name: String = "", val json: StringBuilder = StringBuilder())
        private val blocks = sortedMapOf<Int, Block>()
        private var stop: String? = null
        private var inTokens = -1L
        private var outTokens = -1L

        fun onEvent(event: String?, data: String) {
            val o = try { JSONObject(data) } catch (_: Exception) { return }
            UsageParser.anthropic(o)?.let { u ->
                if (u.input > 0) inTokens = u.input
                if (u.output > 0 || outTokens < 0) outTokens = u.output
            }
            when (o.optString("type", event ?: "")) {
                "error" -> throw LlmException(o.optJSONObject("error")?.optString("message") ?: data)
                "content_block_start" -> {
                    val idx = o.optInt("index")
                    val cb = o.optJSONObject("content_block") ?: return
                    val type = cb.optString("type")
                    blocks[idx] = Block(type, cb.optString("id"), cb.optString("name"))
                    if (type == "tool_use") l.onToolStart(cb.optString("name"))
                }
                "content_block_delta" -> {
                    val idx = o.optInt("index")
                    val d = o.optJSONObject("delta") ?: return
                    when (d.optString("type")) {
                        "text_delta" -> { val s = d.optString("text"); text.append(s); l.onText(s) }
                        "input_json_delta" -> blocks[idx]?.json?.append(d.optString("partial_json"))
                    }
                }
                "message_delta" -> o.optJSONObject("delta")?.optString("stop_reason")?.let { stop = it }
            }
        }

        fun result() = Turn(
            text.toString(),
            blocks.values.filter { it.type == "tool_use" }.map { ToolCall(it.id, it.name, it.json.toString().ifBlank { "{}" }) },
            stop,
            if (inTokens < 0 && outTokens < 0) null else Usage(inTokens.coerceAtLeast(0), outTokens.coerceAtLeast(0)),
        )
    }
}

/**
 * Native Gemini `generateContent` request shape (`contents[].parts[]` with `inline_data`).
 * The app currently reaches Gemini through Google's OpenAI-compatible endpoint, which takes
 * [OpenAiFormat]'s `image_url` data URLs; this builder is for the native API and is unit tested.
 */
object GeminiFormat {
    fun buildRequest(system: String, messages: List<Msg>, tools: List<ToolSpec>): JSONObject {
        val contents = JSONArray()
        var role: String? = null
        var parts = JSONArray()
        fun flush() { if (role != null && parts.length() > 0) contents.put(JSONObject().put("role", role).put("parts", parts)); parts = JSONArray(); role = null }
        fun add(r: String, p: JSONObject) { if (role != r) { flush(); role = r }; parts.put(p) }
        for (m in messages) when (m) {
            is Msg.User -> {
                add("user", JSONObject().put("text", m.text.ifEmpty { " " }))
                m.images.forEach { add("user", JSONObject().put("inline_data", JSONObject().put("mime_type", it.mediaType).put("data", it.base64))) }
            }
            is Msg.Assistant -> {
                if (m.text.isNotBlank()) add("model", JSONObject().put("text", m.text))
                m.toolCalls.forEach {
                    val args = runCatching { JSONObject(it.argsJson) }.getOrDefault(JSONObject())
                    add("model", JSONObject().put("functionCall", JSONObject().put("name", it.name).put("args", args)))
                }
            }
            is Msg.ToolResult -> add("user", JSONObject().put("functionResponse",
                JSONObject().put("name", m.name).put("response", JSONObject().put("content", m.content))))
        }
        flush()
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        if (tools.isNotEmpty()) {
            val decl = JSONArray()
            tools.forEach { decl.put(JSONObject().put("name", it.name).put("description", it.description).put("parameters", it.parameters)) }
            body.put("tools", JSONArray().put(JSONObject().put("function_declarations", decl)))
        }
        return body
    }
}
