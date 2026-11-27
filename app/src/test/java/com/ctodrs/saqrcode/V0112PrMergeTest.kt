package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.git.GhException
import com.ctodrs.saqrcode.git.GitHubApi
import com.ctodrs.saqrcode.git.MergeRecord
import com.ctodrs.saqrcode.git.MergeRecords
import com.ctodrs.saqrcode.git.PrCodec
import com.ctodrs.saqrcode.git.PrMerge
import com.ctodrs.saqrcode.ui.UnifiedDiff
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** v0.1.11: PR review + merge from the phone, with undo. */
class V0112PrMergeTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun prJson(n: Int, title: String, head: String = "feature", base: String = "main", draft: Boolean = false) = """
        {"number":$n,"title":"$title","draft":$draft,"html_url":"https://github.com/o/r/pull/$n",
         "updated_at":"2026-11-27T10:00:00Z","user":{"login":"octocat"},
         "head":{"ref":"$head"},"base":{"ref":"$base"}}
    """.trimIndent()

    @Test fun `list and file payloads parse`() {
        val prs = PrCodec.list(JSONArray("[${prJson(7, "Add feature")},${prJson(8, "Fix bug", draft = true)}]"))
        assertEquals(2, prs.size)
        assertEquals(7, prs[0].number); assertEquals("feature", prs[0].headRef); assertEquals("main", prs[0].baseRef)
        assertEquals("octocat", prs[0].author); assertFalse(prs[0].draft); assertTrue(prs[1].draft)

        val files = PrCodec.files(JSONArray("""[{"filename":"a.kt","status":"modified","additions":3,"deletions":1,"patch":"@@ -1,2 +1,4 @@\n line\n+added"},
            |{"filename":"big.bin","status":"added","additions":0,"deletions":0}]""".trimMargin()))
        assertEquals("a.kt", files[0].path); assertEquals(3, files[0].additions); assertNull(files[1].patch)
        assertEquals(Triple(2, 3, 1), PrMerge.stats(files))
    }

    @Test fun `combined diff feeds the diff viewer`() {
        val files = PrCodec.files(JSONArray("""[{"filename":"src/a.kt","status":"modified","additions":1,"deletions":1,
            |"patch":"@@ -1,2 +1,2 @@\n-old\n+new"}]""".trimMargin()))
        val lines = UnifiedDiff.parse(PrMerge.combinedDiff(files))
        assertEquals(UnifiedDiff.Kind.FILE, lines[0].kind)
        assertTrue(lines.any { it.kind == UnifiedDiff.Kind.ADD && it.text == "new" })
        assertTrue(lines.any { it.kind == UnifiedDiff.Kind.DEL && it.text == "old" })
    }

    @Test fun `undo only while the base branch is still at the merge commit`() {
        val r = MergeRecord("o", "r", 7, "main", "aaa", "bbb", "squash", 1L)
        assertEquals(PrMerge.UndoState.READY, PrMerge.undoState(r, "bbb"))
        assertEquals(PrMerge.UndoState.BASE_MOVED, PrMerge.undoState(r, "ccc"))
        assertEquals(PrMerge.UndoState.BASE_MOVED, PrMerge.undoState(r, null))
        assertEquals(PrMerge.UndoState.UNDONE, PrMerge.undoState(r.copy(undone = true), "bbb"))
        assertEquals(listOf("merge", "squash", "rebase"), PrMerge.METHODS)
    }

    @Test fun `merge records persist, pending and markUndone`() {
        val store = MergeRecords(tmp.newFile("merges.json"))
        assertNull(store.pending("o", "r"))
        store.add(MergeRecord("o", "r", 7, "main", "aaa", "bbb", "merge", 1L))
        store.add(MergeRecord("o", "r", 8, "main", "bbb", "ccc", "squash", 2L))
        store.add(MergeRecord("x", "y", 3, "dev", "111", "222", "merge", 3L))
        // a fresh instance reads the same file: survives a process restart
        val again = MergeRecords(java.io.File(tmp.root, "merges.json"))
        assertEquals(8, again.pending("o", "r")!!.number)
        again.markUndone(again.pending("o", "r")!!)
        assertEquals(7, MergeRecords(java.io.File(tmp.root, "merges.json")).pending("o", "r")!!.number)
        assertEquals(3, again.pending("x", "y")!!.number)
    }

    @Test fun `merge pull captures pre-merge head and merge commit`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"object":{"sha":"aaa111"}}"""))
        server.enqueue(MockResponse().setBody("""{"sha":"bbb222","merged":true,"message":"Pull Request successfully merged"}"""))
        val api = GitHubApi("ghp_test_token_value_123456", server.url("/").toString())
        val rec = api.mergePull("o", "r", 7, "main", "squash", now = 42L)
        assertEquals("aaa111", rec.preMergeSha); assertEquals("bbb222", rec.mergeSha)
        assertEquals("squash", rec.method); assertEquals(42L, rec.mergedAt); assertFalse(rec.undone)
        assertEquals("/repos/o/r/git/ref/heads/main", server.takeRequest().path)
        val mergeReq = server.takeRequest()
        assertEquals("PUT", mergeReq.method); assertTrue(mergeReq.body.readUtf8().contains("\"squash\""))

        // a refused merge throws and records nothing
        server.enqueue(MockResponse().setBody("""{"object":{"sha":"aaa111"}}"""))
        server.enqueue(MockResponse().setResponseCode(405).setBody("""{"message":"Pull Request is not mergeable"}"""))
        val e = runCatching { api.mergePull("o", "r", 8, "main", "merge") }.exceptionOrNull()
        assertTrue(e is GhException)
        server.shutdown()
    }

    @Test fun `undo resets the base ref with force`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"ref":"refs/heads/main","object":{"sha":"aaa111"}}"""))
        val api = GitHubApi("ghp_test_token_value_123456", server.url("/").toString())
        api.resetRef("o", "r", "main", "aaa111")
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/repos/o/r/git/refs/heads/main", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"force\":true") && body.contains("aaa111"))
        server.shutdown()
    }
}
