package com.ctodrs.saqrcode.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.ImagePart
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/** What the composer's "+" menu asks the screen to do. */
enum class ImageAction { IMAGE_TO_CODE, PICK, CAMERA }

@Composable
fun ImageThumb(part: ImagePart, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Saqr.colors
    val bmp = remember(part) { ImageCodec.bitmap(part) }
    val shape = RoundedCornerShape(Radius.sm)
    val desc = stringResource(R.string.image_open)
    Box(
        modifier.size(size).clip(shape).background(c.surface3).border(1.dp, c.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = desc, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, stringResource(R.string.image_label), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else SIcon(Lucide.Image, size = size / 3, tint = c.text3)
    }
}

/** Keeps the image's aspect ratio inside a [maxW]×[maxH] box (never narrower than [minW]). */
fun fitSize(w: Int, h: Int, maxW: Float, maxH: Float, minW: Float = 64f): Pair<Float, Float> {
    if (w <= 0 || h <= 0) return maxH to maxH
    val aspect = w.toFloat() / h
    var fw = maxH * aspect
    var fh = maxH
    if (fw > maxW) { fw = maxW; fh = maxW / aspect }
    return fw.coerceAtLeast(minW) to fh.coerceAtLeast(24f)
}

/** Aspect-correct preview (chat bubble, element sheet). */
@Composable
fun ImagePreview(part: ImagePart, maxW: Dp, maxH: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Saqr.colors
    val bmp = remember(part) { ImageCodec.bitmap(part) }
    val (w, h) = fitSize(bmp?.width ?: 0, bmp?.height ?: 0, maxW.value, maxH.value)
    val shape = RoundedCornerShape(Radius.md)
    val desc = stringResource(R.string.image_open)
    Box(
        modifier.size(w.dp, h.dp).clip(shape).background(c.surface3).border(1.dp, c.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = desc, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, stringResource(R.string.image_label), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else SIcon(Lucide.Image, size = 24.dp, tint = c.text3)
    }
}

/** Thumbnails shown above the text in the user's chat bubble; tap one to see it full screen. */
@Composable
fun UserImages(images: List<ImagePart>) {
    var open by remember { mutableStateOf<ImagePart?>(null) }
    Row(Modifier.fillMaxWidth().padding(bottom = Sp.xs), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
        images.forEach { img ->
            if (images.size == 1) ImagePreview(img, 240.dp, 180.dp, onClick = { open = img })
            else ImageThumb(img, 96.dp, onClick = { open = img })
        }
    }
    open?.let { ImageViewer(it) { open = null } }
}

@Composable
fun ImageViewer(part: ImagePart, onDismiss: () -> Unit) {
    val bmp = remember(part) { ImageCodec.bitmap(part) }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable(onClick = onDismiss)) {
            if (bmp != null) Image(bmp, stringResource(R.string.image_label), Modifier.fillMaxSize().padding(Sp.md), contentScale = ContentScale.Fit)
            Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Sp.md).size(44.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                SIcon(Lucide.X, size = 20.dp, tint = Color.White, contentDescription = stringResource(R.string.close))
            }
        }
    }
}

/** Attachments waiting in the composer, with a remove button each and the "Image → Code" badge. */
@Composable
fun PendingImagesStrip(images: List<ImagePart>, imageToCode: Boolean, onRemove: (Int) -> Unit) {
    val c = Saqr.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = Sp.sm),
        verticalAlignment = Alignment.CenterVertically) {
        if (imageToCode) {
            Row(Modifier.heightIn(min = 30.dp).clip(CircleShape).background(c.accentSoft).border(1.dp, c.accent.copy(alpha = 0.45f), CircleShape)
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Lucide.ScanEye, size = 14.dp, tint = c.accentText)
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.image_to_code), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accentText, maxLines = 1)
            }
            Spacer(Modifier.width(Sp.sm))
        }
        images.forEachIndexed { i, img ->
            Box(Modifier.padding(end = Sp.sm)) {
                ImageThumb(img, 56.dp)
                Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(CircleShape).background(c.bg.copy(alpha = 0.82f))
                    .clickable(onClickLabel = stringResource(R.string.remove_image)) { onRemove(i) }, contentAlignment = Alignment.Center) {
                    SIcon(Lucide.X, size = 12.dp, tint = c.text1, contentDescription = stringResource(R.string.remove_image))
                }
            }
        }
    }
}

/** The selected model has no image input: offer a vision model, sending text only, or cancel. */
@Composable
fun VisionWarningDialog(model: String, suggestion: String?, onSwitch: (String) -> Unit, onTextOnly: () -> Unit, onDismiss: () -> Unit) {
    val c = Saqr.colors
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(Lucide.TriangleAlert, size = 24.dp, tint = c.warning) },
        title = { Text(stringResource(R.string.vision_warn_title), style = Saqr.type.title2) },
        text = {
            Text(stringResource(R.string.vision_warn_body, ltr(model)) + if (suggestion == null) "\n\n" + stringResource(R.string.vision_none_hint) else "",
                style = Saqr.type.body)
        },
        confirmButton = {
            if (suggestion != null) TextButton(onClick = { onSwitch(suggestion) }) {
                Text(stringResource(R.string.vision_switch, ltr(suggestion)), color = c.accentText)
            } else TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.accentText) }
        },
        dismissButton = {
            Row {
                if (suggestion != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) }
                TextButton(onClick = onTextOnly) { Text(stringResource(R.string.vision_text_only), color = c.text2) }
            }
        },
    )
}
