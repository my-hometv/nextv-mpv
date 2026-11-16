package com.softppj.myview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Small, bounded logo loader for user-provided HTTPS artwork.
 * Does not store provider artwork in source code or persist it to disk.
 */
internal object ChannelLogoLoader {
    private val workers = Executors.newFixedThreadPool(2)
    private val memory = object : LruCache<String, Bitmap>(32) {}

    fun bind(view: ImageView, url: String) {
        view.tag = url
        view.setImageDrawable(null)
        if (!PlaylistParser.validHttpsUrl(url)) return
        synchronized(memory) { memory.get(url) }?.let {
            view.setImageBitmap(it)
            return
        }
        workers.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 4000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "image/png,image/jpeg,image/webp")
                if (conn.responseCode !in 200..299 || conn.contentLength > 262144) return@execute
                val bytes = conn.inputStream.use { input ->
                    val data = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (data.size() + count > 262144) return@execute
                        data.write(buffer, 0, count)
                    }
                    data.toByteArray()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@execute
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                val sample = (longest / 144).coerceAtLeast(1)
                val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@execute
                synchronized(memory) { memory.put(url, picture) }
                view.post {
                    if (view.tag == url) view.setImageBitmap(picture)
                }
            } catch (_: Exception) {
                // Missing artwork must never interrupt playback or focus.
            } finally {
                conn?.disconnect()
            }
        }
    }
}
