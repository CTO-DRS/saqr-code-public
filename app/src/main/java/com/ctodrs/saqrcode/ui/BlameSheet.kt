package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.git.BlameGroup
import com.ctodrs.saqrcode.git.BlameKit
import com.ctodrs.saqrcode.git.GitService
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wave E: who last changed each part of the open file, grouped by commit. Read-only; changes nothing. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BlameSheet(dir: File, path: String, onDismiss: () -> Unit) {
    val c = Saqr.colors
    val app = SaqrApp.instance
    var groups by remember { mutableStateOf<List<BlameGroup>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(dir, path) {
        val r = withContext(Dispatchers.IO) {
            runCatching { BlameKit.group(GitService(app.filesDir, app.settings.accounts).blame(dir, path)) }
        }
        groups = r.getOrNull(); failed = r.isFailure
    }
    val arabic = androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)?.language == "ar"
    val now = System.currentTimeMillis() / 1000
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface1,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Sp.lg).padding(bottom = Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.sm),
        ) {
            androidx.compose.material3.Text(stringResource(R.string.blame_title, path), style = Saqr.type.title2, color = c.text1)
            when {
                failed -> androidx.compose.material3.Text(stringResource(R.string.blame_none), style = Saqr.type.body, color = c.text2)
                groups == null -> androidx.compose.material3.Text(stringResource(R.string.blame_loading), style = Saqr.type.body, color = c.text3)
                else -> Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.orEmpty().forEach {
                        androidx.compose.material3.Text(BlameKit.summary(it, now, arabic), style = Saqr.type.monoSmall, color = c.text1)
                    }
                }
            }
        }
    }
}
