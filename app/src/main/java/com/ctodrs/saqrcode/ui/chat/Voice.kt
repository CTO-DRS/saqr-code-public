package com.ctodrs.saqrcode.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ctodrs.saqrcode.agent.SpeechText
import com.ctodrs.saqrcode.agent.VoiceLocale
import java.util.Locale

/** What the composer shows about dictation. */
data class VoiceUi(
    val available: Boolean = true,
    val listening: Boolean = false,
    /** ar-SA / en-US */
    val lang: String = VoiceLocale.EN,
    /** 0..1 microphone level for the pulse. */
    val level: Float = 0f,
)

/**
 * Android SpeechRecognizer wrapper: live partial results through [onText] (prefix + partial),
 * the final text through [onFinal]. Must be used on the main thread.
 */
class VoiceInput(private val ctx: Context) {
    var listening by mutableStateOf(false); private set
    var level by mutableFloatStateOf(0f); private set
    private var recognizer: SpeechRecognizer? = null
    private var prefix = ""
    private var last = ""

    val available: Boolean get() = runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)

    /** [onError] gets a [SpeechRecognizer] error code (or -1 when no service is installed). */
    fun start(lang: String, currentText: String, onText: (String) -> Unit, onFinal: (String) -> Unit, onError: (Int) -> Unit) {
        if (listening) return
        if (!available) { onError(-1); return }
        prefix = currentText; last = ""
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { level = 0f }
            override fun onError(error: Int) {
                listening = false; level = 0f
                // "no match" after some partial text still counts as what the user said
                if (last.isNotBlank()) onFinal(VoiceLocale.merge(prefix, last)) else onError(error)
            }
            override fun onResults(results: Bundle?) {
                listening = false; level = 0f
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { last }
                onFinal(VoiceLocale.merge(prefix, text))
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) { last = text; onText(VoiceLocale.merge(prefix, text)) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        listening = true
        runCatching { r.startListening(intent) }.onFailure { listening = false; onError(SpeechRecognizer.ERROR_CLIENT) }
    }

    /** Tap again: stop listening; the recognizer still delivers the final result. */
    fun stop() { if (listening) runCatching { recognizer?.stopListening() } }

    fun cancel() { runCatching { recognizer?.cancel() }; listening = false; level = 0f }

    fun release() { runCatching { recognizer?.destroy() }; recognizer = null; listening = false }
}

/** "Read aloud" for assistant messages (TextToSpeech; Arabic voice when the text is Arabic and one is installed). */
class Speaker(private val ctx: Context) {
    /** Key of the chat item being read, or -1. */
    var speakingKey by mutableLongStateOf(-1L); private set
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    private fun engine(then: () -> Unit) {
        if (ready && tts != null) { then(); return }
        pending = then
        if (tts == null) tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId?.endsWith(":last") == true) speakingKey = -1L }
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { speakingKey = -1L }
                    override fun onError(utteranceId: String?, errorCode: Int) { speakingKey = -1L }
                })
                pending?.invoke()
            } else onFail?.invoke(Problem.NO_ENGINE)
            pending = null
        }
    }

    enum class Problem { NO_ENGINE, NO_ARABIC_VOICE, NOTHING_TO_READ }
    var onFail: ((Problem) -> Unit)? = null

    fun toggle(key: Long, markdown: String) {
        if (speakingKey == key) { stop(); return }
        val text = SpeechText.clean(markdown)
        if (text.isBlank()) { onFail?.invoke(Problem.NOTHING_TO_READ); return }
        engine {
            val t = tts ?: return@engine
            val arabic = SpeechText.language(text) == "ar"
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.US
            val ok = t.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
            if (arabic && !ok) onFail?.invoke(Problem.NO_ARABIC_VOICE)
            if (ok) t.language = locale
            t.stop()
            val parts = SpeechText.chunks(text, minOf(SpeechText.MAX_CHUNK, TextToSpeech.getMaxSpeechInputLength() - 100))
            speakingKey = key
            parts.forEachIndexed { i, p ->
                t.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$key:$i" + if (i == parts.lastIndex) ":last" else "")
            }
        }
    }

    fun stop() { runCatching { tts?.stop() }; speakingKey = -1L }

    fun release() { runCatching { tts?.shutdown() }; tts = null; ready = false; speakingKey = -1L }
}
