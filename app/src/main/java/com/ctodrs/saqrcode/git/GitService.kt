package com.ctodrs.saqrcode.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.util.FS
import org.json.JSONObject
import java.io.File

/** Real git via JGit; pushes over HTTPS with a user-supplied fine-grained token. */
class GitService(private val homeDir: File) {
    init {
        // Avoid JGit probing $HOME/.gitconfig outside the app sandbox.
        FS.DETECTED.setUserHome(homeDir)
    }

    private val http = OkHttpClient()

    suspend fun status(dir: File): String = withContext(Dispatchers.IO) {
        if (!File(dir, ".git").exists()) return@withContext "Not a git repository yet (it will be initialised on first push)."
        Git.open(dir).use { git ->
            val s = git.status().call()
            val branch = git.repository.branch
            buildString {
                appendLine("Branch: $branch")
                val lines = s.added.map { "A  $it" } + s.changed.map { "M  $it" } + s.modified.map { " M $it" } +
                    s.untracked.map { "?? $it" } + s.missing.map { " D $it" } + s.removed.map { "D  $it" }
                if (lines.isEmpty()) appendLine("Clean working tree.") else lines.take(200).forEach { appendLine(it) }
                runCatching {
                    git.log().setMaxCount(5).call().forEach { appendLine("• ${it.name.take(7)} ${it.shortMessage}") }
                }
            }
        }
    }

    /** Creates the repo on GitHub (private) if it does not exist. Returns a human message. */
    suspend fun ensureRepo(owner: String, repo: String, token: String): String = withContext(Dispatchers.IO) {
        val get = Request.Builder().url("https://api.github.com/repos/$owner/$repo")
            .header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").build()
        http.newCall(get).execute().use { if (it.code == 200) return@withContext "Repository exists." }
        val body = JSONObject().put("name", repo).put("private", true).put("auto_init", false)
            .put("description", "Created with Saqr Code").toString()
        val post = Request.Builder().url("https://api.github.com/user/repos")
            .header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(post).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (r.code == 201) "Created private repository $owner/$repo."
            else error("Could not create repo (HTTP ${r.code}): ${runCatching { JSONObject(txt).optString("message") }.getOrDefault(txt.take(200))}")
        }
    }

    suspend fun commitAndPush(
        dir: File, ownerRepo: String, user: String, token: String, message: String,
    ): String = withContext(Dispatchers.IO) {
        val (owner, repo) = ownerRepo.trim().removeSuffix(".git").split('/').let {
            require(it.size == 2 && it.all { s -> s.isNotBlank() }) { "Use owner/name" }
            it[0] to it[1]
        }
        val git = if (File(dir, ".git").exists()) Git.open(dir) else Git.init().setDirectory(dir).setInitialBranch("main").call()
        git.use { g ->
            ensureGitignore(dir)
            g.add().addFilepattern(".").call()
            // stage deletions too
            g.add().addFilepattern(".").setUpdate(true).call()
            val ident = PersonIdent(user.ifBlank { owner }, "${user.ifBlank { owner }}@users.noreply.github.com")
            val st = g.status().call()
            val hasHead = g.repository.resolve("HEAD") != null
            val log = StringBuilder()
            if (!st.isClean || !hasHead) {
                val c = g.commit().setMessage(message.ifBlank { "Update from Saqr Code" })
                    .setAuthor(ident).setCommitter(ident).setAllowEmpty(!hasHead).call()
                log.appendLine("Committed ${c.name.take(7)}: ${c.shortMessage}")
            } else log.appendLine("Nothing new to commit.")
            val url = "https://github.com/$owner/$repo.git"
            val remotes = g.remoteList().call()
            if (remotes.none { it.name == "origin" }) g.remoteAdd().setName("origin").setUri(URIish(url)).call()
            else g.remoteSetUrl().setRemoteName("origin").setRemoteUri(URIish(url)).call()
            val branch = g.repository.branch
            val res = g.push().setRemote("origin").add(branch)
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(user.ifBlank { "x-access-token" }, token))
                .call()
            res.forEach { r ->
                r.remoteUpdates.forEach { u -> log.appendLine("${u.remoteName}: ${u.status}${u.message?.let { " ($it)" } ?: ""}") }
            }
            log.appendLine(url.removeSuffix(".git"))
            log.toString()
        }
    }

    private fun ensureGitignore(dir: File) {
        val f = File(dir, ".gitignore")
        if (!f.exists()) f.writeText("node_modules/\nbuild/\n.gradle/\n__pycache__/\n.venv/\n.env\n*.keystore\n*.jks\n")
    }
}
