package com.softppj.nextv

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Standalone GStreamer playbin adapter. This class is deliberately not routed
 * from MainActivity until official SDK packaging, bus events and device tests pass.
 */
internal class GstreamerPlaybackView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var handle: Long = 0
    private var closed = false

    init { holder.addCallback(this) }

    fun initialize() {
        check(!closed && handle == 0L)
        System.loadLibrary("gstreamer_android")
        System.loadLibrary("nextv_gst_player")
        handle = nativeCreate()
        check(handle != 0L) { "GStreamer playbin unavailable" }
        if (holder.surface.isValid) nativeSetSurface(handle, holder.surface)
    }

    fun play(url: String) {
        check(!closed && handle != 0L)
        require(url.startsWith("http://") || url.startsWith("https://") ||
            url.startsWith("rtsp://") || url.startsWith("file://")) {
            "Unsupported stream scheme"
        }
        nativePlay(handle, url)
    }

    /** Non-blocking native bus snapshot; call from a lifecycle-aware polling loop. */
    fun pollStatus(): Int = if (handle != 0L && !closed) nativePollStatus(handle) else 3
    fun lastError(): String = if (handle != 0L && !closed) nativeLastError(handle) else "GStreamer player released"

    fun pause() { if (handle != 0L && !closed) nativePause(handle, true) }
    fun resume() { if (handle != 0L && !closed) nativePause(handle, false) }
    fun seekTo(ms: Long) { if (handle != 0L && !closed) nativeSeek(handle, ms.coerceAtLeast(0)) }
    fun setVolume(volume: Int) { if (handle != 0L && !closed) nativeVolume(handle, volume.coerceIn(0, 200)) }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (handle != 0L && !closed) nativeSetSurface(handle, holder.surface)
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (handle != 0L && !closed) nativeSetSurface(handle, null)
    }
    fun release() {
        if (closed) return
        closed = true
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
    private external fun nativeSeek(handle: Long, ms: Long)
    private external fun nativeVolume(handle: Long, volume: Int)
    private external fun nativeRelease(handle: Long)
    private external fun nativePollStatus(handle: Long): Int
    private external fun nativeLastError(handle: Long): String
}
