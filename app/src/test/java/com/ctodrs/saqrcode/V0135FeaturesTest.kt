package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.DesertMode
import com.ctodrs.saqrcode.agent.DesertState
import com.ctodrs.saqrcode.agent.SessionCodec
import com.ctodrs.saqrcode.agent.SessionData
import com.ctodrs.saqrcode.agent.SessionMeta
import com.ctodrs.saqrcode.agent.SessionStore
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.Tools
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * v0.13.5 «وضع صحراوي · Desert mode»: the offline-first policy — mode storage and
 * activation, the cloud/local tool split, clear refusals for every cloud feature,
 * the cached-download rule for the on-device build, and settings persistence.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0135FeaturesTest {

    private fun settings() = com.ctodrs.saqrcode.data.SettingsStore(
        RuntimeEnvironment.getApplication(), com.ctodrs.saqrcode.data.SecureStore(RuntimeEnvironment.getApplication()))

    @After fun reset() { DesertState.mode = DesertMode.Mode.OFF; DesertState.online = true }

    // ------------------------------------------------- mode storage

    @Test
    fun `mode keys round trip and never throw`() {
        assertEquals(DesertMode.Mode.OFF, DesertMode.Mode.from(""))
        assertEquals(DesertMode.Mode.AUTO, DesertMode.Mode.from("auto"))
        assertEquals(DesertMode.Mode.ALWAYS, DesertMode.Mode.from("always"))
        assertEquals(DesertMode.Mode.OFF, DesertMode.Mode.from(null))
        assertEquals(DesertMode.Mode.OFF, DesertMode.Mode.from("junk"))
    }

    @Test
    fun `desert setting persists the mode key`() {
        val s = settings()
        assertEquals("", s.desert)
        s.desert = DesertMode.Mode.AUTO.key
        assertEquals("auto", s.desert)
        assertEquals(DesertMode.Mode.AUTO, DesertMode.Mode.from(s.desert))
        s.desert = ""
    }

    // ------------------------------------------------- activation

    @Test
    fun `off never activates even without network`() {
        assertFalse(DesertMode.active(DesertMode.Mode.OFF, online = true))
        assertFalse(DesertMode.active(DesertMode.Mode.OFF, online = false))
    }

    @Test
    fun `auto activates exactly while offline`() {
        assertFalse(DesertMode.active(DesertMode.Mode.AUTO, online = true))
        assertTrue(DesertMode.active(DesertMode.Mode.AUTO, online = false))
    }

    @Test
    fun `always activates even online`() {
        assertTrue(DesertMode.active(DesertMode.Mode.ALWAYS, online = true))
        assertTrue(DesertMode.active(DesertMode.Mode.ALWAYS, online = false))
    }

    @Test
    fun `live state mirrors mode and online`() {
        assertFalse(DesertState.active) // defaults: OFF + online
        DesertState.mode = DesertMode.Mode.AUTO
        assertFalse(DesertState.active)
        DesertState.online = false
        assertTrue(DesertState.active)
        DesertState.mode = DesertMode.Mode.ALWAYS
        DesertState.online = true
        assertTrue(DesertState.active)
    }

    // ------------------------------------------------- the cloud/local tool split

    @Test
    fun `web and remote git tools are cloud-bound`() {
        assertEquals(DesertMode.Feature.WEB_TOOLS, DesertMode.cloudTool(Tools.WEB_SEARCH))
        assertEquals(DesertMode.Feature.WEB_TOOLS, DesertMode.cloudTool(Tools.WEB_FETCH))
        assertEquals(DesertMode.Feature.GIT_REMOTE, DesertMode.cloudTool(Tools.GIT_PULL))
        assertEquals(DesertMode.Feature.GIT_REMOTE, DesertMode.cloudTool(Tools.GIT_PUSH))
    }

    @Test
    fun `local tools are never cloud-bound`() {
        val local = listOf(Tools.LIST, Tools.READ, Tools.WRITE, Tools.EDIT, Tools.DELETE, Tools.RUN,
            Tools.SEARCH, Tools.PROJECT_SEARCH, Tools.PLAN, Tools.ASK, Tools.MEMORY_SAVE,
            Tools.MEMORY_FORGET, Tools.GIT_STATUS, Tools.GIT_DIFF, Tools.GIT_LOG, Tools.GIT_COMMIT,
            Tools.GIT_BRANCH, Tools.LINT, Tools.TESTS)
        local.forEach { assertNull("local tool gated: $it", DesertMode.cloudTool(it)) }
    }

    @Test
    fun `stdio mcp tools are not classified as remote`() {
        // A stdio server tool rides the same mcp_ prefix; only the HTTP client layer is gated.
        assertNull(DesertMode.cloudTool("mcp_local_tools_list"))
    }

    // ------------------------------------------------- clear refusals

    @Test
    fun `every cloud feature refuses with a clear message while desert is active`() {
        for (f in listOf(DesertMode.Feature.CHAT, DesertMode.Feature.WEB_TOOLS,
                DesertMode.Feature.MCP_REMOTE, DesertMode.Feature.GIT_REMOTE)) {
            val v = DesertMode.gate(f, desert = true)
            assertFalse(f.name, v.allowed)
            assertTrue(f.name, v.message.isNotBlank())
            assertTrue(f.name, v.message.contains("desert mode"))
        }
    }

    @Test
    fun `every feature is allowed when the desert is not active`() {
        for (f in DesertMode.Feature.entries) {
            val v = DesertMode.gate(f, desert = false)
            assertTrue(f.name, v.allowed)
            assertEquals("", v.message)
        }
    }

    @Test
    fun `local never blocks even in the desert`() {
        assertTrue(DesertMode.gate(DesertMode.Feature.LOCAL, desert = true).allowed)
    }

    @Test
    fun `tool refusals name what still works`() {
        assertTrue(DesertMode.toolMessage(DesertMode.Feature.WEB_TOOLS).contains("local files"))
        assertTrue(DesertMode.toolMessage(DesertMode.Feature.GIT_REMOTE).contains("commit"))
        assertTrue(DesertMode.toolMessage(DesertMode.Feature.MCP_REMOTE).contains("stdio"))
    }

    // ------------------------------------------------- the on-device build

    @Test
    fun `downloads run online and stop only when needed and cached is missing`() {
        assertFalse(DesertMode.downloadAllowed(desert = true, cached = false))
        assertTrue(DesertMode.downloadAllowed(desert = true, cached = true))
        assertTrue(DesertMode.downloadAllowed(desert = false, cached = false))
        assertTrue(DesertMode.downloadAllowed(desert = false, cached = true))
    }

    @Test
    fun `the download refusal is readable`() {
        assertTrue(DesertMode.toolMessage(DesertMode.Feature.SDK_DOWNLOAD).contains("not cached"))
    }

    // ------------------------------------------------- the refusal survives a restart

    @Test
    fun `a desert notice survives save and restore`() {
        val dir = java.io.File(RuntimeEnvironment.getApplication().cacheDir, "v0135-" + System.nanoTime())
        dir.mkdirs()
        val store = SessionStore(dir)
        val data = SessionData(
            SessionMeta("s135", "Desert", false, 10L, 1000L, 1, ""),
            listOf(ChatItem.Notice("", kind = ChatItem.Notice.Kind.DESERT)),
            emptyList(), emptyList(), emptyMap(), active = true)
        store.save(SessionCodec.encode(data, store.images))
        val back = SessionStore(dir).load("s135")!!
        val notice = back.items.single() as ChatItem.Notice
        assertEquals(ChatItem.Notice.Kind.DESERT, notice.kind)
        dir.deleteRecursively()
    }
}
