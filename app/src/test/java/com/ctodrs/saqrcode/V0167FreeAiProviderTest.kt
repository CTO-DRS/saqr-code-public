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
        assertTrue(p.keyOptional)
        assertTrue(p.defaultBaseUrl.endsWith("/ai/v1"))
    }
    @Test fun providerIdsAreUnique() {
        assertEquals(Providers.all.size, Providers.all.map { it.id }.toSet().size)
    }
}
