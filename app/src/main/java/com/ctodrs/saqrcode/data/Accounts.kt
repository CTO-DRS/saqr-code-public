package com.ctodrs.saqrcode.data

import com.ctodrs.saqrcode.git.CredentialResolver
import com.ctodrs.saqrcode.git.GitCredential
import java.net.URI

/** Tiny key/value seam so account logic is testable without the Android Keystore. */
interface KeyValue {
    fun get(name: String): String?
    fun put(name: String, value: String?)
}

class MemoryKeyValue : KeyValue {
    val map = HashMap<String, String>()
    override fun get(name: String) = map[name]?.takeIf { it.isNotBlank() }
    override fun put(name: String, value: String?) { if (value.isNullOrBlank()) map.remove(name) else map[name] = value.trim() }
}

data class GitHubAccount(val login: String, val name: String?, val avatarUrl: String?, val id: Long, val scopes: String?, val viaDeviceFlow: Boolean) {
    val noreplyEmail get() = "$id+$login@users.noreply.github.com"
    val displayName get() = name ?: login
}

data class HostToken(val host: String, val username: String) { override fun toString() = "HostToken($host, $username)" }

/**
 * Git hosting accounts. Tokens live only in the encrypted [KeyValue] (EncryptedSharedPreferences
 * backed by the Android Keystore in the app); profile data sits next to them. Implements
 * [CredentialResolver] so JGit gets the right token for each remote host.
 */
class AccountStore(private val kv: KeyValue) : CredentialResolver {

    // ---- GitHub
    val github: GitHubAccount?
        get() {
            if (kv.get(GH_TOKEN) == null) return null
            val login = kv.get(GH_LOGIN) ?: return null
            return GitHubAccount(login, kv.get(GH_NAME), kv.get(GH_AVATAR), kv.get(GH_ID)?.toLongOrNull() ?: 0L, kv.get(GH_SCOPES), kv.get(GH_VIA) == "device")
        }

    fun githubToken(): String? = kv.get(GH_TOKEN)

    fun saveGitHub(token: String, account: GitHubAccount) {
        require(looksLikeToken(token)) { "That doesn't look like a GitHub token." }
        kv.put(GH_TOKEN, token.trim())
        kv.put(GH_LOGIN, account.login); kv.put(GH_NAME, account.name); kv.put(GH_AVATAR, account.avatarUrl)
        kv.put(GH_ID, account.id.toString()); kv.put(GH_SCOPES, account.scopes); kv.put(GH_VIA, if (account.viaDeviceFlow) "device" else "pat")
    }

    fun signOutGitHub() { listOf(GH_TOKEN, GH_LOGIN, GH_NAME, GH_AVATAR, GH_ID, GH_SCOPES, GH_VIA, LEGACY_TOKEN).forEach { kv.put(it, null) } }

    // ---- other hosts (GitLab, Gitea, Bitbucket, self-hosted): HTTPS username + token
    fun hosts(): List<HostToken> = kv.get(HOSTS).orEmpty().split(',').filter { it.isNotBlank() }
        .mapNotNull { h -> kv.get(HOST_USER + h)?.let { HostToken(h, it) } }

    /** v0.10: API dialect per host — "" (auto), "gitlab" or "gitea". */
    fun hostKind(host: String): String {
        val h = normalizeHost(host) ?: return ""
        return kv.get(HOST_KIND + h) ?: ""
    }

    fun setHostKind(host: String, kind: String) {
        val h = normalizeHost(host) ?: return
        kv.put(HOST_KIND + h, kind.trim().lowercase().takeIf { it == "gitlab" || it == "gitea" })
    }

    fun saveHost(hostOrUrl: String, username: String, token: String) {
        val h = normalizeHost(hostOrUrl) ?: throw IllegalArgumentException("Invalid host: $hostOrUrl")
        require(h != "github.com") { "Use the GitHub account section for github.com." }
        require(token.isNotBlank()) { "Token is empty." }
        kv.put(HOST_TOKEN + h, token.trim())
        kv.put(HOST_USER + h, username.trim().ifBlank { "oauth2" })
        val set = LinkedHashSet(kv.get(HOSTS).orEmpty().split(',').filter { it.isNotBlank() }); set += h
        kv.put(HOSTS, set.joinToString(","))
    }

    fun removeHost(host: String) {
        val h = normalizeHost(host) ?: return
        kv.put(HOST_TOKEN + h, null); kv.put(HOST_USER + h, null)
        kv.put(HOSTS, kv.get(HOSTS).orEmpty().split(',').filter { it.isNotBlank() && it != h }.joinToString(","))
    }

    override fun forUrl(url: String): GitCredential? {
        val h = normalizeHost(url) ?: return null
        if (h == "github.com") {
            val t = githubToken() ?: return null
            // GitHub accepts any username with a token; x-access-token works for PATs and OAuth tokens.
            return GitCredential("x-access-token", t)
        }
        val t = kv.get(HOST_TOKEN + h) ?: return null
        return GitCredential(kv.get(HOST_USER + h) ?: "oauth2", t)
    }

    /** One-time move of the v0.1/0.2 token ("github_token") into the account store. */
    fun migrateLegacy(legacyUser: String?) {
        val old = kv.get(LEGACY_TOKEN) ?: return
        if (kv.get(GH_TOKEN) == null) {
            kv.put(GH_TOKEN, old)
            kv.put(GH_LOGIN, legacyUser?.takeIf { it.isNotBlank() } ?: "github")
            kv.put(GH_VIA, "pat")
        }
        kv.put(LEGACY_TOKEN, null)
    }

    companion object {
        private const val GH_TOKEN = "acct_github_token"
        private const val GH_LOGIN = "acct_github_login"
        private const val GH_NAME = "acct_github_name"
        private const val GH_AVATAR = "acct_github_avatar"
        private const val GH_ID = "acct_github_id"
        private const val GH_SCOPES = "acct_github_scopes"
        private const val GH_VIA = "acct_github_via"
        private const val HOSTS = "acct_hosts"
        private const val HOST_TOKEN = "acct_host_token:"
        private const val HOST_USER = "acct_host_user:"
        private const val HOST_KIND = "acct_host_kind:"
        const val LEGACY_TOKEN = "github_token"

        /** "https://GitLab.com/x/y.git" → "gitlab.com"; "git.example.com:3000" kept with port. */
        fun normalizeHost(s: String): String? {
            val t = s.trim()
            if (t.isEmpty()) return null
            if (t.startsWith("git@")) return t.substringAfter('@').substringBefore(':').lowercase().ifBlank { null }
            val withScheme = if ("://" in t) t else "https://$t"
            return runCatching {
                val u = URI(withScheme)
                val host = u.host?.lowercase() ?: return null
                if (u.port > 0 && u.port != 443 && u.port != 80) "$host:${u.port}" else host
            }.getOrNull()
        }

        /** Classic (ghp_), fine-grained (github_pat_), OAuth (gho_) or app tokens; loose check, GitHub verifies. */
        fun looksLikeToken(t: String): Boolean {
            val s = t.trim()
            return s.length in 20..255 && s.none { it.isWhitespace() } &&
                (s.startsWith("ghp_") || s.startsWith("github_pat_") || s.startsWith("gho_") || s.startsWith("ghu_") || s.startsWith("ghs_") || s.matches(Regex("[0-9a-f]{40}")))
        }
    }
}
