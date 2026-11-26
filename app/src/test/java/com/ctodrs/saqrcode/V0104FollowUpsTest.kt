package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.FollowUps
import com.ctodrs.saqrcode.agent.FollowUps.Kind
import com.ctodrs.saqrcode.agent.Tools
import org.junit.Assert.assertEquals
import org.junit.Test

class V0104FollowUpsTest {
    private fun tool(n: String, ok: Boolean = true) = ChatItem.Tool(n, "x").apply { result = if (ok) "ok" else "boom"; this.ok = ok }
    private fun reply(t: String = "Done.") = ChatItem.Assistant().apply { text = t; streaming = false }
    private val u = ChatItem.User("go")

    @Test fun `nothing while running or streaming or empty`() {
        assertEquals(emptyList<Kind>(), FollowUps.suggest(listOf(u, tool(Tools.EDIT), reply()), running = true))
        assertEquals(emptyList<Kind>(), FollowUps.suggest(listOf(u, ChatItem.Assistant().apply { text = "hi"; streaming = true }), running = false))
        assertEquals(emptyList<Kind>(), FollowUps.suggest(emptyList(), running = false))
        assertEquals(emptyList<Kind>(), FollowUps.suggest(listOf(u, reply("short answer")), running = false))
    }

    @Test fun `a failed command offers fix only`() {
        assertEquals(listOf(Kind.FIX_ERROR), FollowUps.suggest(listOf(u, tool(Tools.RUN, ok = false), reply()), running = false))
    }

    @Test fun `file edits offer tests review and commit`() {
        assertEquals(listOf(Kind.RUN_TESTS, Kind.REVIEW, Kind.COMMIT), FollowUps.suggest(listOf(u, tool(Tools.EDIT), reply()), running = false))
    }

    @Test fun `tests already run after the edit and commit done are not offered again`() {
        val tested = listOf(u, tool(Tools.EDIT), tool(Tools.TESTS), reply())
        assertEquals(listOf(Kind.REVIEW, Kind.COMMIT), FollowUps.suggest(tested, running = false))
        val committed = listOf(u, tool(Tools.EDIT), tool(Tools.TESTS), tool(Tools.GIT_COMMIT), reply())
        assertEquals(listOf(Kind.REVIEW), FollowUps.suggest(committed, running = false))
        val testedBefore = listOf(u, tool(Tools.TESTS), tool(Tools.EDIT), reply())
        assertEquals(Kind.RUN_TESTS, FollowUps.suggest(testedBefore, running = false).first())
    }

    @Test fun `only the latest turn counts`() {
        val items = listOf(u, tool(Tools.EDIT), reply(), ChatItem.User("explain"), tool(Tools.READ), reply("x".repeat(600)))
        assertEquals(listOf(Kind.SUMMARIZE, Kind.NEXT_STEPS), FollowUps.suggest(items, running = false))
    }
}
