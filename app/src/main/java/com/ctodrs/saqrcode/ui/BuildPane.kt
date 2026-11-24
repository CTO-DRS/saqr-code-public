package com.ctodrs.saqrcode.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.ondevice.BuildCache
import com.ctodrs.saqrcode.ondevice.OnDeviceBuild
import com.ctodrs.saqrcode.ondevice.SigningConfig
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SaqrSwitch
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch

/**
 * v0.7–v0.9 · لوحة البناء على الجهاز · On-device build pane: prepare the proot build environment
 * (Debian + JDK 17 + Android SDK + arm64 aapt2), build a debug APK or — since v0.9 — a *signed*
 * release APK from a keystore inside the project, install it, check the built app's crashes from
 * logcat, and reclaim build-cache storage (v0.9).
 */
@Composable
fun BuildPane(ws: Workspace, onPaywall: () -> Unit = {}) {
    val ctx = LocalContext.current
    val settings = SaqrApp.instance.settings
    val build = remember(ws.name) {
        OnDeviceBuild(ctx.applicationContext, ws.dir).also {
            it.aapt2Mirror = settings.aapt2Mirror
            it.keepLastApk = settings.keepLastApk
        }
    }
    val phase by build.phase.collectAsState()
    val logs by build.log.collectAsState()
    val line by build.progressLine.collectAsState()
    val apk by build.apk.collectAsState()
    var crashes by remember { mutableStateOf<List<String>?>(null) }
    var verify by remember { mutableStateOf<com.ctodrs.saqrcode.ondevice.ApkVerify.Result?>(null) }
    var storageTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val c = Saqr.colors

    // v0.9 signed release state — passwords stay in memory only
    var signOn by remember { mutableStateOf(false) }
    // v0.11: signed release builds are a paid-tier feature
    var releaseGate by remember { mutableStateOf(false) }
    com.ctodrs.saqrcode.billing.PaywallDialog(
        show = releaseGate, onDismiss = { releaseGate = false }, onOpenPaywall = onPaywall,
    )
    var ksPath by remember { mutableStateOf(settings.releaseKeystorePath) }
    var ksAlias by remember { mutableStateOf(settings.releaseKeyAlias) }
    var ksPass by remember { mutableStateOf("") }
    var ksKeyPass by remember { mutableStateOf("") }
    val ksExists = remember(ws.name, ksPath) { java.io.File(ws.dir, ksPath.trim()).isFile }

    fun signing(): SigningConfig? = if (signOn) SigningConfig(ksPath, ksPass, ksAlias, ksKeyPass) else null

    Column(Modifier.fillMaxSize().padding(horizontal = Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        Row(Modifier.padding(top = Sp.md), verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.Package, size = 18.dp, tint = c.accentText)
            Spacer(Modifier.width(Sp.sm))
            Text(stringResource(R.string.build_title), style = Saqr.type.title3, color = c.text1)
            Spacer(Modifier.weight(1f))
            BuildPhaseTag(phase)
        }
        Text(stringResource(R.string.build_desc), style = Saqr.type.caption, color = c.text2)

        Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
            when (phase) {
                OnDeviceBuild.Phase.NOT_SETUP, OnDeviceBuild.Phase.FAILED ->
                    PrimaryButton(stringResource(R.string.build_prepare), { scope.launch { build.bootstrap() } }, icon = Lucide.CloudDownload)
                OnDeviceBuild.Phase.BOOTSTRAPPING ->
                    GhostButton(stringResource(R.string.build_preparing), {}, icon = Lucide.CloudDownload, enabled = false)
                OnDeviceBuild.Phase.READY ->
                    PrimaryButton(stringResource(R.string.build_run), { scope.launch { build.build(signing()) } }, icon = Lucide.Hammer)
                OnDeviceBuild.Phase.BUILDING ->
                    GhostButton(stringResource(R.string.build_running), {}, icon = Lucide.Hammer, enabled = false)
                OnDeviceBuild.Phase.BUILT -> {
                    PrimaryButton(stringResource(R.string.build_install), {
                        val i = build.installIntent()
                        runCatching { ctx.startActivity(i) }
                    }, icon = Lucide.Rocket)
                    GhostButton(stringResource(R.string.build_again), { scope.launch { build.build(signing()) } }, icon = Lucide.Hammer)
                }
            }
        }

        // ---- v0.9 signed release
        ReleaseCard(
            visible = phase == OnDeviceBuild.Phase.READY || phase == OnDeviceBuild.Phase.BUILT,
            signOn = signOn, onSignOn = {
                // v0.11: gating — free users get the paywall instead of the release fields
                if (it && !settings.limits().signedRelease) releaseGate = true else signOn = it
            },
            ksPath = ksPath, onKsPath = { ksPath = it },
            ksAlias = ksAlias, onKsAlias = { ksAlias = it },
            ksPass = ksPass, onKsPass = { ksPass = it },
            ksKeyPass = ksKeyPass, onKsKeyPass = { ksKeyPass = it },
            ksExists = ksExists,
        )

        if (phase == OnDeviceBuild.Phase.BUILT && apk != null) {
            Text(
                stringResource(R.string.build_apk_ready, apk!!.name, (apk!!.length() / 1024).toString()),
                style = Saqr.type.caption.copy(textDirection = TextDirection.Ltr), color = c.success,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.build_crash_check), {
                    scope.launch { crashes = build.crashReport() }
                }, icon = Lucide.Bug)
                crashes?.let { list ->
                    if (list.isEmpty()) {
                        Text(stringResource(R.string.build_crash_none), style = Saqr.type.caption, color = c.success)
                    } else {
                        Text(stringResource(R.string.build_crash_found, list.size.toString()), style = Saqr.type.caption, color = c.danger)
                    }
                }
            }
            // ---- v0.10: verify the built APK's signature in-app before Install
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.build_verify), {
                    verify = com.ctodrs.saqrcode.ondevice.ApkVerify.verify(apk!!)
                }, icon = Lucide.ShieldCheck)
                verify?.let {
                    if (it.ok) {
                        Text(stringResource(R.string.build_verify_ok, it.scheme ?: "v2"), style = Saqr.type.caption, color = c.success)
                    } else {
                        Text(stringResource(R.string.build_verify_bad), style = Saqr.type.caption, color = c.danger)
                    }
                }
            }
            verify?.let {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg))
                        .background(c.surface1).border(1.dp, c.border, RoundedCornerShape(Radius.lg)).padding(Sp.md),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (it.ok) {
                        Text(stringResource(R.string.build_verify_signer), style = Saqr.type.label, color = c.text1)
                        it.fingerprintLine?.let { fp ->
                            Text(fp, style = Saqr.type.monoMicro, color = c.accentText, maxLines = 2)
                        }
                        it.subject?.let { s -> Text(s, style = Saqr.type.caption, color = c.text2, maxLines = 2) }
                        it.validUntil?.let { d ->
                            Text(stringResource(R.string.build_verify_valid, d), style = Saqr.type.caption, color = c.text2)
                        }
                    } else {
                        Text(it.error ?: "", style = Saqr.type.caption, color = c.danger)
                        Text(stringResource(R.string.build_verify_hint), style = Saqr.type.caption, color = c.text3)
                    }
                }
            }
            crashes?.takeIf { it.isNotEmpty() }?.let { list ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg))
                        .background(c.surface1).border(1.dp, c.border, RoundedCornerShape(Radius.lg)).padding(Sp.md),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    list.forEach { l ->
                        Text(l, style = Saqr.type.monoMicro, color = c.danger)
                    }
                }
            }
        }

        // ---- v0.9 storage card
        if (phase != OnDeviceBuild.Phase.BOOTSTRAPPING && phase != OnDeviceBuild.Phase.BUILDING) {
            StorageCard(components = remember(storageTick) { build.cacheComponents() }, onReclaim = { entry ->
                build.reclaim(entry)
                storageTick++
            })
        }

        Text(
            when (phase) {
                OnDeviceBuild.Phase.BOOTSTRAPPING, OnDeviceBuild.Phase.BUILDING -> line
                else -> ""
            },
            style = Saqr.type.monoMicro, color = c.accentText, maxLines = 1,
        )

        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(Radius.lg))
                .background(c.surface1).border(1.dp, c.border, RoundedCornerShape(Radius.lg)),
        ) {
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.build_log_empty), style = Saqr.type.caption, color = c.text3)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(Sp.md)) {
                    items(logs.size) { i ->
                        Text(
                            logs[i], style = Saqr.type.monoMicro.copy(fontFamily = FontFamily.Monospace),
                            color = if (logs[i].startsWith("✗")) c.danger else if (logs[i].startsWith("✓")) c.success else c.text2,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Sp.sm))
    }

    // keep the log tail in view
    androidx.compose.runtime.LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }
}

/** v0.9: keystore fields for a signed release build. Passwords are never persisted. */
@Composable
private fun ReleaseCard(
    visible: Boolean,
    signOn: Boolean, onSignOn: (Boolean) -> Unit,
    ksPath: String, onKsPath: (String) -> Unit,
    ksAlias: String, onKsAlias: (String) -> Unit,
    ksPass: String, onKsPass: (String) -> Unit,
    ksKeyPass: String, onKsKeyPass: (String) -> Unit,
    ksExists: Boolean,
) {
    if (!visible) return
    val c = Saqr.colors
    val settings = SaqrApp.instance.settings
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg))
            .background(c.surface1).border(1.dp, c.border, RoundedCornerShape(Radius.lg)).padding(Sp.md),
        verticalArrangement = Arrangement.spacedBy(Sp.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.KeyRound, size = 16.dp, tint = c.accentText)
            Spacer(Modifier.width(Sp.sm))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.build_release_title), style = Saqr.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.text1)
                Text(
                    stringResource(if (ksExists) R.string.build_release_found else R.string.build_release_missing, ksPath.trim()),
                    style = Saqr.type.caption, color = if (ksExists) c.success else c.text3, maxLines = 1,
                )
            }
            SaqrSwitch(signOn, {
                onSignOn(it)
                settings.releaseKeystorePath = ksPath
                settings.releaseKeyAlias = ksAlias
            })
        }
        if (signOn) {
            Text(stringResource(R.string.build_release_desc), style = Saqr.type.caption, color = c.text3)
            SaqrField(ksPath, onKsPath, label = stringResource(R.string.build_release_keystore), placeholder = "keystore/release.jks", mono = true)
            SaqrField(ksAlias, onKsAlias, label = stringResource(R.string.build_release_alias), placeholder = "saqr", mono = true)
            SaqrField(ksPass, onKsPass, label = stringResource(R.string.build_release_pass),
                visual = PasswordVisualTransformation(), keyboard = KeyboardOptions(keyboardType = KeyboardType.Password), mono = true)
            SaqrField(ksKeyPass, onKsKeyPass, label = stringResource(R.string.build_release_key_pass),
                visual = PasswordVisualTransformation(), keyboard = KeyboardOptions(keyboardType = KeyboardType.Password), mono = true)
        }
    }
}

/** v0.9: what the build environment occupies, with one-tap reclaim per component. */
@Composable
private fun StorageCard(components: List<com.ctodrs.saqrcode.ondevice.BuildCache.Entry>, onReclaim: (com.ctodrs.saqrcode.ondevice.BuildCache.Entry) -> Unit) {
    val c = Saqr.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg))
            .background(c.surface1).border(1.dp, c.border, RoundedCornerShape(Radius.lg)).padding(Sp.md),
        verticalArrangement = Arrangement.spacedBy(Sp.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.HardDrive, size = 16.dp, tint = c.accentText)
            Spacer(Modifier.width(Sp.sm))
            Text(
                stringResource(R.string.build_storage_title, BuildCache.formatBytes(BuildCache.total(components))),
                style = Saqr.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = c.text1, modifier = Modifier.weight(1f),
            )
        }
        components.forEach { e ->
            Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.label, style = Saqr.type.caption, color = c.text1, maxLines = 1)
                }
                Text(BuildCache.formatBytes(e.sizeBytes), style = Saqr.type.monoSmall, color = c.text2)
                Spacer(Modifier.width(Sp.sm))
                if (e.sizeBytes > 0) {
                    GhostButton(stringResource(R.string.build_storage_reclaim), { onReclaim(e) }, icon = Lucide.Trash2)
                }
            }
        }
    }
}

@Composable
private fun BuildPhaseTag(phase: OnDeviceBuild.Phase) {
    val c = Saqr.colors
    val (res, color) = when (phase) {
        OnDeviceBuild.Phase.NOT_SETUP -> R.string.build_phase_setup to c.text3
        OnDeviceBuild.Phase.BOOTSTRAPPING -> R.string.build_phase_setup_run to c.warning
        OnDeviceBuild.Phase.READY -> R.string.build_phase_ready to c.success
        OnDeviceBuild.Phase.BUILDING -> R.string.build_phase_building to c.warning
        OnDeviceBuild.Phase.BUILT -> R.string.build_phase_built to c.success
        OnDeviceBuild.Phase.FAILED -> R.string.build_phase_failed to c.danger
    }
    com.ctodrs.saqrcode.ui.components.Tag(stringResource(res), color = color, dot = true)
}
