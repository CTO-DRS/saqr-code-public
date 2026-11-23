package com.ctodrs.saqrcode.git

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * v0.10 · دعم GitLab وGitea · GitLab / Gitea REST clients.
 *
 * The account store already keeps tokens for any HTTPS host; the missing half was the API:
 * creating a repository and opening a merge request / pull request on GitLab and Gitea.
 * Both hosts speak their own REST dialect, so [Forge.detect] resolves the dialect for a
 * host — from the user's explicit pick in Settings → Accounts, or automatically from the
 * hostname when the pick is "auto" (gitlab.com and *gitlab* → GitLab; *gitea* and codeberg
 * → Gitea). Tokens travel only in headers, never in URLs or logs (redaction applies).
 *
 * Pure helpers ([Forge.detect], [GitLabApi.projectId], [GitLabApi.visibility]) are
 * unit-tested in V010FeaturesTest.
 */
object Forge {

    enum class Kind { GITLAB, GITEA }

    /** [setting] is "auto" (or blank), "gitlab", "gitea" — as stored per host in [com.ctodrs.saqrcode.data.AccountStore]. */
    fun detect(host: String, setting: String): Kind? {
        val s = setting.trim().lowercase()
        when (s) {
            "gitlab" -> return Kind.GITLAB
            "gitea" -> return Kind.GITEA
        }
        val h = host.lowercase()
        return when {
            h.startsWith("gitlab.") || h.contains("gitlab") -> Kind.GITLAB
            h.contains("gitea") || h.startsWith("codeberg.") || h == "codeberg.org" -> Kind.GITEA
            else -> null
        }
    }

    /**
     * Repository path inside a clone URL for [host]: `https://gitlab.com/group/sub/p.git` →
     * `group/sub/p`, `git@codeberg.org:owner/repo.git` → `owner/repo`. Groups make GitLab
     * paths deeper than owner/repo, so this keeps every segment.
     */
    fun repoPath(url: String): String? {
        var s = url.trim()
        if (s.startsWith("git@")) {
            s = s.substringAfter('@').substringAfter(':', "")
        } else {
            val withScheme = if ("://" in s) s else "https://$s"
            s = runCatching { java.net.URI(withScheme).path }.getOrNull() ?: return null
        }
        val clean = s.trim('/').removeSuffix(".git")
        if (clean.isEmpty() || !clean.contains('/')) return null
        return clean
    }
}

/** One repo as returned by a forge API — clone URL is what JGit pushes to. */
data class ForgeRepo(val fullName: String, val cloneUrl: String, val htmlUrl: String, val defaultBranch: String)

abstract class ForgeApi(
    protected val host: String,
    protected val token: String,
    http: OkHttpClient?,
) {
    protected val client: OkHttpClient = (http ?: GitHubApi.defaultClient).newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Creates a repository on the user's account. */
    abstract fun createRepo(name: String, private: Boolean, description: String?): ForgeRepo

    /** Opens a merge request (GitLab) or pull request (Gitea). */
    abstract fun openPull(fullPath: String, title: String, source: String, target: String, body: String?): GhPull

    protected fun json(body: String): okhttp3.RequestBody = body.toRequestBody(JSON)

    protected fun fail(code: Int, raw: String, what: String): Nothing {
        val msg = runCatching { JSONObject(raw).optString("message") }.getOrNull().orEmpty().ifBlank { "HTTP $code" }
        throw GhException(code, Redact.text("$what: $msg (HTTP $code)", token))
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

/** GitLab REST v4: PRIVATE-TOKEN auth, URL-encoded project ids, `visibility` instead of `private`. */
class GitLabApi(host: String, token: String, http: OkHttpClient? = null) : ForgeApi(host, token, http) {

    private val api get() = "https://$host/api/v4"

    override fun createRepo(name: String, private: Boolean, description: String?): ForgeRepo {
        val b = JSONObject()
            .put("name", name)
            .put("path", name)
            .put("visibility", visibility(private))
            .put("initialize_with_readme", false)
        if (!description.isNullOrBlank()) b.put("description", description)
        val req = Request.Builder().url("$api/projects").header("PRIVATE-TOKEN", token).post(json(b.toString())).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                // Project already exists under this namespace? Reuse it.
                if (r.code == 400 && t.contains("has already been taken")) {
                    val me = me()
                    return repoByPath(me, name)
                }
                fail(r.code, t, "GitLab")
            }
            val o = JSONObject(t)
            return ForgeRepo(
                fullName = o.getString("path_with_namespace"),
                cloneUrl = o.getString("http_url_to_repo"),
                htmlUrl = o.getString("web_url"),
                defaultBranch = o.optString("default_branch", "main").ifBlank { "main" },
            )
        }
    }

    override fun openPull(fullPath: String, title: String, source: String, target: String, body: String?): GhPull {
        val b = JSONObject()
            .put("source_branch", source)
            .put("target_branch", target)
            .put("title", title)
        if (!body.isNullOrBlank()) b.put("description", body)
        val req = Request.Builder()
            .url("$api/projects/${projectId(fullPath)}/merge_requests")
            .header("PRIVATE-TOKEN", token).post(json(b.toString())).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) fail(r.code, t, "GitLab MR")
            val o = JSONObject(t)
            return GhPull(o.getInt("iid"), o.optString("web_url"), o.optString("title"))
        }
    }

    /** The namespace owner of a fresh repo — the authenticated user for user projects. */
    private fun me(): String {
        val req = Request.Builder().url("$api/user").header("PRIVATE-TOKEN", token).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) fail(r.code, t, "GitLab")
            return JSONObject(t).optString("username").ifBlank { "root" }
        }
    }

    private fun repoByPath(owner: String, name: String): ForgeRepo {
        val req = Request.Builder().url("$api/projects/${projectId("$owner/$name")}")
            .header("PRIVATE-TOKEN", token).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) fail(r.code, t, "GitLab")
            val o = JSONObject(t)
            return ForgeRepo(
                fullName = o.getString("path_with_namespace"),
                cloneUrl = o.getString("http_url_to_repo"),
                htmlUrl = o.getString("web_url"),
                defaultBranch = o.optString("default_branch", "main").ifBlank { "main" },
            )
        }
    }

    companion object {
        /** `group/sub/proj` → `group%2Fsub%2Fproj` (GitLab wants the whole path encoded). */
        fun projectId(fullPath: String): String =
            URLEncoder.encode(fullPath.trim().trim('/'), "UTF-8")

        fun visibility(private: Boolean): String = if (private) "private" else "public"
    }
}

/** Gitea REST v1 (also works for Codeberg and Forgejo instances): `token` header auth. */
class GiteaApi(host: String, token: String, http: OkHttpClient? = null) : ForgeApi(host, token, http) {

    private val api get() = "https://$host/api/v1"

    override fun createRepo(name: String, private: Boolean, description: String?): ForgeRepo {
        val b = JSONObject()
            .put("name", name)
            .put("private", private)
            .put("auto_init", false)
        if (!description.isNullOrBlank()) b.put("description", description)
        val req = Request.Builder().url("$api/user/repos").header("Authorization", "token $token").post(json(b.toString())).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                // 409 = already exists: reuse it under the user's namespace.
                if (r.code == 409) return repo(me().login, name)
                fail(r.code, t, "Gitea")
            }
            return repoJson(JSONObject(t))
        }
    }

    override fun openPull(fullPath: String, title: String, source: String, target: String, body: String?): GhPull {
        val (owner, name) = fullPath.split('/').let { it.first() to it.last() }
        val b = JSONObject().put("title", title).put("head", source).put("base", target)
        if (!body.isNullOrBlank()) b.put("body", body)
        val req = Request.Builder().url("$api/repos/$owner/$name/pulls")
            .header("Authorization", "token $token").post(json(b.toString())).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) fail(r.code, t, "Gitea PR")
            val o = JSONObject(t)
            return GhPull(o.getInt("number"), o.optString("html_url"), o.optString("title"))
        }
    }

    private fun me(): ForgeUser {
        val req = Request.Builder().url("$api/user").header("Authorization", "token $token").build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) fail(r.code, t, "Gitea")
            return ForgeUser(JSONObject(t).optString("login").ifBlank { "user" })
        }
    }

    private fun repo(owner: String, name: String): ForgeRepo {
        val req = Request.Builder().url("$api/repos/$owner/$name").header("Authorization", "token $token").build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) fail(r.code, t, "Gitea")
            return repoJson(JSONObject(t))
        }
    }

    private fun repoJson(o: JSONObject): ForgeRepo {
        val full = o.getString("full_name")
        return ForgeRepo(
            fullName = full,
            cloneUrl = o.optString("clone_url").ifBlank { "https://$host/$full.git" },
            htmlUrl = o.optString("html_url").ifBlank { "https://$host/$full" },
            defaultBranch = o.optString("default_branch", "main").ifBlank { "main" },
        )
    }

    data class ForgeUser(val login: String)
}
