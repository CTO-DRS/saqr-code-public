package com.ctodrs.saqrcode.git

import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.util.FS
import java.io.ByteArrayOutputStream
import java.io.File

/** HTTPS credentials for one remote (never logged; see [Redact]). */
data class GitCredential(val username: String, val token: String) {
    override fun toString() = "GitCredential($username, ***)"
    fun provider(): CredentialsProvider = UsernamePasswordCredentialsProvider(username, token)
}

/** Picks the credential for a remote URL (by host). */
fun interface CredentialResolver { fun forUrl(url: String): GitCredential? }

enum class ChangeKind { ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICT }

/** One path in `git status`, with what is staged and what is not, plus +/- line counts. */
data class GitFileStatus(
    val path: String,
    val staged: ChangeKind? = null,
    val unstaged: ChangeKind? = null,
    val added: Int = 0,
    val removed: Int = 0,
) {
    val conflict get() = staged == ChangeKind.CONFLICT || unstaged == ChangeKind.CONFLICT
    val isStaged get() = staged != null && staged != ChangeKind.CONFLICT
    val isUnstaged get() = unstaged != null
    /** The kind to show for the row (worktree first, like most git UIs). */
    val kind get() = if (conflict) ChangeKind.CONFLICT else unstaged ?: staged ?: ChangeKind.MODIFIED
}

data class GitStatus(
    val isRepo: Boolean,
    val branch: String? = null,
    val headShort: String? = null,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val files: List<GitFileStatus> = emptyList(),
    val state: String = "SAFE",
    val hasCommits: Boolean = false,
) {
    val staged get() = files.filter { it.isStaged }
    val unstaged get() = files.filter { it.isUnstaged }
    val conflicts get() = files.filter { it.conflict }
    val clean get() = files.isEmpty()
    val merging get() = state.startsWith("MERGING")
}

data class GitCommitInfo(
    val id: String, val shortId: String, val message: String, val fullMessage: String,
    val author: String, val email: String, val time: Long, val parents: Int, val refs: List<String>,
)

data class GitBranch(val name: String, val shortName: String, val remote: Boolean, val current: Boolean, val headShort: String)

data class GitRemote(val name: String, val url: String)

data class MergeOutcome(val status: String, val success: Boolean, val conflicts: List<String>, val newHead: String?)

data class PushOutcome(val ok: Boolean, val lines: List<String>)

/** Thrown for user-facing git failures; message never contains secrets. */
class GitException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Removes tokens / basic-auth userinfo from any text before it can reach logs or the UI. */
object Redact {
    private val userInfo = Regex("""(https?://)[^/@\s]+@""")
    private val ghTokens = Regex("""\b(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|glpat-[A-Za-z0-9_\-]{20,})\b""")
    fun text(s: String?, vararg secrets: String?): String {
        var out = s ?: return ""
        secrets.filterNotNull().filter { it.length >= 6 }.forEach { out = out.replace(it, "***") }
        out = userInfo.replace(out) { it.groupValues[1] + "***@" }
        return ghTokens.replace(out, "***")
    }
}

/**
 * Real git on the device via Eclipse JGit (pure Java, no git binary needed).
 * All calls are blocking: run them on Dispatchers.IO. Every function works on a plain
 * directory so it is unit-testable on the JVM with temp repos.
 */
class GitService(homeDir: File? = null, private val credentials: CredentialResolver = CredentialResolver { null }) {
    init {
        // Keep JGit from probing a $HOME/.gitconfig outside the app sandbox.
        if (homeDir != null) FS.DETECTED.setUserHome(homeDir)
    }

    // ------------------------------------------------------------------ repo lifecycle

    fun isRepo(dir: File) = File(dir, ".git").exists()

    fun init(dir: File, branch: String = "main"): String {
        if (isRepo(dir)) return "Already a git repository."
        Git.init().setDirectory(dir).setInitialBranch(branch).call().close()
        ensureGitignore(dir)
        return "Initialised empty repository on '$branch'."
    }

    fun clone(url: String, dir: File, branch: String? = null, monitor: ProgressMonitor? = null): String = wrap(url) {
        require(!dir.exists() || (dir.listFiles()?.isEmpty() != false)) { "Target folder is not empty: ${dir.name}" }
        val cmd = Git.cloneRepository().setURI(url.trim()).setDirectory(dir).setCloneAllBranches(false)
        credentials.forUrl(url)?.let { cmd.setCredentialsProvider(it.provider()) }
        if (!branch.isNullOrBlank()) cmd.setBranch(branch)
        if (monitor != null) cmd.setProgressMonitor(monitor)
        cmd.call().use { g -> "Cloned ${Redact.text(url)} (${g.repository.branch})." }
    }

    // ------------------------------------------------------------------ status

    fun status(dir: File, withCounts: Boolean = true): GitStatus {
        if (!isRepo(dir)) return GitStatus(isRepo = false)
        return open(dir).use { git ->
            val repo = git.repository
            val s = git.status().call()
            val map = LinkedHashMap<String, GitFileStatus>()
            fun put(path: String, staged: ChangeKind? = null, unstaged: ChangeKind? = null) {
                val old = map[path] ?: GitFileStatus(path)
                map[path] = old.copy(staged = staged ?: old.staged, unstaged = unstaged ?: old.unstaged)
            }
            s.conflicting.forEach { put(it, ChangeKind.CONFLICT, ChangeKind.CONFLICT) }
            s.added.forEach { if (it !in s.conflicting) put(it, staged = ChangeKind.ADDED) }
            s.changed.forEach { if (it !in s.conflicting) put(it, staged = ChangeKind.MODIFIED) }
            s.removed.forEach { if (it !in s.conflicting) put(it, staged = ChangeKind.DELETED) }
            s.modified.forEach { if (it !in s.conflicting) put(it, unstaged = ChangeKind.MODIFIED) }
            s.missing.forEach { if (it !in s.conflicting) put(it, unstaged = ChangeKind.DELETED) }
            s.untracked.forEach { put(it, unstaged = ChangeKind.UNTRACKED) }
            var files = map.values.sortedWith(compareBy({ !it.conflict }, { it.path }))
            if (withCounts && files.size <= 400) {
                val counts = lineCounts(git, files.map { it.path })
                files = files.map { f -> counts[f.path]?.let { f.copy(added = it.first, removed = it.second) } ?: f }
            }
            val head = repo.resolve(Constants.HEAD)
            val branch = repo.branch
            val tracking = runCatching { BranchTrackingStatus.of(repo, branch) }.getOrNull()
            GitStatus(
                isRepo = true, branch = branch, headShort = head?.name?.take(7),
                upstream = tracking?.remoteTrackingBranch?.let { Repository.shortenRefName(it) },
                ahead = tracking?.aheadCount ?: 0, behind = tracking?.behindCount ?: 0,
                files = files, state = repo.repositoryState.name, hasCommits = head != null,
            )
        }
    }

    /** path -> (added, removed) comparing HEAD with the working tree (untracked: all lines added). */
    private fun lineCounts(git: Git, paths: List<String>): Map<String, Pair<Int, Int>> {
        val out = HashMap<String, Pair<Int, Int>>()
        if (paths.isEmpty()) return out
        val repo = git.repository
        DiffFormatter(NullOut).use { fmt ->
            fmt.setRepository(repo)
            fmt.setDiffComparator(RawTextComparator.DEFAULT)
            fmt.isDetectRenames = false
            fmt.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilterGroup.createFromStrings(paths))
            val headTree = headTreeIterator(repo)
            val wt = org.eclipse.jgit.treewalk.FileTreeIterator(repo)
            val entries = runCatching { fmt.scan(headTree, wt) }.getOrDefault(emptyList())
            for (e in entries) {
                val path = if (e.changeType == DiffEntry.ChangeType.DELETE) e.oldPath else e.newPath
                val edits = runCatching { fmt.toFileHeader(e).toEditList() }.getOrNull() ?: continue
                var a = 0; var r = 0
                for (ed in edits) { a += ed.lengthB; r += ed.lengthA }
                out[path] = a to r
            }
        }
        return out
    }

    // ------------------------------------------------------------------ staging

    fun stage(dir: File, paths: Collection<String>) = open(dir).use { git ->
        val st = git.status().call()
        val missing = st.missing
        val toAdd = paths.filter { it !in missing }
        val toRm = paths.filter { it in missing }
        if (toAdd.isNotEmpty()) git.add().apply { toAdd.forEach { addFilepattern(it) } }.call()
        if (toRm.isNotEmpty()) git.rm().setCached(true).apply { toRm.forEach { addFilepattern(it) } }.call()
    }

    fun stageAll(dir: File) = open(dir).use { git ->
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
    }

    fun unstage(dir: File, paths: Collection<String>) = open(dir).use { git ->
        if (git.repository.resolve(Constants.HEAD) == null) {
            // Unborn branch: nothing to reset to, just drop the paths from the index.
            git.rm().setCached(true).apply { paths.forEach { addFilepattern(it) } }.call()
        } else {
            git.reset().apply { paths.forEach { addPath(it) } }.call()
        }
    }

    fun unstageAll(dir: File) {
        val s = status(dir, withCounts = false)
        if (s.staged.isNotEmpty()) unstage(dir, s.staged.map { it.path })
    }

    /** Throws away working-tree changes of [path] (untracked files are deleted). */
    fun discard(dir: File, path: String) = open(dir).use { git ->
        val st = git.status().call()
        when {
            path in st.untracked -> File(dir, path).delete()
            path in st.added -> { git.rm().setCached(true).addFilepattern(path).call(); File(dir, path).delete() }
            else -> {
                if (git.repository.resolve(Constants.HEAD) != null && (path in st.changed || path in st.removed)) {
                    git.reset().addPath(path).call()
                }
                git.checkout().addPath(path).call()
            }
        }
    }

    // ------------------------------------------------------------------ commit / history

    fun commit(dir: File, message: String, name: String, email: String, amend: Boolean = false, all: Boolean = false): GitCommitInfo =
        open(dir).use { git ->
            require(message.isNotBlank()) { "Commit message is empty." }
            val pre = git.status().call()
            require(pre.conflicting.isEmpty()) { "Resolve conflicts first, then stage the files: ${pre.conflicting.joinToString()}" }
            if (all) { git.add().addFilepattern(".").setUpdate(true).call() }
            val st = git.status().call()
            val hasStaged = st.added.isNotEmpty() || st.changed.isNotEmpty() || st.removed.isNotEmpty()
            val merging = git.repository.repositoryState == RepositoryState.MERGING_RESOLVED
            require(hasStaged || amend || merging) { "Nothing staged to commit." }
            val who = PersonIdent(name.ifBlank { "Saqr Code" }, email.ifBlank { "saqr@users.noreply.github.com" })
            val c = git.commit().setMessage(message.trim()).setAuthor(who).setCommitter(who).setAmend(amend).call()
            toInfo(c, emptyMap())
        }

    fun log(dir: File, max: Int = 100): List<GitCommitInfo> {
        if (!isRepo(dir)) return emptyList()
        return open(dir).use { git ->
            val repo = git.repository
            if (repo.resolve(Constants.HEAD) == null) return@use emptyList()
            val refs = HashMap<ObjectId, MutableList<String>>()
            repo.refDatabase.refs.forEach { r ->
                val id = r.peeledObjectId ?: r.objectId ?: return@forEach
                if (r.name == Constants.HEAD) return@forEach
                refs.getOrPut(id) { ArrayList() } += Repository.shortenRefName(r.name)
            }
            git.log().setMaxCount(max).call().map { toInfo(it, refs) }
        }
    }

    private fun toInfo(c: RevCommit, refs: Map<ObjectId, List<String>>) = GitCommitInfo(
        id = c.name, shortId = c.name.take(7), message = c.shortMessage, fullMessage = c.fullMessage.trim(),
        author = c.authorIdent.name, email = c.authorIdent.emailAddress, time = c.authorIdent.`when`.time,
        parents = c.parentCount, refs = refs[c.id].orEmpty(),
    )

    // ------------------------------------------------------------------ diffs

    /** Unified diff of one path (or all when [path] is null). staged=true: HEAD↔index, else index↔worktree. */
    fun diff(dir: File, path: String? = null, staged: Boolean = false, maxChars: Int = 200_000): String = open(dir).use { git ->
        val repo = git.repository
        val out = ByteArrayOutputStream()
        val cmd = git.diff().setCached(staged).setOutputStream(out).setContextLines(3)
        if (path != null) cmd.setPathFilter(PathFilter.create(path))
        if (staged && repo.resolve(Constants.HEAD) == null) cmd.setOldTree(EmptyTreeIterator())
        cmd.call()
        var text = out.toString(Charsets.UTF_8.name())
        if (!staged) {
            // Untracked files are not part of `git diff`; show them as new files.
            val st = git.status().call()
            val untracked = st.untracked.filter { path == null || it == path }
            for (u in untracked.take(50)) text += newFileDiff(dir, u)
        }
        if (text.length > maxChars) text.take(maxChars) + "\n…[diff truncated]" else text
    }

    /** Diff introduced by one commit (against its first parent, or the empty tree). */
    fun commitDiff(dir: File, commitId: String, maxChars: Int = 200_000): String = open(dir).use { git ->
        val repo = git.repository
        RevWalk(repo).use { rw ->
            val c = rw.parseCommit(repo.resolve(commitId) ?: throw GitException("Unknown commit $commitId"))
            val newTree = CanonicalTreeParser().apply { repo.newObjectReader().use { r -> reset(r, c.tree) } }
            val oldTree: AbstractTreeIterator = if (c.parentCount > 0) {
                val p = rw.parseCommit(c.getParent(0).id)
                CanonicalTreeParser().apply { repo.newObjectReader().use { r -> reset(r, p.tree) } }
            } else EmptyTreeIterator()
            val out = ByteArrayOutputStream()
            DiffFormatter(out).use { fmt -> fmt.setRepository(repo); fmt.format(fmt.scan(oldTree, newTree)) }
            val text = out.toString(Charsets.UTF_8.name())
            if (text.length > maxChars) text.take(maxChars) + "\n…[diff truncated]" else text
        }
    }

    private fun newFileDiff(dir: File, rel: String): String {
        val f = File(dir, rel)
        if (!f.isFile || f.length() > 512_000) return "diff --git a/$rel b/$rel\nnew file\nBinary or large file\n"
        val text = runCatching { f.readText() }.getOrDefault("")
        if (text.contains('\u0000')) return "diff --git a/$rel b/$rel\nnew file\nBinary file\n"
        val lines = text.split('\n').let { if (text.endsWith("\n")) it.dropLast(1) else it }
        return buildString {
            append("diff --git a/$rel b/$rel\nnew file mode 100644\n--- /dev/null\n+++ b/$rel\n@@ -0,0 +1,${lines.size} @@\n")
            lines.forEach { append('+').append(it).append('\n') }
        }
    }

    // ------------------------------------------------------------------ branches

    fun branches(dir: File): List<GitBranch> = open(dir).use { git ->
        val repo = git.repository
        val current = repo.fullBranch
        git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
            .filter { !it.name.endsWith("/HEAD") }
            .map { r ->
                GitBranch(
                    name = r.name, shortName = Repository.shortenRefName(r.name), remote = r.name.startsWith(Constants.R_REMOTES),
                    current = r.name == current, headShort = r.objectId?.name?.take(7) ?: "",
                )
            }.sortedWith(compareBy({ it.remote }, { !it.current }, { it.shortName }))
    }

    fun createBranch(dir: File, name: String, checkout: Boolean = true, startPoint: String? = null) = open(dir).use { git ->
        validateBranchName(name)
        require(git.repository.resolve(Constants.HEAD) != null) { "Make a first commit before creating branches." }
        if (checkout) git.checkout().setCreateBranch(true).setName(name).apply { if (startPoint != null) setStartPoint(startPoint) }.call()
        else git.branchCreate().setName(name).apply { if (startPoint != null) setStartPoint(startPoint) }.call()
        Unit
    }

    /** Switches to a local branch, or creates a tracking branch for "origin/x". */
    fun checkout(dir: File, name: String) = open(dir).use { git ->
        val repo = git.repository
        val local = repo.findRef(Constants.R_HEADS + name)
        if (local != null) { git.checkout().setName(name).call(); return@use }
        val remote = repo.findRef(Constants.R_REMOTES + name)
            ?: throw GitException("No such branch: $name")
        val localName = name.substringAfter('/')
        if (repo.findRef(Constants.R_HEADS + localName) != null) { git.checkout().setName(localName).call(); return@use }
        git.checkout().setCreateBranch(true).setName(localName).setStartPoint(remote.name)
            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call()
        Unit
    }

    fun deleteBranch(dir: File, name: String, force: Boolean = false) = open(dir).use { git ->
        require(name != git.repository.branch) { "Cannot delete the current branch." }
        git.branchDelete().setBranchNames(Constants.R_HEADS + name).setForce(force).call()
        Unit
    }

    fun merge(dir: File, branch: String, name: String, email: String): MergeOutcome = open(dir).use { git ->
        val repo = git.repository
        val ref = repo.findRef(branch) ?: throw GitException("No such branch: $branch")
        val who = PersonIdent(name.ifBlank { "Saqr Code" }, email.ifBlank { "saqr@users.noreply.github.com" })
        // JGit takes the committer from config, so set it for this repo.
        repo.config.apply { setString("user", null, "name", who.name); setString("user", null, "email", who.emailAddress); save() }
        val r = git.merge().include(ref).setCommit(true).setFastForward(MergeCommand.FastForwardMode.FF)
            .setMessage("Merge branch '${Repository.shortenRefName(ref.name)}' into ${repo.branch}").call()
        toOutcome(r)
    }

    fun abortMerge(dir: File) = open(dir).use { git ->
        val repo = git.repository
        git.reset().setMode(ResetCommand.ResetType.HARD).call()
        repo.writeMergeHeads(null); repo.writeMergeCommitMsg(null)
    }

    /** v0.1.28: shelves tracked and untracked changes (like `git stash -u`). Returns the stash id, or null when there was nothing to shelve. */
    fun stash(dir: File, name: String, email: String, message: String? = null): String? = open(dir).use { git ->
        val repo = git.repository
        if (repo.resolve(Constants.HEAD) == null) throw GitException("Commit once before stashing")
        val who = PersonIdent(name.ifBlank { "Saqr Code" }, email.ifBlank { "saqr@users.noreply.github.com" })
        repo.config.apply { setString("user", null, "name", who.name); setString("user", null, "email", who.emailAddress); save() }
        val c = git.stashCreate().setIncludeUntracked(true)
            .setWorkingDirectoryMessage(message?.takeIf { it.isNotBlank() } ?: "Saqr stash on ${repo.branch}")
            .call()
        c?.name?.take(7)
    }

    /** v0.1.28: how many stashes this repo holds. */
    fun stashCount(dir: File): Int = open(dir).use { git -> git.stashList().call().size }

    /** v0.1.28: applies the newest stash and drops it. On conflicts the stash is kept and the conflicted paths are returned. */
    fun stashPop(dir: File): List<String> = open(dir).use { git ->
        if (git.stashList().call().isEmpty()) throw GitException("No stash to apply")
        try {
            git.stashApply().setStashRef("stash@{0}").call()
        } catch (e: org.eclipse.jgit.api.errors.StashApplyFailureException) {
            return@use git.status().call().conflicting.sorted().ifEmpty { throw GitException("Stash did not apply: ${e.message}", e) }
        }
        git.stashDrop().setStashRef(0).call()
        emptyList()
    }

    private fun toOutcome(r: MergeResult) = MergeOutcome(
        status = r.mergeStatus.name, success = r.mergeStatus.isSuccessful,
        conflicts = r.conflicts?.keys?.sorted().orEmpty(), newHead = r.newHead?.name?.take(7),
    )

    private fun validateBranchName(n: String) {
        require(n.isNotBlank() && Repository.isValidRefName(Constants.R_HEADS + n) && !n.contains(' ')) { "Invalid branch name: $n" }
    }

    // ------------------------------------------------------------------ remotes / network

    fun remotes(dir: File): List<GitRemote> = open(dir).use { git ->
        git.remoteList().call().map { GitRemote(it.name, it.urIs.firstOrNull()?.toString().orEmpty()) }
    }

    fun setRemote(dir: File, name: String, url: String) = open(dir).use { git ->
        require(name.isNotBlank() && url.isNotBlank()) { "Remote name and URL are required." }
        require(!Regex("""https?://[^/\s]+@""").containsMatchIn(url)) { "Don't put tokens in the remote URL; add them in Settings → Accounts." }
        if (git.remoteList().call().any { it.name == name }) git.remoteSetUrl().setRemoteName(name).setRemoteUri(URIish(url.trim())).call()
        else git.remoteAdd().setName(name).setUri(URIish(url.trim())).call()
        Unit
    }

    fun removeRemote(dir: File, name: String) = open(dir).use { git -> git.remoteRemove().setRemoteName(name).call(); Unit }

    fun remoteUrl(dir: File, remote: String = "origin"): String? =
        open(dir).use { it.repository.config.getString("remote", remote, "url") }

    fun fetch(dir: File, remote: String = "origin", monitor: ProgressMonitor? = null): String = open(dir).use { git ->
        val url = remoteUrl(git, remote)
        wrap(url) {
            val cmd = git.fetch().setRemote(remote).setRemoveDeletedRefs(true)
            credentials.forUrl(url)?.let { cmd.setCredentialsProvider(it.provider()) }
            if (monitor != null) cmd.setProgressMonitor(monitor)
            val r = cmd.call()
            val n = r.trackingRefUpdates.size
            if (n == 0) "Already up to date." else "Fetched $n ref(s) from $remote."
        }
    }

    fun pull(dir: File, remote: String = "origin", name: String = "", email: String = "", monitor: ProgressMonitor? = null): MergeOutcome =
        open(dir).use { git ->
            val repo = git.repository
            val url = remoteUrl(git, remote)
            if (name.isNotBlank()) repo.config.apply { setString("user", null, "name", name); setString("user", null, "email", email); save() }
            wrap(url) {
                val branch = repo.branch
                val cmd = git.pull().setRemote(remote).setRemoteBranchName(upstreamBranch(repo, branch))
                credentials.forUrl(url)?.let { cmd.setCredentialsProvider(it.provider()) }
                if (monitor != null) cmd.setProgressMonitor(monitor)
                val r = cmd.call()
                val m = r.mergeResult
                if (m != null) toOutcome(m)
                else MergeOutcome(if (r.isSuccessful) "OK" else "FAILED", r.isSuccessful, emptyList(), null)
            }
        }

    fun push(dir: File, remote: String = "origin", branch: String? = null, force: Boolean = false, monitor: ProgressMonitor? = null): PushOutcome =
        open(dir).use { git ->
            val repo = git.repository
            val url = remoteUrl(git, remote) ?: throw GitException("No remote '$remote'. Add one first.")
            val b = branch ?: repo.branch
            require(repo.resolve(Constants.HEAD) != null) { "Nothing to push yet: make a commit first." }
            wrap(url) {
                val cmd = git.push().setRemote(remote).setRefSpecs(RefSpec("refs/heads/$b:refs/heads/$b")).setForce(force)
                credentials.forUrl(url)?.let { cmd.setCredentialsProvider(it.provider()) }
                if (monitor != null) cmd.setProgressMonitor(monitor)
                val lines = ArrayList<String>()
                var ok = true
                cmd.call().forEach { res ->
                    res.remoteUpdates.forEach { u ->
                        val good = u.status == RemoteRefUpdate.Status.OK || u.status == RemoteRefUpdate.Status.UP_TO_DATE
                        if (!good) ok = false
                        lines += "${Repository.shortenRefName(u.remoteName)}: ${u.status}${u.message?.let { " ($it)" } ?: ""}"
                    }
                }
                if (ok) setUpstreamIfMissing(repo, b, remote)
                PushOutcome(ok, lines)
            }
        }

    private fun setUpstreamIfMissing(repo: Repository, branch: String, remote: String) {
        val cfg = repo.config
        if (cfg.getString("branch", branch, "remote") == null) {
            cfg.setString("branch", branch, "remote", remote)
            cfg.setString("branch", branch, "merge", Constants.R_HEADS + branch)
            cfg.save()
        }
    }

    private fun upstreamBranch(repo: Repository, branch: String): String =
        repo.config.getString("branch", branch, "merge")?.removePrefix(Constants.R_HEADS) ?: branch

    private fun remoteUrl(git: Git, remote: String) = git.repository.config.getString("remote", remote, "url")

    // ------------------------------------------------------------------ .gitignore

    fun ensureGitignore(dir: File): File {
        val f = File(dir, ".gitignore")
        if (!f.exists()) f.writeText(DEFAULT_GITIGNORE)
        return f
    }

    fun addToGitignore(dir: File, pattern: String) {
        val f = ensureGitignore(dir)
        val lines = f.readLines()
        if (lines.none { it.trim() == pattern.trim() }) f.appendText((if (f.readText().endsWith("\n") || f.length() == 0L) "" else "\n") + pattern.trim() + "\n")
    }

    // ------------------------------------------------------------------ helpers

    private fun open(dir: File): Git {
        if (!isRepo(dir)) throw GitException("Not a git repository. Initialise it first.")
        return Git.open(dir)
    }

    private fun <T> wrap(url: String?, block: () -> T): T = try {
        block()
    } catch (e: GitException) { throw e } catch (e: IllegalArgumentException) { throw e } catch (e: Exception) {
        val cred = url?.let { credentials.forUrl(it) }
        val raw = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.firstOrNull() ?: e.javaClass.simpleName
        val msg = Redact.text(raw, cred?.token)
        val hint = when {
            raw.contains("not authorized", true) || raw.contains("Authentication is required", true) || raw.contains("401") ->
                " — check the token in Settings → Accounts (needs Contents: Read and write)."
            raw.contains("not found", true) || raw.contains("404") -> " — check the repository URL and that the token can access it."
            else -> ""
        }
        throw GitException(msg + hint, null)
    }

    private fun headTreeIterator(repo: Repository): AbstractTreeIterator {
        val head = repo.resolve("HEAD^{tree}") ?: return EmptyTreeIterator()
        return CanonicalTreeParser().apply { repo.newObjectReader().use { reset(it, head) } }
    }

    private object NullOut : java.io.OutputStream() { override fun write(b: Int) {} }

    companion object {
        const val DEFAULT_GITIGNORE = "# Saqr Code defaults\nnode_modules/\nbuild/\n.gradle/\n__pycache__/\n.venv/\n.env\n*.keystore\n*.jks\n.saqr/\n"

        /** Counts insertions/deletions of a unified diff (ignores headers). */
        fun diffStats(diff: String): Pair<Int, Int> {
            var a = 0; var r = 0
            diff.lineSequence().forEach { l ->
                if (l.startsWith("+++") || l.startsWith("---")) return@forEach
                if (l.startsWith("+")) a++ else if (l.startsWith("-")) r++
            }
            return a to r
        }

        /** "owner/repo" from common GitHub URL forms, or null. */
        fun githubSlug(url: String?): Pair<String, String>? {
            val u = url?.trim() ?: return null
            val m = Regex("""(?:https?://(?:[^@/]+@)?github\.com/|git@github\.com:)([^/\s]+)/([^/\s]+?)(?:\.git)?/?$""").find(u) ?: return null
            return m.groupValues[1] to m.groupValues[2]
        }
    }
}
