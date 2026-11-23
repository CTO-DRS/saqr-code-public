package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.GitTools
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.git.GitService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitToolsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun run(git: GitService, dir: File, name: String, args: String = "{}") =
        GitTools.run(name, JSONObject(args), dir, git, "Saqr Test", "test@example.com")

    @Test fun specsRegistered() {
        val names = Tools.specs.map { it.name }.toSet()
        assertTrue(names.containsAll(Tools.GIT))
        assertEquals(Tools.specs.size, names.size)
    }

    @Test fun commitStatusLogBranchPushFlow() {
        val home = tmp.newFolder("home")
        val git = GitService(home)
        val dir = tmp.newFolder("proj")
        assertTrue(run(git, dir, Tools.GIT_STATUS).contains("Not a git repository"))

        File(dir, "a.txt").writeText("hello\n")
        val c1 = run(git, dir, Tools.GIT_COMMIT, """{"message":"first"}""")
        assertTrue(c1, c1.startsWith("Committed") && c1.contains("Saqr Test"))
        assertTrue(run(git, dir, Tools.GIT_STATUS).contains("Working tree clean"))

        File(dir, "a.txt").writeText("hello\nworld\n")
        assertTrue(run(git, dir, Tools.GIT_DIFF).contains("+world"))
        run(git, dir, Tools.GIT_COMMIT, """{"message":"second"}""")
        val log = run(git, dir, Tools.GIT_LOG, """{"max_count":5}""")
        assertTrue(log, log.lines().size == 2 && log.contains("second"))

        assertTrue(run(git, dir, Tools.GIT_BRANCH, """{"action":"create","name":"feat"}""").contains("feat"))
        assertTrue(run(git, dir, Tools.GIT_BRANCH).contains("* feat"))
        run(git, dir, Tools.GIT_BRANCH, """{"action":"switch","name":"main"}""")

        // push to a local bare remote
        val bare = tmp.newFolder("remote.git")
        org.eclipse.jgit.api.Git.init().setBare(true).setDirectory(bare).call().close()
        git.setRemote(dir, "origin", bare.toURI().toString())
        val desc = GitTools.describePush(JSONObject(), dir, git)
        assertTrue(desc, desc.startsWith("git push origin main"))
        val pushed = GitTools.push(JSONObject(), dir, git)
        assertTrue(pushed, pushed.startsWith("Pushed"))
        assertTrue(run(git, dir, Tools.GIT_PULL).startsWith("Pull:"))
    }
}
