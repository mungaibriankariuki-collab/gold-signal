package com.goldsignal.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun thumbnail(jpeg: ByteArray, maxSide: Int = 480): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: throw IOException("not an image")
        return compress(bmp, maxSide, 75)
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
