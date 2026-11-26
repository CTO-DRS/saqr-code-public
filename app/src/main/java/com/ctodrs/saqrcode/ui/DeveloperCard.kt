package com.ctodrs.saqrcode.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.Brands
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/** The developer's public links, in display order. Pure data so the list is testable. */
object DeveloperLinks {
    const val NAME = "CTO.DRS"
    const val WEBSITE = "https://cto-drs.com"
    data class Link(val id: String, val url: String, val brand: Long)
    val all = listOf(
        Link("whatsapp", "https://wa.me/message/MVTVWPOPFOVFF1", 0xFF25D366),
        Link("telegram", "https://t.me/CTO_DRS", 0xFF229ED9),
        Link("tiktok", "https://www.tiktok.com/@cto.drs", 0xFF111111),
        Link("instagram", "https://www.instagram.com/cto.drs", 0xFFE1306C),
        Link("x", "https://x.com/cto_drs", 0xFF000000),
        Link("youtube", "https://youtube.com/@cto-drs", 0xFFFF0000),
        Link("github", "https://github.com/CTO-DRS", 0xFF24292F),
        Link("website", WEBSITE, 0xFF4F46E5),
    )
}

private fun iconFor(id: String): ImageVector = when (id) {
    "website" -> Lucide.Globe
    "github" -> Brands.GitHub
    "x" -> Brands.X
    "instagram" -> Brands.Instagram
    "tiktok" -> Brands.TikTok
    "youtube" -> Brands.YouTube
    "telegram" -> Brands.Telegram
    else -> Brands.WhatsApp
}

@Composable
private fun labelFor(id: String): String = stringResource(when (id) {
    "website" -> R.string.dev_l_website
    "github" -> R.string.dev_l_github
    "x" -> R.string.dev_l_x
    "instagram" -> R.string.dev_l_instagram
    "tiktok" -> R.string.dev_l_tiktok
    "youtube" -> R.string.dev_l_youtube
    "telegram" -> R.string.dev_l_telegram
    else -> R.string.dev_l_whatsapp
})

private val Ring = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF06B6D4), Color(0xFFA855F7)))
private val Rim = Brush.linearGradient(listOf(Color(0x5C8E5BD0), Color(0x5C5558C2), Color(0x5C2FA9C0)))

/** "About the developer": glass panel, ringed photo with verified badge, name, role, brand-coloured link grid, footer. */
@Composable
fun DeveloperCard(modifier: Modifier = Modifier, versionName: String = "", onOpen: ((String) -> Unit)? = null) {
    val ctx = LocalContext.current
    val open = onOpen ?: { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
    var viewer by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<DeveloperLinks.Link?>(null) }
    val shape = RoundedCornerShape(24.dp)
    val photoDesc = stringResource(R.string.dev_photo_desc, DeveloperLinks.NAME)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Color(0xF20B0C24)).background(Color(0x0CFFFFFF))
            .background(Brush.verticalGradient(listOf(Color(0x1CFFFFFF), Color.Transparent), endY = 160f))
            .border(1.dp, Rim, shape).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(96.dp)) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Ring).padding(3.dp).clip(CircleShape).background(Color(0xFF161437)).padding(3.dp)) {
                Image(
                    painterResource(R.drawable.dev_avatar), contentDescription = photoDesc, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape).clickable(role = Role.Button) { viewer = true },
                )
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Color(0xFF1D9BF0)).border(2.dp, Color(0xFF161437), CircleShape),
                contentAlignment = Alignment.Center,
            ) { SIcon(Lucide.Check, size = 14.dp, tint = Color.White) }
        }
        Spacer(Modifier.height(12.dp))
        Text(DeveloperLinks.NAME, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr), color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.dev_role), style = TextStyle(fontSize = 13.sp, textDirection = TextDirection.Content, textAlign = TextAlign.Center), color = Color(0xFFA5B4FC))
        Spacer(Modifier.height(16.dp))
        DeveloperLinks.all.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { link ->
                    val label = labelFor(link.id)
                    Column(
                        Modifier.width(68.dp).minimumInteractiveComponentSize().pressable { sheetFor = link }
                            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color(link.brand)).border(1.dp, Color(0x40FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { SIcon(iconFor(link.id), size = 22.dp, tint = Color.White) }
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Center, textDirection = TextDirection.Content), color = Color(0xFFCBD5E1), maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.dev_footer, versionName), style = TextStyle(fontSize = 11.sp, textDirection = TextDirection.Content), color = Color(0xFF64748B))
    }
    sheetFor?.let { l -> DeveloperLinkSheet(l, onDismiss = { sheetFor = null }, onOpen = { open(l.url) }) }
    if (viewer) Dialog(onDismissRequest = { viewer = false }) {
        Image(
            painterResource(R.drawable.dev_avatar), contentDescription = photoDesc, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).clickable { viewer = false },
        )
    }
}

/** Tap on an account: a glass sheet with an on-device QR code, plus Open and Copy link. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DeveloperLinkSheet(link: DeveloperLinks.Link, onDismiss: () -> Unit, onOpen: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF20B0C24), contentColor = Color.White, scrimColor = Color(0x99000000),
    ) {
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        val toastCtx = LocalContext.current
        val copied = stringResource(R.string.dev_qr_copied)
        DeveloperLinkSheetContent(
            link,
            onOpen = { onOpen(); onDismiss() },
            onCopy = { clip.setText(androidx.compose.ui.text.AnnotatedString(link.url)); android.widget.Toast.makeText(toastCtx, copied, android.widget.Toast.LENGTH_SHORT).show() },
        )
    }
}

@Composable
fun DeveloperLinkSheetContent(link: DeveloperLinks.Link, onOpen: () -> Unit, onCopy: () -> Unit) {
    val label = labelFor(link.id)
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(link.brand)).border(1.dp, Color(0x40FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                SIcon(iconFor(link.id), size = 20.dp, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
                Text(stringResource(R.string.dev_qr_scan), style = TextStyle(fontSize = 12.sp), color = Color(0xFFA5B4FC))
            }
        }
        Spacer(Modifier.height(16.dp))
        QrImage(link.url, Modifier.size(208.dp).semantics { contentDescription = "QR: $label" })
        Spacer(Modifier.height(12.dp))
        Text(
            link.url, style = TextStyle(fontSize = 12.sp, textDirection = TextDirection.Ltr, textAlign = TextAlign.Center, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = Color(0xFFCBD5E1),
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetButton(stringResource(R.string.dev_qr_open), Lucide.ExternalLink, true, Modifier.weight(1f), onOpen)
            SheetButton(stringResource(R.string.dev_qr_copy), Lucide.Copy, false, Modifier.weight(1f), onCopy)
        }
    }
}

@Composable
private fun SheetButton(text: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (primary) Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))) else Brush.linearGradient(listOf(Color(0x1AFFFFFF), Color(0x1AFFFFFF)))
    Row(
        modifier.height(52.dp).clip(shape).background(bg).border(1.dp, Color(0x33FFFFFF), shape).pressable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = text },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(icon, size = 18.dp, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}

/** White tile with a 2-module quiet zone; the matrix is drawn dark on white so every scanner reads it. */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QrCode.encode(text) }
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White)) {
        val n = matrix.size + 4
        val cell = size.minDimension / n
        for (y in matrix.indices) for (x in matrix.indices) if (matrix[y][x])
            drawRect(Color(0xFF0B0C24), Offset((x + 2) * cell, (y + 2) * cell), Size(cell + 0.5f, cell + 0.5f))
    }
}
