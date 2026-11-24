package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.MemoryBank
import com.ctodrs.saqrcode.agent.SystemPrompt
import com.ctodrs.saqrcode.agent.Tools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** v0.13 «عقل الصقر» · memory bank: Arabic keys, upsert, cap, recall budget, secrets guard, prompt injection. */
class V013FeaturesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var n = 0
    private fun bank(): MemoryBank = MemoryBank(java.io.File(tmp.root, ".saqr${n++}").apply { mkdirs() })

    // ------------------------------------------------------------ Arabic keys

    @Test
    fun `normalization unifies alef forms, taa marbouta, yaa and diacritics`() {
        // alef forms + shadda + tatweel all collapse to one key
        assertEquals(MemoryBank.normalize("التطبيق"), MemoryBank.normalize("التّطبيق"))
        assertEquals(MemoryBank.normalize("التطبيق"), MemoryBank.normalize("إلتطبيق"))
        assertEquals(MemoryBank.normalize("التطبيق"), MemoryBank.normalize("ألتطبيق"))
        // ى → ي
        assertEquals(MemoryBank.normalize("على"), MemoryBank.normalize("علي"))
        // ة → ه
        assertEquals(MemoryBank.normalize("الشبكة"), MemoryBank.normalize("الشبكه"))
        // diacritics and tatweel are noise
        assertEquals(MemoryBank.normalize("مُشْتَرَك"), MemoryBank.normalize("مشترك"))
        assertEquals(MemoryBank.normalize("مشــترك"), MemoryBank.normalize("مشترك"))
        // spaces and punctuation never split a key
        assertEquals(MemoryBank.normalize("لغة الواجهة"), MemoryBank.normalize("لغةـالواجهة"))
        assertTrue(MemoryBank.normalize("…!!").isEmpty())
    }

    // ------------------------------------------------------------ save / upsert / cap

    @Test
    fun `saving the same key twice updates the entry and keeps one`() {
        val b = bank()
        assertTrue(b.save("قاعدة البيانات", "نستعمل SQLite", MemoryBank.Kind.DECISION).startsWith("Remembered"))
        val first = b.all.single()
        val createdAt = first.createdAt
        Thread.sleep(5) // updatedAt must move
        b.save("قاعده البيانات", "نستعمل Room بدل SQLite", MemoryBank.Kind.DECISION) // ة/ه variant = same key
        assertEquals(1, b.count)
        val e = b.all.single()
        assertEquals("نستعمل Room بدل SQLite", e.value)
        assertEquals(createdAt, e.createdAt)
        assertTrue(e.updatedAt >= first.updatedAt)
    }

    @Test
    fun `bank holds at most 60 entries and evicts the oldest update`() {
        val b = bank()
        b.save("key-1", "oldest", MemoryBank.Kind.FACT)
        Thread.sleep(5) // key-1 must stay strictly the oldest-updated
        (2..MemoryBank.MAX_ENTRIES).forEach { b.save("key-$it", "v$it", MemoryBank.Kind.FACT) }
        assertEquals(MemoryBank.MAX_ENTRIES, b.count)
        Thread.sleep(5)
        b.save("مفتاح جديد", "قيمة", MemoryBank.Kind.FACT) // pushes out key-1 (oldest)
        assertEquals(MemoryBank.MAX_ENTRIES, b.count)
        assertTrue("key-1 was evicted", b.all.none { it.key == "key-1" })
        assertNotNull(b.search("مفتاح جديد").firstOrNull())
    }

    @Test
    fun `secrets are refused and nothing is stored`() {
        val b = bank()
        val r1 = b.save("كلمة السر", "hunter2", MemoryBank.Kind.FACT)
        val r2 = b.save("api token", "Bearer ghp_ABCDEFGHIJKLMNOPQRSTUVWX", MemoryBank.Kind.FACT)
        assertTrue(r1.startsWith("Refused"))
        assertTrue(r2.startsWith("Refused"))
        assertTrue(b.isEmpty())
        assertTrue(b.save("password = 123456", "x", MemoryBank.Kind.FACT).startsWith("Refused"))
        assertTrue(b.save("كلمه السر", "x", MemoryBank.Kind.FACT).startsWith("Refused")) // normalized variant
    }

    @Test
    fun `empty or oversized input is rejected with a message`() {
        val b = bank()
        assertTrue(b.save("", "x", MemoryBank.Kind.FACT).startsWith("Error"))
        assertTrue(b.save("k", " ", MemoryBank.Kind.FACT).startsWith("Error"))
        assertTrue(b.save("k", "x".repeat(MemoryBank.MAX_VALUE_LEN + 1), MemoryBank.Kind.FACT).startsWith("Error"))
        assertTrue(b.save("…", "x", MemoryBank.Kind.FACT).startsWith("Error")) // no letters/digits
    }

    // ------------------------------------------------------------ forget / search

    @Test
    fun `forget matches the exact key first, then substring over key and value`() {
        val b = bank()
        b.save("إدارة الحالة", "Riverpod", MemoryBank.Kind.DECISION)
        b.save("اللغة", "كل الردود بالعربية", MemoryBank.Kind.PREFERENCE)
        // exact (normalized) removes only that one
        assertTrue(b.forget("اداره الحاله").startsWith("Forgot"))
        assertEquals(1, b.count)
        // substring over the value
        assertTrue(b.forget("بالعربية").startsWith("Forgot"))
        assertEquals(0, b.count)
        // nothing matches → unchanged
        assertTrue(b.forget("غير موجود").startsWith("Nothing matched"))
        assertTrue(b.forget("").startsWith("Error"))
    }

    @Test
    fun `search is normalized so Arabic variants match`() {
        val b = bank()
        b.save("الشبكة", "Retrofit + OkHttp", MemoryBank.Kind.FACT)
        assertNotNull(b.search("الشبكه").firstOrNull())
        assertNotNull(b.search("retrofit").firstOrNull())
        assertTrue(b.search("django").isEmpty())
        assertTrue(b.search("").size == 1) // empty query returns everything
    }

    // ------------------------------------------------------------ render / recall

    @Test
    fun `render groups preference, decision, fact and respects the budget`() {
        val b = bank()
        b.save("اللغة", "عربي دائمًا", MemoryBank.Kind.PREFERENCE)
        b.save("state", "Riverpod", MemoryBank.Kind.DECISION)
        b.save("entry", "main.dart", MemoryBank.Kind.FACT)
        val out = b.render()
        assertTrue(out.contains("User preferences"))
        assertTrue(out.contains("Decisions"))
        assertTrue(out.contains("Project facts"))
        val iPref = out.indexOf("User preferences")
        val iDec = out.indexOf("Decisions")
        val iFact = out.indexOf("Project facts")
        assertTrue(iPref < iDec && iDec < iFact)
        assertTrue(out.contains("اللغة — عربي دائمًا"))
        // a tiny budget keeps only the top group
        val small = b.render(maxChars = 40)
        assertFalse(small.contains("Project facts"))
        assertTrue(small.length <= 40)
        // empty bank renders nothing
        assertEquals("", bank().render())
    }

    // ------------------------------------------------------------ persistence

    @Test
    fun `memory survives a restart and recovers from a corrupt file`() {
        val dir = tmp.newFolder(".saqr2")
        val b = MemoryBank(dir)
        b.save("الطقم", "Compose", MemoryBank.Kind.DECISION)
        // a new instance reads the same file
        val b2 = MemoryBank(dir)
        assertEquals(1, b2.count)
        assertEquals("Compose", b2.all.single().value)
        assertEquals(MemoryBank.Kind.DECISION, b2.all.single().kind)
        // corrupt the file: the bank starts empty and the next save repairs it
        java.io.File(dir, MemoryBank.FILE_NAME).writeText("{not json")
        val b3 = MemoryBank(dir)
        assertTrue(b3.isEmpty())
        b3.save("k", "v", MemoryBank.Kind.FACT)
        assertEquals(1, MemoryBank(dir).count)
    }

    // ------------------------------------------------------------ wiring

    @Test
    fun `memory tools are exposed to the model with required arguments`() {
        val save = Tools.specs.firstOrNull { it.name == Tools.MEMORY_SAVE }
        val forget = Tools.specs.firstOrNull { it.name == Tools.MEMORY_FORGET }
        assertNotNull(save)
        assertNotNull(forget)
        val req = save!!.parameters.optJSONArray("required")
        assertEquals(2, req!!.length())
        assertEquals("key", req.getString(0)); assertEquals("value", req.getString(1))
        val kinds = save.parameters.optJSONObject("properties")!!.optJSONObject("kind")
        assertTrue(kinds!!.optJSONArray("enum").let { a -> (0 until a.length()).any { a.getString(it) == "preference" } })
        val fReq = forget!!.parameters.optJSONArray("required")
        assertEquals(1, fReq!!.length()); assertEquals("key", fReq.getString(0))
    }

    @Test
    fun `system prompt carries the memory section only when the bank is not empty`() {
        val dir = tmp.newFolder("proj")
        val empty = SystemPrompt.build(dir, emptyList(), linuxReady = false)
        assertFalse(empty.contains("# MEMORY (remembered"))

        val bank = MemoryBank(java.io.File(dir, ".saqr"))
        bank.save("اللغة", "الردود بالعربية", MemoryBank.Kind.PREFERENCE)
        val full = SystemPrompt.build(dir, emptyList(), linuxReady = false, memory = bank.render())
        assertTrue(full.contains("# MEMORY (remembered"))
        assertTrue(full.contains("اللغة"))
        assertTrue(full.contains("memory_save"))
    }
}
