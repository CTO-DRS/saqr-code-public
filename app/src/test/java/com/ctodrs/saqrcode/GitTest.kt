package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.data.AccountStore
import com.ctodrs.saqrcode.data.GitHubAccount
import com.ctodrs.saqrcode.data.MemoryKeyValue
import com.ctodrs.saqrcode.git.ChangeKind
import com.ctodrs.saqrcode.git.GitHubApi
import com.ctodrs.saqrcode.git.GitHubDeviceFlow
import com.ctodrs.saqrcode.git.GitService
import com.ctodrs.saqrcode.git.GhException
import com.ctodrs.saqrcode.git.Redact
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Real git operations with JGit on temporary repositories (no network). */
class GitServiceTest {
    private lateinit var tmp: File
    private lateinit var dir: File
    private val git = GitService()
    private val me = "Rashid" to "rashid@example.com"

    @Before fun setUp() {
        tmp = Files.createTempDirectory("saqr-git").toFile()
        dir = File(tmp, "work").apply { mkdirs() }
    }

    @After fun tearDown() { tmp.deleteRecursively() }

    private fun write(rel: String, text: String) = File(dir, rel).apply { parentFile?.mkdirs(); writeText(text) }
    private fun commitAll(msg: String) { git.stageAll(dir); git.commit(dir, msg, me.first, me.second) }

    @Test fun initStatusStageCommitLog() {
        assertFalse(git.status(dir).isRepo)
        git.init(dir)
        assertTrue(File(dir, ".gitignore").isFile)
        write("index.html", "<h1>hi</h1>\n")
        write("app.js", "a\nb\nc\n")
        var s = git.status(dir)
        assertEquals("main", s.branch)
        assertFalse(s.hasCommits)
        assertEquals(ChangeKind.UNTRACKED, s.files.first { it.path == "app.js" }.unstaged)
        assertEquals(3, s.files.first { it.path == "app.js" }.added)

        git.stage(dir, listOf("app.js"))
        s = git.status(dir)
        assertEquals(ChangeKind.ADDED, s.files.first { it.path == "app.js" }.staged)
        git.unstage(dir, listOf("app.js")) // unborn branch path
        assertNull(git.status(dir).files.first { it.path == "app.js" }.staged)

        git.stageAll(dir)
        val c = git.commit(dir, "first commit", me.first, me.second)
        assertEquals("first commit", c.message)
        assertTrue(git.status(dir).clean)

        write("app.js", "a\nB\nc\nd\n")
        File(dir, "index.html").delete()
        s = git.status(dir)
        val js = s.files.first { it.path == "app.js" }
        assertEquals(ChangeKind.MODIFIED, js.unstaged)
        assertEquals(2, js.added); assertEquals(1, js.removed)
        assertEquals(ChangeKind.DELETED, s.files.first { it.path == "index.html" }.unstaged)

        git.stage(dir, listOf("app.js", "index.html"))
        s = git.status(dir)
        assertEquals(ChangeKind.DELETED, s.files.first { it.path == "index.html" }.staged)
        git.unstage(dir, listOf("app.js"))
        assertNull(git.status(dir).files.first { it.path == "app.js" }.staged)
        git.stageAll(dir)
        git.commit(dir, "second", me.first, me.second)

        val log = git.log(dir)
        assertEquals(listOf("second", "first commit"), log.map { it.message })
        assertEquals("Rashid", log[0].author)
        assertTrue(log[0].refs.contains("main"))
        val cd = git.commitDiff(dir, log[0].id)
        assertTrue(cd.contains("+B")); assertTrue(cd.contains("-b"))
        assertTrue(git.commitDiff(dir, log[1].id).contains("+++ b/index.html"))
    }

    @Test fun diffStagedUnstagedAndUntracked() {
        git.init(dir); write("a.txt", "one\ntwo\n"); commitAll("init")
        write("a.txt", "one\nTWO\n"); write("new.md", "# x\n")
        val d = git.diff(dir)
        assertTrue(d.contains("-two")); assertTrue(d.contains("+TWO")); assertTrue(d.contains("+++ b/new.md"))
        assertEquals("", git.diff(dir, staged = true))
        git.stage(dir, listOf("a.txt"))
        assertTrue(git.diff(dir, "a.txt", staged = true).contains("+TWO"))
        assertEquals(1 to 1, GitService.diffStats(git.diff(dir, "a.txt", staged = true)))
    }

    @Test fun discardRestoresAndDeletesUntracked() {
        git.init(dir); write("a.txt", "orig\n"); commitAll("init")
        write("a.txt", "changed\n"); write("junk.tmp", "x")
        git.discard(dir, "a.txt"); git.discard(dir, "junk.tmp")
        assertEquals("orig\n", File(dir, "a.txt").readText())
        assertFalse(File(dir, "junk.tmp").exists())
        // staged change is discarded too
        write("a.txt", "staged\n"); git.stage(dir, listOf("a.txt")); git.discard(dir, "a.txt")
        assertEquals("orig\n", File(dir, "a.txt").readText())
        assertTrue(git.status(dir).clean)
    }

    @Test fun branchesFastForwardMergeAndDelete() {
        git.init(dir); write("a.txt", "1\n"); commitAll("c1")
        git.createBranch(dir, "feature")
        assertEquals("feature", git.status(dir).branch)
        write("b.txt", "feature\n"); commitAll("c2 on feature")
        git.checkout(dir, "main")
        assertFalse(File(dir, "b.txt").exists())
        val m = git.merge(dir, "feature", me.first, me.second)
        assertTrue(m.success); assertEquals("FAST_FORWARD", m.status)
        assertTrue(File(dir, "b.txt").exists())
        assertEquals(setOf("main", "feature"), git.branches(dir).map { it.shortName }.toSet())
        assertTrue(git.branches(dir).first { it.shortName == "main" }.current)
        git.deleteBranch(dir, "feature")
        assertEquals(listOf("main"), git.branches(dir).map { it.shortName })
        runCatching { git.createBranch(dir, "bad name") }.let { assertTrue(it.isFailure) }
    }

    @Test fun conflictIsReportedAndCanBeAborted() {
        git.init(dir); write("a.txt", "base\n"); commitAll("base")
        git.createBranch(dir, "other"); write("a.txt", "theirs\n"); commitAll("theirs")
        git.checkout(dir, "main"); write("a.txt", "ours\n"); commitAll("ours")
        val m = git.merge(dir, "other", me.first, me.second)
        assertFalse(m.success)
        assertEquals(listOf("a.txt"), m.conflicts)
        val s = git.status(dir)
        assertTrue(s.merging)
        assertEquals(ChangeKind.CONFLICT, s.files.single().kind)
        assertTrue(File(dir, "a.txt").readText().contains("<<<<<<<"))
        // committing with conflicts is refused
        assertTrue(runCatching { git.commit(dir, "x", me.first, me.second, all = true) }.isFailure)
        git.abortMerge(dir)
        assertEquals("ours\n", File(dir, "a.txt").readText())
        assertFalse(git.status(dir).merging)
    }

    @Test fun cloneFetchPullPushAgainstLocalBareRemote() {
        // "remote": a bare repo on disk, seeded from another clone
        val bare = File(tmp, "remote.git")
        Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        val url = bare.absolutePath
        git.init(dir); write("README.md", "# hello\n"); commitAll("seed")
        git.setRemote(dir, "origin", url)
        assertEquals(url, git.remotes(dir).single().url)
        val pushed = git.push(dir)
        assertTrue(pushed.lines.toString(), pushed.ok)
        assertEquals("origin/main", git.status(dir).upstream)

        val clone2 = File(tmp, "clone2")
        git.clone(url, clone2)
        assertEquals("# hello\n", File(clone2, "README.md").readText())

        // new commit from clone2 → pull into dir
        File(clone2, "README.md").writeText("# hello\nmore\n")
        git.stageAll(clone2); git.commit(clone2, "more", me.first, me.second)
        assertTrue(git.push(clone2).ok)
        assertEquals("Fetched 1 ref(s) from origin.", git.fetch(dir))
        assertEquals(1, git.status(dir).behind)
        val p = git.pull(dir, name = me.first, email = me.second)
        assertTrue(p.success)
        assertEquals("# hello\nmore\n", File(dir, "README.md").readText())
        assertEquals(0, git.status(dir).behind)

        // ahead count after a local commit
        write("x.txt", "x\n"); commitAll("local")
        assertEquals(1, git.status(dir).ahead)

        // remote branch checkout creates a tracking branch
        git.createBranch(dir, "dev"); assertTrue(git.push(dir, branch = "dev").ok)
        git.fetch(clone2)
        git.checkout(clone2, "origin/dev")
        assertEquals("dev", git.status(clone2).branch)

        // tokens are refused inside remote URLs
        assertTrue(runCatching { git.setRemote(dir, "bad", "https://user:ghp_abcdefghijklmnopqrstuvwxyz0123@github.com/a/b.git") }.isFailure)
        git.removeRemote(dir, "origin"); assertTrue(git.remotes(dir).isEmpty())
    }

    @Test fun gitignoreHelpersAndSlug() {
        git.init(dir)
        git.addToGitignore(dir, "secrets.txt"); git.addToGitignore(dir, "secrets.txt")
        assertEquals(1, File(dir, ".gitignore").readLines().count { it == "secrets.txt" })
        write("secrets.txt", "x")
        assertTrue(git.status(dir).files.none { it.path == "secrets.txt" })
        assertEquals("cto-drs" to "saqr-code", GitService.githubSlug("https://github.com/cto-drs/saqr-code.git"))
        assertEquals("a" to "b", GitService.githubSlug("git@github.com:a/b.git"))
        assertNull(GitService.githubSlug("https://gitlab.com/a/b"))
    }
}

class AccountStoreTest {
    private val pat = "github_pat_11ABCDEFG0123456789_abcdefghijklmnopqrstuvwxyz"

    @Test fun githubSaveResolveSignOut() {
        val kv = MemoryKeyValue()
        val store = AccountStore(kv)
        assertNull(store.github)
        assertNull(store.forUrl("https://github.com/a/b.git"))
        store.saveGitHub(pat, GitHubAccount("octocat", "Octo Cat", "https://avatars/x", 42, null, false))
        assertEquals("octocat", store.github?.login)
        assertEquals("42+octocat@users.noreply.github.com", store.github?.noreplyEmail)
        val cred = store.forUrl("https://GitHub.com/a/b.git")!!
        assertEquals(pat, cred.token)
        assertFalse("toString must not leak the token", cred.toString().contains(pat))
        assertNull(store.forUrl("https://gitlab.com/a/b.git"))
        store.signOutGitHub()
        assertNull(store.github); assertNull(store.githubToken())
        assertTrue(kv.map.isEmpty())
    }

    @Test fun rejectsGarbageTokens() {
        val store = AccountStore(MemoryKeyValue())
        assertTrue(runCatching { store.saveGitHub("hello world", GitHubAccount("a", null, null, 1, null, false)) }.isFailure)
        assertTrue(AccountStore.looksLikeToken("ghp_" + "a".repeat(36)))
        assertTrue(AccountStore.looksLikeToken(pat))
        assertFalse(AccountStore.looksLikeToken("short"))
    }

    @Test fun otherHostsGitLabGitea() {
        val store = AccountStore(MemoryKeyValue())
        store.saveHost("https://gitlab.com/group/proj.git", "", "glpat-xxxxxxxxxxxxxxxxxxxx")
        store.saveHost("git.example.com:3000", "rashid", "gitea-token-123")
        assertEquals(listOf("gitlab.com", "git.example.com:3000"), store.hosts().map { it.host })
        assertEquals("oauth2", store.forUrl("https://gitlab.com/x/y.git")?.username)
        assertEquals("rashid", store.forUrl("http://git.example.com:3000/r/s.git")?.username)
        assertNull(store.forUrl("http://git.example.com/r/s.git"))
        assertTrue(runCatching { store.saveHost("github.com", "u", "t") }.isFailure)
        store.removeHost("gitlab.com")
        assertEquals(listOf("git.example.com:3000"), store.hosts().map { it.host })
    }

    @Test fun legacyTokenMigrates() {
        val kv = MemoryKeyValue(); kv.put(AccountStore.LEGACY_TOKEN, pat)
        val store = AccountStore(kv)
        store.migrateLegacy("cto-drs")
        assertEquals("cto-drs", store.github?.login)
        assertEquals(pat, store.githubToken())
        assertNull(kv.get(AccountStore.LEGACY_TOKEN))
    }

    @Test fun hostNormalization() {
        assertEquals("github.com", AccountStore.normalizeHost("https://github.com/a/b"))
        assertEquals("github.com", AccountStore.normalizeHost("git@github.com:a/b.git"))
        assertEquals("gitea.local:3000", AccountStore.normalizeHost("gitea.local:3000"))
        assertEquals("gitlab.com", AccountStore.normalizeHost("https://gitlab.com:443/x"))
        assertNull(AccountStore.normalizeHost("   "))
    }

    @Test fun redaction() {
        val t = "ghp_" + "Z".repeat(36)
        assertEquals("failed https://***@github.com/a *** ***", Redact.text("failed https://user:$t@github.com/a $t secret-123", "secret-123"))
        assertFalse(Redact.text("github_pat_11AAAAAAAAAAAAAAAAAAAAAAAA").contains("github_pat_"))
    }
}

class GitHubApiTest {
    private val server = MockWebServer()
    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    @Test fun verifyUserListReposCreateRepoAndPull() {
        server.enqueue(MockResponse().setBody("""{"login":"octocat","name":"The Octocat","id":583231,"avatar_url":"https://a/u/583231","html_url":"https://github.com/octocat"}""")
            .addHeader("X-OAuth-Scopes", "repo, read:user"))
        server.enqueue(MockResponse().setBody("""[{"full_name":"octocat/hello","name":"hello","owner":{"login":"octocat"},"private":true,"description":null,
            "default_branch":"main","clone_url":"https://github.com/octocat/hello.git","html_url":"https://github.com/octocat/hello","updated_at":"2026-11-20T10:00:00Z","language":"Kotlin","stargazers_count":3,"fork":false}]"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"full_name":"octocat/new","name":"new","owner":{"login":"octocat"},"private":true,"default_branch":"main","clone_url":"https://github.com/octocat/new.git","html_url":"https://github.com/octocat/new"}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"number":7,"html_url":"https://github.com/octocat/hello/pull/7","title":"Add feature"}"""))
        val api = GitHubApi("ghp_test_token_value_123456", server.url("/").toString())

        val me = api.me()
        assertEquals("octocat", me.login); assertEquals("repo, read:user", me.scopes)
        assertEquals("583231+octocat@users.noreply.github.com", me.noreplyEmail)
        val r1 = server.takeRequest()
        assertEquals("Bearer ghp_test_token_value_123456", r1.getHeader("Authorization"))
        assertEquals("/user", r1.path)

        val repos = api.repos()
        assertEquals("octocat/hello", repos.single().fullName); assertTrue(repos.single().private)
        assertNull(repos.single().description)
        assertTrue(server.takeRequest().path!!.startsWith("/user/repos?sort=updated"))

        assertEquals("octocat/new", api.createRepo("new", true, "desc").fullName)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"private\":true"))

        val pr = api.createPull("octocat", "hello", "Add feature", "feature", "main", "body")
        assertEquals(7, pr.number)
        assertEquals("/repos/octocat/hello/pulls", server.takeRequest().path)
    }

    @Test fun badTokenGivesCleanError() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))
        val e = runCatching { GitHubApi("ghp_should_not_leak_123456789", server.url("/").toString()).me() }.exceptionOrNull()
        assertTrue(e is GhException)
        assertEquals(401, (e as GhException).code)
        assertTrue(e.message!!.contains("Bad credentials"))
        assertFalse(e.message!!.contains("ghp_should_not_leak"))
    }

    @Test fun deviceFlow() {
        server.enqueue(MockResponse().setBody("""{"device_code":"dc","user_code":"WDJB-MJHT","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""))
        server.enqueue(MockResponse().setBody("""{"error":"authorization_pending"}"""))
        server.enqueue(MockResponse().setBody("""{"error":"slow_down","interval":10}"""))
        server.enqueue(MockResponse().setBody("""{"access_token":"gho_abc","token_type":"bearer","scope":"repo"}"""))
        val flow = GitHubDeviceFlow(server.url("/").toString())
        val code = flow.start("Iv1.client")
        assertEquals("WDJB-MJHT", code.userCode)
        assertTrue(server.takeRequest().body.readUtf8().contains("client_id=Iv1.client"))
        assertEquals(GitHubDeviceFlow.Poll.Pending, flow.poll("Iv1.client", "dc"))
        assertEquals(GitHubDeviceFlow.Poll.SlowDown(10), flow.poll("Iv1.client", "dc"))
        val tok = flow.poll("Iv1.client", "dc")
        assertNotNull(tok as? GitHubDeviceFlow.Poll.Token)
        assertEquals("gho_abc", (tok as GitHubDeviceFlow.Poll.Token).accessToken)
        assertFalse(tok.toString().contains("gho_abc"))
    }
}
