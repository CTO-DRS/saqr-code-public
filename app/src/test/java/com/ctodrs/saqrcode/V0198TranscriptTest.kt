package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.FileChange
import com.ctodrs.saqrcode.ui.chat.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V0198TranscriptTest {
    private fun asst(t: String) = ChatItem.Assistant().apply { text = t; streaming = false }

    private val items = listOf(
        ChatItem.User("أين الحزمة؟"),
        ChatItem.Tool("run_command", "zip -r a.zip app\nsecond line"),
        ChatItem.Approval(listOf(FileChange("src/A.kt", null, "x"))).apply { decision = true },
        ChatItem.Approval(listOf(FileChange("src/Rejected.kt", null, "x"))).apply { decision = false },
        asst("تم إنشاء الحزمة"),
        ChatItem.User("Thanks"),
        asst(""),
    )

    @Test fun `search ignores case, diacritics, tatweel and alef forms`() {
        assertEquals(listOf(0, 4), Transcript.search(items, "الحزمة"))
        assertEquals(listOf(4), Transcript.search(items, "انشاء")) // plain alef finds "إنشاء"
        assertEquals(listOf(0, 4), Transcript.search(items, "الحَزْمَة")) // with tashkeel
        assertEquals(listOf(5), Transcript.search(items, "THANKS"))
    }

    @Test fun `short or empty queries and blank replies give nothing`() {
        assertTrue(Transcript.search(items, "").isEmpty()); assertTrue(Transcript.search(items, "ا").isEmpty())
        assertTrue(Transcript.search(items, "zzzz").isEmpty())
        assertFalse(Transcript.search(items, "Thanks").contains(6))
    }

    @Test fun `markdown transcript has messages, tool lines and only approved file edits`() {
        val md = Transcript.toMarkdown(items, "مشروعي", "أنت", "صقر")
        assertTrue(md.startsWith("# مشروعي\n"))
        assertTrue(md.contains("## أنت\n\nأين الحزمة؟"))
        assertTrue(md.contains("- `run_command: zip -r a.zip app`"))
        assertTrue(md.contains("- `edit: src/A.kt`"))
        assertFalse(md.contains("Rejected.kt")); assertFalse(md.contains("second line"))
        assertTrue(md.indexOf("## صقر") > md.indexOf("edit: src/A.kt"))
        assertEquals("# Saqr chat\n", Transcript.toMarkdown(emptyList(), ""))
    }
}
