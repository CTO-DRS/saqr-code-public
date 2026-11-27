package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.DirImageRepo
import com.ctodrs.saqrcode.agent.SessionCodec
import com.ctodrs.saqrcode.agent.SessionData
import com.ctodrs.saqrcode.agent.SessionMeta
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.50: a very long chat keeps unique list keys, and filtering and saving it stay fast. Bounds are generous so slow CI never flakes. */
class V0150LongChatTest {
    private val tmp: File = Files.createTempDirectory("saqr-long").toFile()

    @After fun tearDown() { tmp.deleteRecursively() }

    private fun chat(n: Int): List<ChatItem> {
        val out = ArrayList<ChatItem>()
        var i = 0
        while (out.size < n) {
            out += ChatItem.User("question $i")
            out += ChatItem.Assistant().also { it.text = if (i % 3 == 0) "" else "answer $i with some text"; it.streaming = false }
            out += ChatItem.Tool("read_file", "src/F$i.kt", "call-$i").also { it.result = "ok"; it.ok = true }
            out += ChatItem.Notice("note $i")
            i++
        }
        return out.take(n)
    }

    @Test fun `2000 mixed items have unique list keys`() {
        val items = chat(2000)
        assertEquals(2000, items.size)
        assertEquals(2000, items.map { it.key }.toSet().size)
    }

    @Test fun `hiding blank tool-only replies is quick on a 2000 item chat`() {
        val items = chat(2000)
        val t0 = System.nanoTime()
        repeat(50) {
            val shown = items.filter { it !is ChatItem.Assistant || it.visible }
            shown.indexOfLast { it is ChatItem.Assistant }
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("50 filters took $ms ms", ms < 2_000)
    }

    @Test fun `a 2000 item chat saves and loads quickly and loses nothing`() {
        val items = chat(2000)
        val repo = DirImageRepo(File(tmp, "img"))
        val meta = SessionMeta("long", "t", true, 1L, 2L, 1000, "")
        val t0 = System.nanoTime()
        val json = SessionCodec.encode(SessionData(meta, items), repo)
        val back = SessionCodec.decode(JSONObject(json.toString()), repo)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(2000, back.items.size)
        assertTrue("encode+decode took $ms ms", ms < 5_000)
    }
}
