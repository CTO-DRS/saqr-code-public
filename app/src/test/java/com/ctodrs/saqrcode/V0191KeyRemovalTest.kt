package com.ctodrs.saqrcode

import android.content.Context
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** v0.19.1: the Settings «حذف المفتاح · Remove key» button calls clearKey; it must drop only that provider's key. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0191KeyRemovalTest {
    private fun settings(): SettingsStore {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("saqr_settings", Context.MODE_PRIVATE).edit().clear().commit()
        return SettingsStore(app, SecureStore(app))
    }

    @Test
    fun `clearKey removes the key and keeps url and model`() {
        val s = settings()
        s.saveProvider("openai", "https://api.openai.com/v1", "gpt-4o", "sk-test-123")
        assertTrue(s.config("openai").hasKey)
        s.clearKey("openai")
        assertFalse(s.config("openai").hasKey)
        assertNull(s.apiKey("openai"))
        assertEquals("gpt-4o", s.config("openai").model)
    }

    @Test
    fun `clearKey leaves other providers alone and is safe twice`() {
        val s = settings()
        s.saveProvider("openai", "https://api.openai.com/v1", "gpt-4o", "sk-a")
        s.saveProvider("anthropic", "https://api.anthropic.com", "claude", "sk-b")
        s.clearKey("openai"); s.clearKey("openai")
        assertEquals("sk-b", s.apiKey("anthropic"))
        assertFalse(s.config("openai").hasKey)
    }
}
