package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.cloud.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class V0173LongRunTest {
    @Test fun parsePollSplitsOutputAndExit() {
        assertEquals(listOf("a", "b") to null, CloudLongRun.parsePoll("a\nb\n__SAQR_EXIT__"))
        assertEquals(listOf("a") to 0, CloudLongRun.parsePoll("a\n__SAQR_EXIT__0"))
        assertEquals(emptyList<String>() to 2, CloudLongRun.parsePoll("__SAQR_EXIT__2\n"))
    }

    @Test fun startCommandQuotesSingleQuotes() {
        val c = CloudLongRun.startCommand("j1", "echo 'hi'")
        assertTrue(c.contains("'echo '\\''hi'\\'''"))
        assertTrue(c.endsWith("echo started"))
    }

    @Test fun jobIdIsShellSafe() = assertTrue(CloudLongRun.jobId(-123).matches(Regex("j[0-9]+")))

    private class FakeSb(val polls: List<String>) : CloudSandbox {
        var i = 0
        val cmds = mutableListOf<String>()
        override suspend fun upload(files: List<CloudFile>) {}
        override suspend fun exec(command: String, timeoutSec: Int, onLine: (String) -> Unit): CloudExecResult {
            cmds += command
            val out = if (command.contains("__SAQR_EXIT__")) polls[minOf(i++, polls.size - 1)] else "started"
            return CloudExecResult(0, out, false, 1)
        }
        override suspend fun close() {}
    }

    @Test fun pollsUntilExitAndStreams() = runBlocking {
        val sb = FakeSb(listOf("l1\n__SAQR_EXIT__", "l2\n__SAQR_EXIT__", "l3\n__SAQR_EXIT__3"))
        val seen = mutableListOf<String>()
        val r = CloudLongRun.run(sb, "make", wait = {}, onLine = { seen += it })
        assertEquals(3, r.exitCode)
        assertEquals(listOf("l1", "l2", "l3"), seen)
        assertFalse(r.timedOut)
        assertTrue(sb.cmds[2].contains("tail -n +3"))
    }

    @Test fun timesOutAndKills() = runBlocking {
        val sb = FakeSb(listOf("__SAQR_EXIT__"))
        var t = 0L
        val r = CloudLongRun.run(sb, "sleep 9", totalSec = 10, now = { t }, wait = { t += 6000 })
        assertTrue(r.timedOut)
        assertEquals(124, r.exitCode)
        assertTrue(sb.cmds.last().startsWith("pkill"))
    }
}
