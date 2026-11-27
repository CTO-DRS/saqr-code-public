package com.ctodrs.saqrcode.git

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ctodrs.saqrcode.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A short result line for the Git screen ("Pushed to origin/main", errors in red). */
data class GitToast(val text: String, val error: Boolean = false, val link: String? = null)

/**
 * UI state holder for one project's repository. All JGit work happens on Dispatchers.IO;
 * the Compose screen only reads the snapshot fields below.
 */
class GitController(val dir: File, private val settings: SettingsStore, private val homeDir: File) {
    val git = GitService(homeDir, settings.accounts)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var status by mutableStateOf(GitStatus(isRepo = File(dir, ".git").exists()))
        private set
    var log by mutableStateOf<List<GitCommitInfo>>(emptyList())
        private set
    var branches by mutableStateOf<List<GitBranch>>(emptyList())
        private set
    var remotes by mutableStateOf<List<GitRemote>>(emptyList())
        private set
    /** Non-null while a long operation runs (label shown next to a spinner). */
    var busy by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<GitToast?>(null)

    val originUrl get() = remotes.firstOrNull { it.name == "origin" }?.url ?: remotes.firstOrNull()?.url
    val githubSlug get() = GitService.githubSlug(originUrl)

    fun refresh() = scope.launch {
        val (s, l, b, r) = withContext(Dispatchers.IO) {
            val s = runCatching { git.status(dir) }.getOrElse { GitStatus(isRepo = File(dir, ".git").exists()) }
            if (!s.isRepo) return@withContext Quad(s, emptyList(), emptyList(), emptyList())
            Quad(s, runCatching { git.log(dir, 200) }.getOrDefault(emptyList()),
                runCatching { git.branches(dir) }.getOrDefault(emptyList()), runCatching { git.remotes(dir) }.getOrDefault(emptyList()))
        }
        status = s; log = l; branches = b; remotes = r
    }

    /** Runs [block] on IO with a busy label; refreshes afterwards; failures become a red toast. */
    fun op(label: String, block: suspend GitService.() -> String?) = scope.launch {
        if (busy != null) return@launch
        busy = label
        try {
            val msg = withContext(Dispatchers.IO) { git.block() }
            if (msg != null) toast = GitToast(msg)
        } catch (e: Exception) {
            toast = GitToast(Redact.text(e.message ?: e.javaClass.simpleName, settings.githubToken), error = true)
        } finally {
            busy = null
            refresh()
        }
    }

    fun init() = op("init") { init(dir) }
    fun toggleStage(f: GitFileStatus) = op("stage") { if (f.isStaged && !f.isUnstaged) unstage(dir, listOf(f.path)) else stage(dir, listOf(f.path)); null }
    fun stageAll() = op("stage") { stageAll(dir); null }
    fun unstageAll() = op("stage") { unstageAll(dir); null }
    fun discard(path: String) = op("discard") { discard(dir, path); null }

    fun commit(message: String, amend: Boolean = false, thenPush: Boolean = false) = op(if (thenPush) "push" else "commit") {
        val st = status(dir, withCounts = false)
        val all = st.staged.isEmpty() && !amend && st.conflicts.isEmpty() // nothing staged: commit everything (like "commit -a" + new files)
        if (all) stageAll(dir)
        val c = commit(dir, message, settings.gitName, settings.gitEmail, amend = amend)
        if (thenPush) {
            val p = push(dir)
            if (!p.ok) throw GitException(p.lines.joinToString("\n"))
            "✓ ${c.shortId} · ${p.lines.joinToString(" · ")}"
        } else "✓ ${c.shortId} ${c.message}"
    }

    fun fetch() = op("fetch") { fetch(dir) }
    fun pull() = op("pull") {
        val m = pull(dir, name = settings.gitName, email = settings.gitEmail)
        if (m.conflicts.isNotEmpty()) throw GitException("Conflicts: ${m.conflicts.joinToString()}")
        m.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
    fun push(force: Boolean = false) = op("push") {
        val p = push(dir, force = force)
        if (!p.ok) throw GitException(p.lines.joinToString("\n"))
        p.lines.joinToString(" · ")
    }

    fun createBranch(name: String) = op("branch") { createBranch(dir, name.trim()); "✓ $name" }
    fun checkout(name: String) = op("checkout") { checkout(dir, name); "✓ $name" }
    fun deleteBranch(name: String) = op("branch") { deleteBranch(dir, name, force = true); "✓ −$name" }
    fun merge(name: String) = op("merge") {
        val m = merge(dir, name, settings.gitName, settings.gitEmail)
        if (!m.success) throw GitException("Merge: ${m.status}. Conflicts: ${m.conflicts.joinToString()}")
        m.status.replace('_', ' ').lowercase()
    }
    fun stash() = op("stash") { stash(dir, settings.gitName, settings.gitEmail)?.let { "✓ stash $it" } ?: "Nothing to stash" }
    fun stashPop() = op("stash") {
        val c = stashPop(dir)
        if (c.isNotEmpty()) throw GitException("Conflicts: ${c.joinToString()} (stash kept)")
        "✓ stash applied"
    }
    fun abortMerge() = op("merge") { abortMerge(dir); null }

    /** v0.1.22: the working-tree text of a conflicted file, or null when [path] escapes the project or is unreadable. */
    fun conflictText(path: String): String? = runCatching {
        val f = File(dir, path).canonicalFile
        if (!f.path.startsWith(dir.canonicalPath + File.separator) || !f.isFile) null else f.readText()
    }.getOrNull()

    /** v0.1.22: writes the resolved [text] over a conflicted file and stages it. */
    fun resolveConflict(path: String, text: String) = op("stage") {
        val f = File(dir, path).canonicalFile
        require(f.path.startsWith(dir.canonicalPath + File.separator)) { "Path is outside the project." }
        f.writeText(text)
        stage(dir, listOf(path))
        null
    }
    fun setRemote(name: String, url: String) = op("remote") { setRemote(dir, name, url); null }
    fun removeRemote(name: String) = op("remote") { removeRemote(dir, name); null }

    /** Creates a GitHub repo for this project, wires it as origin and pushes. */
    fun publishToGitHub(name: String, private: Boolean, description: String?) = op("publish") {
        val token = settings.accounts.githubToken() ?: throw GitException("Link your GitHub account first (Settings → Accounts).")
        val api = GitHubApi(token)
        val repo = runCatching { api.createRepo(name, private, description) }.getOrElse { e ->
            // Already exists under this account? Reuse it.
            val login = settings.accounts.github?.login
            if (e is GhException && e.code == 422 && login != null) api.repo(login, name) else throw e
        }
        if (!isRepo(dir)) init(dir)
        if (status(dir, false).hasCommits.not()) { stageAll(dir); commit(dir, "Initial commit", settings.gitName, settings.gitEmail) }
        setRemote(dir, "origin", repo.cloneUrl)
        val p = push(dir)
        if (!p.ok) throw GitException(p.lines.joinToString("\n"))
        toast = GitToast("✓ ${repo.fullName}", link = repo.htmlUrl)
        null
    }

    /**
     * v0.10: same publish flow for GitLab / Gitea hosts stored in Accounts. The API dialect
     * comes from the per-host pick (or auto-detection); the token comes from the account store.
     */
    fun publishToForge(host: String, name: String, private: Boolean, description: String?) = op("publish") {
        val kind = Forge.detect(host, settings.accounts.hostKind(host))
            ?: throw GitException("Pick GitLab or Gitea for $host in Settings → Accounts (auto-detect failed).")
        val cred = settings.accounts.forUrl("https://$host")
            ?: throw GitException("No token saved for $host (Settings → Accounts).")
        val repo = when (kind) {
            Forge.Kind.GITLAB -> GitLabApi(host, cred.token).createRepo(name, private, description)
            Forge.Kind.GITEA -> GiteaApi(host, cred.token).createRepo(name, private, description)
        }
        if (!isRepo(dir)) init(dir)
        if (status(dir, false).hasCommits.not()) { stageAll(dir); commit(dir, "Initial commit", settings.gitName, settings.gitEmail) }
        setRemote(dir, "origin", repo.cloneUrl)
        val p = push(dir)
        if (!p.ok) throw GitException(p.lines.joinToString("\n"))
        toast = GitToast("✓ ${repo.fullName}", link = repo.htmlUrl)
        null
    }

    fun openPullRequest(title: String, base: String, body: String?) = op("pr") {
        val token = settings.accounts.githubToken() ?: throw GitException("Link your GitHub account first (Settings → Accounts).")
        val (owner, repo) = GitService.githubSlug(remoteUrl(dir)) ?: throw GitException("origin is not a GitHub repository.")
        val branch = status(dir, false).branch ?: throw GitException("No branch")
        require(branch != base) { "Switch to a feature branch first (current: $branch)." }
        val p = push(dir, branch = branch)
        if (!p.ok) throw GitException(p.lines.joinToString("\n"))
        val pr = GitHubApi(token).createPull(owner, repo, title, branch, base, body)
        toast = GitToast("✓ PR #${pr.number}", link = pr.htmlUrl)
        null
    }

    /**
     * v0.10: MR / PR on the forge that owns origin — GitLab merge request or Gitea pull
     * request. [host] must match origin's host; the repo path is read from the remote URL.
     */
    fun openForgePullRequest(host: String, title: String, base: String, body: String?) = op("pr") {
        val url = remoteUrl(dir) ?: throw GitException("No origin remote.")
        val kind = Forge.detect(host, settings.accounts.hostKind(host))
            ?: throw GitException("Pick GitLab or Gitea for $host in Settings → Accounts.")
        val cred = settings.accounts.forUrl("https://$host")
            ?: throw GitException("No token saved for $host (Settings → Accounts).")
        val path = Forge.repoPath(url) ?: throw GitException("origin is not a $host repository: $url")
        val branch = status(dir, false).branch ?: throw GitException("No branch")
        require(branch != base) { "Switch to a feature branch first (current: $branch)." }
        val p = push(dir, branch = branch)
        if (!p.ok) throw GitException(p.lines.joinToString("\n"))
        val pr = when (kind) {
            Forge.Kind.GITLAB -> GitLabApi(host, cred.token).openPull(path, title, branch, base, body)
            Forge.Kind.GITEA -> GiteaApi(host, cred.token).openPull(path, title, branch, base, body)
        }
        toast = GitToast("✓ MR/PR #${pr.number}", link = pr.htmlUrl)
        null
    }

    // ------------------------------------------------------------------ v0.1.11: pull requests (GitHub)

    private val mergeStore by lazy { MergeRecords(File(homeDir, "pr_merges.json")) }

    /** Open PRs of the GitHub origin; null = never loaded (the PR tab triggers the first load). */
    var prs by mutableStateOf<List<PrInfo>?>(null)
        private set
    /** The merge that can still be undone, and whether the base branch is still at the merge commit. */
    var pendingMerge by mutableStateOf<MergeRecord?>(null)
        private set
    var pendingMergeReady by mutableStateOf(false)
        private set

    private fun ghApi(): Pair<GitHubApi, Pair<String, String>>? {
        val slug = githubSlug ?: return null
        val token = settings.accounts.githubToken() ?: return null
        return GitHubApi(token) to slug
    }

    /** Loads the open PRs and re-checks whether the last merge is still undoable. */
    fun loadPrs() = scope.launch {
        if (busy != null) return@launch
        val (api, slug) = ghApi() ?: return@launch
        busy = "prs"
        try {
            val r = withContext(Dispatchers.IO) {
                val list = api.listPulls(slug.first, slug.second)
                val p = mergeStore.pending(slug.first, slug.second)
                val head = if (p != null) runCatching { api.refSha(slug.first, slug.second, p.baseRef) }.getOrNull() else null
                Triple(list, p, head)
            }
            prs = r.first
            pendingMerge = r.second
            pendingMergeReady = r.second != null && PrMerge.undoState(r.second!!, r.third) == PrMerge.UndoState.READY
        } catch (e: Exception) {
            toast = GitToast(Redact.text(e.message ?: e.javaClass.simpleName, settings.githubToken), error = true)
        } finally {
            busy = null
        }
    }

    /** The PR's counts (single fetch); throws on network errors. */
    suspend fun prDetail(pr: PrInfo): PrDetail = withContext(Dispatchers.IO) {
        val (api, slug) = ghApi() ?: throw GitException("Link your GitHub account first (Settings → Accounts).")
        api.pull(slug.first, slug.second, pr.number)
    }

    /** The PR's whole diff as one unified-diff text for the diff viewer. */
    suspend fun prDiff(pr: PrInfo): String = withContext(Dispatchers.IO) {
        val (api, slug) = ghApi() ?: throw GitException("Link your GitHub account first (Settings → Accounts).")
        PrMerge.combinedDiff(api.pullFiles(slug.first, slug.second, pr.number))
    }

    /** Merges on GitHub and stores the undo record; the banner offers Undo until the base branch moves. */
    fun mergePr(pr: PrInfo, method: String) = scope.launch {
        if (busy != null) return@launch
        val (api, slug) = ghApi() ?: return@launch
        busy = "merge"
        try {
            val rec = withContext(Dispatchers.IO) { api.mergePull(slug.first, slug.second, pr.number, pr.baseRef, method) }
            mergeStore.add(rec)
            pendingMerge = rec; pendingMergeReady = true
            toast = GitToast("✓ Merged #${pr.number}", link = pr.htmlUrl)
        } catch (e: Exception) {
            toast = GitToast(Redact.text(e.message ?: e.javaClass.simpleName, settings.githubToken), error = true)
        } finally {
            busy = null
            loadPrs()
        }
    }

    /** Undoes the pending merge by moving the base branch back; refuses when the branch moved on. */
    fun undoMerge() = scope.launch {
        if (busy != null) return@launch
        val (api, slug) = ghApi() ?: return@launch
        val rec = pendingMerge ?: return@launch
        busy = "undo"
        try {
            withContext(Dispatchers.IO) {
                val head = api.refSha(slug.first, slug.second, rec.baseRef)
                if (PrMerge.undoState(rec, head) != PrMerge.UndoState.READY)
                    throw GitException("${rec.baseRef} moved on — undo is no longer safe.")
                api.resetRef(slug.first, slug.second, rec.baseRef, rec.preMergeSha)
            }
            mergeStore.markUndone(rec)
            pendingMerge = null; pendingMergeReady = false
            toast = GitToast("✓ Merge of #${rec.number} undone")
        } catch (e: Exception) {
            toast = GitToast(Redact.text(e.message ?: e.javaClass.simpleName, settings.githubToken), error = true)
        } finally {
            busy = null
            loadPrs()
        }
    }

    suspend fun diffOf(path: String, staged: Boolean): String = withContext(Dispatchers.IO) {
        runCatching { git.diff(dir, path, staged) }.getOrElse { it.message ?: "" }
    }

    suspend fun commitDiff(id: String): String = withContext(Dispatchers.IO) {
        runCatching { git.commitDiff(dir, id) }.getOrElse { it.message ?: "" }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
