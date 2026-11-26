package com.ctodrs.saqrcode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ctodrs.saqrcode.agent.PromptLibrary
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.ui.chat.PromptLibraryContent
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rSA-ldrtl")
class V0103PromptLibraryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val mine = SlashCommand("ship", "Release the app.\nSecond line")

    @Test fun `library lists prompts only and includes custom ones`() {
        val names = PromptLibrary.prompts(listOf(mine)).map { it.name }
        assertTrue("ship" in names && "refactor" in names && "debug" in names)
        assertFalse("clear" in names || "model" in names)
    }

    @Test fun `filter matches name alias and description, arabic-insensitive`() {
        val all = PromptLibrary.prompts(emptyList())
        val d = { c: SlashCommand -> if (c.name == "perf") "Find and fix performance problems" else "" }
        assertEquals(listOf("perf"), PromptLibrary.filter(all, "/perf", d).map { it.name })
        assertEquals(listOf("secure"), PromptLibrary.filter(all, "أمن", d).map { it.name })
        assertEquals(listOf("perf"), PromptLibrary.filter(all, "performance", d).map { it.name })
        assertEquals(all, PromptLibrary.filter(all, "  ", d))
        assertTrue(PromptLibrary.filter(all, "zzzz", d).isEmpty())
    }

    @Test fun `insertion leaves the composer ready for arguments and parses back`() {
        val c = SlashCommands.builtins.first { it.name == "refactor" }
        val text = PromptLibrary.insertion(c)
        assertEquals("/refactor ", text)
        assertEquals("refactor", SlashCommands.parse(text + "the parser")?.command?.name)
        assertEquals("refactor", SlashCommands.parse("/حسن")?.command?.name)
    }

    @Test fun `tapping a row picks that command`() {
        var picked: String? = null
        rule.setContent { SaqrTheme("dark") { PromptLibraryContent(listOf(mine)) { picked = it.name } } }
        rule.onNodeWithText("/fix").performClick()
        assertEquals("fix", picked)
    }
}
