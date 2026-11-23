package com.ctodrs.saqrcode.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ctodrs.saqrcode.agent.ImageMath
import com.ctodrs.saqrcode.agent.ImagePart
import java.io.ByteArrayOutputStream

/** Decode / downscale / encode attachments (max 1568 px long side, JPEG 85). */
object ImageCodec {
    fun encode(src: Bitmap): ImagePart {
        val (w, h) = ImageMath.targetSize(src.width, src.height)
        val scaled = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        // JPEG has no alpha: flatten transparent areas onto white instead of black
        val flat = if (scaled.hasAlpha()) Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            val c = android.graphics.Canvas(it); c.drawColor(android.graphics.Color.WHITE); c.drawBitmap(scaled, 0f, 0f, null)
        } else scaled
        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, ImageMath.JPEG_QUALITY, out)
        return ImagePart(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/jpeg")
    }

    /** Reads a picked/captured image (EXIF rotation applied on API 28+), downscaled while decoding. */
    fun load(ctx: Context, uri: Uri): ImagePart? = runCatching {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                val (w, h) = ImageMath.targetSize(info.size.width, info.size.height)
                d.setTargetSize(w, h)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts = BitmapFactory.Options().apply { inSampleSize = ImageMath.inSampleSize(bounds.outWidth, bounds.outHeight) }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        }
        encode(bmp)
    }.getOrNull()

    private val cache = LruCache<Int, ImageBitmap>(24)

    /** Decoded bitmap for display (cached by content). */
    fun bitmap(part: ImagePart): ImageBitmap? {
        val key = part.base64.hashCode() * 31 + part.base64.length
        cache.get(key)?.let { return it }
        return runCatching {
            val bytes = Base64.decode(part.base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()?.also { cache.put(key, it) }
    }
}
