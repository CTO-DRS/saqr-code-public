package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.Dictation
import com.ctodrs.saqrcode.agent.NotifText
import com.ctodrs.saqrcode.agent.WakePhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * v0.13.4 «صوت بلا يدين · Hands-free voice»: wake phrase, dictation policy,
 * command buffer, settings persistence and the bilingual notification.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0134FeaturesTest {

    private fun settings() = com.ctodrs.saqrcode.data.SettingsStore(
        RuntimeEnvironment.getApplication(), com.ctodrs.saqrcode.data.SecureStore(RuntimeEnvironment.getApplication()))

    // ------------------------------------------------- wake phrase «يا صقر»

    @Test
    fun `wake phrase in plain arabic yields the command`() {
        assertEquals("افتح الملف الرئيسي", WakePhrase.after("يا صقر افتح الملف الرئيسي"))
        assertEquals("", WakePhrase.after("يا صقر"))
    }

    @Test
    fun `wake phrase survives harakat alef forms and fusion`() {
        assertEquals("شغّل الاختبارات", WakePhrase.after("يَا صَقَر شغّل الاختبارات"))
        assertEquals("افتح الطرفية", WakePhrase.after("ياصقر افتح الطرفية"))
        assertEquals("ابنِ المشروع", WakePhrase.after("يا صَقرا ابنِ المشروع"))
    }

    @Test
    fun `wake phrase works in latin spellings case-insensitively`() {
        assertEquals("run the tests", WakePhrase.after("Ya Saqr run the tests"))
        assertEquals("open git", WakePhrase.after("hey saqer open git"))
        assertEquals("commit", WakePhrase.after("OK SAQR commit"))
    }

    @Test
    fun `no wake phrase means null and no false positives`() {
        assertNull(WakePhrase.after("افتح الملف من فضلك"))
        assertNull(WakePhrase.after("يا اخي ساعدني"))
        assertNull(WakePhrase.after("الصقر طائر جميل"))
        assertNull(WakePhrase.after("صقر افتح الملف")) // name without «يا» is not the wake
        assertNull(WakePhrase.after(""))
        assertNull(WakePhrase.after("   "))
    }

    @Test
    fun `wake phrase inside a longer sentence is still caught`() {
        assertEquals("افتح الإعدادات", WakePhrase.after("من فضلك يا صقر افتح الإعدادات"))
    }

    @Test
    fun `heardIn mirrors after`() {
        assertTrue(WakePhrase.heardIn("يا صقر ماذا ترى"))
        assertFalse(WakePhrase.heardIn("مرحبا بك"))
    }

    // ------------------------------------------------- dictation policy

    @Test
    fun `mode keys round-trip and unknown values fall back to off`() {
        assertEquals(Dictation.Mode.OFF, Dictation.Mode.from(""))
        assertEquals(Dictation.Mode.TYPING, Dictation.Mode.from("on"))
        assertEquals(Dictation.Mode.COMMAND, Dictation.Mode.from("wake"))
        assertEquals(Dictation.Mode.OFF, Dictation.Mode.from(null))
        assertEquals(Dictation.Mode.OFF, Dictation.Mode.from("bogus"))
        assertEquals("", Dictation.Mode.OFF.key)
    }

    @Test
    fun `transient errors keep listening while fatal ones end the session`() {
        for (e in intArrayOf(
            android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            android.speech.SpeechRecognizer.ERROR_NETWORK,
            android.speech.SpeechRecognizer.ERROR_SERVER,
            android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            android.speech.SpeechRecognizer.ERROR_NO_MATCH,
            android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        )) assertTrue("error $e should be retryable", Dictation.retryable(e))

        for (e in intArrayOf(
            android.speech.SpeechRecognizer.ERROR_AUDIO,
            android.speech.SpeechRecognizer.ERROR_CLIENT,
            android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        )) assertFalse("error $e should be fatal", Dictation.retryable(e))
    }

    // ------------------------------------------------- command buffer

    @Test
    fun `a wake command accumulates and finalizes exactly once`() {
        val b = Dictation.CommandBuffer()
        assertFalse(b.armed)
        b.wake("")                                  // «يا صقر» with nothing after it
        assertTrue(b.armed)
        b.feed("افتح الملف")
        b.feed("افتح الملف الرئيسي")                 // partials replace, not append
        assertEquals("افتح الملف الرئيسي", b.draft)
        assertEquals("افتح الملف الرئيسي", b.finalize())
        assertFalse(b.armed)
        assertEquals("", b.draft)
    }

    @Test
    fun `a command in the same breath as the wake is captured directly`() {
        val b = Dictation.CommandBuffer()
        b.wake("شغّل البناء الآن")
        assertEquals("شغّل البناء الآن", b.draft)
        assertEquals("شغّل البناء الآن", b.finalize())
    }

    @Test
    fun `re-waking replaces the draft and feeding when unarmed is a no-op`() {
        val b = Dictation.CommandBuffer()
        b.feed("إهمال قبل الإيقاظ")                  // not armed yet
        assertEquals("", b.draft)
        b.wake("الأمر الأول")
        b.wake("الأمر الثاني")                       // re-wake: latest wins
        assertEquals("الأمر الثاني", b.finalize())
        b.feed("لا شيء")
        assertEquals("", b.draft)
    }

    @Test
    fun `the draft is capped so a long rambling never floods the composer`() {
        val b = Dictation.CommandBuffer(maxLen = 50)
        b.wake("ك".repeat(120))
        assertEquals(50, b.draft.length)
    }

    @Test
    fun `blank feeds never create a draft`() {
        val b = Dictation.CommandBuffer()
        b.wake("")
        b.feed("   ")
        assertEquals("", b.draft)
    }

    // ------------------------------------------------- settings + notification text

    @Test
    fun `hands-free mode persists in settings`() {
        val s = settings()
        assertEquals("", s.dictation)              // default off
        s.dictation = Dictation.Mode.COMMAND.key
        assertEquals("wake", s.dictation)
        s.dictation = Dictation.Mode.TYPING.key
        assertEquals("on", s.dictation)
        s.dictation = Dictation.Mode.OFF.key
        assertEquals("", s.dictation)
    }

    @Test
    fun `the dictation notification is bilingual`() {
        for (lang in listOf("ar", "ar-SA", "en", "en-US", "")) {
            assertTrue(NotifText.dictationTitle(lang).isNotBlank())
            assertTrue(NotifText.dictationBody(lang).isNotBlank())
        }
        assertTrue(NotifText.isArabic("ar"))
        assertFalse(NotifText.isArabic("en"))
    }
}
