package com.softppj.nextv

import java.io.Closeable
import java.net.HttpURLConnection
import kotlin.concurrent.thread

/**
 * Cancels one EPG refresh without blocking the Android remote/UI thread.
 * A cancellation flag stops parsing and prevents a cancelled generation from
 * publishing a guide. Closing the active socket/stream on a worker also
 * unblocks a pending network read instead of waiting for its timeout.
 */
internal class EpgDownloadCancellation {
    @Volatile private var cancelled = false
    private val lock = Any()
    private var connection: HttpURLConnection? = null
    private var stream: Closeable? = null

    fun isCancelled(): Boolean = cancelled

    fun attachConnection(value: HttpURLConnection): Boolean {
        val accepted = synchronized(lock) {
            if (cancelled) false else {
                connection = value
                true
            }
        }
        if (!accepted) closeAsync(value, null)
        return accepted
    }

    fun attachStream(value: Closeable): Boolean {
        val accepted = synchronized(lock) {
            if (cancelled) false else {
                stream = value
                true
            }
        }
        if (!accepted) closeAsync(null, value)
        return accepted
    }

    fun releaseConnection(value: HttpURLConnection) {
        synchronized(lock) {
            if (connection === value) {
                connection = null
                stream = null
            }
        }
    }

    fun cancel() {
        val resources = synchronized(lock) {
            cancelled = true
            val current = Pair(connection, stream)
            connection = null
            stream = null
            current
        }
        closeAsync(resources.first, resources.second)
    }

    private fun closeAsync(connection: HttpURLConnection?, stream: Closeable?) {
        if (connection == null && stream == null) return
        thread(name = "MyView-epg-cancel", isDaemon = true) {
            try { connection?.disconnect() } catch (_: Exception) {}
            try { stream?.close() } catch (_: Exception) {}
        }
    }
}
