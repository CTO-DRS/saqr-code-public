package com.ctodrs.saqrcode.git

import com.ctodrs.saqrcode.agent.SessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * v0.1.11: review and merge GitHub pull requests from the phone, with an undo.
 * The pure half: models, JSON parsing, the combined diff text and the undo safety check.
 * The network half lives in GitHubApi (listPulls / pull / pullFiles / mergePull / refSha / resetRef).
 */

/** One open pull request, as shown in the PR tab (from GET /repos/{o}/{r}/pulls). */
data class PrInfo(
    val number: Int, val title: String, val headRef: String, val baseRef: String,
    val author: String, val draft: Boolean, val htmlUrl: String, val updatedAt: String,
)

/** One changed file of a PR (from GET /pulls/{n}/files); [patch] is null for very large files. */
data class PrFile(val path: String, val status: String, val additions: Int, val deletions: Int, val patch: String?)

/** The single-PR fetch adds the counts the list payload lacks. */
data class PrDetail(
    val info: PrInfo, val additions: Int, val deletions: Int, val changedFiles: Int,
    /** null while GitHub is still computing it. */
    val mergeable: Boolean?,
)

/** A merge Saqr performed, kept so it can be undone while the base branch has not moved since. */
data class MergeRecord(
    val owner: String, val repo: String, val number: Int, val baseRef: String,
    val preMergeSha: String, val mergeSha: String, val method: String, val mergedAt: Long, val undone: Boolean = false,
)

object PrMerge {
    enum class UndoState { READY, BASE_MOVED, UNDONE }

    /** Undo is safe only while the base branch tip is still the merge commit this record made. */
    fun undoState(r: MergeRecord, baseHeadNow: String?): UndoState = when {
        r.undone -> UndoState.UNDONE
        baseHeadNow == null -> UndoState.BASE_MOVED
        baseHeadNow.equals(r.mergeSha, ignoreCase = true) -> UndoState.READY
        else -> UndoState.BASE_MOVED
    }

    /** The merge methods GitHub accepts, in the order the dialog offers them. */
    val METHODS = listOf("merge", "squash", "rebase")

    /** One unified diff from a PR's file list, ready for UnifiedDiff.parse; patchless files keep their header. */
    fun combinedDiff(files: List<PrFile>): String = buildString {
        for (f in files) {
            append("diff --git a/").append(f.path).append(" b/").append(f.path).append('\n')
            when (f.status) {
                "added" -> append("new file mode 100644\n--- /dev/null\n+++ b/").append(f.path).append('\n')
                "removed" -> append("deleted file mode 100644\n--- a/").append(f.path).append("\n+++ /dev/null\n")
                "renamed" -> Unit // the patch carries the content change; the header line is enough
                else -> append("--- a/").append(f.path).append("\n+++ b/").append(f.path).append('\n')
            }
            f.patch?.let { append(it.trimEnd('\n')).append('\n') }
        }
    }

    /** (files, additions, deletions) of a PR. */
    fun stats(files: List<PrFile>): Triple<Int, Int, Int> =
        Triple(files.size, files.sumOf { it.additions }, files.sumOf { it.deletions })
}

/** JSON parsing kept pure so unit tests exercise it without the network. */
object PrCodec {
    fun info(o: JSONObject) = PrInfo(
        number = o.getInt("number"), title = o.optString("title"),
        headRef = o.getJSONObject("head").optString("ref"), baseRef = o.getJSONObject("base").optString("ref"),
        author = o.optJSONObject("user")?.optString("login").orEmpty(), draft = o.optBoolean("draft"),
        htmlUrl = o.optString("html_url"), updatedAt = o.optString("updated_at"),
    )

    fun list(arr: JSONArray): List<PrInfo> = (0 until arr.length()).map { info(arr.getJSONObject(it)) }

    fun file(o: JSONObject) = PrFile(
        path = o.getString("filename"), status = o.optString("status", "modified"),
        additions = o.optInt("additions"), deletions = o.optInt("deletions"),
        patch = o.optString("patch").takeIf { it.isNotBlank() },
    )

    fun files(arr: JSONArray): List<PrFile> = (0 until arr.length()).map { file(arr.getJSONObject(it)) }

    fun detail(o: JSONObject) = PrDetail(
        info(o), o.optInt("additions"), o.optInt("deletions"), o.optInt("changed_files"),
        if (o.isNull("mergeable")) null else o.optBoolean("mergeable"),
    )

    fun mergeRecord(o: JSONObject, owner: String, repo: String, number: Int, baseRef: String, preMergeSha: String, method: String, now: Long) =
        MergeRecord(owner, repo, number, baseRef, preMergeSha, o.getString("sha"), method, now)

    fun refSha(o: JSONObject): String = o.getJSONObject("object").getString("sha")
}

/** Merges Saqr performed, persisted as JSON so Undo survives a process restart. */
class MergeRecords(private val file: File) {
    fun all(): List<MergeRecord> = runCatching {
        if (!file.isFile) emptyList() else JSONArray(file.readText()).let { a -> (0 until a.length()).map { read(a.getJSONObject(it)) } }
    }.getOrDefault(emptyList())

    fun forRepo(owner: String, repo: String) = all().filter { it.owner == owner && it.repo == repo }

    /** The newest merge of this repo that was not undone yet. */
    fun pending(owner: String, repo: String) = forRepo(owner, repo).lastOrNull { !it.undone }

    fun add(r: MergeRecord) = write(all() + r)

    fun markUndone(r: MergeRecord) =
        write(all().map { if (it.mergeSha == r.mergeSha && it.number == r.number) it.copy(undone = true) else it })

    private fun write(rs: List<MergeRecord>) {
        val a = JSONArray()
        rs.takeLast(50).forEach { a.put(json(it)) }
        runCatching { SessionStore.atomicWrite(file, a.toString()) }
    }

    private fun json(r: MergeRecord) = JSONObject().put("owner", r.owner).put("repo", r.repo).put("number", r.number)
        .put("baseRef", r.baseRef).put("pre", r.preMergeSha).put("merge", r.mergeSha).put("method", r.method)
        .put("at", r.mergedAt).put("undone", r.undone)

    private fun read(o: JSONObject) = MergeRecord(
        o.getString("owner"), o.getString("repo"), o.getInt("number"), o.getString("baseRef"),
        o.getString("pre"), o.getString("merge"), o.optString("method", "merge"), o.optLong("at"), o.optBoolean("undone"),
    )
}
