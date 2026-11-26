package com.ctodrs.saqrcode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.ui.chat.ChatSearchOverlay
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.19.8 Majlis: the search bar and export button, in RTL. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rSA-ldrtl")
class V0198SearchUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun asst(t: String) = ChatItem.Assistant().apply { text = t; streaming = false }
    private val items = listOf(ChatItem.User("أين الحزمة؟"), asst("هذه الحزمة"), ChatItem.User("شكرا"))

    private fun show() = rule.setContent { SaqrTheme("dark") { ChatSearchOverlay(items, rememberLazyListState(), "صقر") } }

    @Test fun `search and export buttons are 48dp and labelled`() {
        show()
        rule.onNodeWithContentDescription("بحث في المحادثة").assertWidthIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("تصدير المحادثة").assertWidthIsAtLeast(48.dp)
    }

    @Test fun `typing a query shows the match counter and no-result state`() {
        show()
        rule.onNodeWithContentDescription("بحث في المحادثة").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("الحزمة")
        rule.onNodeWithText("1/2").assertExists()
        rule.onNodeWithContentDescription("النتيجة التالية").performClick()
        rule.onNodeWithText("2/2").assertExists()
        rule.onNode(hasSetTextAction()).performTextInput("zzz")
        rule.onNodeWithText("لا نتائج").assertExists()
    }
}
