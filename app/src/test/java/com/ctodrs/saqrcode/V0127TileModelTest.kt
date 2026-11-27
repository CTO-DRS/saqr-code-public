package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.TileModel
import com.ctodrs.saqrcode.agent.TileModel.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.24: Quick Settings tile content. */
class V0127TileModelTest {
    @Test fun `idle tile opens the app`() {
        val m = TileModel.of(emptyList(), false)
        assertEquals(State.IDLE, m.state); assertEquals("Idle", m.subtitle); assertFalse(m.tapStops)
    }

    @Test fun `running tile stops the turn and names the project`() {
        val m = TileModel.of(listOf("shop" to false), false)
        assertEquals(State.RUNNING, m.state); assertEquals("Running: shop", m.subtitle); assertTrue(m.tapStops)
    }

    @Test fun `a turn waiting for the user wins and tap opens instead of stopping`() {
        val m = TileModel.of(listOf("a" to false, "b" to true), false)
        assertEquals(State.WAITING, m.state); assertEquals("Needs you: b +1", m.subtitle); assertFalse(m.tapStops)
    }

    @Test fun `arabic labels and long names are cut`() {
        val m = TileModel.of(listOf("a-very-long-project-name-indeed" to false), true)
        assertEquals("صقر", m.label); assertEquals("يشتغل: a-very-long-projec", m.subtitle)
        assertEquals("جاهز", TileModel.of(emptyList(), true).subtitle)
    }
}
