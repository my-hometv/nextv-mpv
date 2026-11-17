package com.softppj.nextv

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.Surface
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Actual native playback for the LibVLC version packaged in the current
 * Gradle flavor. This is NOT three-version coexistence in one APK.
 * Native instances are owned exclusively by this service process.
 */
class IsolatedVlcPlaybackService : Service() {
    private val gate = IsolatedVlcRequestGate()
    private val lock = Any()
    // Native LibVLC events may be emitted from a decoder thread while a
    // Binder stop holds lock. Never wait for that lock inside the callback:
    // native stop can itself wait for the decoder callback to return.
    private val eventHandler = Handler(Looper.getMainLooper())
    private var vlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var activeGeneration: Long? = null
    private var released = false
    private val playbackTracker = NativeVlcPlaybackTracker()

    private fun packagedEngineId(): String = "LibVLC-" + BuildConfig.LIBVLC_VERSION

    /** A version label is not sufficient evidence of an installed native
     * backend. Each APK currently contains only its flavor's LibVLC runtime. */
    private fun matchesPackagedRuntime(engineId: String?): Boolean =
        engineId == packagedEngineId() && gate.acceptsEngine(engineId)

    private fun ready(engineId: String?): Boolean {
        if (released || !matchesPackagedRuntime(engineId)) return false
        if (vlc != null) return true
        // The probe is diagnostic, not a prerequisite: some upstream
        // versions expose libvlc through a different loader/SONAME.
        // The real readiness check is successful Java LibVLC construction.
        return try {
            vlc = LibVLC(applicationContext, arrayListOf("--no-video-title-show"))
            true
        } catch (_: Exception) {
            vlc = null
            false
        } catch (_: LinkageError) {
            vlc = null
            false
        }
    }

    private fun stopNative() {
        val old = player
        player = null
        activeGeneration = null
        playbackTracker.reset()
        if (old != null) {
            try { old.stop() } catch (_: Exception) { }
            try { old.vlcVout.detachViews() } catch (_: Exception) { }
            try { old.release() } catch (_: Exception) { }
        }
    }

    private val endpoint = object : IIsolatedVlcPlayback.Stub() {
        override fun isRuntimeReady(engineId: String?): Boolean = synchronized(lock) {
            ready(engineId)
        }

        override fun start(engineId: String?, url: String?, requestGeneration: Long): Boolean = false

        override fun startWithSurface(
            engineId: String?, url: String?, requestGeneration: Long, surface: Surface?
        ): Boolean = synchronized(lock) {
            if (surface == null || !surface.isValid || url.isNullOrBlank() ||
                !ready(engineId)) return@synchronized false
            if (!gate.reserveStart(engineId, url, requestGeneration)) return@synchronized false
            stopNative()
            val nativeVlc = vlc ?: return@synchronized false
            var next: MediaPlayer? = null
            try {
                next = MediaPlayer(nativeVlc)
                val candidate = next
                candidate.setEventListener { event ->
                    val eventType = event.type
                    // Queue state updates instead of acquiring our Binder/native
                    // teardown monitor from LibVLC's callback thread.
                    eventHandler.post {
                        synchronized(lock) {
                            if (player !== candidate || activeGeneration != requestGeneration) return@synchronized
                            when (eventType) {
                            MediaPlayer.Event.Playing -> playbackTracker.playing()
                            MediaPlayer.Event.TimeChanged ->
                                playbackTracker.timeChanged(candidate.time)
                            MediaPlayer.Event.PositionChanged ->
                                playbackTracker.positionChanged(candidate.position)
                            MediaPlayer.Event.EncounteredError -> playbackTracker.error()
                            MediaPlayer.Event.EndReached -> playbackTracker.ended()
                            MediaPlayer.Event.Stopped -> playbackTracker.ended()
                            }
                        }
                    }
                }
                // The Surface belongs to the Activity; native output must be
                // detached before SurfaceHolder destruction.
                // Establish callback ownership before attaching native output.
                // Some LibVLC builds dispatch events synchronously while the
                // Surface/media is configured; early events must not be lost.
                player = next
                activeGeneration = requestGeneration
                playbackTracker.start()
                next.vlcVout.setVideoSurface(surface, null)
                next.vlcVout.attachViews()
                val media = Media(nativeVlc, Uri.parse(url))
                try { next.media = media } finally { media.release() }
                next.play()
                // Accepted by native player, not proof that stream decoded.
                true
            } catch (_: Exception) {
                try { next?.stop() } catch (_: Exception) { }
                try { next?.vlcVout?.detachViews() } catch (_: Exception) { }
                try { next?.release() } catch (_: Exception) { }
                player = null
                activeGeneration = null
                playbackTracker.reset()
                false
            } catch (_: LinkageError) {
                try { next?.stop() } catch (_: Exception) { }
                try { next?.vlcVout?.detachViews() } catch (_: Exception) { }
                try { next?.release() } catch (_: Exception) { }
                player = null
                activeGeneration = null
                playbackTracker.reset()
                false
            }
        }

        override fun playbackState(requestGeneration: Long): Int = synchronized(lock) {
            if (released || activeGeneration != requestGeneration) 0 else playbackTracker.state
        }

        override fun playbackProgressCount(requestGeneration: Long): Long = synchronized(lock) {
            if (released || activeGeneration != requestGeneration) -1L else playbackTracker.progressCount
        }

        override fun stop(requestGeneration: Long) = synchronized(lock) {
            if (activeGeneration == requestGeneration && gate.stop(requestGeneration)) {
                stopNative()
            }
        }

        override fun release() = synchronized(lock) {
            if (released) return@synchronized
            released = true
            gate.release()
            stopNative()
            try { vlc?.release() } catch (_: Exception) { }
            vlc = null
        }
    }

    override fun onBind(intent: Intent?): IBinder = endpoint

    override fun onDestroy() {
        endpoint.release()
        super.onDestroy()
    }
}

/**
 * Pure state machine, deliberately separate from Android Binder and JNI.
 * Engine IDs are validated against the stable, known version set.
 */
internal class IsolatedVlcRequestGate {
    private var generation = Long.MIN_VALUE
    private var activeGeneration: Long? = null
    private var released = false
    private val knownEngines = setOf("LibVLC-3.5.1", "LibVLC-3.7.7", "LibVLC-4.0.0-eap29")

    @Synchronized
    fun acceptsEngine(engineId: String?): Boolean =
        !released && engineId != null && engineId in knownEngines

    @Synchronized
    fun reserveStart(engineId: String?, url: String?, requestGeneration: Long): Boolean {
        if (!acceptsEngine(engineId) || url.isNullOrBlank() ||
            requestGeneration <= generation) return false
        generation = requestGeneration
        activeGeneration = requestGeneration
        return true
    }

    @Synchronized
    fun stop(requestGeneration: Long): Boolean {
        if (released || requestGeneration != generation || activeGeneration != requestGeneration) return false
        activeGeneration = null
        return true
    }

    @Synchronized
    fun release() {
        released = true
        activeGeneration = null
    }
}
