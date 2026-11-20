package com.softppj.nextv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.view.Surface

/**
 * Android Binder client for the isolated playback service. Fail closed when the
 * remote process is absent, dies, or has not verified a relocated native engine.
 * All state changes are guarded to avoid resurrecting a disconnected session.
 */
internal class IsolatedVlcServiceClient(context: Context) {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var endpoint: IIsolatedVlcPlayback? = null
    private var bound = false
    private var bindingInProgress = false
    private var closed = false
    private var generation = Long.MIN_VALUE

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            synchronized(lock) {
                if (!closed && (bound || bindingInProgress) && binder != null) {
                    endpoint = IIsolatedVlcPlayback.Stub.asInterface(binder)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) { endpoint = null }
        }

        override fun onBindingDied(name: ComponentName?) {
            synchronized(lock) {
                endpoint = null
                // A dead binding must not be reported as healthy forever.
                // Allow the next explicit bind attempt to re-establish it.
                if (bound) {
                    try { applicationContext.unbindService(this) }
                    catch (_: IllegalArgumentException) { }
                    bound = false
                }
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            synchronized(lock) {
                endpoint = null
                if (bound) {
                    try { applicationContext.unbindService(this) }
                    catch (_: IllegalArgumentException) { }
                    bound = false
                }
            }
        }
    }

    fun bind(): Boolean = synchronized(lock) {
        if (closed) return@synchronized false
        if (bound) return@synchronized true
        bindingInProgress = true
        val success = try {
            applicationContext.bindService(
                Intent(applicationContext, IsolatedVlcPlaybackService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        } catch (_: RuntimeException) {
            false
        }
        bound = success
        bindingInProgress = false
        if (!success) endpoint = null
        success
    }

    /**
     * A dead or null Binder binding can be explicitly re-established after
     * the ServiceConnection has invalidated it. Do not claim readiness while
     * the asynchronous bind is still pending.
     */
    fun isReady(engineId: String): Boolean {
        if (engineId.isBlank()) return false
        val shouldRebind = synchronized(lock) { !closed && !bound && !bindingInProgress }
        if (shouldRebind) bind()
        return synchronized(lock) {
            if (closed) return@synchronized false
            try { endpoint?.isRuntimeReady(engineId) == true }
            catch (_: RemoteException) { endpoint = null; false }
        }
    }

    fun start(engineId: String, url: String, requestGeneration: Long): Boolean = synchronized(lock) {
        if (closed || engineId.isBlank() || url.isBlank() ||
            requestGeneration <= generation) return@synchronized false
        val remote = endpoint ?: return@synchronized false
        try {
            if (!remote.isRuntimeReady(engineId)) return@synchronized false
            // A failed or timed-out Binder start must not let an older
            // asynchronous request acquire playback ownership later.
            // Preserve a high-water mark across Binder failures: an older
            // asynchronous callback must never regain session ownership.
            generation = requestGeneration
            remote.start(engineId, url, requestGeneration)
        } catch (_: RemoteException) {
            endpoint = null
            false
        }
    }

    /** Starts video only when the remote runtime confirms a usable native
     * decoder and a valid Android Surface. Never falls back silently to the
     * legacy no-surface start, which cannot render TV video. */
    fun startWithSurface(
        engineId: String, url: String, requestGeneration: Long, surface: Surface
    ): Boolean = synchronized(lock) {
        if (closed || engineId.isBlank() || url.isBlank() ||
            !surface.isValid || requestGeneration <= generation) return@synchronized false
        val remote = endpoint ?: return@synchronized false
        try {
            if (!remote.isRuntimeReady(engineId)) return@synchronized false
            // Keep the high-water mark even if the remote process dies.
            generation = requestGeneration
            remote.startWithSurface(engineId, url, requestGeneration, surface)
        } catch (_: RemoteException) {
            endpoint = null
            false
        }
    }

    /** Returns 0 for a stale/disconnected session; never reports success on Binder failure. */
    fun playbackState(requestGeneration: Long): Int = synchronized(lock) {
        if (closed || requestGeneration != generation) return@synchronized 0
        try { endpoint?.playbackState(requestGeneration) ?: 0 }
        catch (_: RemoteException) { endpoint = null; 0 }
    }

    /** -1 means the session is no longer observable; never treat it as progress. */
    fun playbackProgressCount(requestGeneration: Long): Long = synchronized(lock) {
        if (closed || requestGeneration != generation) return@synchronized -1L
        try { endpoint?.playbackProgressCount(requestGeneration) ?: -1L }
        catch (_: RemoteException) { endpoint = null; -1L }
    }

    fun playbackPositionMs(requestGeneration: Long): Long = synchronized(lock) {
        if (closed || requestGeneration != generation) return@synchronized -1L
        try { endpoint?.playbackPositionMs(requestGeneration) ?: -1L }
        catch (_: RemoteException) { endpoint = null; -1L }
    }

    fun seek(requestGeneration: Long, positionMs: Long): Boolean = synchronized(lock) {
        if (closed || requestGeneration != generation || positionMs <= 0L)
            return@synchronized false
        try { endpoint?.seek(requestGeneration, positionMs) == true }
        catch (_: RemoteException) { endpoint = null; false }
    }

    fun stop(requestGeneration: Long) = synchronized(lock) {
        if (closed || requestGeneration != generation) return@synchronized
        try { endpoint?.stop(requestGeneration) }
        catch (_: RemoteException) { endpoint = null }
        // Keep the high-water mark: a delayed older request must never
        // resurrect a session after stop.
    }

    fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        // A shared client must not tear down a newer service session after
        // its own Binder endpoint has already disconnected.
        try { endpoint?.release() } catch (_: RemoteException) { }
        endpoint = null
        if (bound) {
            try { applicationContext.unbindService(connection) } catch (_: IllegalArgumentException) { }
            bound = false
        }
        bindingInProgress = false
    }
}
