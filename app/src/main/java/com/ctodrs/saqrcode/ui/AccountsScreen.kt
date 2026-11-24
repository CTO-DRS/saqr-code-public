package com.ctodrs.saqrcode.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.data.AccountStore
import com.ctodrs.saqrcode.data.GitHubAccount
import com.ctodrs.saqrcode.git.GitHubApi
import com.ctodrs.saqrcode.git.Redact
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.GlassIconButton
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SectionLabel
import com.ctodrs.saqrcode.ui.components.Segmented
import com.ctodrs.saqrcode.ui.components.Tag
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

const val GITHUB_NEW_TOKEN_URL = "https://github.com/settings/personal-access-tokens/new"

/** What the accounts screen needs; [AccountsScreen] wires it to the real stores. */
class AccountsUi(
    val github: GitHubAccount?,
    val gitName: String,
    val gitEmail: String,
    /** Validates the token with GitHub and links it; returns the account or the error. */
    val verifyGithub: suspend (token: String) -> Result<GitHubAccount>,
    /** v0.9: same but marks the account as linked through the OAuth Device Flow. */
    val verifyDevice: suspend (token: String) -> Result<GitHubAccount> = verifyGithub,
    val onSignOut: () -> Unit,
    val onSaveIdentity: (name: String, email: String) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val loadAvatar: suspend (url: String) -> ImageBitmap? = { null },
    /** v0.9 Device Flow: the user's OAuth App Client ID (public value). */
    val clientId: String = "",
    val onClientId: (String) -> Unit = {},
    /** v0.10 GitLab / Gitea: linked hosts, their API dialect, add / change / remove. */
    val hosts: List<com.ctodrs.saqrcode.data.HostToken> = emptyList(),
    val hostKind: (String) -> String = { "" },
    val onAddHost: (host: String, username: String, token: String) -> String? = { _, _, _ -> null },
    val onHostKind: (host: String, kind: String) -> Unit = { _, _ -> },
    val onRemoveHost: (host: String) -> Unit = {},
)

@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val settings = SaqrApp.instance.settings
    val version by settings.version.collectAsState()
    version
    val ctx = LocalContext.current
    // v0.11: opened when a free-tier user tries to link a second git host
    var hostGate by remember { mutableStateOf(false) }
    val ui = AccountsUi(
        github = settings.accounts.github,
        gitName = settings.gitName,
        gitEmail = settings.gitEmail,
        verifyGithub = { raw -> verifyAccount(settings, raw, viaDeviceFlow = false) },
        verifyDevice = { raw -> verifyAccount(settings, raw, viaDeviceFlow = true) },
        onSignOut = { settings.accounts.signOutGitHub(); settings.accountsChanged() },
        onSaveIdentity = { n, e -> settings.gitName = n; settings.gitEmail = e },
        onOpenUrl = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
        loadAvatar = { url ->
            withContext(Dispatchers.IO) {
                runCatching {
                    GitHubApi.defaultClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        val bytes = r.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        },
        clientId = settings.githubClientId,
        onClientId = { settings.githubClientId = it },
        hosts = settings.accounts.hosts(),
        hostKind = { settings.accounts.hostKind(it) },
        onAddHost = { h, u, t ->
            try {
                // v0.11: free tier links one git host — the rest need Pro.
                val existing = settings.accounts.hosts()
                if (!existing.any { it.host.equals(h.trim(), ignoreCase = true) } &&
                    !settings.limits().unlimitedHosts && existing.size >= settings.limits().maxGitHosts
                ) {
                    hostGate = true
                    "Free plan: 1 git host — upgrade to Pro to link more."
                } else {
                    settings.accounts.saveHost(h, u, t); settings.accountsChanged(); null
                }
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        },
        onHostKind = { h, k -> settings.accounts.setHostKind(h, k); settings.accountsChanged() },
        onRemoveHost = { h -> settings.accounts.removeHost(h); settings.accountsChanged() },
    )
    com.ctodrs.saqrcode.billing.PaywallDialog(
        show = hostGate, onDismiss = { hostGate = false }, onOpenPaywall = onBack,
    )
    AccountsContent(ui, onBack)
}

/** Shared token verification: shape-check → /user → save. [viaDeviceFlow] only changes the badge. */
private suspend fun verifyAccount(settings: com.ctodrs.saqrcode.data.SettingsStore, raw: String, viaDeviceFlow: Boolean): Result<GitHubAccount> {
    val token = raw.trim()
    return runCatching {
        require(AccountStore.looksLikeToken(token)) { "That doesn't look like a GitHub token (github_pat_… or ghp_…)." }
        val me = withContext(Dispatchers.IO) { GitHubApi(token).me() }
        val acct = GitHubAccount(me.login, me.name, me.avatarUrl, me.id, me.scopes, viaDeviceFlow = viaDeviceFlow)
        settings.accounts.saveGitHub(token, acct)
        settings.githubUser = me.login
        settings.accountsChanged()
        acct
    }.recoverCatching { throw IllegalStateException(Redact.text(it.message ?: it.toString(), token)) }
}

@Composable
fun AccountsContent(ui: AccountsUi, onBack: () -> Unit) {
    val c = Saqr.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Sp.md, vertical = Sp.md), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Lucide.ArrowLeft, stringResource(R.string.back), onBack)
            Spacer(Modifier.width(Sp.md))
            Text(stringResource(R.string.accounts), style = Saqr.type.title1, color = c.text1)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sp.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Sp.sm),
        ) {
            SectionLabel(stringResource(R.string.github), Modifier.padding(top = Sp.xs))
            Group { GitHubCard(ui) }

            SectionLabel(stringResource(R.string.other_hosts), Modifier.padding(top = Sp.md))
            Group { OtherHostsCard(ui) }

            SectionLabel(stringResource(R.string.commit_identity), Modifier.padding(top = Sp.md))
            Group { IdentityCard(ui) }

            Row(Modifier.padding(horizontal = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Lucide.ShieldCheck, size = 14.dp, tint = c.success)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.keys_secure_note), style = Saqr.type.caption, color = c.text3)
            }
            Spacer(Modifier.height(Sp.xxl))
        }
    }
}

@Composable
private fun GitHubCard(ui: AccountsUi) {
    val c = Saqr.colors
    val acct = ui.github
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deviceMode by remember { mutableStateOf(false) }

    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        if (acct != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(acct, ui)
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(acct.displayName, style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.gh_signed_in_as, acct.login), style = Saqr.type.caption, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Tag(
                    stringResource(if (acct.viaDeviceFlow) R.string.gh_via_device else R.string.connected),
                    color = c.success, dot = true,
                )
            }
            acct.scopes?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.gh_scopes, it), style = Saqr.type.monoSmall, color = c.text3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.gh_profile), { ui.onOpenUrl("https://github.com/${acct.login}") }, icon = Lucide.ExternalLink)
                GhostButton(stringResource(R.string.gh_sign_out), ui.onSignOut, icon = Lucide.LogOut, tint = c.danger)
            }
        } else if (deviceMode) {
            DeviceFlowCard(ui)
            GhostButton(stringResource(R.string.df_use_token), { deviceMode = false; error = null }, icon = Lucide.KeyRound)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledIcon(Lucide.Github, stringResource(R.string.github_account), Modifier.weight(1f))
                Tag(stringResource(R.string.not_connected), color = c.text3, dot = true)
            }
            // short scope guide
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface2).padding(Sp.md),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.gh_guide_title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                listOf(R.string.gh_guide_1, R.string.gh_guide_2, R.string.gh_guide_3, R.string.gh_guide_4).forEachIndexed { i, res ->
                    Row {
                        Text("${i + 1}.", style = Saqr.type.caption, color = c.accentText, modifier = Modifier.width(18.dp))
                        Text(stringResource(res), style = Saqr.type.caption, color = c.text2)
                    }
                }
            }
            GhostButton(stringResource(R.string.gh_create_token), { ui.onOpenUrl(GITHUB_NEW_TOKEN_URL) }, icon = Lucide.ExternalLink)
            SaqrField(token, { token = it; error = null }, label = stringResource(R.string.github_token), placeholder = "github_pat_…",
                visual = PasswordVisualTransformation(), keyboard = KeyboardOptions(keyboardType = KeyboardType.Password),
                mono = true, isError = error != null, supporting = error)
            PrimaryButton(stringResource(if (busy) R.string.gh_verifying else R.string.gh_verify), {
                if (busy || token.isBlank()) return@PrimaryButton
                busy = true; error = null
                scope.launch {
                    ui.verifyGithub(token).onSuccess { token = "" }.onFailure { error = it.message ?: it.toString() }
                    busy = false
                }
            }, icon = Lucide.KeyRound, enabled = !busy && token.isNotBlank())
            GhostButton(stringResource(R.string.df_use_code), { deviceMode = true; error = null }, icon = Lucide.Smartphone)
        }
    }
}

/**
 * v0.9 · الدخول برمز الجهاز · GitHub OAuth Device Flow (RFC 8628) UI: no token pasting — the
 * user registers an OAuth App once, pastes its public Client ID, gets a short code, opens
 * github.com/login/device in any browser, and the app polls until approved.
 */
@Composable
private fun DeviceFlowCard(ui: AccountsUi) {
    val c = Saqr.colors
    val scope = rememberCoroutineScope()
    var clientId by remember(ui.clientId) { mutableStateOf(ui.clientId) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<com.ctodrs.saqrcode.git.GitHubDeviceFlow.Code?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface2).padding(Sp.md), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        Text(stringResource(R.string.df_title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
        Text(stringResource(R.string.df_desc), style = Saqr.type.caption, color = c.text2)
        SaqrField(clientId, { clientId = it; error = null }, label = stringResource(R.string.df_client_id), placeholder = "Iv1.… / Ov23li…", mono = true,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
        GhostButton(stringResource(R.string.save), { ui.onClientId(clientId) }, icon = Lucide.Save)

        val got = code
        if (got == null) {
            PrimaryButton(stringResource(R.string.df_start), {
                if (busy || clientId.isBlank()) return@PrimaryButton
                ui.onClientId(clientId)
                busy = true; error = null
                job = scope.launch(Dispatchers.IO) {
                    try {
                        val flow = com.ctodrs.saqrcode.git.GitHubDeviceFlow()
                        val got = flow.start(clientId.trim())
                        withContext(kotlinx.coroutines.Dispatchers.Main) { code = got; busy = false }
                        var interval = got.interval.toLong().coerceAtLeast(5)
                        // cancellation lands through delay(), which throws on job?.cancel()
                        while (true) {
                            kotlinx.coroutines.delay(interval * 1_000)
                            when (val p = flow.poll(clientId.trim(), got.deviceCode)) {
                                is com.ctodrs.saqrcode.git.GitHubDeviceFlow.Poll.Token -> {
                                    val r = ui.verifyDevice(p.accessToken)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        r.onFailure { error = it.message ?: it.toString() }
                                        code = null
                                        busy = false
                                    }
                                    break
                                }
                                is com.ctodrs.saqrcode.git.GitHubDeviceFlow.Poll.SlowDown -> interval = p.interval.toLong().coerceAtLeast(5)
                                is com.ctodrs.saqrcode.git.GitHubDeviceFlow.Poll.Failed -> {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) { error = p.error; code = null; busy = false }
                                    break
                                }
                                com.ctodrs.saqrcode.git.GitHubDeviceFlow.Poll.Pending -> { /* keep waiting */ }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(kotlinx.coroutines.Dispatchers.Main) { error = e.message ?: e.toString(); code = null; busy = false }
                    }
                }
            }, icon = Lucide.Smartphone, enabled = !busy && clientId.isNotBlank())
        } else {
            Text(stringResource(R.string.df_code_hint), style = Saqr.type.caption, color = c.text2)
            Text(
                got.userCode,
                style = Saqr.type.mono.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                color = c.accentText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(stringResource(R.string.df_open_browser), { ui.onOpenUrl(got.verificationUri) }, icon = Lucide.ExternalLink)
                GhostButton(stringResource(R.string.cancel), {
                    job?.cancel(); code = null; busy = false
                }, icon = Lucide.X)
            }
            if (busy) Text(stringResource(R.string.df_waiting), style = Saqr.type.caption, color = c.text3)
        }
        error?.let { Text(it, style = Saqr.type.caption, color = c.danger) }
    }
}

@Composable
private fun Avatar(acct: GitHubAccount, ui: AccountsUi) {
    val c = Saqr.colors
    var img by remember(acct.avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(acct.avatarUrl) { acct.avatarUrl?.let { img = ui.loadAvatar(it) } }
    Box(Modifier.size(44.dp).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
        val bmp = img
        if (bmp != null) Image(bmp, acct.login, Modifier.size(44.dp), contentScale = ContentScale.Crop)
        else Text(acct.login.take(1).uppercase(), style = Saqr.type.title3, color = c.accentText)
    }
}

@Composable
private fun IdentityCard(ui: AccountsUi) {
    val c = Saqr.colors
    var name by remember(ui.gitName) { mutableStateOf(ui.gitName) }
    var email by remember(ui.gitEmail) { mutableStateOf(ui.gitEmail) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        Text(stringResource(R.string.commit_identity_desc), style = Saqr.type.caption, color = c.text2)
        SaqrField(name, { name = it; saved = false }, label = stringResource(R.string.git_author_name), placeholder = "Your Name")
        SaqrField(email, { email = it; saved = false }, label = stringResource(R.string.git_author_email),
            placeholder = ui.github?.noreplyEmail ?: "you@example.com", mono = true,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(stringResource(R.string.save), { ui.onSaveIdentity(name, email); saved = true }, icon = Lucide.Save)
            if (saved) {
                Spacer(Modifier.width(Sp.md)); SIcon(Lucide.CircleCheck, size = 16.dp, tint = c.success)
                Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.saved), style = Saqr.type.caption, color = c.success)
            }
        }
    }
}

/**
 * v0.10 · GitLab / Gitea hosts · Linked HTTPS hosts with a token (GitLab.com, Codeberg,
 * self-hosted Gitea…). Each host gets an API-dialect pick — Auto (hostname heuristics),
 * GitLab or Gitea — used by Publish and the MR/PR flow; tokens live encrypted.
 */
@Composable
private fun OtherHostsCard(ui: AccountsUi) {
    val c = Saqr.colors
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        Text(stringResource(R.string.other_hosts_desc), style = Saqr.type.caption, color = c.text2)
        ui.hosts.forEach { h ->
            val kind = ui.hostKind(h.host)
            val auto = com.ctodrs.saqrcode.git.Forge.detect(h.host, kind)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface2).padding(Sp.md),
                verticalArrangement = Arrangement.spacedBy(Sp.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SIcon(Lucide.Globe, size = 14.dp, tint = c.accentText)
                    Spacer(Modifier.width(Sp.sm))
                    Text(h.host, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, modifier = Modifier.weight(1f))
                    auto?.let { Tag(it.name.lowercase(), color = c.success, dot = false) }
                    Text(h.username, style = Saqr.type.monoSmall, color = c.text3)
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { ui.onRemoveHost(h.host) }, contentAlignment = Alignment.Center) {
                        SIcon(Lucide.Trash2, size = 15.dp, tint = c.danger)
                    }
                }
                Text(stringResource(R.string.other_hosts_kind), style = Saqr.type.caption, color = c.text3)
                Segmented(
                    listOf(stringResource(R.string.other_hosts_auto), "GitLab", "Gitea"),
                    when (kind) { "gitlab" -> 1; "gitea" -> 2; else -> 0 },
                    { i -> ui.onHostKind(h.host, when (i) { 1 -> "gitlab"; 2 -> "gitea"; else -> "" }) },
                )
            }
        }
        SaqrField(host, { host = it; error = null }, label = stringResource(R.string.other_hosts_host),
            placeholder = "gitlab.com / codeberg.org / git.example.com", mono = true)
        SaqrField(user, { user = it; error = null }, label = stringResource(R.string.other_hosts_user),
            placeholder = "username (GitLab: oauth2 is fine)", mono = true)
        SaqrField(token, { token = it; error = null }, label = stringResource(R.string.other_hosts_token),
            placeholder = "glpat-… / token…", visual = PasswordVisualTransformation(),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Password), mono = true,
            isError = error != null, supporting = error)
        PrimaryButton(stringResource(R.string.other_hosts_add), {
            val err = ui.onAddHost(host.trim(), user.trim(), token.trim())
            if (err == null) {
                host = ""; user = ""; token = ""; error = null
            } else error = err
        }, icon = Lucide.Plug, enabled = host.isNotBlank() && token.isNotBlank())
        Text(stringResource(R.string.other_hosts_scope_note), style = Saqr.type.caption, color = c.text3)
    }
}
