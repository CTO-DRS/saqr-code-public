package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.cloud.*
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** v0.1.66: the proxy client against a local stand-in for the Worker. */
class V0166ProxyProviderTest {
    private val server = java.net.ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
    private val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
    @Volatile private var zipNames = listOf<String>()
    private val base get() = "http://127.0.0.1:${server.localPort}"

    init {
        Thread {
            while (!server.isClosed) {
                val sock = try { server.accept() } catch (e: Exception) { break }
                sock.use { s ->
                    val inp = java.io.BufferedInputStream(s.getInputStream())
                    fun line(): String { val sb = StringBuilder(); while (true) { val c = inp.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) sb.append(c.toChar()) }; return sb.toString() }
                    val first = line().split(" ")
                    val method = first[0]; val path = first[1]
                    var len = 0; var tok = ""
                    while (true) {
                        val h = line(); if (h.isEmpty()) break
                        val k = h.substringBefore(":").lowercase(); val v = h.substringAfter(":").trim()
                        if (k == "content-length") len = v.toInt()
                        if (k == "x-saqr-token") tok = v
                    }
                    val body = ByteArray(len); var got = 0
                    while (got < len) { val n = inp.read(body, got, len - got); if (n < 0) break; got += n }
                    seen += "$method $path"
                    val (code, out) = when {
                        tok != "good" -> 401 to """{"error":"unauthorized"}"""
                        path == "/open" -> 200 to """{"id":"sb1","state":"started"}"""
                        path == "/s/sb1/zip" -> {
                            ZipInputStream(body.inputStream()).use { z ->
                                val n = mutableListOf<String>(); var e = z.nextEntry
                                while (e != null) { n += e.name; e = z.nextEntry }
                                zipNames = n
                            }
                            200 to """{"ok":true,"status":200}"""
                        }
                        path == "/s/sb1/exec" -> 200 to """{"exit":0,"output":"line1\nline2\n","timedOut":false}"""
                        method == "DELETE" -> 200 to """{"ok":true}"""
                        else -> 404 to "{}"
                    }
                    val bytes = out.toByteArray()
                    s.getOutputStream().apply {
                        write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    @After fun stop() { server.close() }

    @Test fun `open upload exec close round trip`() = runBlocking {
        val sb = ProxyCloudProvider(base, "good").open()
        sb.upload(listOf(CloudFile("src/a.py", "x".toByteArray()), CloudFile("b.txt", "y".toByteArray())))
        val lines = mutableListOf<String>()
        val r = sb.exec("python src/a.py", 30) { lines += it }
        sb.close(); sb.close()
        assertEquals(0, r.exitCode)
        assertEquals(listOf("line1", "line2"), lines)
        assertEquals(listOf("src/a.py", "b.txt"), zipNames)
        assertEquals(1, seen.count { it == "DELETE /s/sb1" })
        assertTrue(seen.first() == "POST /open")
    }

    @Test fun `a wrong token says so in plain words`() = runBlocking {
        try { ProxyCloudProvider(base, "bad").open(); fail("expected CloudException") }
        catch (e: CloudException) { assertTrue(e.message, e.message!!.contains("token")) }
    }

    @Test fun `an unreachable proxy is a plain error, not a crash`() = runBlocking {
        try { ProxyCloudProvider("http://127.0.0.1:1", "good").open(); fail("expected CloudException") }
        catch (e: CloudException) { assertTrue(e.message!!.contains("reach")) }
    }
}
