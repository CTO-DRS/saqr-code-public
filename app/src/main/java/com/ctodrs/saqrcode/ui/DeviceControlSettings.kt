package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.device.CommandEngine
import com.ctodrs.saqrcode.device.DeviceAuditLog
import com.ctodrs.saqrcode.device.DeviceDiagnostics
import com.ctodrs.saqrcode.device.ShizukuEngine
import com.ctodrs.saqrcode.device.LocalShellEngine
import com.ctodrs.saqrcode.device.RootEngine
import com.ctodrs.saqrcode.device.ShizukuProbe
import com.ctodrs.saqrcode.device.ShizukuState
import com.ctodrs.saqrcode.ui.components.ListRow
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.RowDivider
import com.ctodrs.saqrcode.ui.components.SecondaryButton
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch

/** v0.1.72 · Device control page: engine status, read-only diagnostics, recent audit entries. */
@Composable
internal fun DeviceControlSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shizuku by remember { mutableStateOf(ShizukuProbe.state(ctx)) }
    var root by remember { mutableStateOf<Boolean?>(null) }
    var lines by remember { mutableStateOf<List<DeviceDiagnostics.Line>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val audit = remember { DeviceAuditLog(java.io.File(ctx.filesDir, "device-audit.jsonl")) }
    var recent by remember { mutableStateOf(audit.entries(8)) }

    Group {
        Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
            Text(stringResource(R.string.device_about), style = Saqr.type.caption, color = Saqr.colors.text2)
        }
    }
    Group {
        Column {
            ListRow(stringResource(R.string.device_engine_local), stringResource(R.string.device_engine_local_d), icon = Lucide.Smartphone)
            RowDivider()
            ListRow(
                stringResource(R.string.device_engine_shizuku),
                stringResource(when (shizuku) {
                    ShizukuState.NOT_INSTALLED -> R.string.device_shizuku_missing
                    ShizukuState.NOT_RUNNING -> R.string.device_shizuku_stopped
                    ShizukuState.NEEDS_PERMISSION -> R.string.device_shizuku_perm
                    ShizukuState.READY -> R.string.device_shizuku_ready
                }),
                icon = Lucide.Plug,
            )
            RowDivider()
            ListRow(
                stringResource(R.string.device_engine_root),
                stringResource(when (root) { null -> R.string.device_root_unknown; true -> R.string.device_root_yes; false -> R.string.device_root_no }),
                icon = Lucide.ShieldCheck,
            )
        }
    }
    Group {
        Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
            if (shizuku == ShizukuState.NEEDS_PERMISSION) PrimaryButton(stringResource(R.string.device_grant), { ShizukuProbe.requestPermission() })
            SecondaryButton(stringResource(R.string.device_recheck), {
                shizuku = ShizukuProbe.state(ctx)
                scope.launch { root = RootEngine().available() }
            })
            PrimaryButton(stringResource(R.string.device_run_diag), {
                if (!busy) scope.launch {
                    busy = true
                    val ce = CommandEngine(listOf(ShizukuEngine(ctx), LocalShellEngine()), audit) { _, _ -> false } // diagnostics are SAFE only: nothing to confirm
                    lines = DeviceDiagnostics(ce).run()
                    recent = audit.entries(8)
                    busy = false
                }
            })
        }
    }
    if (lines.isNotEmpty()) Group {
        Column {
            lines.forEachIndexed { i, l ->
                if (i > 0) RowDivider()
                ListRow(l.label, l.value, icon = if (l.ok) Lucide.CircleCheck else Lucide.Info)
            }
        }
    }
    if (recent.isNotEmpty()) Group {
        Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
            Text(stringResource(R.string.device_audit), style = Saqr.type.body, color = Saqr.colors.text1)
            recent.takeLast(8).forEach {
                Text("${it.optString("risk")} · ${it.optString("engine")} · ${it.optString("cmd").take(60)}", style = Saqr.type.caption, color = Saqr.colors.text3)
            }
        }
    }
}
