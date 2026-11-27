package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.VoiceCommands
import com.ctodrs.saqrcode.agent.VoiceCommands.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.12: Gulf-dialect voice commands map to app actions, whole-utterance only. */
class V0113VoiceCommandsTest {

    @Test
    fun `gulf stop spellings all stop the agent`() {
        for (p in listOf("وقف", "قِف", "توقف", "بس", "خلاص", "كفاية", "بطّل", "أوقف")) {
            assertEquals(p, Command.STOP, VoiceCommands.match(p))
        }
    }

    @Test
    fun `dialect variants of send undo regenerate read and sleep`() {
        assertEquals(Command.SEND, VoiceCommands.match("ابعت"))
        assertEquals(Command.SEND, VoiceCommands.match("أرسل"))
        assertEquals(Command.UNDO, VoiceCommands.match("رجّع"))
        assertEquals(Command.REGENERATE, VoiceCommands.match("عيد"))
        assertEquals(Command.REGENERATE, VoiceCommands.match("مرة ثانية"))
        assertEquals(Command.READ, VoiceCommands.match("اقرى"))
        assertEquals(Command.MUTE, VoiceCommands.match("اسكت"))
        assertEquals(Command.NEW_CHAT, VoiceCommands.match("محادثة جديدة"))
        assertEquals(Command.SESSIONS, VoiceCommands.match("ورّيني المحادثات"))
        assertEquals(Command.CLEAR, VoiceCommands.match("امسح"))
        assertEquals(Command.SLEEP, VoiceCommands.match("طفّي المايك"))
        assertEquals(Command.SLEEP, VoiceCommands.match("وقف الاستماع"))
    }

    @Test
    fun `english set works`() {
        assertEquals(Command.STOP, VoiceCommands.match("stop"))
        assertEquals(Command.SEND, VoiceCommands.match("send"))
        assertEquals(Command.UNDO, VoiceCommands.match("undo"))
    }

    @Test
    fun `a prompt containing a command word is not hijacked`() {
        assertNull(VoiceCommands.match("وقف عند الخطأ وصلحه"))
        assertNull(VoiceCommands.match("اكتب لي دالة ترسل البيانات"))
        assertNull(VoiceCommands.match(""))
        assertNull(VoiceCommands.match("   "))
    }

    @Test
    fun `harakat and alef variants normalize to the same command`() {
        assertEquals(Command.SEND, VoiceCommands.match("أَرْسِلْ"))
        assertEquals(Command.CLEAR, VoiceCommands.match("اِمْسَح"))
        assertEquals(Command.UNDO, VoiceCommands.match("إرجع"))
    }

    @Test
    fun `allowed gates commands on the live state`() {
        assertFalse(VoiceCommands.allowed(Command.STOP, running = false, speaking = false, hasHistory = true))
        assertTrue(VoiceCommands.allowed(Command.STOP, running = true, speaking = false, hasHistory = true))
        assertFalse(VoiceCommands.allowed(Command.MUTE, running = false, speaking = false, hasHistory = true))
        assertTrue(VoiceCommands.allowed(Command.MUTE, running = false, speaking = true, hasHistory = true))
        assertFalse(VoiceCommands.allowed(Command.REGENERATE, running = true, speaking = false, hasHistory = true))
        assertFalse(VoiceCommands.allowed(Command.UNDO, running = false, speaking = false, hasHistory = false))
        assertTrue(VoiceCommands.allowed(Command.NEW_CHAT, running = false, speaking = false, hasHistory = false))
    }
}
