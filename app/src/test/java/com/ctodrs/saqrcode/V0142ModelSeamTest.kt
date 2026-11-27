package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.LlmException
import com.ctodrs.saqrcode.agent.ModelClient
import com.ctodrs.saqrcode.agent.ModelFactory
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.StreamListener
import com.ctodrs.saqrcode.agent.Turn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** v0.1.42: the model seam lets the agent loop be driven by a scripted fake. */
class V0142ModelSeamTest {
    /** Replays [script] turn by turn and records every request it receives. */
    private class ScriptedModel(private val script: List<Result<Turn>>) : ModelClient {
        val systems = mutableListOf<String>()
        private var i = 0
        override suspend fun complete(system: String, messages: List<Msg>, tools: List<com.ctodrs.saqrcode.agent.ToolSpec>, l: StreamListener): Turn {
            systems += system
            val r = script[i++]
            val t = r.getOrThrow()
            l.onText(t.text)
            return t
        }
    }

    private fun turn(text: String) = Turn(text, emptyList(), "stop")

    @Test fun `a fake model streams text to the listener and returns the turn`() = runBlocking {
        val m = ScriptedModel(listOf(Result.success(turn("hello"))))
        val got = StringBuilder()
        val t = m.complete("sys", emptyList(), emptyList(), object : StreamListener { override fun onText(delta: String) { got.append(delta) } })
        assertEquals("hello", t.text)
        assertEquals("hello", got.toString())
        assertEquals(listOf("sys"), m.systems)
    }

    @Test fun `a scripted failure surfaces as the provider error`() = runBlocking {
        val m = ScriptedModel(listOf(Result.failure(LlmException("HTTP 503: overloaded"))))
        try {
            m.complete("s", emptyList(), emptyList(), object : StreamListener { override fun onText(delta: String) {} })
            fail("expected failure")
        } catch (e: LlmException) {
            assertTrue(e.message!!.contains("503"))
        }
    }

    @Test fun `the factory hands out the fake for any provider and model`() {
        val fake = ScriptedModel(emptyList())
        val f = ModelFactory { _, _, _, _ -> fake }
        val def = Providers.all.first()
        assertTrue(f.create(def, "http://x", "m1", null) === fake)
        assertTrue(f.create(def, "http://y", "m2", "k") === fake)
    }

    @Test fun `the default factory builds the real client behind the same interface`() {
        val def = Providers.all.first()
        val c: ModelClient = ModelFactory.DEFAULT.create(def, "http://localhost", "m", null)
        assertTrue(c is com.ctodrs.saqrcode.agent.LlmClient)
    }
}
