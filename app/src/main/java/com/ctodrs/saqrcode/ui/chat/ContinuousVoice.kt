package com.ctodrs.saqrcode.ui.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ctodrs.saqrcode.agent.Dictation

/**
 * v0.13.4 «صوت بلا يدين»: keeps [VoiceInput] re-listening while [active], chaining one
 * recognizer utterance to the next so dictation runs continuously while a project is open.
 *
 * The [buffer] callback supplies the text each new session starts from (the composer text
 * in typing mode, nothing in wake mode), so the prefix is always current.
 *
 * Restart policy follows [Dictation.retryable]; consecutive failures back off
 * (350ms → 4s) and give up after 4 so a broken service ends with a clear message
 * instead of an endless loop.
 */
class ContinuousDictation(private val ctx: Context) {
    /** Mirrors the inner recognizer: live during a session, off in the brief restart gap. */
    val listening: Boolean get() = inner.listening
    /** 0..1 microphone level for the pulse. */
    val level: Float get() = inner.level

    /** True between start() and stop()/give-up, even during the brief restart gap. */
    var active by mutableStateOf(false)
        private set

    private val inner = VoiceInput(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private var buffer: (() -> String) = { "" }
    private var lang = com.ctodrs.saqrcode.agent.VoiceLocale.AR
    private var onText: ((String) -> Unit) = {}
    private var onFinal: ((String) -> Unit) = {}
    private var onError: ((Int) -> Unit) = {}
    private var restartDelay = 350L
    private var strikes = 0
    private var restartPending = false

    val available: Boolean get() = inner.available

    /** Starts (or is a no-op while already active). Callbacks run on the main thread. */
    fun start(
        lang: String,
        buffer: () -> String,
        onText: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (Int) -> Unit,
    ) {
        this.lang = lang
        this.buffer = buffer
        this.onText = onText
        this.onFinal = onFinal
        this.onError = onError
        if (active) return
        active = true
        strikes = 0
        restartDelay = 350L
        listen()
    }

    private fun listen() {
        if (!active || restartPending) return
        inner.start(lang, buffer(), onText = onText, onFinal = { text ->
            strikes = 0; restartDelay = 350L
            onFinal(text)
            scheduleRestart()
        }, onError = { code ->
            if (Dictation.retryable(code)) {
                strikes++
                if (strikes >= 4) { stop(); onError(code) } else {
                    onError(code)
                    scheduleRestart()
                }
            } else { stop(); onError(code) }
        })
    }

    private fun scheduleRestart() {
        if (!active || restartPending) return
        restartPending = true
        handler.postDelayed({
            restartPending = false
            listen()
        }, restartDelay)
        restartDelay = (restartDelay * 2).coerceAtMost(4_000L)
    }

    /** Stops listening for good; any running utterance is cancelled without a final result. */
    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        restartPending = false
        inner.cancel()
    }

    fun release() { stop(); inner.release() }
}
