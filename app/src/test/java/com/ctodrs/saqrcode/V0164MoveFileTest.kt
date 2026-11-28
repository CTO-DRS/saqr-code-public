package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.AgentRole
import com.ctodrs.saqrcode.agent.ToolRegistry
import com.ctodrs.saqrcode.agent.Tools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V0164MoveFileTest {
    @Test
    fun `valid move has no problem`() = assertNull(Tools.moveProblem("a.txt", "b/a.txt", true, false))

    @Test
    fun `same path, missing source and taken destination are refused`() {
        assertTrue(Tools.moveProblem("a", "a", true, false)!!.contains("same"))
        assertTrue(Tools.moveProblem("a", "b", false, false)!!.contains("does not exist"))
        assertTrue(Tools.moveProblem("a", "b", true, true)!!.contains("already exists"))
    }

    @Test
    fun `move tool is registered, mutating and allowed for developers`() {
        assertTrue(Tools.MOVE in Tools.MUTATING)
        val spec = Tools.specs.single { it.name == Tools.MOVE }
        assertTrue(spec.parameters.toString().contains("\"to\""))
        assertNotNull(ToolRegistry.get(Tools.MOVE))
        assertTrue(AgentRole.DEVELOPER.tools.contains(Tools.MOVE))
        assertEquals(Tools.specs.size, Tools.specs.map { it.name }.toSet().size)
    }
}
