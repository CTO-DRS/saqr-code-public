package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.data.CrashLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** v0.1.30: the local crash recorder. */
class V0130CrashLogTest {
    private lateinit var dir: File

    @Before fun setUp() { dir = Files.createTempDirectory("saqr-crash").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test fun reportNamesVersionDeviceAndException() {
        val r = CrashLog.format("main", IllegalStateException("boom"), "0.1.30", "Pixel 8", 34, 0L)
        assertTrue(r.contains("Saqr Code 0.1.30"))
        assertTrue(r.contains("Pixel 8"))
        assertTrue(r.contains("API 34"))
        assertTrue(r.contains("1970-01-01 00:00:00 UTC"))
        assertTrue(r.contains("java.lang.IllegalStateException: boom"))
    }

    @Test fun tokensAreScrubbedFromMessagesAndCauses() {
        val token = "ghp_" + "a".repeat(30)
        val e = RuntimeException("push failed https://user:pw@github.com/x with $token", IllegalArgumentException("inner $token"))
        val r = CrashLog.format("io", e, "0.1.30", "d", 30, 1L)
        assertFalse(r.contains(token))
        assertTrue(r.contains("Caused by: java.lang.IllegalArgumentException"))
    }

    @Test fun reportIsCapped() {
        val e = RuntimeException("x".repeat(50_000))
        assertEquals(CrashLog.MAX_CHARS, CrashLog.format("t", e, "v", "d", 1, 0L).length)
    }

    @Test fun keepsOnlyTheNewestFiveAndReturnsTheLatest() {
        for (i in 1..8) CrashLog.record(dir, "report $i", 1_000L + i)
        assertEquals(CrashLog.KEEP, dir.listFiles()!!.size)
        assertEquals("report 8", CrashLog.latest(dir))
    }

    @Test fun clearRemovesEverythingAndLatestIsNull() {
        assertNull(CrashLog.latest(dir))
        CrashLog.record(dir, "r", 5L)
        CrashLog.clear(dir)
        assertNull(CrashLog.latest(dir))
    }

    @Test fun reportScrubsProviderKeysAndBearerTokens() {
        val t = RuntimeException("HTTP 401 for sk-proj-ABCDEFGH12345678abcd with Authorization: Bearer abcdef1234567890 and api_key=zzTOPSECRET99")
        val r = CrashLog.format("main", t, "0.1.56", "Pixel 8", 34, 0L)
        assertFalse(r.contains("sk-proj-ABCDEFGH12345678abcd"))
        assertFalse(r.contains("abcdef1234567890"))
        assertFalse(r.contains("zzTOPSECRET99"))
        assertTrue(r.contains("HTTP 401"))
    }
}
