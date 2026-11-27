package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * v0.11 · MCP عبر stdio · Model Context Protocol over the stdio transport, bridged through the
 * app's own terminal environment (proot Linux when installed, /system/bin/sh otherwise).
 *
 * This unlocks the long tail of local MCP servers (filesystem, git, sqlite…) that only speak
 * stdin/stdout: a newline-delimited JSON-RPC stream. Each server is one user-configured command
 * run once per agent turn; its tools join the `mcp_<server>_<tool>` namespace exactly like HTTP
 * servers, so Reviewer/filtering/limits apply unchanged. Only the framing + config here are
 * unit-tested; the process plumbing needs a real terminal (LinuxEnv).
 */
object McpStdio {

    /** One user-configured stdio server: [command] runs inside the terminal env (cwd = project). */
    data class StdioServer(val name: String, val command: String, val envText: String = "") {
        val slug: String get() = Mcp.slugify(name)
    }

    // ------------------------------------------------------------ config (pure)

    fun encodeConfig(servers: List<StdioServer>): String = JSONArray().apply {
        servers.forEach {
            put(JSONObject().put("name", it.name).put("command", it.command).put("env", it.envText))
        }
    }.toString()

    fun decodeConfig(json: String?): List<StdioServer> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val n = o.optString("name").trim()
            val c = o.optString("command").trim()
            if (n.isEmpty() || c.isEmpty()) null else StdioServer(n, c, o.optString("env").trim())
        }
    }.getOrDefault(emptyList())

    /** A command is acceptable when it is one shell line; anything else is a config error. */
    fun validateCommand(command: String): String? = when {
        command.isBlank() -> "command is empty"
        command.contains('\n') || command.contains('\r') -> "command must be a single line"
        else -> null
    }

    /**
     * Parses the env text into `K=V` process-environment entries: one pair per line, or comma
     * separated on one line. Blank fragments and entries without `=` are skipped.
     */
    fun parseEnv(envText: String): List<String> = envText
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.contains('=') && !it.startsWith("=") }
        .map { it.take(400) }

    /** Env text with every value removed ("K=V" becomes "K="), so names survive a sync but secrets do not. */
    fun stripEnvValues(envText: String): String =
        parseEnv(envText).joinToString("\n") { it.substringBefore('=') + "=" }

    /**
     * Sync apply: servers arrive with empty env values. Fill each from the same server's local env
     * (matched by server name and variable name); a variable with no local value stays empty.
     */
    fun mergeLocalEnv(incoming: List<StdioServer>, local: List<StdioServer>): List<StdioServer> = incoming.map { inc ->
        val have = local.firstOrNull { it.name == inc.name }?.let { l ->
            parseEnv(l.envText).associate { it.substringBefore('=') to it.substringAfter('=') }
        }.orEmpty()
        inc.copy(envText = parseEnv(inc.envText).joinToString("\n") { kv ->
            val k = kv.substringBefore('=')
            val v = kv.substringAfter('=')
            k + "=" + v.ifEmpty { have[k].orEmpty() }
        })
    }

    // ------------------------------------------------------------ protocol (pure)

    fun rpc(id: Int, method: String) = JSONObject()
        .put("jsonrpc", "2.0").put("id", id).put("method", method)

    fun initializeRequest(id: Int): JSONObject = rpc(id, "initialize").put("params", JSONObject()
        .put("protocolVersion", Mcp.PROTOCOL_VERSION)
        .put("capabilities", JSONObject())
        .put("clientInfo", JSONObject().put("name", "Saqr Code").put("version", "0.11.0")))

    fun initializedNotification(): JSONObject = JSONObject()
        .put("jsonrpc", "2.0").put("method", "notifications/initialized")

    fun toolsListRequest(id: Int): JSONObject = rpc(id, "tools/list").put("params", JSONObject())

    fun toolsCallRequest(id: Int, tool: String, args: JSONObject): JSONObject =
        rpc(id, "tools/call").put("params", JSONObject()
            .put("name", tool)
            .put("arguments", args))

    /** stdio frames are single-line JSON objects; anything else (banners, logs) is ignored. */
    fun parseLine(line: String): JSONObject? {
        val t = line.trim()
        if (!t.startsWith("{")) return null
        val o = runCatching { JSONObject(t) }.getOrNull() ?: return null
        return if (o.has("result") || o.has("error") || o.has("method")) o else null
    }

    /** The JSON-RPC id of a response message, or null for notifications. */
    fun idOf(msg: JSONObject): Int? = if (msg.isNull("id") || !msg.has("id")) null else msg.optInt("id", Int.MIN_VALUE)

    /** True when [msg] is the response to request [id] (result or error both count). */
    fun respondsTo(msg: JSONObject, id: Int): Boolean = idOf(msg) == id && (msg.has("result") || msg.has("error"))
}

/**
 * Runtime half of stdio MCP: spawns the server command through [LinuxEnv] and speaks the protocol
 * over its pipes. One instance per server per agent turn; [close] must be called to reap the child.
 */
class McpStdioClient(
    private val server: McpStdio.StdioServer,
    private val env: com.ctodrs.saqrcode.terminal.LinuxEnv,
    private val projectDir: File,
) {

    var endpointError: String? = null; private set
    private var proc: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var nextId = 1

    /** Starts the process and completes the initialize handshake. False = see [endpointError]. */
    fun start(): Boolean {
        val err = McpStdio.validateCommand(server.command)
        if (err != null) { endpointError = err; return false }
        val pb = env.processBuilder(projectDir, listOf("-c", server.command), mergeStderr = false)
        for (kv in McpStdio.parseEnv(server.envText)) pb.environment().put(kv.substringBefore('='), kv.substringAfter('='))
        val p = runCatching { pb.start() }.getOrElse { endpointError = "cannot start: ${it.message}"; return false }
        proc = p
        reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8), 32 * 1024)
        writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
        val init = request(McpStdio.initializeRequest(nextId++), HANDSHAKE_TIMEOUT_MS)
            ?: return false
        Mcp.errorOf(init)?.let { endpointError = it; return false }
        if (init.optJSONObject("result") == null) { endpointError = "initialize returned no result"; return false }
        send(McpStdio.initializedNotification())
        return true
    }

    /** Handshake + tool listing; same mapping as HTTP servers. */
    fun listTools(): List<Mcp.Tool>? {
        if (proc == null && !start()) return null
        val list = request(McpStdio.toolsListRequest(nextId++), TOOL_TIMEOUT_MS) ?: return null
        Mcp.errorOf(list)?.let { endpointError = it; return null }
        val result = list.optJSONObject("result") ?: return null.also { endpointError = "tools/list returned no result" }
        return Mcp.parseToolsList(server.slug, result)
    }

    /** Executes one tool; returns model-readable text or "Error: …". */
    fun callTool(tool: String, args: JSONObject, maxChars: Int = 12_000): String {
        val resp = request(McpStdio.toolsCallRequest(nextId++, tool, args), CALL_TIMEOUT_MS)
            ?: return "Error: ${endpointError ?: "server '$server' did not answer in time"}"
        Mcp.errorOf(resp)?.let { return "Error: $it" }
        val result = resp.optJSONObject("result") ?: return "Error: tools/call returned no result"
        return Mcp.renderCallResult(result, maxChars)
    }

    /** Kills the child process; safe to call repeatedly. */
    fun close() {
        runCatching { proc?.destroy() }
        proc = null; reader = null; writer = null
    }

    // ------------------------------------------------------------ plumbing

    private fun send(msg: JSONObject) {
        val w = writer ?: return
        runCatching {
            w.write(msg.toString()); w.write("\n"); w.flush()
        }.onFailure { endpointError = "write failed: ${it.message}" }
    }

    /** Writes [req] then reads lines until the matching response or the deadline. */
    private fun request(req: JSONObject, timeoutMs: Long): JSONObject? {
        if (proc?.isAlive == false) { endpointError = "server process exited"; return null }
        send(req)
        if (endpointError != null) return null
        val r = reader ?: return null.also { endpointError = "not started" }
        val id = McpStdio.idOf(req)
        val deadline = System.currentTimeMillis() + timeoutMs
        var seen = 0
        while (System.currentTimeMillis() < deadline) {
            val line: String?
            if (!r.ready()) {
                if (proc?.isAlive == false) { endpointError = "server process exited"; return null }
                Thread.sleep(40); line = null
            } else {
                line = runCatching { r.readLine() }.getOrElse { endpointError = "read failed: ${it.message}"; return null }
                if (line == null) { endpointError = "server closed the stream (EOF)"; return null }
            }
            if (line == null) continue
            val msg = McpStdio.parseLine(line) ?: continue
            if (id != null && McpStdio.respondsTo(msg, id)) return msg
            // notifications / stray responses are dropped; a flood means we are talking to the wrong stream
            if (++seen > 1000) { endpointError = "too many stray messages"; return null }
        }
        endpointError = "timeout after ${timeoutMs / 1000}s waiting for ${req.optString("method")}"
        return null
    }

    companion object {
        const val HANDSHAKE_TIMEOUT_MS = 20_000L
        const val TOOL_TIMEOUT_MS = 20_000L
        const val CALL_TIMEOUT_MS = 120_000L
    }
}
