package com.goldsignal.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

object Images {
    /** Reads an image and shrinks it to a JPEG whose longest side is at most [maxSide]. */
    fun loadJpeg(ctx: Context, uri: Uri, maxSide: Int = 1600): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(ctx, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not an image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = open(ctx, uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("not an image")
        return compress(bmp, maxSide, 85)
    }

    /** Decodes a JPEG at roughly [maxSide] pixels to keep memory low. */
    fun decodeSmall(jpeg: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** One thumbnail with all the screenshots side by side. */
    fun strip(jpegs: List<ByteArray>, height: Int = 240): ByteArray {
        val bmps = jpegs.mapNotNull { decodeSmall(it, height * 2) }
        if (bmps.isEmpty()) throw IOException("not an image")
        val widths = bmps.map { (it.width * height.toFloat() / it.height).roundToInt().coerceAtLeast(1) }
        val gap = 6
        val out = Bitmap.createBitmap(widths.sum() + gap * (bmps.size - 1), height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        var x = 0
        bmps.forEachIndexed { i, bmp ->
            canvas.drawBitmap(bmp, null, Rect(x, 0, x + widths[i], height), null)
            x += widths[i] + gap
        }
        return compress(out, 2400, 75)
    }

    private fun open(ctx: Context, uri: Uri) =
        ctx.contentResolver.openInputStream(uri) ?: throw IOException("can't read $uri")

    private fun compress(src: Bitmap, maxSide: Int, quality: Int): ByteArray {
        val scale = maxSide.toFloat() / max(src.width, src.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt(), true)
        } else {
            src
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }
}
