package com.ctodrs.saqrcode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Planner
import com.ctodrs.saqrcode.ui.chat.PlanReviewCard
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-ldltr-w411dp-h891dp-night-xxhdpi")
class V0110PlanModeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `edited plan is trimmed, blanks dropped, capped`() {
        val p = Planner.fromEdited(listOf("  a   b ", "", "   ", "c"))!!
        assertEquals(listOf("a b", "c"), p.tasks)
        assertNull(Planner.fromEdited(listOf(" ", "")))
        assertEquals(Planner.MAX_TEAM_TASKS, Planner.fromEdited((1..20).map { "t$it" })!!.tasks.size)
    }

    @Test fun `move reorders and ignores out of range`() {
        assertEquals(listOf("b", "a", "c"), Planner.move(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("a", "b", "c"), Planner.move(listOf("a", "b", "c"), 0, -1))
        assertEquals(listOf("a", "c", "b"), Planner.move(listOf("a", "b", "c"), 2, -1))
    }

    @Test fun `run and cancel report the decision, controls are 48dp`() {
        val item = ChatItem.PlanReview(listOf("Explore files", "Implement"))
        var got: Boolean? = null
        rule.setContent { SaqrTheme("dark") { PlanReviewCard(item) { _, ok -> got = ok } } }
        rule.onAllNodesWithDesc("Remove step")[0].assertExists()
        rule.onAllNodesWithDesc("Move step down")[0].assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Run plan").performClick()
        assertEquals(true, got)
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(false, got)
    }

    @Test fun `session repair cancels a pending plan so the loop never hangs`() {
        val item = ChatItem.PlanReview(listOf("x"))
        com.ctodrs.saqrcode.agent.SessionRepair.repair(listOf(item))
        assertEquals(ChatItem.PlanReview.Status.CANCELLED, item.status)
        assertTrue(item.deferred.isCompleted)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithDesc(d: String) =
    onAllNodes(androidx.compose.ui.test.hasContentDescription(d))
