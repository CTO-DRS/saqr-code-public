package com.ctodrs.saqrcode.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import java.io.File

/** Sharing for the developer card: the link as text, or the QR code as a PNG rendered on the device. */
object DeveloperShare {
    /** Renders the QR to a bitmap: black modules on white with a 4-module quiet zone. No network. */
    fun qrBitmap(text: String, modulePx: Int = 16): Bitmap {
        val m = QrCode.encode(text)
        val n = m.size + 8
        val bmp = Bitmap.createBitmap(n * modulePx, n * modulePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        for (y in m.indices) for (x in m.indices) if (m[y][x]) {
            val l = ((x + 4) * modulePx).toFloat(); val t = ((y + 4) * modulePx).toFloat()
            c.drawRect(l, t, l + modulePx, t + modulePx, p)
        }
        return bmp
    }

    /** Ready-to-send invitation with the app's download page. */
    fun shareApp(ctx: Context, message: String) = shareLink(ctx, message)

    fun allAccountsText(header: String, lines: List<Pair<String, String>>): String =
        header + "\n\n" + lines.joinToString("\n") { (n, u) -> "$n: $u" }

    fun shareLink(ctx: Context, url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun shareQr(ctx: Context, url: String, name: String) {
        runCatching {
            val dir = File(ctx.cacheDir, "qr").apply { mkdirs() }
            val f = File(dir, "qr-" + name.filter { it.isLetterOrDigit() }.ifEmpty { "link" } + ".png")
            f.outputStream().use { qrBitmap(url).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val send = Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, url)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri("QR", uri)
            ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
}
