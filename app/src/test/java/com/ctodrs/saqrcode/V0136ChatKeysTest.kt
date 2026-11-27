package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ui.chat.ChatKeys
import com.ctodrs.saqrcode.ui.chat.ChatKeys.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class V0136ChatKeysTest {
    private fun d(ctrl: Boolean = false, enter: Boolean = false, esc: Boolean = false, running: Boolean = false, can: Boolean = true) =
        ChatKeys.decide(ctrl, enter, esc, running, can)

    @Test fun ctrlEnterSends() = assertEquals(Action.SEND, d(ctrl = true, enter = true))
    @Test fun plainEnterIsANewline() = assertEquals(Action.NONE, d(enter = true))
    @Test fun ctrlEnterWithNothingToSendDoesNothing() = assertEquals(Action.NONE, d(ctrl = true, enter = true, can = false))
    @Test fun ctrlEnterWhileRunningDoesNotSendASecondTurn() = assertEquals(Action.NONE, d(ctrl = true, enter = true, running = true))
    @Test fun escapeStopsARunningTurn() = assertEquals(Action.STOP, d(esc = true, running = true))
    @Test fun escapeWhenIdleDoesNothing() = assertEquals(Action.NONE, d(esc = true))
    @Test fun otherKeysDoNothing() = assertEquals(Action.NONE, d(ctrl = true))
}
