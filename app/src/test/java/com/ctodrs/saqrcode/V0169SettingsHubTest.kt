package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ui.SETTINGS_CATS
import com.ctodrs.saqrcode.ui.settingsCatOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.69: Settings is a hub of 8 category pages; every old section lives in exactly one of them. */
class V0169SettingsHubTest {
    private val allKeys = listOf(
        "appearance", "editor_section", "providers", "agent_section", "mcp_section", "build_section", "voice_section",
        "desert_section", "slash_section", "pack_section", "gallery_section", "skills_section", "sync_section",
        "usage_section", "cloud_section", "device_section", "accounts", "billing_section", "coach_title", "guide_section", "about", "about_developer",
    )

    @Test fun sevenCategories() {
        assertEquals(9, SETTINGS_CATS.size)
        assertEquals(9, SETTINGS_CATS.map { it.first }.toSet().size)
    }

    @Test fun everySectionMapsToARealCategory() {
        val cats = SETTINGS_CATS.map { it.first }.toSet()
        allKeys.forEach { assertTrue(it, settingsCatOf(it) in cats) }
        assertEquals(cats, allKeys.map { settingsCatOf(it) }.toSet())
    }

    @Test fun providersAndUsageShareTheModelsPage() {
        assertEquals("models", settingsCatOf("providers"))
        assertEquals("models", settingsCatOf("usage_section"))
        assertEquals("project", settingsCatOf("sync_section"))
        assertEquals("tools", settingsCatOf("mcp_section"))
    }
}

class V0169PairTest {
    @Test fun parsesPairLink() {
        assertEquals("p1.1795863257000.Ab_-9", com.ctodrs.saqrcode.cloud.FreeAi.parsePairCode("saqr://pair?code=p1.1795863257000.Ab_-9"))
        assertEquals("p1.17.x", com.ctodrs.saqrcode.cloud.FreeAi.parsePairCode("saqr://pair?a=b&code=p1.17.x"))
    }

    @Test fun rejectsOtherLinks() {
        assertEquals(null, com.ctodrs.saqrcode.cloud.FreeAi.parsePairCode("saqr://oauth/callback?code=abc"))
        assertEquals(null, com.ctodrs.saqrcode.cloud.FreeAi.parsePairCode("saqr://pair?code=i1.abc.def"))
        assertEquals(null, com.ctodrs.saqrcode.cloud.FreeAi.parsePairCode("https://evil.example/pair?code=p1.1.a"))
    }
}

class V0170FreeAiBusyTest {
    @Test fun allProvidersBusyIsARateLimitNotAServerFault() {
        val k = com.ctodrs.saqrcode.agent.ErrorExplainer.classify(RuntimeException("HTTP 503: all providers busy"))
        assertEquals(com.ctodrs.saqrcode.agent.ErrorExplainer.Kind.RATE_LIMIT, k)
    }

    @Test fun noTestRunnerCountsAsFailure() {
        assertTrue(com.ctodrs.saqrcode.agent.LoopDetector.isFailure("No test runner detected (no package.json test script)."))
    }
}
