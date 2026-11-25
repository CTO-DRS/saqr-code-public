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
import com.ctodrs.saqrcode.git.CloudBuild
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
    // v0.12.1: field-kit timings + semantic haptics for build outcomes.
    val fieldRecords by build.field.collectAsState()
    val haptics = com.ctodrs.saqrcode.ui.theme.rememberSaqrHaptics()
    androidx.compose.runtime.LaunchedEffect(phase) {
        when (phase) {
            OnDeviceBuild.Phase.BUILT -> haptics.perform(com.ctodrs.saqrcode.ui.theme.HapticKind.CONFIRM)
            OnDeviceBuild.Phase.FAILED -> haptics.perform(com.ctodrs.saqrcode.ui.theme.HapticKind.REJECT)
            else -> Unit
        }
    }
    // v0.12: keep the last build result per project for the live home cards.
    val builds = remember { com.ctodrs.saqrcode.data.BuildHistory(ctx.applicationContext) }
    androidx.compose.runtime.LaunchedEffect(phase, apk) {
        when (phase) {
            OnDeviceBuild.Phase.BUILT -> apk?.let { builds.record(ws.name, true, it.name) }
            OnDeviceBuild.Phase.FAILED -> builds.record(ws.name, false, "")
            else -> Unit
        }
    }
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

        // ---- v0.12.1 field kit: build-step timings for the README field table
        FieldKitCard(fieldRecords)

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

        // ---- v0.14.2 cloud build (BYOC): the build runs on the user's own GitHub repository
        val cloud = remember(ws.name) {
            val git = com.ctodrs.saqrcode.git.GitService(ctx.applicationContext.filesDir, settings.accounts)
            com.ctodrs.saqrcode.git.CloudBuild(
                ctx.applicationContext,
                remoteUrl = {
                    val rs = runCatching { git.remotes(ws.dir) }.getOrDefault(emptyList())
                    (rs.firstOrNull { it.name == "origin" } ?: rs.firstOrNull())?.url
                },
                token = { settings.githubToken },
            )
        }
        val cloudPhase by cloud.phase.collectAsState()
        val cloudMsg by cloud.msg.collectAsState()
        val cloudLog by cloud.log.collectAsState()
        val cloudApk by cloud.apk.collectAsState()
        val cloudRunUrl by cloud.runUrl.collectAsState()
        CloudCard(
            phase = cloudPhase, msg = cloudMsg, log = cloudLog, apk = cloudApk, runUrl = cloudRunUrl,
            onStart = { cloud.start(scope) },
            onStop = { cloud.cancel() },
            onInstall = { runCatching { ctx.startActivity(cloud.installIntent()) } },
            onOpen = {
                runCatching {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cloudRunUrl)))
                }
            },
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

/**
 * v0.14.2 · بطاقة البناء السحابي · Cloud build (BYOC) card: start/stop a build on the user's
 * own GitHub Actions, a live log tail of the running job, and a one-tap install of the APK
 * that comes back as an artifact. Hints name exactly what is missing (remote or token).
 */
@Composable
private fun CloudCard(
    phase: com.ctodrs.saqrcode.git.CloudBuild.Phase,
    msg: com.ctodrs.saqrcode.git.CloudBuild.Msg?,
    log: List<String>,
    apk: java.io.File?,
    runUrl: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = Saqr.colors
    val busy = phase == CloudBuild.Phase.PREPARING || phase == CloudBuild.Phase.RUNNING || phase == CloudBuild.Phase.DOWNLOADING
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg))
            .background(c.surface1).border(1.dp, c.border, RoundedCornerShape(Radius.lg)).padding(Sp.md),
        verticalArrangement = Arrangement.spacedBy(Sp.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.CloudUpload, size = 16.dp, tint = c.accentText)
            Spacer(Modifier.width(Sp.sm))
            Text(stringResource(R.string.build_cloud_title), style = Saqr.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.text1, modifier = Modifier.weight(1f))
            val (tagRes, tagColor) = when (phase) {
                CloudBuild.Phase.PREPARING -> R.string.build_cloud_state_queued to c.warning
                CloudBuild.Phase.RUNNING -> R.string.build_cloud_state_running to c.warning
                CloudBuild.Phase.DOWNLOADING -> R.string.build_cloud_state_downloading to c.warning
                CloudBuild.Phase.READY -> R.string.build_cloud_state_success to c.success
                CloudBuild.Phase.FAILED -> R.string.build_cloud_state_failed to c.danger
                CloudBuild.Phase.IDLE -> return@Row
            }
            com.ctodrs.saqrcode.ui.components.Tag(stringResource(tagRes), color = tagColor, dot = true)
        }
        Text(stringResource(R.string.build_cloud_desc), style = Saqr.type.caption, color = c.text3)
        msg?.let { m ->
            val (res, color) = when (m) {
                com.ctodrs.saqrcode.git.CloudBuild.Msg.NEEDS_REMOTE -> R.string.build_cloud_needs_remote to c.text3
                com.ctodrs.saqrcode.git.CloudBuild.Msg.NEEDS_TOKEN -> R.string.build_cloud_needs_token to c.text3
                com.ctodrs.saqrcode.git.CloudBuild.Msg.WF_INSTALLED -> R.string.build_cloud_wf_installed to c.success
                com.ctodrs.saqrcode.git.CloudBuild.Msg.DISPATCHED -> R.string.build_cloud_dispatched to c.accentText
                com.ctodrs.saqrcode.git.CloudBuild.Msg.TIMEOUT -> R.string.build_cloud_timeout to c.danger
                com.ctodrs.saqrcode.git.CloudBuild.Msg.NO_APK -> R.string.build_cloud_no_apk to c.danger
                com.ctodrs.saqrcode.git.CloudBuild.Msg.FAILED -> R.string.build_cloud_generic_fail to c.danger
                com.ctodrs.saqrcode.git.CloudBuild.Msg.CANCELLED -> R.string.build_cloud_cancelled to c.text2
            }
            Text(
                if (m == com.ctodrs.saqrcode.git.CloudBuild.Msg.TIMEOUT)
                    stringResource(res, com.ctodrs.saqrcode.git.CloudBuildKit.TIMEOUT_MINUTES.toInt())
                else stringResource(res),
                style = Saqr.type.caption, color = color,
            )
        }
        when {
            busy -> Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.build_cloud_working), {}, icon = Lucide.CloudUpload, enabled = false)
                GhostButton(stringResource(R.string.build_cloud_stop), onStop, icon = Lucide.Square)
            }
            phase == CloudBuild.Phase.READY && apk != null -> Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(stringResource(R.string.build_cloud_install), onInstall, icon = Lucide.Rocket)
                GhostButton(stringResource(R.string.build_cloud_again), onStart, icon = Lucide.CloudUpload)
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(stringResource(R.string.build_cloud_start), onStart, icon = Lucide.CloudUpload)
                if (runUrl != null) {
                    GhostButton(stringResource(R.string.build_cloud_open), onOpen, icon = Lucide.ExternalLink)
                }
            }
        }
        if (phase == CloudBuild.Phase.READY && apk != null) {
            Text(
                stringResource(R.string.build_cloud_apk_ready, apk.name, (apk.length() / 1024).toString()),
                style = Saqr.type.caption.copy(textDirection = TextDirection.Ltr), color = c.success,
            )
        }
        if (log.isNotEmpty()) {
            Text(stringResource(R.string.build_cloud_log_hint), style = Saqr.type.caption, color = c.text3)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2).padding(Sp.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                log.takeLast(8).forEach { line ->
                    Text(
                        line, style = Saqr.type.monoMicro.copy(fontFamily = FontFamily.Monospace),
                        color = if (line.startsWith("✗")) c.danger else if (line.startsWith("✓")) c.success else c.text2,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/**
 * v0.12.1 · عدّة الميدان · Field kit card: the recorded build-step timings with one-tap
 * export — a README-ready Markdown table and a shareable CSV — so field numbers on
 * Snapdragon/MediaTek hardware land in the repo straight from the device.
 */
@Composable
private fun FieldKitCard(records: List<com.ctodrs.saqrcode.ondevice.FieldKit.Record>) {
    if (records.isEmpty()) return
    val c = Saqr.colors
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg))
            .background(c.surface1).border(1.dp, c.border, RoundedCornerShape(Radius.lg)).padding(Sp.md),
        verticalArrangement = Arrangement.spacedBy(Sp.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.Crosshair, size = 16.dp, tint = c.accentText)
            Spacer(Modifier.width(Sp.sm))
            Text(
                stringResource(R.string.field_title),
                style = Saqr.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = c.text1, modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.field_records, records.size.toString()), style = Saqr.type.micro, color = c.text3)
        }
        val lastBuild = records.lastOrNull { it.step == "gradle" }
        lastBuild?.let {
            Text(
                stringResource(R.string.field_last, com.ctodrs.saqrcode.ondevice.FieldKit.formatMs(it.ms), it.device),
                style = Saqr.type.caption, color = c.text2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
            GhostButton(stringResource(R.string.field_copy_md), {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText(
                    "saqr-field", com.ctodrs.saqrcode.ondevice.FieldKit.markdown(records)))
                android.widget.Toast.makeText(ctx, R.string.field_copied, android.widget.Toast.LENGTH_SHORT).show()
            }, icon = Lucide.Copy)
            GhostButton(stringResource(R.string.field_share), {
                val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Saqr Code — field numbers")
                    putExtra(android.content.Intent.EXTRA_TEXT, com.ctodrs.saqrcode.ondevice.FieldKit.csv(records))
                }
                runCatching { ctx.startActivity(android.content.Intent.createChooser(i, null)) }
            }, icon = Lucide.Upload)
        }
    }
}
