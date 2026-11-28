package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.cloud.*
import com.ctodrs.saqrcode.data.MemoryKeyValue
import org.junit.Assert.*
import org.junit.Test

class V0178CloudSecretsTest {
    @Test fun names() {
        assertTrue(ProjectSecrets.validName("NPM_TOKEN"))
        assertFalse(ProjectSecrets.validName("npm"))
        assertFalse(ProjectSecrets.validName("A B"))
        assertFalse(ProjectSecrets.validName("A;rm"))
    }

    @Test fun envPrefixQuotes() {
        assertEquals("export A='it'\\''s'; ", ProjectSecrets.envPrefix(mapOf("A" to "it's")))
        assertEquals("", ProjectSecrets.envPrefix(emptyMap()))
        assertEquals("", ProjectSecrets.envPrefix(mapOf("bad name" to "x")))
    }

    @Test fun redactsValuesNotNames() {
        val env = mapOf("TOKEN" to "abcd1234", "SHORT" to "xy")
        assertEquals("key=***TOKEN*** and xy", ProjectSecrets.redact("key=abcd1234 and xy", env))
    }

    @Test fun storeRoundTrip() {
        val s = ProjectSecretStore(MemoryKeyValue())
        assertTrue(s.put("B_KEY", "v2")); assertTrue(s.put("A_KEY", "v1")); assertFalse(s.put("bad", "x")); assertFalse(s.put("C", " "))
        assertEquals(listOf("A_KEY", "B_KEY"), s.list())
        assertEquals(mapOf("A_KEY" to "v1", "B_KEY" to "v2"), s.all())
        s.remove("A_KEY")
        assertEquals(listOf("B_KEY"), s.list())
        assertNull(s.all()["A_KEY"])
    }
}
