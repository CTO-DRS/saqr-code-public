package com.ctodrs.saqrcode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Rewind
import com.ctodrs.saqrcode.ui.chat.ChatRow
import com.ctodrs.saqrcode.ui.chat.MessageActionBar
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.19.7 Majlis: regenerate and edit-and-resend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rSA-ldrtl")
class V0197RewindTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun asst() = ChatItem.Assistant().apply { text = "رد"; streaming = false }

    @Test fun `rewind finds the last user message and a specific user message by identity`() {
        val u1 = ChatItem.User("one"); val u2 = ChatItem.User("one")
        val items = listOf(u1, asst(), ChatItem.Tool("read_file", "a"), u2, asst())
        assertEquals(3, Rewind.lastUserIndex(items))
        assertEquals(0, Rewind.indexOfUser(items, u1)) // equal text, different object: identity wins
        assertEquals(-1, Rewind.indexOfUser(items, ChatItem.User("one")))
        assertEquals(-1, Rewind.lastUserIndex(listOf(asst())))
        assertTrue(Rewind.NOTE_REGENERATE.contains("regenerated") && Rewind.NOTE_EDIT.contains("edited"))
    }

    @Test fun `regenerate button is 48dp, labelled and fires`() {
        var n = 0
        rule.setContent { SaqrTheme("dark") { MessageActionBar("نص", 0, {}, false, null, onRegenerate = { n++ }) } }
        rule.onNodeWithContentDescription("إعادة التوليد").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, n)
    }

    @Test fun `no regenerate button when the action is not offered`() {
        rule.setContent { SaqrTheme("dark") { MessageActionBar("نص", 0, {}, false, null) } }
        assertTrue(rule.onAllNodesWithContentDescriptionCount("إعادة التوليد") == 0)
    }

    @Test fun `edit opens a dialog and resends the text`() {
        var sent: String? = null
        val u = ChatItem.User("اشرح لي الكود")
        rule.setContent { SaqrTheme("dark") { ChatRow(u, { _, _ -> }, onEditResend = { _, t -> sent = t }) } }
        rule.onNodeWithContentDescription("تعديل وإعادة الإرسال").assertWidthIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("تعديل الرسالة").assertWidthIsAtLeast(10.dp)
        rule.onNodeWithText("إرسال").performClick()
        assertEquals("اشرح لي الكود", sent)
    }

    @Test fun `no edit button while the offer is withheld`() {
        rule.setContent { SaqrTheme("dark") { ChatRow(ChatItem.User("x"), { _, _ -> }) } }
        assertEquals(0, rule.onAllNodesWithContentDescriptionCount("تعديل وإعادة الإرسال"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithContentDescriptionCount(d: String) =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(d)).fetchSemanticsNodes().size
}
