package com.ctodrs.saqrcode.agent

/*
 * v0.1.12 «أوامر صوتية»: hands-free voice commands in Arabic (Gulf spellings first) with a
 * small English set, as pure logic so it is unit-testable. The recognizer hears the same
 * word many ways (harakat, alef forms, fused phrases), so every phrase is keyed by
 * MemoryBank.normalize and the whole utterance must match — a prompt that merely contains
 * «وقف» still goes to the agent untouched. Android wiring lives in ui/IdeScreen.kt.
 */

object VoiceCommands {
    /** What a recognized phrase asks the app to do. */
    enum class Command {
        /** Stop the running agent turn. */
        STOP,
        /** Send the composer text now. */
        SEND,
        /** Rewind the last turn. */
        UNDO,
        /** Regenerate the last answer. */
        REGENERATE,
        /** Start a new chat session. */
        NEW_CHAT,
        /** Open the sessions sheet. */
        SESSIONS,
        /** Read the last answer aloud. */
        READ,
        /** Stop reading aloud. */
        MUTE,
        /** Clear the composer. */
        CLEAR,
        /** Turn hands-free listening off. */
        SLEEP,
    }

    /**
     * Phrase table: each key is the MemoryBank.normalize form of one spoken phrase
     * (diacritics stripped, alef unified, ة→ه, spaces and punctuation fused away).
     * Gulf spellings («بس», «بطل», «خلاص», «كفاية», «عيد», «اقرى») sit next to MSA ones.
     */
    private val PHRASES: Map<String, Command> = buildMap {
        // STOP — stop the agent turn
        for (p in listOf("وقف", "قف", "توقف", "اوقف", "بس", "خلاص", "كفايه", "بطل", "stop", "cancel")) put(p, Command.STOP)
        // SEND — send what is in the composer
        for (p in listOf("ارسل", "ابعت", "ارسله", "ابعته", "send")) put(p, Command.SEND)
        // UNDO — rewind the last turn
        for (p in listOf("تراجع", "رجع", "ارجع", "undo")) put(p, Command.UNDO)
        // REGENERATE — answer again
        for (p in listOf("عيد", "اعد", "عيدها", "اعدها", "منجديد", "مرهثانيه", "مرهثانية", "retry", "regenerate", "again")) put(p, Command.REGENERATE)
        // NEW_CHAT — fresh session
        for (p in listOf("جديد", "محادثهجديده", "شاتجديد", "جلسهجديده", "newchat")) put(p, Command.NEW_CHAT)
        // SESSIONS — open the session list
        for (p in listOf("المحادثات", "الجلسات", "افتحالمحادثات", "ورنيالمحادثات", "ورينيالمحادثات", "sessions", "chats")) put(p, Command.SESSIONS)
        // READ — read the last answer aloud
        for (p in listOf("اقرا", "اقري", "اسمع", "اسمعني", "اقراها", "read", "readit")) put(p, Command.READ)
        // MUTE — stop the reader
        for (p in listOf("اسكت", "اخرس", "وقفالقراءه", "وقفالقرايه", "mute", "quiet")) put(p, Command.MUTE)
        // CLEAR — empty the composer
        for (p in listOf("امسح", "امسحه", "نظف", "clear")) put(p, Command.CLEAR)
        // SLEEP — hands-free off (explicitly about the mic/listening so «وقف» stays STOP)
        for (p in listOf("نام", "نوم", "قفلالمايك", "طفيالمايك", "وقفالاستماع", "بطليالاستماع", "sleep", "stoplistening")) put(p, Command.SLEEP)
    }

    /**
     * The command a whole recognized utterance asks for, or null when it is not one.
     * Matches the entire normalized utterance only, never a substring.
     */
    fun match(utterance: String): Command? {
        val key = MemoryBank.normalize(utterance)
        if (key.isEmpty()) return null
        return PHRASES[key]
    }

    /**
     * Whether [cmd] makes sense right now: STOP needs a running turn, MUTE needs the
     * reader speaking, REGENERATE/UNDO need history ([hasHistory]); the rest always apply.
     */
    fun allowed(cmd: Command, running: Boolean, speaking: Boolean, hasHistory: Boolean): Boolean = when (cmd) {
        Command.STOP -> running
        Command.MUTE -> speaking
        Command.REGENERATE, Command.UNDO -> hasHistory && !running
        Command.SEND, Command.NEW_CHAT, Command.SESSIONS, Command.READ, Command.CLEAR, Command.SLEEP -> true
    }
}
