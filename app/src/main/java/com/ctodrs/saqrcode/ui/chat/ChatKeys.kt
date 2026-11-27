package com.ctodrs.saqrcode.ui.chat

/** Wave E: hardware-keyboard shortcuts for the chat box, as pure logic so they are unit-tested. */
object ChatKeys {
    enum class Action { SEND, STOP, NONE }

    /**
     * Ctrl+Enter (or Cmd+Enter) sends when there is something to send and no turn is running.
     * Esc stops a running turn. Plain Enter and Shift+Enter stay a newline, so nothing changes for touch typing.
     */
    fun decide(ctrlOrMeta: Boolean, enter: Boolean, escape: Boolean, running: Boolean, canSend: Boolean): Action = when {
        escape && running -> Action.STOP
        ctrlOrMeta && enter && !running && canSend -> Action.SEND
        else -> Action.NONE
    }
}
