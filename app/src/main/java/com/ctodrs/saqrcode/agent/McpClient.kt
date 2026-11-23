package com.ctodrs.saqrcode.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val MCP_JSON = "application/json; charset=utf-8"

/**
 * v0.8 · دعم MCP · Model Context Protocol client over the Streamable-HTTP transport.
 *
 * A user-configured server (name + HTTPS URL + optional auth header) is initialised once per
 * agent turn, its `tools/list` result is mapped to agent tools named `mcp_<server>_<tool>`,
 * and `tools/call` executes them. Only HTTPS and public hosts are allowed (WebGuard applies).
 * The protocol parts below (protocol version handshake, tool listing, content extraction,
 * name sanitising) are pure and unit-tested in V08FeaturesTest.
 */
object Mcp {

    const val PROTOCOL_VERSION = "2025-06-18"
    const val PREFIX = "mcp_"

    // ---------------------------------------------------------------- config

    /** One user-configured MCP server. [authHeader] travels in the encrypted SecureStore. */
    data class Server(val name: String, val url: String, val authHeader: String? = null) {
        /** `My Server` → `myserver`; alphanumeric only, so tool names stay parseable. */
        val slug: String = slugify(name)
    }

    fun slugify(name: String): String = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]"), "").take(24).ifEmpty { "server" }

    /** The full agent tool name for a server tool: `mcp_<server-slug>_<tool>`, sanitised and capped. */
    fun toolName(serverSlug: String, tool: String): String {
        val s = slugify(serverSlug)
        val t = tool.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return (PREFIX + s + "_" + t).take(60).trimEnd('_')
    }

    /** Parses `mcp_myserver_weather_lookup` back to (server slug, raw tool). */
    fun splitToolName(full: String): Pair<String, String>? {
        if (!full.startsWith(PREFIX)) return null
        val rest = full.removePrefix(PREFIX)
        val i = rest.indexOf('_')
        if (i <= 0 || i >= rest.length - 1) return null
        return rest.substring(0, i) to rest.substring(i + 1)
    }

    fun encodeConfig(servers: List<Server>): String = JSONArray().apply {
        servers.forEach { put(JSONObject().put("name", it.name).put("url", it.url)) }
    }.toString()

    fun decodeConfig(json: String?): List<Server> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val n = o.optString("name").trim()
            val u = o.optString("url").trim()
            if (n.isEmpty() || u.isEmpty()) null else Server(n, u)
        }
    }.getOrDefault(emptyList())

    // ---------------------------------------------------------------- protocol (pure)

    /** JSON-RPC request body for `initialize` (streamable HTTP requires the capability handshake). */
    fun initializeRequest(): JSONObject = rpc("initialize").put("params", JSONObject()
        .put("protocolVersion", PROTOCOL_VERSION)
        .put("capabilities", JSONObject())
        .put("clientInfo", JSONObject().put("name", "Saqr Code").put("version", "0.8.0")))

    fun initializedNotification(): JSONObject = JSONObject()
        .put("jsonrpc", "2.0").put("method", "notifications/initialized")

    fun toolsListRequest(): JSONObject = rpc("tools/list").put("params", JSONObject())

    fun toolsCallRequest(tool: String, args: JSONObject): JSONObject =
        rpc("tools/call").put("params", JSONObject()
            .put("name", tool)
            .put("arguments", args))

    private fun rpc(method: String) = JSONObject()
        .put("jsonrpc", "2.0").put("id", 1).put("method", method)

    /** The server's endpoint when it answers SSE: `event: message` + `data: {json}` — last data line wins. */
    fun parseSseData(body: String): JSONObject? {
        val dataLines = body.lineSequence().filter { it.startsWith("data:") }.map { it.removePrefix("data:").trim() }.toList()
        for (line in dataLines.reversed()) {
            if (!line.startsWith("{")) continue
            val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
            if (o.has("result") || o.has("error")) return o
        }
        return null
    }

    /** Unwraps either a plain JSON-RPC body or an SSE body into the response object. */
    fun parseResponse(contentType: String?, body: String): JSONObject? {
        val t = contentType?.lowercase().orEmpty()
        return if ("text/event-stream" in t) parseSseData(body)
        else runCatching { JSONObject(body) }.getOrNull()
            ?.takeIf { it.has("result") || it.has("error") }
    }

    /** One server tool as the agent sees it. */
    data class Tool(val name: String, val description: String, val inputSchema: JSONObject)

    /** Maps `tools/list` result → agent tools. Names are prefixed per [Server.slug]. */
    fun parseToolsList(serverSlug: String, result: JSONObject): List<Tool> {
        val arr = result.optJSONArray("tools") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val raw = o.optString("name").trim()
            if (raw.isEmpty()) return@mapNotNull null
            Tool(
                toolName(serverSlug, raw),
                o.optString("description").trim().ifEmpty { "MCP tool $raw" }.take(400),
                o.optJSONObject("inputSchema") ?: JSONObject().put("type", "object"),
            )
        }.take(32)
    }

    /** `tools/call` result → flat text for the model; isError surfaces as an error string. */
    fun renderCallResult(result: JSONObject, maxChars: Int = 12_000): String {
        if (result.optBoolean("isError", false)) return "Error: " + contentText(result).take(maxChars)
        val text = contentText(result)
        if (text.isBlank()) return "(the tool returned no content)"
        return text.take(maxChars)
    }

    private fun contentText(result: JSONObject): String = buildString {
        val arr = result.optJSONArray("content")
        if (arr != null) for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            when (c.optString("type", "text")) {
                "text" -> appendLine(c.optString("text"))
                "resource" -> c.optJSONObject("resource")?.let { r ->
                    appendLine(r.optString("text", r.optString("uri")))
                }
                "image" -> appendLine("[image content not shown]")
            }
        }
        result.optJSONObject("structuredContent")?.let { if (isEmpty()) appendLine(it.toString()) }
    }.trim()

    /** A JSON-RPC error string, or null when the response carries a result. */
    fun errorOf(response: JSONObject): String? {
        val e = response.optJSONObject("error") ?: return null
        val msg = e.optString("message", "MCP error")
        return "MCP error ${e.optInt("code", 0)}: $msg"
    }

    /** JSON schema of an MCP tool → the compact spec description the model gets. */
    fun describeSchema(schema: JSONObject): String {
        val props = schema.optJSONObject("properties") ?: return "no arguments"
        val req = schema.optJSONArray("required")
        val parts = ArrayList<String>()
        props.keys().forEach { k ->
            val o = props.optJSONObject(k) ?: return@forEach
            val flag = if (req != null && (0 until req.length()).any { req.optString(it) == k }) "required" else "optional"
            parts.add("$k (${o.optString("type", "string")}, $flag): ${o.optString("description").take(120)}")
        }
        return if (parts.isEmpty()) "no arguments" else parts.joinToString("; ")
    }
}

/**
 * Runtime half of MCP: real HTTP calls. Kept apart from the pure protocol helpers so unit
 * tests never touch the network. One client per server; created lazily and closed by the caller.
 */
class McpClient(private val server: Mcp.Server, http: OkHttpClient? = null) {

    private val client: OkHttpClient = (http ?: OkHttpClient())
        .newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    var endpointError: String? = null; private set

    /** POSTs a JSON-RPC payload; returns the response object (JSON or SSE body). */
    private fun post(payload: JSONObject): JSONObject? {
        val rb = Request.Builder().url(server.url)
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", Mcp.PROTOCOL_VERSION)
            .header("User-Agent", "SaqrCode/0.8")
            .post(payload.toString().toRequestBody(MCP_JSON.toMediaType()))
        server.authHeader?.takeIf { it.isNotBlank() }?.let { rb.header("Authorization", it) }
        client.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                endpointError = "HTTP ${resp.code} from ${server.url}"
                return null
            }
            val ct = resp.header("Content-Type")
            val bodyText = resp.body!!.string()
            val parsed = Mcp.parseResponse(ct, bodyText)
            if (parsed == null) endpointError = "unreadable response from ${server.name}"
            return parsed
        }
    }

    /** Handshake + tool listing. Returns the tools, or null (see [endpointError]). */
    fun listTools(): List<Mcp.Tool>? {
        val init = post(Mcp.initializeRequest())
            ?: run { if (endpointError == null) endpointError = "initialize failed"; return null }
        Mcp.errorOf(init)?.let { endpointError = it; return null }
        // streamable HTTP: the initialized notification is sent as its own POST (2xx is enough)
        runCatching {
            val rb = Request.Builder().url(server.url)
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", Mcp.PROTOCOL_VERSION)
                .post(Mcp.initializedNotification().toString().toRequestBody(MCP_JSON.toMediaType()))
            server.authHeader?.takeIf { it.isNotBlank() }?.let { rb.header("Authorization", it) }
            client.newCall(rb.build()).execute().use { }
        }
        val list = post(Mcp.toolsListRequest()) ?: return null
        Mcp.errorOf(list)?.let { endpointError = it; return null }
        val result = list.optJSONObject("result") ?: return null.also { endpointError = "tools/list returned no result" }
        return Mcp.parseToolsList(server.slug, result)
    }

    /** Executes one tool; returns model-readable text or "Error: …". */
    fun callTool(tool: String, args: JSONObject, maxChars: Int = 12_000): String {
        val resp = post(Mcp.toolsCallRequest(tool, args)) ?: return "Error: ${endpointError ?: "server unreachable"}"
        Mcp.errorOf(resp)?.let { return "Error: $it" }
        val result = resp.optJSONObject("result") ?: return "Error: tools/call returned no result"
        return Mcp.renderCallResult(result, maxChars)
    }
}
