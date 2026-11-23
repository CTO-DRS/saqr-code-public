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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.ondevice.OnDeviceBuild
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch

/**
 * v0.7/v0.8 · لوحة البناء على الجهاز · On-device build pane: prepare the proot build environment
 * (Debian + JDK 17 + Android SDK + arm64 aapt2), build a debug APK of the project, install it,
 * and (v0.8) check the built app's recent crashes from logcat. Downloads are pinned and resumable.
 */
@Composable
fun BuildPane(ws: Workspace) {
    val ctx = LocalContext.current
    val build = remember(ws.name) {
        OnDeviceBuild(ctx.applicationContext, ws.dir).also { it.aapt2Mirror = SaqrApp.instance.settings.aapt2Mirror }
    }
    val phase by build.phase.collectAsState()
    val logs by build.log.collectAsState()
    val line by build.progressLine.collectAsState()
    val apk by build.apk.collectAsState()
    var crashes by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val c = Saqr.colors

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
                    PrimaryButton(stringResource(R.string.build_run), { scope.launch { build.build() } }, icon = Lucide.Hammer)
                OnDeviceBuild.Phase.BUILDING ->
                    GhostButton(stringResource(R.string.build_running), {}, icon = Lucide.Hammer, enabled = false)
                OnDeviceBuild.Phase.BUILT -> {
                    PrimaryButton(stringResource(R.string.build_install), {
                        val i = build.installIntent()
                        runCatching { ctx.startActivity(i) }
                    }, icon = Lucide.Rocket)
                    GhostButton(stringResource(R.string.build_again), { scope.launch { build.build() } }, icon = Lucide.Hammer)
                }
            }
        }

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
