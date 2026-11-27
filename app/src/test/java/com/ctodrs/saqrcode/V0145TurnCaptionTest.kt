package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.TurnCaption
import com.ctodrs.saqrcode.agent.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v0.1.45: the per-reply caption under each answer. */
class V0145TurnCaptionTest {
    @Test fun `nothing reported shows nothing`() {
        assertNull(TurnCaption.format(null, 0.01))
        assertNull(TurnCaption.format(Usage(0, 0), null))
    }

    @Test fun `tokens and cost`() {
        assertEquals("1.2k · \$0.0041", TurnCaption.format(Usage(1000, 200), 0.0041))
    }

    @Test fun `unpriced model leaves the cost out`() {
        assertEquals("850", TurnCaption.format(Usage(800, 50), null))
        assertEquals("850", TurnCaption.format(Usage(800, 50), 0.0))
    }

    @Test fun `estimates carry a tilde and big numbers compact`() {
        assertEquals("~2k", TurnCaption.format(Usage(1500, 500, estimated = true), null))
        assertEquals("1.5M · \$3.20", TurnCaption.format(Usage(1_000_000, 500_000), 3.2))
    }
}
