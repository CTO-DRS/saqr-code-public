package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.LearningMode
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.agent.SystemPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** v0.1.17: learning mode prompt section and the /learn command. */
class V0118LearningModeTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun prompt(learning: Boolean, arabic: Boolean = true) =
        SystemPrompt.build(tmp.root, emptyList(), false, learning = learning, learningArabic = arabic)

    @Test
    fun `section appears only when learning mode is on`() {
        assertFalse(prompt(false).contains("LEARNING MODE"))
        assertTrue(prompt(true).contains("LEARNING MODE"))
    }

    @Test
    fun `arabic flag picks the explanation language`() {
        assertTrue(prompt(true, arabic = true).contains("clear Arabic"))
        val en = prompt(true, arabic = false)
        assertFalse(en.contains("clear Arabic"))
        assertTrue(en.contains("user's language"))
    }

    @Test
    fun `section keeps the work unchanged and asks for line by line`() {
        val s = LearningMode.section(true)
        assertTrue(s.contains("line by line"))
        assertTrue(s.contains("never a reason to skip"))
    }

    @Test
    fun `learn command parses in English and Arabic and is read-only`() {
        val en = SlashCommands.parse("/learn", emptyList())
        assertNotNull(en)
        assertEquals(SlashCommands.LEARN, en!!.command.name)
        val ar = SlashCommands.parse("/علمني", emptyList())
        assertNotNull(ar)
        assertEquals(SlashCommands.LEARN, ar!!.command.name)
        assertTrue(en.command.template.contains("Do not modify any files"))
        assertTrue(en.command.template.contains("line by line"))
    }
}
