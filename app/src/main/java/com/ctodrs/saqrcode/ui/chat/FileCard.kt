package com.ctodrs.saqrcode.ui.chat

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.icons.FileTypeIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import java.io.File

/** Majlis file card: what the agent produced, with Open and Share, right in the conversation. */
@Composable
fun FileCard(file: File, modifier: Modifier = Modifier) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(Radius.md)
    fun uri() = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    fun launch(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Column(modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.border, shape).padding(Sp.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileTypeIcon(file.name, 28.dp)
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = Saqr.type.label, color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Deliverables.human(file.length()), style = Saqr.type.monoMicro, color = c.text3)
            }
        }
        Row(Modifier.padding(top = Sp.sm), horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
            GhostButton(stringResource(R.string.open), {
                launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri(), Deliverables.mime(file)))
            }, icon = Lucide.ExternalLink)
            GhostButton(stringResource(R.string.skills_share), {
                launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(Deliverables.mime(file)).putExtra(Intent.EXTRA_STREAM, uri())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), file.name))
            })
        }
    }
}
