package com.ctodrs.saqrcode.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val onSignOut: () -> Unit,
    val onSaveIdentity: (name: String, email: String) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val loadAvatar: suspend (url: String) -> ImageBitmap? = { null },
)

@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val settings = SaqrApp.instance.settings
    val version by settings.version.collectAsState()
    version
    val ctx = LocalContext.current
    val ui = AccountsUi(
        github = settings.accounts.github,
        gitName = settings.gitName,
        gitEmail = settings.gitEmail,
        verifyGithub = { raw ->
            val token = raw.trim()
            runCatching {
                require(AccountStore.looksLikeToken(token)) { "That doesn't look like a GitHub token (github_pat_… or ghp_…)." }
                val me = withContext(Dispatchers.IO) { GitHubApi(token).me() }
                val acct = GitHubAccount(me.login, me.name, me.avatarUrl, me.id, me.scopes, viaDeviceFlow = false)
                settings.accounts.saveGitHub(token, acct)
                settings.githubUser = me.login
                settings.accountsChanged()
                acct
            }.recoverCatching { throw IllegalStateException(Redact.text(it.message ?: it.toString(), token)) }
        },
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
    )
    AccountsContent(ui, onBack)
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

    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        if (acct != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(acct, ui)
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(acct.displayName, style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.gh_signed_in_as, acct.login), style = Saqr.type.caption, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Tag(stringResource(R.string.connected), color = c.success, dot = true)
            }
            acct.scopes?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.gh_scopes, it), style = Saqr.type.monoSmall, color = c.text3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.gh_profile), { ui.onOpenUrl("https://github.com/${acct.login}") }, icon = Lucide.ExternalLink)
                GhostButton(stringResource(R.string.gh_sign_out), ui.onSignOut, icon = Lucide.LogOut, tint = c.danger)
            }
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
        }
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
