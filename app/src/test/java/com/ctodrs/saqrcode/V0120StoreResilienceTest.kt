package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.SessionCodec
import com.ctodrs.saqrcode.agent.SessionData
import com.ctodrs.saqrcode.agent.SessionMeta
import com.ctodrs.saqrcode.agent.SessionStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** v0.1.18: the session store survives the app being killed mid-write and files damaged on disk. */
class V0120StoreResilienceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun save(store: SessionStore, id: String, at: Long) {
        val meta = SessionMeta(id, "t-$id", true, at, at, 1, "")
        store.save(SessionCodec.encode(SessionData(meta, emptyList()), store.images))
    }

    @Test fun `corrupt index is rebuilt from the session files`() {
        val dir = File(tmp.root, "s").apply { mkdirs() }
        val a = SessionStore(dir); save(a, "one", 10); save(a, "two", 20)
        File(dir, "index.json").writeText("{ this is not json")
        val b = SessionStore(dir)
        assertEquals(listOf("two", "one"), b.list().map { it.id })
        assertEquals("two", b.last())
    }

    @Test fun `a truncated session file is skipped and does not hide the others`() {
        val dir = File(tmp.root, "s").apply { mkdirs() }
        val a = SessionStore(dir); save(a, "good", 10); save(a, "bad", 20)
        File(dir, "bad.json").writeText("{\"meta\":{\"id\":\"bad\"")
        File(dir, "index.json").delete()
        val b = SessionStore(dir)
        assertEquals(listOf("good"), b.list().map { it.id })
        assertNull(b.load("bad"))
        assertNotNull(b.load("good"))
    }

    @Test fun `index entries whose file is gone are dropped`() {
        val dir = File(tmp.root, "s").apply { mkdirs() }
        val a = SessionStore(dir); save(a, "x", 10); save(a, "y", 20)
        File(dir, "y.json").delete()
        val b = SessionStore(dir)
        assertEquals(listOf("x"), b.list().map { it.id })
        assertEquals("x", b.last())
    }

    @Test fun `empty store has no last session and loads nothing`() {
        val s = SessionStore(File(tmp.root, "none"))
        assertTrue(s.list().isEmpty())
        assertNull(s.last())
        assertNull(s.load("missing"))
    }

    @Test fun `a session with damaged items still decodes the intact ones`() {
        val store = SessionStore(File(tmp.root, "s"))
        val meta = SessionMeta("m", "t", true, 1L, 2L, 1, "")
        val json = SessionCodec.encode(SessionData(meta, emptyList()), store.images)
        json.put("items", org.json.JSONArray().put("garbage").put(org.json.JSONObject().put("type", "unknown-future-type")))
        val d = SessionCodec.decode(json, store.images)
        assertEquals("m", d.meta.id)
    }
}
