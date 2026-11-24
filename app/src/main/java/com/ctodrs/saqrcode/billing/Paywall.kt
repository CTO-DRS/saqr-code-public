package com.ctodrs.saqrcode.billing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.BuildConfig
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.payments.LicenseCodec
import com.ctodrs.saqrcode.payments.Moyasar
import com.ctodrs.saqrcode.payments.MoyasarClient
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.GlassIconButton
import com.ctodrs.saqrcode.ui.components.SaqrCard
import com.ctodrs.saqrcode.ui.components.SectionLabel
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SecondaryButton
import com.ctodrs.saqrcode.ui.components.Tag
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * v0.11 · صفحة الخطط · The paywall: plan cards with SAR prices, activation through Google Play
 * (when available) or a Moyasar payment link + license token, plus restore. The free-tier gates
 * open [PaywallDialog]; the merchant tools card (Moyasar key, invoices, token minting) lives in
 * Settings → Billing.
 */
object Paywall {

    /** The license HMAC key from build config (keystore.properties → licenseKey). */
    fun licenseKey(): ByteArray? = LicenseCodec.decodeKey(BuildConfig.SAQR_LICENSE_KEY)

    /** Verifies a pasted token against the embedded key and applies it; returns null on success. */
    fun activate(settings: SettingsStore, token: String, now: Long = System.currentTimeMillis()): String? {
        val key = licenseKey() ?: return "no license key in this build"
        val verified = LicenseCodec.verify(token, key, now).getOrElse { return it.message ?: "invalid token" }
        settings.applyLicense(verified)
        return null
    }
}

/** Small gate dialog opened when a free-tier limit is hit. */
@Composable
fun PaywallDialog(show: Boolean, onDismiss: () -> Unit, onOpenPaywall: () -> Unit, message: String? = null) {
    if (!show) return
    val c = Saqr.colors
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(Lucide.Star, size = 24.dp, tint = c.accentText) },
        title = { Text(stringResource(R.string.paywall_gate_title), style = Saqr.type.title3, color = c.text1) },
        text = { Text(message ?: stringResource(R.string.paywall_gate_body), style = Saqr.type.body, color = c.text2) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onOpenPaywall() }) {
                Text(stringResource(R.string.paywall_gate_cta), color = c.accentText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}

private fun tierNameRes(tier: PlanTier): Int = when (tier) {
    PlanTier.FREE -> R.string.tier_free; PlanTier.PRO -> R.string.tier_pro
    PlanTier.STUDENT -> R.string.tier_student; PlanTier.LIFETIME -> R.string.tier_lifetime
}

private fun planTitleRes(tier: PlanTier): Int = when (tier) {
    PlanTier.PRO -> R.string.plan_pro; PlanTier.STUDENT -> R.string.plan_student
    PlanTier.LIFETIME -> R.string.plan_lifetime; PlanTier.FREE -> R.string.tier_free
}

private fun planDescRes(tier: PlanTier): Int = when (tier) {
    PlanTier.PRO -> R.string.plan_pro_desc; PlanTier.STUDENT -> R.string.plan_student_desc
    PlanTier.LIFETIME -> R.string.plan_lifetime_desc; PlanTier.FREE -> R.string.tier_free_sub
}

private fun fmtDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

@Composable
fun PaywallScreen(onBack: () -> Unit, settings: SettingsStore, play: PlayBilling? = null) {
    val c = Saqr.colors
    val version by settings.version.collectAsState(); version
    val tier = settings.planTier()
    val ent = settings.entitlement()
    val playStatus: String by run {
        val flow = play?.status ?: remember { kotlinx.coroutines.flow.MutableStateFlow("idle") }
        flow.collectAsState()
    }
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    var msg by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf("") }
    // hoisted strings: button callbacks are not composable scopes
    val activatedOk = stringResource(R.string.paywall_activated_ok)
    val activatedErrFmt = stringResource(R.string.paywall_activated_err)
    val restoreDone = stringResource(R.string.paywall_restore_done)

    msg?.let { m ->
        AlertDialog(onDismissRequest = { msg = null }, containerColor = c.surface1,
            title = { Text(stringResource(R.string.paywall_status), style = Saqr.type.title3, color = c.text1) },
            text = { Text(m, style = Saqr.type.body, color = c.text2) },
            confirmButton = { TextButton(onClick = { msg = null }) { Text(stringResource(R.string.ok), color = c.accentText) } })
    }

    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(bottom = Sp.xxxl)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Sp.md, vertical = Sp.md), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Lucide.ArrowLeft, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(Sp.md))
                Text(stringResource(R.string.paywall_title), style = Saqr.type.title2.copy(fontWeight = FontWeight.Bold), color = c.text1)
            }
        }
        // ---- current status
        item {
            SaqrCard(Modifier.padding(horizontal = Sp.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SIcon(Lucide.Star, size = 22.dp, tint = if (tier == PlanTier.FREE) c.text3 else c.accentText)
                    Spacer(Modifier.width(Sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(tierNameRes(tier)), style = Saqr.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                        val sub = when {
                            ent == null -> stringResource(R.string.tier_free_sub)
                            ent.expiresAt == null -> stringResource(R.string.tier_never)
                            else -> stringResource(R.string.tier_until, fmtDate(ent.expiresAt))
                        }
                        Text(sub, style = Saqr.type.caption, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // ---- plan cards
        items(Plans.CATALOG.size, key = { Plans.CATALOG[it].id }) { i ->
            val plan = Plans.CATALOG[i]
            val active = tier == plan.tier
            SaqrCard(Modifier.padding(horizontal = Sp.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SIcon(if (plan.tier == PlanTier.STUDENT) Lucide.Users else if (plan.tier == PlanTier.LIFETIME) Lucide.Rocket else Lucide.Sparkles, size = 16.dp, tint = c.accentText)
                            Spacer(Modifier.width(Sp.xs))
                            Text(stringResource(planTitleRes(plan.tier)), style = Saqr.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(planDescRes(plan.tier)), style = Saqr.type.caption, color = c.text3, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(Sp.md))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${Plans.amountText(plan)} ${stringResource(R.string.sar)}", style = Saqr.type.body.copy(fontWeight = FontWeight.Bold), color = c.accentText)
                        Text(stringResource(if (plan.monthly) R.string.per_month else R.string.one_time), style = Saqr.type.caption, color = c.text3)
                    }
                }
                Spacer(Modifier.height(Sp.md))
                if (active) {
                    Tag(stringResource(R.string.current_plan), color = c.success, dot = true)
                } else {
                    PrimaryButton(stringResource(R.string.paywall_buy_play), {
                        val act = ctx as? android.app.Activity
                        if (act == null) msg = "no activity"
                        else play?.purchase(act, plan) { err -> msg = err }
                    }, enabled = playStatus == "ready")
                    if (playStatus != "ready") {
                        Spacer(Modifier.height(Sp.xs))
                        Text(
                            stringResource(R.string.paywall_play_unavailable),
                            style = Saqr.type.caption, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Sp.md))
        }
        // ---- Moyasar / manual path
        item {
            SectionLabel(stringResource(R.string.paywall_manual), Modifier.padding(top = Sp.lg, bottom = Sp.xs))
            SaqrCard(Modifier.padding(horizontal = Sp.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                    Text(stringResource(R.string.paywall_manual_desc), style = Saqr.type.caption, color = c.text2)
                    if (settings.moyasarPayLink.isNotBlank()) {
                        SecondaryButton(stringResource(R.string.paywall_open_link), {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.moyasarPayLink))) }
                        }, icon = Lucide.ExternalLink)
                    }
                    SaqrField(value = token, onChange = { token = it }, placeholder = stringResource(R.string.paywall_token_hint), mono = true)
                    PrimaryButton(stringResource(R.string.paywall_activate), {
                        val err = Paywall.activate(settings, token)
                        if (err == null) { token = ""; msg = activatedOk }
                        else msg = String.format(activatedErrFmt, err)
                    }, enabled = token.isNotBlank())
                    if (play != null && playStatus == "ready") {
                        GhostButton(stringResource(R.string.paywall_restore), {
                            play.restore(); msg = restoreDone
                        }, icon = Lucide.RefreshCw)
                    }
                    Text(stringResource(R.string.paywall_student_note), style = Saqr.type.caption, color = c.text3)
                }
            }
        }
    }
}

/**
 * v0.11 · أدوات التاجر · Merchant card in Settings → Billing: Moyasar secret key + payment link,
 * invoice creation per plan, status polling, and license-token minting once the invoice is paid.
 * Everything here belongs to the developer; end users never see this card's inputs' values.
 */
@Composable
fun MerchantToolsCard(settings: SettingsStore) {
    val c = Saqr.colors
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var payLink by remember { mutableStateOf(settings.moyasarPayLink) }
    var secret by remember { mutableStateOf(settings.moyasarSecret() ?: "") }
    var email by remember { mutableStateOf("") }
    var invoice by remember { mutableStateOf<Moyasar.Invoice?>(null) }
    var token by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.merchant_saved)
    val tokenReady = stringResource(R.string.merchant_token_ready)

    fun client(): MoyasarClient? {
        val key = settings.moyasarSecret()
        if (key.isNullOrBlank()) { note = "Set the Moyasar secret key first"; return null }
        return MoyasarClient(key)
    }

    SaqrCard(Modifier.padding(horizontal = Sp.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
            Text(stringResource(R.string.merchant_title), style = Saqr.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
            Text(stringResource(R.string.merchant_desc), style = Saqr.type.caption, color = c.text3)
            SaqrField(value = payLink, onChange = { payLink = it }, label = stringResource(R.string.merchant_pay_link))
            SaqrField(value = secret, onChange = { secret = it }, label = stringResource(R.string.merchant_secret),
                mono = true, visual = PasswordVisualTransformation())
            SecondaryButton(stringResource(R.string.merchant_save), {
                settings.moyasarPayLink = payLink
                settings.setMoyasarSecret(secret)
                note = savedMsg
            }, icon = Lucide.Check)
            SaqrField(value = email, onChange = { email = it }, label = stringResource(R.string.merchant_email), placeholder = "name@example.com")
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.md)) {
                Plans.CATALOG.forEach { plan ->
                    GhostButton(stringResource(planTitleRes(plan.tier)), {
                        val cl = client() ?: return@GhostButton
                        busy = true; token = ""
                        scope.launch {
                            val body = Moyasar.createInvoiceBody(
                                plan,
                                description = "Saqr Code ${plan.id}",
                                metadata = mapOf("saqr_plan" to plan.id, "saqr_email" to email),
                            )
                            cl.createInvoice(body)
                                .onSuccess { invoice = it; note = it.url }
                                .onFailure { note = it.message }
                            busy = false
                        }
                    }, enabled = !busy)
                }
            }
            invoice?.let { inv ->
                Column(verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    Text("${inv.id}", style = Saqr.type.monoMicro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.merchant_invoice_status, inv.status.name.lowercase()), style = Saqr.type.caption, color = c.text2)
                    if (inv.url.isNotBlank()) {
                        GhostButton(stringResource(R.string.merchant_open_invoice), {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(inv.url))) }
                        }, icon = Lucide.ExternalLink)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.md)) {
                        GhostButton(stringResource(R.string.merchant_refresh), {
                            val cl = client() ?: return@GhostButton
                            busy = true
                            scope.launch {
                                cl.fetchInvoice(inv.id)
                                    .onSuccess { invoice = it }
                                    .onFailure { note = it.message }
                                busy = false
                            }
                        }, icon = Lucide.RefreshCw, enabled = !busy)
                        if (inv.status == Moyasar.InvoiceStatus.PAID && token.isBlank()) {
                            GhostButton(stringResource(R.string.merchant_mint_token), {
                                val key = Paywall.licenseKey()
                                if (key == null) note = "no license key in this build"
                                else {
                                    val tierStr = when (inv.description.substringAfter("Saqr Code ", "pro")) {
                                        "student_monthly" -> LicenseCodec.TIER_STUDENT
                                        "lifetime" -> LicenseCodec.TIER_LIFETIME
                                        else -> LicenseCodec.TIER_PRO
                                    }
                                    val exp = if (tierStr == LicenseCodec.TIER_LIFETIME) null else System.currentTimeMillis() + Entitlements.MONTH_MS
                                    token = LicenseCodec.sign(tierStr, email.ifBlank { null }, inv.id, exp, System.currentTimeMillis(), key)
                                    note = tokenReady
                                }
                            }, icon = Lucide.KeyRound)
                        }
                    }
                    if (token.isNotBlank()) {
                        Text(token, style = Saqr.type.monoSmall, color = c.text1, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        SecondaryButton(stringResource(R.string.merchant_copy_token), {
                            clipboard.setText(AnnotatedString(token))
                        }, icon = Lucide.Copy)
                    }
                }
            }
            note?.let { Text(it, style = Saqr.type.caption, color = c.text2, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        }
    }
}
