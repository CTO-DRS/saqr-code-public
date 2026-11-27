package com.ctodrs.saqrcode

import android.content.Context
import com.ctodrs.saqrcode.agent.FallbackTarget
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.data.SyncKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** v0.19 sync round: the v0.15–v0.18 behaviour switches and limits travel with the snapshot; secrets still do not. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V019SyncTest {

    private fun settings(): SettingsStore {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("saqr_settings", Context.MODE_PRIVATE).edit().clear().commit()
        return SettingsStore(app, SecureStore(app))
    }

    private fun customised(): SettingsStore = settings().apply {
        parallelTools = false; confirmRiskyCommands = false; smartLoopGuard = false; verifyBeforeDone = false
        retryLlm = false; fallbackModel = FallbackTarget("openai", "gpt-4o-mini")
        taskJournal = false; auditLog = false; untrustedMarking = false
        maxSteps = 55; maxMinutes = 90; maxCostUsd = 2.5
        chiefMode = true; teamMaxConcurrent = 2; teamCostEstimate = false; teamDisabledRoles = setOf("research", "security")
        memoryRelevance = true; memoryTopK = 12; sessionSummary = true; memoryLessons = false; globalMemory = true
        smartRouter = true; dailySpendCap = 1.5; monthlySpendCap = 20.0
        skillsEnabled = false; autoAttachSkills = true
    }

    @Test
    fun `new behaviour settings round trip through snapshot and apply`() {
        val src = customised()
        val payload = SyncKit.parse(SyncKit.toJson(SyncKit.snapshot(src, "phone-a", "0.19", 1L)))!!
        val dst = settings() // defaults
        SyncKit.apply(payload, dst)
        assertFalse(dst.parallelTools); assertFalse(dst.confirmRiskyCommands); assertFalse(dst.smartLoopGuard)
        assertFalse(dst.verifyBeforeDone); assertFalse(dst.retryLlm); assertFalse(dst.taskJournal)
        assertFalse(dst.auditLog); assertFalse(dst.untrustedMarking)
        assertEquals(FallbackTarget("openai", "gpt-4o-mini"), dst.fallbackModel)
        assertEquals(55, dst.maxSteps); assertEquals(90, dst.maxMinutes); assertEquals(2.5, dst.maxCostUsd, 0.0)
        assertTrue(dst.chiefMode); assertEquals(2, dst.teamMaxConcurrent); assertFalse(dst.teamCostEstimate)
        assertEquals(setOf("research", "security"), dst.teamDisabledRoles)
        assertTrue(dst.memoryRelevance); assertEquals(12, dst.memoryTopK); assertTrue(dst.sessionSummary)
        assertFalse(dst.memoryLessons); assertTrue(dst.globalMemory); assertTrue(dst.smartRouter)
        assertEquals(1.5, dst.dailySpendCap, 0.0); assertEquals(20.0, dst.monthlySpendCap, 0.0)
        assertFalse(dst.skillsEnabled); assertTrue(dst.autoAttachSkills)
    }

    @Test
    fun `an older snapshot without the new keys leaves this device's values alone`() {
        val old = SyncKit.snapshot(settings(), "phone-old", "0.14", 1L)
        val withoutNew = old.copy(settings = old.settings.filterKeys { it in setOf("editor_font", "editor_wrap") })
        val dst = customised()
        SyncKit.apply(withoutNew, dst)
        assertEquals(55, dst.maxSteps)
        assertTrue(dst.chiefMode)
        assertEquals(setOf("research", "security"), dst.teamDisabledRoles)
    }

    @Test
    fun `broken numeric values are ignored and limits stay clamped`() {
        val p = SyncKit.snapshot(customised(), "d", "0.19", 1L)
        val bad = p.copy(settings = p.settings + mapOf("max_steps" to "not-a-number", "memory_top_k" to "99999", "max_minutes" to "-5"))
        val dst = settings()
        SyncKit.apply(bad, dst)
        assertEquals(settings().maxSteps, dst.maxSteps)
        assertTrue(dst.memoryTopK <= com.ctodrs.saqrcode.agent.memory.MemoryRecall.MAX_K)
        assertEquals(0, dst.maxMinutes)
    }

    @Test
    fun `secrets and installed skill bodies stay out of the whitelist`() {
        val keys = SyncKit.SETTINGS.map { it.key }
        assertEquals(keys.size, keys.toSet().size) // unique
        for (k in keys) assertFalse("whitelist key looks secret: $k", Regex("key|token|secret|password|auth|entitlement|skills_json|saqr_skills", RegexOption.IGNORE_CASE).containsMatchIn(k.replace("mcp_oauth_configs", "")))
        val s = customised()
        s.skillsJson = "[{\"secret-skill\":\"BODY-MARKER-123\"}]"
        val text = SyncKit.toJson(SyncKit.snapshot(s, "d", "0.19", 1L))
        assertFalse(text.contains("BODY-MARKER-123"))
    }

    @Test
    fun `stdio server env values never travel but local ones survive an apply`() {
        val src = settings()
        src.saveProvider("openai", "https://api.openai.com/v1", "gpt-4o", "sk-live-PROVIDERKEY-1234")
        src.mcpStdioServers = listOf(com.ctodrs.saqrcode.agent.McpStdio.StdioServer("gh", "npx srv", "GITHUB_TOKEN=ghp_SECRETVALUE_9999\nMODE=fast"))
        val text = SyncKit.toJson(SyncKit.snapshot(src, "d", "0.1.54", 1L))
        assertFalse(text.contains("ghp_SECRETVALUE_9999"))
        assertFalse(text.contains("sk-live-PROVIDERKEY-1234"))
        assertTrue(text.contains("GITHUB_TOKEN="))
        // the receiving device keeps its own secret for the same server and variable
        val dst = settings()
        dst.mcpStdioServers = listOf(com.ctodrs.saqrcode.agent.McpStdio.StdioServer("gh", "old", "GITHUB_TOKEN=local-secret-1\nOTHER=x"))
        SyncKit.apply(SyncKit.parse(text)!!, dst)
        val env = dst.mcpStdioServers.single().envText
        assertTrue(env.contains("GITHUB_TOKEN=local-secret-1"))
        assertFalse(env.contains("ghp_SECRETVALUE_9999"))
        assertEquals("npx srv", dst.mcpStdioServers.single().command)
    }

    @Test
    fun `mcp server url secrets are stripped for sync and refilled from the local copy`() {
        assertEquals("https://x.io/mcp?key=&mode=a#f", com.ctodrs.saqrcode.agent.Mcp.stripUrlSecrets("https://x.io/mcp?key=abc123&mode=a#f"))
        assertEquals("https://x.io/mcp", com.ctodrs.saqrcode.agent.Mcp.stripUrlSecrets("https://x.io/mcp"))
        val src = settings()
        src.mcpServers = listOf(com.ctodrs.saqrcode.agent.Mcp.Server("srv", "https://x.io/mcp?api_key=SECRETURLVALUE&v=2"))
        val text = SyncKit.toJson(SyncKit.snapshot(src, "d", "0.1.55", 1L))
        assertFalse(text.contains("SECRETURLVALUE"))
        assertTrue(text.contains("api_key="))
        val dst = settings()
        dst.mcpServers = listOf(com.ctodrs.saqrcode.agent.Mcp.Server("srv", "https://old.io/mcp?api_key=localkey1"))
        SyncKit.apply(SyncKit.parse(text)!!, dst)
        assertEquals("https://x.io/mcp?api_key=localkey1&v=2", dst.mcpServers.single().url)
    }

    @Test
    fun `the sync result says so when skills were added or refused`() {
        assertFalse(com.ctodrs.saqrcode.data.SyncApplied(5, 1, 2, 0).hasSkillNews)
        assertTrue(com.ctodrs.saqrcode.data.SyncApplied(5, 1, 2, 0, skills = 2).hasSkillNews)
        assertTrue(com.ctodrs.saqrcode.data.SyncApplied(5, 1, 2, 0, skillsRefused = 1).hasSkillNews)
        val a = SyncKit.apply(SyncKit.parse(SyncKit.toJson(SyncKit.snapshot(customised(), "d", "0.1.58", 1L)))!!, settings())
        assertFalse(a.hasSkillNews)
    }
}
