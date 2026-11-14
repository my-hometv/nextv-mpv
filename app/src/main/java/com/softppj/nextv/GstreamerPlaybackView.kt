package com.softppj.nextv

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Experimental GStreamer playbin adapter. MainActivity can opt into this route,
 * but native SDK packaging and device playback have not yet been verified.
 */
internal class GstreamerPlaybackView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var handle: Long = 0
    private var closed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var polling = false
    private var playbackGeneration = 0L
    private var lastReportedStatus: Int? = null
    var listener: ((status: Int, error: String?) -> Unit)? = null
    /** Callbacks run on the main thread and may synchronously switch channels or release. */
    /** Called once per stream when a video buffer first reaches the native sink. */
    var onFirstVideoBuffer: ((elapsedMs: Long) -> Unit)? = null
    private var startedAtMs = 0L
    private var firstBufferReported = false
    private var slowStartupReported = false
    private var volumePercent = 100
    private var nativeFailure: String? = null
    /** Diagnostic threshold only; does not stop or restart the stream. */
    var slowStartupThresholdMs: Long = 10_000L
    var onSlowStartup: ((elapsedMs: Long) -> Unit)? = null

    private val statusPoll = object : Runnable {
        override fun run() {
            if (!polling || closed || handle == 0L) return
            val generationAtPoll = playbackGeneration
            val status = try {
                pollStatus()
            } catch (failure: Throwable) {
                // Never crash the Android TV UI thread because an experimental
                // JNI polling call failed; report failure to the MPV recovery route.
                nativeFailure = failure.message ?: failure.javaClass.simpleName
                3
            }
            // Native polling or its future implementation must not let an obsolete
            // stream update callbacks after a channel switch or release.
            if (generationAtPoll != playbackGeneration || !polling || closed || handle == 0L) return
            if (status == 5 && !firstBufferReported) {
                firstBufferReported = true
                onFirstVideoBuffer?.invoke((SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L))
                if (generationAtPoll != playbackGeneration || closed) return
            }
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
            if (!firstBufferReported && !slowStartupReported && elapsed >= slowStartupThresholdMs.coerceAtLeast(1L) && status != 2 && status != 3) {
                slowStartupReported = true
                onSlowStartup?.invoke(elapsed)
                if (generationAtPoll != playbackGeneration || closed) return
            }
            if (status != lastReportedStatus) {
                lastReportedStatus = status
                listener?.invoke(status, if (status == 3) nativeFailure ?: runCatching { lastError() }.getOrDefault("Native GStreamer error") else null)
                if (generationAtPoll != playbackGeneration || closed) return
            }
            if (status != 2 && status != 3 && polling) mainHandler.postDelayed(this, 250L)
            else polling = false
        }
    }

    private fun startStatusPolling() {
        mainHandler.removeCallbacks(statusPoll)
        playbackGeneration++
        lastReportedStatus = null
        nativeFailure = null
        firstBufferReported = false
        slowStartupReported = false
        startedAtMs = SystemClock.elapsedRealtime()
        polling = true
        mainHandler.post(statusPoll)
    }

    private fun stopStatusPolling() {
        playbackGeneration++
        polling = false
        mainHandler.removeCallbacks(statusPoll)
    }

    init { holder.addCallback(this) }

    fun initialize() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Initialize GStreamer on the main thread" }
        check(!closed && handle == 0L)
        System.loadLibrary("gstreamer_android")
        System.loadLibrary("nextv_gst_player")
        handle = nativeCreate()
        check(handle != 0L) { "GStreamer playbin unavailable" }
        nativeVolume(handle, volumePercent)
        if (holder.surface.isValid) nativeSetSurface(handle, holder.surface)
    }

    fun play(url: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Start playback on the main thread" }
        check(!closed && handle != 0L)
        require(url.startsWith("http://") || url.startsWith("https://") ||
            url.startsWith("rtsp://") || url.startsWith("file://")) {
            "Unsupported stream scheme"
        }
        // Schedule polling before nativePlay: even a synchronous startup failure
        // must be observed and delivered to MainActivity's MPV fallback.
        startStatusPolling()
        try {
            nativePlay(handle, url)
        } catch (failure: Throwable) {
            stopStatusPolling()
            throw failure
        }
    }

    /** Stop playback without releasing the Surface or native player. */
    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Stop GStreamer on the main thread" }
        stopStatusPolling()
        if (!closed && handle != 0L) nativeStop(handle)
        lastReportedStatus = null
        firstBufferReported = false
        slowStartupReported = false
    }

    /** Native states: 0 waiting, 1 buffering, 2 EOS, 3 error, 4 PLAYING, 5 video buffer at sink. */
    /** State 5 is not confirmation that the frame appeared on the display. */
    fun pollStatus(): Int = if (handle != 0L && !closed) nativePollStatus(handle) else 3
    fun lastError(): String = if (handle != 0L && !closed) nativeLastError(handle) else "GStreamer player released"

    fun pause() { if (handle != 0L && !closed) nativePause(handle, true) }
    fun resume() { if (handle != 0L && !closed) nativePause(handle, false) }
    fun seekTo(ms: Long) { if (handle != 0L && !closed) nativeSeek(handle, ms.coerceAtLeast(0)) }
    fun setVolume(volume: Int) {
        volumePercent = volume.coerceIn(0, 200)
        if (handle != 0L && !closed) nativeVolume(handle, volumePercent)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (handle != 0L && !closed) nativeSetSurface(handle, holder.surface)
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (handle != 0L && !closed) nativeSetSurface(handle, null)
    }
    fun release() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Release GStreamer on the main thread" }
        if (closed) return
        closed = true
        stopStatusPolling()
        listener = null
        onFirstVideoBuffer = null
        onSlowStartup = null
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0
        }
        holder.removeCallback(this)
    }

    private external fun nativeCreate(): Long
    private external fun nativeSetSurface(handle: Long, surface: android.view.Surface?)
    private external fun nativePlay(handle: Long, url: String)
    private external fun nativePause(handle: Long, paused: Boolean)
    private external fun nativeStop(handle: Long)
    private external fun nativeSeek(handle: Long, ms: Long)
    private external fun nativeVolume(handle: Long, volume: Int)
    private external fun nativeRelease(handle: Long)
    private external fun nativePollStatus(handle: Long): Int
    private external fun nativeLastError(handle: Long): String
}
