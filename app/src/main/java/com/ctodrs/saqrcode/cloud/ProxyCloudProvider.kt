package com.ctodrs.saqrcode.cloud

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * v0.1.65 · The real provider: a thin client for our own token proxy (a free Cloudflare Worker
 * in front of Daytona). The app only knows the proxy address and a shared token; the Daytona
 * key lives in the Worker as a secret and never reaches the phone.
 *
 * The snapshot goes up as one zip, is unpacked into ~/work on the sandbox, and every command
 * runs there. Output arrives when the command finishes, then is replayed line by line.
 */
class ProxyCloudProvider(private val baseUrl: String, private val token: String) : CloudProvider {
    override suspend fun open(): CloudSandbox {
        val r = call("POST", "/open", null, null, 60_000)
        val id = r.optString("id")
        if (id.isBlank()) throw CloudException(r.optString("detail").ifBlank { "The cloud machine did not start (${r.optString("error", "no reply")})." })
        return ProxySandbox(id)
    }

    private inner class ProxySandbox(val id: String) : CloudSandbox {
        private var closed = false

        override suspend fun upload(files: List<CloudFile>) {
            val zip = ByteArrayOutputStream()
            ZipOutputStream(zip).use { z ->
                files.forEach { f ->
                    z.putNextEntry(ZipEntry(f.path.trimStart('/')))
                    z.write(f.bytes)
                    z.closeEntry()
                }
            }
            val up = call("PUT", "/s/$id/zip", zip.toByteArray(), "application/zip", 120_000)
            if (!up.optBoolean("ok")) throw CloudException("Upload to the cloud machine failed (${up.optString("error", up.optString("status"))}).")
            val un = exec("rm -rf \"\$HOME/work\"; mkdir -p \"\$HOME/work\" && cd \"\$HOME/work\" && (unzip -oq /tmp/snap.zip || python3 -m zipfile -e /tmp/snap.zip .)", 120)
            if (un.exitCode != 0) throw CloudException("Could not unpack the project on the cloud machine.")
        }

        override suspend fun exec(command: String, timeoutSec: Int, onLine: (String) -> Unit): CloudExecResult {
            val started = System.currentTimeMillis()
            val body = JSONObject().put("command", "cd \"\$HOME/work\" 2>/dev/null; $command").put("timeout", timeoutSec).toString().toByteArray()
            val r = call("POST", "/s/$id/exec", body, "application/json", (timeoutSec + 30) * 1000L)
            if (r.has("error")) throw CloudException(r.optString("detail").ifBlank { "Cloud command failed (${r.optString("error")})." })
            val out = r.optString("output")
            out.lineSequence().forEach { if (it.isNotEmpty()) onLine(it) }
            return CloudExecResult(r.optInt("exit", -1), out, r.optBoolean("timedOut"), System.currentTimeMillis() - started)
        }

        override suspend fun close() {
            if (closed) return
            closed = true
            runCatching { call("DELETE", "/s/$id", null, null, 30_000) }
        }
    }

    private suspend fun call(method: String, path: String, body: ByteArray?, type: String?, timeoutMs: Long): JSONObject =
        withContext(Dispatchers.IO) {
            val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 15_000
                c.readTimeout = timeoutMs.toInt()
                c.setRequestProperty("x-saqr-token", token)
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("content-type", type ?: "application/octet-stream")
                    c.outputStream.use { it.write(body) }
                }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                if (code == 401) throw CloudException("The cloud service refused the token. Check the cloud token in Settings.")
                runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", "HTTP $code") }
            } catch (e: CloudException) {
                throw e
            } catch (e: Exception) {
                throw CloudException("Could not reach the cloud service (${e.javaClass.simpleName}).")
            } finally {
                c.disconnect()
            }
        }
}
