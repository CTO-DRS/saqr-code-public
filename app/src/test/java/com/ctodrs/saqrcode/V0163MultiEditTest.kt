package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.AgentRole
import com.ctodrs.saqrcode.agent.ToolRegistry
import com.ctodrs.saqrcode.agent.Tools
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class V0163MultiEditTest {
    private fun edits(vararg p: Pair<String, String>) = JSONArray().also { a ->
        p.forEach { a.put(JSONObject().put("old_string", it.first).put("new_string", it.second)) }
    }

    @Test
    fun `edits apply in order and see earlier results`() {
        val out = Tools.applyEdits("a = 1\nb = 2\n", edits("a = 1" to "a = 10", "a = 10" to "a = 11", "b = 2" to "b = 3"))
        assertEquals("a = 11\nb = 3\n", out)
    }

    @Test
    fun `a failing edit names its number and changes nothing`() {
        try {
            Tools.applyEdits("x\ny\n", edits("x" to "X", "zzz" to "Z"))
            fail("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("edit #2 of 2"))
            assertTrue(e.message!!.contains("nothing was changed"))
        }
    }

    @Test
    fun `empty edits are refused`() {
        try { Tools.applyEdits("x", JSONArray()); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("empty")) }
    }

    @Test
    fun `wrong indentation still matches when unique`() {
        val src = "fun f() {\n    val a = 1\n    val b = 2\n}\n"
        val out = Tools.applyEdit(src, "val a = 1\nval b = 2", "val a = 5\nval b = 6", false)
        assertEquals("fun f() {\n    val a = 5\n    val b = 6\n}\n", out)
    }

    @Test
    fun `ambiguous or missing tolerant match is not guessed`() {
        val dup = "  x = 1\n\tx = 1\n"
        assertNull(Tools.lineTolerant(dup, "x = 1", "x = 2"))
        assertNull(Tools.lineTolerant("a\nb\n", "c", "d"))
        try { Tools.applyEdit(dup, "x = 1 ;", "x", false); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test
    fun `tool is registered, mutating and allowed for editing roles`() {
        assertTrue(Tools.MULTI_EDIT in Tools.MUTATING)
        assertTrue(Tools.specs.any { it.name == Tools.MULTI_EDIT })
        assertNotNull(ToolRegistry.get(Tools.MULTI_EDIT))
        assertTrue(AgentRole.DEVELOPER.tools.contains(Tools.MULTI_EDIT))
    }
}
