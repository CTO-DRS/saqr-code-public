package com.ctodrs.saqrcode.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * v0.13.4 «صوت بلا يدين»: the continuous-dictation policy as pure logic — which mode is
 * stored, which recognizer errors justify re-listening, and how a wake-phrase command is
 * buffered from partial results until it is complete. Android wiring lives in ui/chat.
 */

object Dictation {
    /** Settings value stored in prefs; [OFF] is the default and [from] never throws. */
    enum class Mode(val key: String) {
        OFF(""),
        /** Continuous dictation types into the composer; the user sends manually. */
        TYPING("on"),
        /** Wake mode: only «يا صقر» starts a command, and the command sends itself. */
        COMMAND("wake");

        companion object {
            fun from(key: String?): Mode = entries.firstOrNull { it.key == key } ?: OFF
        }
    }

    /**
     * Whether continuous listening should restart after [error]
     * (a [android.speech.SpeechRecognizer] error code). Transient failures
     * (silence, network, busy service) retry; fatal ones (no permission, broken
     * client, audio device) end the session so the user sees a clear message.
     */
    fun retryable(error: Int): Boolean = when (error) {
        android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        android.speech.SpeechRecognizer.ERROR_NETWORK,
        android.speech.SpeechRecognizer.ERROR_SERVER,
        android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        android.speech.SpeechRecognizer.ERROR_NO_MATCH,
        android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> true
        else -> false
    }

    /**
     * Buffers one wake-phrase command across partial results. «يا صقر» arms the buffer,
     * every partial/final feeds it, re-waking replaces the draft, and [finalize] hands
     * the command over exactly once.
     */
    class CommandBuffer(private val maxLen: Int = 2_000) {
        /** True while a wake phrase has been heard and the command is still accumulating. */
        var armed by mutableStateOf(false)
            private set

        /** The accumulated command text so far (may be empty while the user is still talking). */
        var draft by mutableStateOf("")
            private set

        /** «يا صقر» was heard; [first] is whatever followed the phrase in the same breath. */
        fun wake(first: String) {
            armed = true
            draft = first.take(maxLen)
        }

        /** Feeds the latest partial/final [text] — it replaces the previous partial, standard recognizer semantics. */
        fun feed(text: String) {
            if (!armed || text.isBlank()) return
            draft = text.trim().take(maxLen)
        }

        /** Completes the command: returns it (blank when nothing was said) and disarms the buffer. */
        fun finalize(): String {
            val out = draft
            disarm()
            return out
        }

        /** Drops the draft without returning it. */
        fun disarm() { armed = false; draft = "" }
    }
}
