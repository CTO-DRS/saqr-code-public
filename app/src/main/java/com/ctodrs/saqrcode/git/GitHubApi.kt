package com.ctodrs.saqrcode.git

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GhUser(val login: String, val name: String?, val avatarUrl: String?, val id: Long, val scopes: String?, val htmlUrl: String?) {
    /** GitHub's no-reply address, good default for commit email. */
    val noreplyEmail get() = "$id+$login@users.noreply.github.com"
}

data class GhRepo(
    val fullName: String, val name: String, val owner: String, val private: Boolean, val description: String?,
    val defaultBranch: String, val cloneUrl: String, val htmlUrl: String, val updatedAt: String?, val language: String?,
    val stars: Int, val fork: Boolean,
)

data class GhPull(val number: Int, val htmlUrl: String, val title: String)

class GhException(val code: Int, message: String) : Exception(message)

/**
 * Minimal GitHub REST client (official API, token auth). The token only ever goes into the
 * Authorization header; it is never logged or put in URLs.
 */
class GitHubApi(
    private val token: String,
    private val baseUrl: String = "https://api.github.com",
    private val http: OkHttpClient = defaultClient,
) {
    fun me(): GhUser {
        val (o, headers) = getObj("/user")
        return GhUser(
            login = o.getString("login"), name = o.optString("name").takeIf { it.isNotBlank() && it != "null" },
            avatarUrl = o.optString("avatar_url").takeIf { it.isNotBlank() }, id = o.optLong("id"),
            scopes = headers["X-OAuth-Scopes"], htmlUrl = o.optString("html_url").takeIf { it.isNotBlank() },
        )
    }

    fun repos(page: Int = 1, perPage: Int = 50): List<GhRepo> {
        val arr = JSONArray(call(req("/user/repos?sort=updated&per_page=$perPage&page=$page&affiliation=owner,collaborator,organization_member")).first)
        return (0 until arr.length()).map { repo(arr.getJSONObject(it)) }
    }

    fun repo(owner: String, name: String): GhRepo = repo(getObj("/repos/$owner/$name").first)

    fun createRepo(name: String, private: Boolean, description: String? = null): GhRepo {
        val body = JSONObject().put("name", name).put("private", private).put("auto_init", false)
        if (!description.isNullOrBlank()) body.put("description", description)
        return repo(JSONObject(call(req("/user/repos").post(body.toString().toRequestBody(JSON))).first))
    }

    fun createPull(owner: String, repo: String, title: String, head: String, base: String, body: String?): GhPull {
        val b = JSONObject().put("title", title).put("head", head).put("base", base)
        if (!body.isNullOrBlank()) b.put("body", body)
        val o = JSONObject(call(req("/repos/$owner/$repo/pulls").post(b.toString().toRequestBody(JSON))).first)
        return GhPull(o.getInt("number"), o.getString("html_url"), o.optString("title"))
    }

    private fun repo(o: JSONObject) = GhRepo(
        fullName = o.getString("full_name"), name = o.getString("name"), owner = o.getJSONObject("owner").getString("login"),
        private = o.optBoolean("private"), description = o.optString("description").takeIf { it.isNotBlank() && it != "null" },
        defaultBranch = o.optString("default_branch", "main"), cloneUrl = o.optString("clone_url"), htmlUrl = o.optString("html_url"),
        updatedAt = o.optString("updated_at").takeIf { it.isNotBlank() }, language = o.optString("language").takeIf { it.isNotBlank() && it != "null" },
        stars = o.optInt("stargazers_count"), fork = o.optBoolean("fork"),
    )

    private fun getObj(path: String): Pair<JSONObject, Map<String, String>> {
        val (body, headers) = call(req(path))
        return JSONObject(body) to headers
    }

    private fun req(path: String) = Request.Builder().url(baseUrl.trimEnd('/') + path)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "SaqrCode-Android")

    private fun call(b: Request.Builder): Pair<String, Map<String, String>> {
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty().ifBlank { "HTTP ${r.code}" }
                throw GhException(r.code, Redact.text("GitHub: $msg (HTTP ${r.code})", token))
            }
            return text to r.headers.toMultimap().mapValues { it.value.joinToString(",") }.mapKeys { canonical(it.key) }
        }
    }

    private fun canonical(k: String) = k.split('-').joinToString("-") { p -> p.replaceFirstChar { it.uppercase() } }
        .replace("Oauth", "OAuth")

    companion object {
        private val JSON = "application/json".toMediaType()
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        }
        /** Fine-grained PAT permissions Saqr needs, shown in the UI. */
        val REQUIRED_PERMISSIONS = listOf("Contents: Read and write", "Metadata: Read-only")
        val OPTIONAL_PERMISSIONS = listOf("Pull requests: Read and write", "Administration: Read and write (create repositories)")
    }
}

/**
 * GitHub OAuth Device Flow (RFC 8628). Needs only a public client_id (no secret, no backend):
 * the user registers an OAuth App with "Device flow" enabled and pastes its client ID in settings.
 */
class GitHubDeviceFlow(private val base: String = "https://github.com", private val http: OkHttpClient = GitHubApi.defaultClient) {
    data class Code(val deviceCode: String, val userCode: String, val verificationUri: String, val expiresIn: Int, val interval: Int)

    sealed class Poll {
        data class Token(val accessToken: String, val scope: String) : Poll() { override fun toString() = "Token(***)" }
        object Pending : Poll()
        data class SlowDown(val interval: Int) : Poll()
        data class Failed(val error: String) : Poll()
    }

    fun start(clientId: String, scope: String = "repo read:user"): Code {
        val o = post("/login/device/code", FormBody.Builder().add("client_id", clientId.trim()).add("scope", scope).build())
        if (o.has("error")) throw GhException(400, o.optString("error_description", o.optString("error")))
        return Code(o.getString("device_code"), o.getString("user_code"), o.optString("verification_uri", "https://github.com/login/device"),
            o.optInt("expires_in", 900), o.optInt("interval", 5))
    }

    fun poll(clientId: String, deviceCode: String): Poll {
        val o = post("/login/oauth/access_token", FormBody.Builder().add("client_id", clientId.trim())
            .add("device_code", deviceCode).add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build())
        o.optString("access_token").takeIf { it.isNotBlank() }?.let { return Poll.Token(it, o.optString("scope")) }
        return when (val e = o.optString("error")) {
            "authorization_pending" -> Poll.Pending
            "slow_down" -> Poll.SlowDown(o.optInt("interval", 10))
            else -> Poll.Failed(o.optString("error_description").ifBlank { e.ifBlank { "unknown_error" } })
        }
    }

    private fun post(path: String, body: FormBody): JSONObject {
        val r = Request.Builder().url(base.trimEnd('/') + path).header("Accept", "application/json").post(body).build()
        http.newCall(r).execute().use { resp ->
            val t = resp.body?.string().orEmpty()
            return runCatching { JSONObject(t) }.getOrElse { throw GhException(resp.code, "GitHub: HTTP ${resp.code}") }
        }
    }
}
