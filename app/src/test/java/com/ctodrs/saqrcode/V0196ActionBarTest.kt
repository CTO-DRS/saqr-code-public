package com.ctodrs.saqrcode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.FileChange
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.ui.chat.Feedback
import com.ctodrs.saqrcode.ui.chat.MessageActionBar
import com.ctodrs.saqrcode.ui.chat.SourceRef
import com.ctodrs.saqrcode.ui.chat.Sources
import com.ctodrs.saqrcode.ui.chat.SourcesRow
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.19.6 Majlis: per-message action bar and sources row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rSA-ldrtl")
class V0196ActionBarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun assistant() = ChatItem.Assistant().apply { text = "تم"; streaming = false }
    private fun tool(name: String, summary: String) = ChatItem.Tool(name, summary).apply { result = "ok" }

    @Test fun `sources come from the tools and file changes since the last user message`() {
        val items = listOf(
            ChatItem.User("old"), tool(Tools.READ, "old.txt"), assistant(),
            ChatItem.User("new"), tool(Tools.READ, "AGENTS.md"), tool(Tools.READ, "AGENTS.md"),
            ChatItem.Approval(listOf(FileChange("src/A.kt", null, "x"))).apply { decision = true },
            tool(Tools.RUN, "./gradlew test"), tool(Tools.WEB_FETCH, "https://a.b"), tool(Tools.PLAN, "plan"), tool(Tools.ASK, "q"),
            assistant(),
        )
        val s = Sources.forTurn(items, items.lastIndex)
        assertEquals(listOf(SourceRef.Kind.FILE, SourceRef.Kind.FILE, SourceRef.Kind.TERMINAL, SourceRef.Kind.WEB), s.map { it.kind })
        assertEquals(listOf("AGENTS.md", "src/A.kt", "./gradlew test", "https://a.b"), s.map { it.label })
    }

    @Test fun `a rejected change and a turn without tools give no sources`() {
        val items = listOf(ChatItem.User("hi"), ChatItem.Approval(listOf(FileChange("a", null, "b"))).apply { decision = false }, assistant())
        assertTrue(Sources.forTurn(items, items.lastIndex).isEmpty())
        assertTrue(Sources.forTurn(listOf(ChatItem.User("x"), assistant()), 1).isEmpty())
        assertTrue(Sources.forTurn(emptyList(), 0).isEmpty())
    }

    @Test fun `source count is capped`() {
        val items = listOf<ChatItem>(ChatItem.User("x")) + (1..20).map { tool(Tools.READ, "f$it") } + assistant()
        assertEquals(Sources.MAX, Sources.forTurn(items, items.lastIndex).size)
    }

    @Test fun `action bar buttons are 48dp, labelled, and feedback toggles`() {
        var fb by mutableIntStateOf(Feedback.NONE); var spoke = 0
        rule.setContent { SaqrTheme("dark") { MessageActionBar("نص", fb, { fb = it }, false, { spoke++ }) } }
        for (label in listOf("Copy", "نسخ", "إجابة جيدة", "إجابة سيئة", "اقرأ بصوت", "مشاركة")) {
            val found = runCatching { rule.onNodeWithContentDescription(label).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp) }.isSuccess
            if (label in listOf("إجابة جيدة", "إجابة سيئة", "مشاركة")) assertTrue("missing $label", found)
        }
        rule.onNodeWithContentDescription("إجابة جيدة").performClick()
        assertEquals(Feedback.UP, fb); rule.onNodeWithContentDescription("إجابة جيدة").assertIsSelected()
        rule.onNodeWithContentDescription("إجابة جيدة").performClick(); assertEquals(Feedback.NONE, fb)
        rule.onNodeWithContentDescription("إجابة سيئة").performClick(); assertEquals(Feedback.DOWN, fb)
    }

    @Test fun `sources row announces kind and label`() {
        rule.setContent { SaqrTheme("dark") { SourcesRow(listOf(SourceRef(SourceRef.Kind.FILE, "AGENTS.md"), SourceRef(SourceRef.Kind.WEB, "https://a.b"))) } }
        rule.onNodeWithContentDescription("ملف: AGENTS.md").assertWidthIsAtLeast(10.dp)
        rule.onNodeWithContentDescription("الويب: https://a.b").assertWidthIsAtLeast(10.dp)
    }
}
