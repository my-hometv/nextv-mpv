package com.softppj.nextv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Defensive channel-logo decoding. IPTV playlists can reference arbitrary remote
 * artwork; cap compressed transfer bytes BEFORE decoding, then subsample pixels.
 */
internal object BoundedLogoDecoder {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_DIMENSION = 8192
    private const val DISPLAY_DIMENSION = 256

    /** Returns null for an oversized response; never reads more than MAX_BYTES + 1. */
    fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val remaining = MAX_BYTES - total
            val n = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
            if (n < 0) break
            if (n == 0) return null
            if (n > remaining) return null
            output.write(buffer, 0, n)
            total += n
        }
        return output.toByteArray()
    }

    fun decode(input: InputStream): Bitmap? {
        val encoded = readBounded(input) ?: return null
        if (encoded.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        if (bounds.outWidth !in 1..MAX_DIMENSION ||
            bounds.outHeight !in 1..MAX_DIMENSION) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > DISPLAY_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
            ?: return null
        if (bitmap.width <= DISPLAY_DIMENSION && bitmap.height <= DISPLAY_DIMENSION)
            return bitmap
        val scale = minOf(DISPLAY_DIMENSION.toFloat() / bitmap.width,
            DISPLAY_DIMENSION.toFloat() / bitmap.height)
        val resized = Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        if (resized !== bitmap) bitmap.recycle()
        return resized
    }
}
