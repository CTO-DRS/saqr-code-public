package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.SessionCodec
import com.ctodrs.saqrcode.agent.SessionData
import com.ctodrs.saqrcode.agent.SessionMeta
import com.ctodrs.saqrcode.agent.SessionStore
import com.ctodrs.saqrcode.data.ChatBackup
import com.ctodrs.saqrcode.data.SyncException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class V0133ChatBackupTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pass = "correct horse battery"

    private fun save(root: File, project: String, id: String, at: Long, title: String = "t-$id") {
        val store = SessionStore(File(root, project))
        val meta = SessionMeta(id, title, false, at, at, 1, "")
        store.save(SessionCodec.encode(SessionData(meta, emptyList()), store.images), makeLast = false)
    }

    @Test fun `round trip restores sessions into an empty device`() {
        val a = File(tmp.root, "a").apply { mkdirs() }
        save(a, "app1", "s1", 10); save(a, "app1", "s2", 20); save(a, "app2", "s3", 30)
        val box = ChatBackup.seal(a, pass, 99)
        assertFalse("ciphertext must not contain titles", box.contains("t-s1"))
        val b = File(tmp.root, "b").apply { mkdirs() }
        val r = ChatBackup.restore(b, box, pass)
        assertEquals(3, r.sessions); assertEquals(0, r.skipped)
        assertEquals(listOf("s2", "s1"), SessionStore(File(b, "app1")).list().map { it.id })
        assertEquals(listOf("s3"), SessionStore(File(b, "app2")).list().map { it.id })
    }

    @Test fun `restore never overwrites a newer or equal session`() {
        val a = File(tmp.root, "a").apply { mkdirs() }
        save(a, "p", "s1", 10, "old title")
        val box = ChatBackup.seal(a, pass, 1)
        val b = File(tmp.root, "b").apply { mkdirs() }
        save(b, "p", "s1", 50, "newer here")
        val r = ChatBackup.restore(b, box, pass)
        assertEquals(0, r.sessions); assertEquals(1, r.skipped)
        assertEquals("newer here", SessionStore(File(b, "p")).list().single().title)
    }

    @Test fun `an older session on this device is replaced by the newer backup`() {
        val a = File(tmp.root, "a").apply { mkdirs() }
        save(a, "p", "s1", 90, "from backup")
        val box = ChatBackup.seal(a, pass, 1)
        val b = File(tmp.root, "b").apply { mkdirs() }
        save(b, "p", "s1", 5, "stale")
        assertEquals(1, ChatBackup.restore(b, box, pass).sessions)
        assertEquals("from backup", SessionStore(File(b, "p")).list().single().title)
    }

    @Test fun `images travel with the backup`() {
        val a = File(tmp.root, "a").apply { mkdirs() }
        save(a, "p", "s1", 10)
        File(a, "p/img").mkdirs(); File(a, "p/img/abc.png").writeBytes(byteArrayOf(1, 2, 3))
        val b = File(tmp.root, "b").apply { mkdirs() }
        assertEquals(1, ChatBackup.restore(b, ChatBackup.seal(a, pass, 1), pass).images)
        assertEquals(listOf<Byte>(1, 2, 3), File(b, "p/img/abc.png").readBytes().toList())
    }

    @Test fun `wrong passphrase and foreign containers fail honestly`() {
        val a = File(tmp.root, "a").apply { mkdirs() }
        save(a, "p", "s1", 10)
        val box = ChatBackup.seal(a, pass, 1)
        val b = File(tmp.root, "b").apply { mkdirs() }
        try { ChatBackup.restore(b, box, "another passphrase"); fail() } catch (e: SyncException) { assertEquals("passphrase", e.reason) }
        try { ChatBackup.restore(b, "hello", pass); fail() } catch (e: SyncException) { assertEquals("container", e.reason) }
        try { ChatBackup.restore(b, box, "short"); fail() } catch (e: SyncException) { assertTrue(e.reason == "passphrase" || e.reason == "short") }
    }

    @Test fun `a settings snapshot is not accepted as a chat backup`() {
        val other = com.ctodrs.saqrcode.data.SyncKit.encrypt("{\"kind\":\"settings\",\"v\":1}", pass)
        try { ChatBackup.restore(File(tmp.root, "b"), other, pass); fail() } catch (e: SyncException) { assertEquals("container", e.reason) }
    }

    @Test fun `hostile project and image names stay inside the chats directory`() {
        val plain = """{"kind":"saqr-chats","v":1,"projects":{"../evil":{"sessions":[],"images":{"../../x.png":"AQID","a.sh":"AQID"}}}}"""
        val box = com.ctodrs.saqrcode.data.SyncKit.encrypt(plain, pass)
        val root = File(tmp.root, "root").apply { mkdirs() }
        ChatBackup.restore(root, box, pass)
        assertFalse(File(tmp.root, "evil").exists())
        assertFalse(File(tmp.root, "x.png").exists())
        assertFalse(File(root, "evil/img/a.sh").exists())
    }
}
