package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ProviderKind
import com.ctodrs.saqrcode.agent.Providers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.67: the built-in free provider is present and needs no pasted key. */
class V0167FreeAiProviderTest {
    @Test fun freeProviderIsDefined() {
        val p = Providers.byId("saqr-free")
        assertEquals("saqr-free", p.id)
        assertEquals(ProviderKind.OPENAI_COMPAT, p.kind)
        assertTrue(!p.keyOptional)
        assertTrue(p.defaultBaseUrl.endsWith("/ai/v1"))
    }
    @Test fun providerIdsAreUnique() {
        assertEquals(Providers.all.size, Providers.all.map { it.id }.toSet().size)
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0167FreeAiAdoptTest {
    private fun settings(): com.ctodrs.saqrcode.data.SettingsStore {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.getSharedPreferences("saqr_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        return com.ctodrs.saqrcode.data.SettingsStore(app, com.ctodrs.saqrcode.data.SecureStore(app))
    }

    @Test fun freshInstallSwitchesToFreeAi() {
        val s = settings()
        s.activeProvider = "openrouter" // like the phone in the bug report: active, but no key
        assertTrue(s.adoptFreeAiIfNothingReady())
        assertEquals("saqr-free", s.activeProvider)
    }

    @Test fun neverOverridesAProviderWithAKey() {
        val s = settings()
        s.saveProvider("openai", "https://api.openai.com/v1", "gpt-4.1-mini", "sk-test")
        s.activeProvider = "openrouter"
        org.junit.Assert.assertFalse(s.adoptFreeAiIfNothingReady())
        assertEquals("openrouter", s.activeProvider)
    }

    @Test fun tokenMakesFreeAiReady() {
        val s = settings()
        s.activeProvider = "saqr-free"
        org.junit.Assert.assertFalse(s.activeReady())
        s.freeAiToken = "i1.abcdefghijklmnopqrstuvwxyz.sig"
        assertTrue(s.activeReady())
        assertEquals("i1.abcdefghijklmnopqrstuvwxyz.sig", s.apiKey("saqr-free"))
    }

    @Test fun parsesProxyToken() {
        assertEquals("i1.aaaaaaaaaaaaaaaaaaaa.bbbb", com.ctodrs.saqrcode.cloud.FreeAi.parseToken("{\"token\":\"i1.aaaaaaaaaaaaaaaaaaaa.bbbb\"}"))
        assertEquals(null, com.ctodrs.saqrcode.cloud.FreeAi.parseToken("{\"error\":\"x\"}"))
        assertEquals(null, com.ctodrs.saqrcode.cloud.FreeAi.parseToken("not json"))
        assertTrue(com.ctodrs.saqrcode.cloud.FreeAi.newInstallId().length >= 16)
    }
}
