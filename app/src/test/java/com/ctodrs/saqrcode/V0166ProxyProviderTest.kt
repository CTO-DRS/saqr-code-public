package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.cloud.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** v0.1.66: the proxy client against a local stand-in for the Worker. */
class V0166ProxyProviderTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val seen = mutableListOf<String>()
    private var zipNames = listOf<String>()
    private val base get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { ex ->
            val tok = ex.requestHeaders.getFirst("x-saqr-token")
            val path = ex.requestURI.path
            val body = ex.requestBody.readBytes()
            seen += "${ex.requestMethod} $path"
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
                ex.requestMethod == "DELETE" -> 200 to """{"ok":true}"""
                else -> 404 to "{}"
            }
            val bytes = out.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After fun stop() { server.stop(0) }

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
