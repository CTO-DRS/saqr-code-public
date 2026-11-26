package com.ctodrs.saqrcode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.agent.BranchOps
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.ui.chat.BranchSwitcher
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi")
class V0109BranchingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun asst(t: String) = ChatItem.Assistant().apply { text = t; streaming = false }
    private fun texts(items: List<ChatItem>) = items.map { when (it) { is ChatItem.User -> "U:" + it.text; is ChatItem.Assistant -> "A:" + it.text; else -> "?" } }

    @Test fun `edit keeps the old branch and switching restores it`() {
        val items = mutableListOf<ChatItem>(ChatItem.User("q1"), asst("a1"), ChatItem.User("q2"), asst("a2"), ChatItem.User("q3"), asst("a3"))
        val b = BranchOps.fork(items, 2)            // user edits q2
        assertEquals(listOf("U:q1", "A:a1"), texts(items))
        items += ChatItem.User("q2 edited"); items += asst("a2b")
        assertTrue(BranchOps.attach(items, 2, b))
        assertEquals(2, b.count); assertEquals(1, b.active)
        assertTrue(BranchOps.switch(items, 2, -1))   // back to the original
        assertEquals(listOf("U:q1", "A:a1", "U:q2", "A:a2", "U:q3", "A:a3"), texts(items))
        assertEquals(0, b.active)
        assertTrue(BranchOps.switch(items, 2, 1))    // forward again
        assertEquals(listOf("U:q1", "A:a1", "U:q2 edited", "A:a2b"), texts(items))
    }

    @Test fun `three branches wrap around and share one object`() {
        val items = mutableListOf<ChatItem>(ChatItem.User("q"), asst("one"))
        val b = BranchOps.fork(items, 0); items += ChatItem.User("q"); items += asst("two"); BranchOps.attach(items, 0, b)
        val b2 = BranchOps.fork(items, 0); items += ChatItem.User("q"); items += asst("three"); BranchOps.attach(items, 0, b2)
        assertSame(b, b2); assertEquals(3, b.count); assertEquals(2, b.active)
        BranchOps.switch(items, 0, 1); assertEquals("A:one", texts(items)[1]); assertEquals(0, b.active)
        BranchOps.switch(items, 0, -1); assertEquals("A:three", texts(items)[1])
        BranchOps.switch(items, 0, -1); assertEquals("A:two", texts(items)[1])
    }

    @Test fun `a send that did not happen puts the old branch back`() {
        val items = mutableListOf<ChatItem>(ChatItem.User("q"), asst("a"))
        val b = BranchOps.fork(items, 0)
        assertTrue(!BranchOps.attach(items, 0, b))
        BranchOps.undo(items, b)
        assertEquals(listOf("U:q", "A:a"), texts(items)); assertEquals(1, b.count)
    }

    @Test fun `switching with a single branch does nothing`() {
        val items = mutableListOf<ChatItem>(ChatItem.User("q"), asst("a"))
        assertTrue(!BranchOps.switch(items, 0, 1))
    }

    @Test fun `switcher shows the position and its buttons are 48dp and fire`() {
        var prev = 0; var next = 0
        rule.setContent { SaqrTheme("dark") { BranchSwitcher(2, 3, { prev++ }, { next++ }) } }
        rule.onNodeWithContentDescription("2 / 3").assertExists()
        rule.onNodeWithContentDescription("النسخة السابقة").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithContentDescription("النسخة التالية").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, prev); assertEquals(1, next)
    }
}
