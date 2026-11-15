package com.softppj.nextv

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib

/**
 * Single-session libmpv Android surface. The native AAR owns codec selection,
 * FFmpeg MPEG-TS demuxing, colorspace conversion and rendering.
 * No Media3/LibVLC video player is used by this class.
 */
class MpvPlaybackView(context: Context) : SurfaceView(context), SurfaceHolder.Callback,
    MPVLib.EventObserver {
    interface Listener {
        fun onFirstPlayback()
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var mpv: MPVLib? = null
    private var pendingUrl: String? = null
    private var selectedDecoder = "Automatic"
    private var selectedOutputMode = "Auto"
    private var started = false
    private var liveStream = false
    private var failureReported = false
    @Volatile private var closed = false
    private var attached = false
    @Volatile private var generation = 0
    var listener: Listener? = null

    init {
        // Request a defined 8-bit RGBA Android surface instead of relying on
        // vendor-specific default pixel formats for the GPU compositor.
        holder.setFormat(PixelFormat.RGBA_8888)
    }

    /**
     * SurfaceView.display can be null before the view is attached. Resolve the
     * Android default display explicitly for first-tune HDR capability checks.
     * This checks advertised capabilities, not whether libmpv negotiated HDR.
     */
    private fun hdr10DisplaySupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val activeDisplay = display ?: runCatching {
            (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull()
        return runCatching {
            activeDisplay?.hdrCapabilities?.supportedHdrTypes?.contains(
                Display.HdrCapabilities.HDR_TYPE_HDR10) == true
        }.getOrDefault(false)
    }

    fun initialize(decoder: String = "Automatic", outputMode: String = "Auto", silentPreview: Boolean = false) {
        check(mpv == null && !closed)
        selectedDecoder = decoder
        selectedOutputMode = outputMode
        val engine = MPVLib.create(context.applicationContext)
            ?: throw IllegalStateException("Cannot initialize libmpv")
        try {
            engine.setOptionString("config", "no")
            engine.setOptionString("vo", "gpu")
            engine.setOptionString("gpu-context", "android")
            engine.setOptionString("opengl-es", "yes")
            // A preview must never take over or interrupt foreground audio.
            if (silentPreview) {
                engine.setOptionString("audio", "no")
                engine.setOptionString("ao", "null")
            } else {
                engine.setOptionString("ao", "audiotrack,opensles")
            }
            engine.setOptionString("hwdec", MpvPlaybackPolicy.hardwareDecoder(decoder))
            engine.setOptionString("video-sync", "audio")
            // A requested HDR target must never be forced on an SDR-only TV.
            // Preserve automatic source/output negotiation unless the display
            // actually advertises HDR10. This is not a claim that the Android
            // SurfaceView pipeline itself can switch into an HDR buffer mode.
            val hdr10Supported = hdr10DisplaySupported()
            selectedOutputMode = MpvPlaybackPolicy.effectiveOutputMode(outputMode, hdr10Supported)
            when (selectedOutputMode) {
                "Force HDR10" -> {
                    engine.setOptionString("target-prim", "bt.2020")
                    engine.setOptionString("target-trc", "pq")
                }
                "Force SDR" -> {
                    engine.setOptionString("target-prim", "bt.709")
                    engine.setOptionString("target-trc", "bt.1886")
                }
            }
            // Use source metadata for matrix, range and transfer. Never force BT.709
            // or full-range on SD broadcasts; that causes washed-out colours.
            // Preserve source colour metadata; avoid forcing a fixed output matrix.
            // Avoid changing video output backends during Android surface transitions.
            engine.setOptionString("vd-lavc-dr", "no")
            engine.setOptionString("interpolation", "no")
            // Preserve source colour metadata and avoid forced RGB levels or
            // BT.709 conversion. Apply GPU debanding only to reduce visible
            // 8-bit colour banding; do not alter brightness or saturation.
            engine.setOptionString("deband", "yes")
            engine.setOptionString("dither-depth", "auto")
            engine.setOptionString("keep-open", "no")
            engine.setOptionString("force-window", "no")
            engine.setOptionString("idle", "yes")
            engine.setOptionString("cache", "yes")
            engine.setOptionString("demuxer-max-bytes", "32MiB")
            engine.setOptionString("cache-secs", "3")
            engine.setOptionString("network-timeout", "8")
            engine.init()
            engine.addObserver(this)
            engine.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            engine.observeProperty("video-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            engine.observeProperty("estimated-vf-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            engine.observeProperty("frame-drop-count", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            engine.observeProperty("decoder-frame-drop-count", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            engine.observeProperty("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            mpv = engine
            holder.addCallback(this)
            if (holder.surface?.isValid == true) surfaceCreated(holder)
        } catch (e: Exception) {
            try { engine.destroy() } catch (_: Exception) {}
            throw e
        }
    }

    fun play(url: String, headers: Map<String, String> = emptyMap(), isLive: Boolean = false) {
        if (closed) return
        generation++
        pendingUrl = url
        liveStream = isLive
        started = false
        failureReported = false
        pendingHeaders = headers
        if (attached) launchPending()
    }

    private var pendingHeaders: Map<String, String> = emptyMap()
    private fun launchPending() {
        val url = pendingUrl ?: return
        val engine = mpv ?: return
        if (!attached) return
        pendingUrl = null
        val headers = pendingHeaders
        // Do not include URLs or headers in logs: IPTV links may contain credentials.
        val httpHeaders = headers.entries
            .filter { (k, v) -> k.matches(Regex("[A-Za-z0-9-]+")) &&
                !v.contains("\r") && !v.contains("\n") }
            .joinToString(",") { "${it.key}: ${it.value}" }
        if (httpHeaders.isNotBlank()) engine.setPropertyString("http-header-fields", httpHeaders)
        else engine.setPropertyString("http-header-fields", "")
        // Native FFmpeg handles MPEG-TS packet timing, PAT/PMT, PCR/PTS and A/V sync.
        // Hint the demuxer only for explicit .ts resources; HLS manifests must
        // retain automatic format detection, even when their segments are MPEG-TS.
        val explicitTs = MpvPlaybackPolicy.isExplicitTransportStream(url)
        if (explicitTs) {
            engine.command(arrayOf("loadfile", url, "replace", "demuxer-lavf-format=mpegts"))
        } else {
            engine.command(arrayOf("loadfile", url, "replace"))
        }
    }

    val hasStarted: Boolean
        get() = started && !closed

    val isPlaying: Boolean
        get() = if (closed) false else mpv?.getPropertyBoolean("pause") == false

    val durationMs: Long
        get() = if (closed) 0L else ((mpv?.getPropertyDouble("duration") ?: 0.0) * 1000).toLong()

    val positionMs: Long
        get() = if (closed) 0L else ((mpv?.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong()

    fun pause() { if (!closed) mpv?.setPropertyBoolean("pause", true) }
    fun resume() { if (!closed) mpv?.setPropertyBoolean("pause", false) }
    fun togglePause() { if (isPlaying) pause() else resume() }
    fun seekTo(ms: Long) {
        if (!closed) mpv?.command(arrayOf("seek", (ms / 1000.0).toString(), "absolute"))
    }

    data class StreamTrack(val id: Int, val label: String, val selected: Boolean)

    fun tracks(type: String): List<StreamTrack> {
        val engine = mpv ?: return emptyList()
        if (closed) return emptyList()
        val count = (engine.getPropertyInt("track-list/count") ?: 0).coerceIn(0, 100)
        return buildList {
            for (i in 0 until count) {
                if (engine.getPropertyString("track-list/$i/type") != type) continue
                val id = engine.getPropertyInt("track-list/$i/id") ?: continue
                val title = engine.getPropertyString("track-list/$i/title").orEmpty()
                val lang = engine.getPropertyString("track-list/$i/lang").orEmpty()
                val selected = engine.getPropertyBoolean("track-list/$i/selected") == true
                add(StreamTrack(id, listOf(lang, title).filter { it.isNotBlank() }
                    .joinToString(" · ").ifBlank { "$type #$id" }, selected))
            }
        }
    }

    // Some streams expose multiple independently selectable video tracks.
    // A single adaptive HLS/DASH track cannot be bitrate-limited by selecting
    // a track; in that case leave the broadcaster's original quality intact.
    fun selectVideoQuality(maxHeight: Int?) {
        val engine = mpv ?: return
        if (closed) return
        val count = (engine.getPropertyInt("track-list/count") ?: 0).coerceIn(0, 100)
        val variants = buildList {
            for (i in 0 until count) {
                if (engine.getPropertyString("track-list/$i/type") != "video") continue
                val id = engine.getPropertyInt("track-list/$i/id") ?: continue
                val height = engine.getPropertyInt("track-list/$i/demux-h")
                    ?: engine.getPropertyInt("track-list/$i/h")
                    ?: continue
                if (height > 0) add(id to height)
            }
        }
        if (variants.size < 2) return
        val eligible = maxHeight?.let { limit -> variants.filter { it.second <= limit } }
            ?.takeIf { it.isNotEmpty() } ?: variants
        val chosen = eligible.maxByOrNull { it.second } ?: return
        engine.setPropertyInt("vid", chosen.first)
    }

    fun choosePreferredAudioLanguage(language: String) {
        if (closed) return
        mpv?.setPropertyString("alang", language)
        mpv?.setPropertyString("aid", "auto")
    }

    fun chooseDefaultAudioTrack() {
        if (!closed) mpv?.setPropertyString("aid", "auto")
    }

    fun chooseDefaultSubtitleTrack() {
        if (!closed) mpv?.setPropertyString("sid", "auto")
    }

    fun chooseAudioTrack(id: Int) {
        if (!closed) mpv?.setPropertyInt("aid", id)
    }

    fun chooseSubtitleTrack(id: Int?) {
        if (closed) return
        if (id == null) mpv?.setPropertyString("sid", "no")
        else mpv?.setPropertyInt("sid", id)
    }

    // mpv's ten-band equalizer uses FFmpeg's audio equalizer filter.
    // Rebuild only on a user preset change; reset to the untouched stream
    // for Standard so no processing is applied by default.
    fun setAudioPreset(preset: String) {
        val engine = mpv ?: return
        if (closed) return
        val gains = when (preset) {
            "Cinema" -> listOf(2.5, 1.2, 0.0, 1.6, 1.2)
            "Music" -> listOf(1.8, 0.8, 0.0, 1.0, 1.6)
            "Dialogue" -> listOf(-1.6, -0.8, 1.6, 2.8, 1.2)
            "Bass Boost" -> listOf(3.5, 2.4, 0.0, -0.5, -0.8)
            "Night Mode" -> listOf(-1.5, -0.8, 1.0, 1.4, -1.0)
            else -> null
        }
        if (gains == null) {
            engine.command(arrayOf("af", "clr"))
            return
        }
        val frequencies = listOf(100, 400, 1200, 3500, 9000)
        val filters = frequencies.zip(gains).map { (frequency, gain) ->
            "equalizer=f=$frequency:t=h:w=1:g=$gain"
        }
        engine.command(arrayOf("af", "set", filters.joinToString(",")))
    }

    fun setVolume(level: Int) {
        if (!closed) mpv?.setPropertyDouble("volume", level.coerceIn(0, 200).toDouble())
    }

    fun setPictureScale(mode: String) {
        val engine = mpv ?: return
        if (closed) return
        val zoom = when (mode) {
            "Zoom 110%" -> 1.10
            "Zoom 125%" -> 1.25
            "Zoom 150%" -> 1.50
            else -> 1.0
        }
        engine.setPropertyDouble("video-zoom", kotlin.math.ln(zoom) / kotlin.math.ln(2.0))
        engine.setPropertyString("video-aspect-override", when (mode) {
            "16:9" -> "16:9"
            "4:3" -> "4:3"
            "21:9" -> "21:9"
            "1:1" -> "1:1"
            else -> "no"
        })
        engine.setPropertyBoolean("video-unscaled", mode == "Original")
    }

    fun setPicture(brightness: Float, contrast: Float, saturation: Float) {
        val engine = mpv ?: return
        if (closed) return
        // mpv video equalizer values are -100..100; zero is unmodified source.
        engine.setPropertyInt("brightness", ((brightness - 1f) * 100f).toInt().coerceIn(-50, 50))
        engine.setPropertyInt("contrast", ((contrast - 1f) * 100f).toInt().coerceIn(-50, 50))
        engine.setPropertyInt("saturation", ((saturation - 1f) * 100f).toInt().coerceIn(-50, 50))
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (closed || attached || !holder.surface.isValid) return
        val engine = mpv ?: return
        engine.attachSurface(holder.surface)
        attached = true
        // Keep the configured GPU output stable; changing vo here tears down
        // the renderer and can cause a black frame on Android TV.
        if (pendingUrl != null) launchPending()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!closed) mpv?.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!attached) return
        attached = false
        val engine = mpv ?: return
        try {
            // Detach only the Android surface; do not replace the active VO.
            engine.detachSurface()
        } catch (e: Exception) {
            Log.w("NexTV-mpv", "Surface detach failed", e)
        }
    }

    override fun event(eventId: Int) {
        val request = generation
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> main.post {
                if (!closed && request == generation && !started) {
                    started = true
                    listener?.onFirstPlayback()
                }
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> main.post {
                if (!closed && request == generation && !failureReported && (!started || liveStream)) {
                    failureReported = true
                    listener?.onError(if (started) "MPV live stream ended" else "MPV stream ended before playing")
                }
            }
        }
    }

    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: Long) {
        if (!closed && (property == "frame-drop-count" || property == "decoder-frame-drop-count") && value > 0L && value % 30L == 0L)
            Log.w("NexTV-mpv", "$property=$value")
    }
    override fun eventProperty(property: String, value: Double) {
        if (!closed && property == "estimated-vf-fps" && value > 0.0)
            Log.d("NexTV-mpv", "estimated-video-fps=$value")
    }
    override fun eventProperty(property: String, value: Boolean) {}
    override fun eventProperty(property: String, value: String) {
        if (!closed && property == "hwdec-current") Log.i("NexTV-mpv", "decoder=$value")
    }

    /** Read-only, credential-free diagnostics for TV troubleshooting. */
    fun diagnosticSnapshot(): String {
        val engine=mpv ?: return "MPV is not initialized"
        if(closed) return "MPV is closed"
        fun str(name:String)=runCatching { engine.getPropertyString(name) }.getOrNull().orEmpty().ifBlank { "unknown" }
        fun num(name:String)=runCatching { engine.getPropertyDouble(name) }.getOrNull()?.toString() ?: "unknown"
        fun count(name:String)=runCatching { engine.getPropertyInt(name) }.getOrNull()?.toString() ?: "unknown"
        // Report display HDR support independently of stream metadata. A playlist
        // name containing HDR10 does not establish the actual transfer function.
        val displayHdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                val currentDisplay=display ?: (context.getSystemService(Context.DISPLAY_SERVICE)
                    as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
                val types = (currentDisplay?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf())
                if (types.isEmpty()) "none reported" else types.joinToString(",") { type ->
                    when (type) {
                        android.view.Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                        android.view.Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                        android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                        else -> "type-$type"
                    }
                }
            }.getOrDefault("unknown")
        } else "unavailable"
        val hdr10Supported=displayHdr.split(",").any { it.trim()=="HDR10" }
        val sourceTransfer=str("video-params/gamma")
        val sourcePrimaries=str("video-params/primaries")
        return listOf(
            "Source dynamic range: "+MpvPlaybackPolicy.sourceDynamicRange(sourceTransfer,sourcePrimaries),
            "Output interpretation: "+MpvPlaybackPolicy.outputInterpretation(selectedOutputMode,hdr10Supported),
            "Display HDR capabilities: "+displayHdr,
            "MPV version: "+str("mpv-version"),
            "FFmpeg version: "+str("ffmpeg-version"),
            "Selected mode: "+selectedDecoder,
            "Requested output: "+selectedOutputMode,
            "Decoder: "+str("hwdec-current"),
            "Video codec: "+str("video-codec"),
            "Video format: "+str("video-params/pixelformat"),
            "Source matrix: "+str("video-params/colormatrix"),
            "Source levels: "+str("video-params/colorlevels"),
            "Source primaries: "+str("video-params/primaries"),
            "Source transfer: "+str("video-params/gamma"),
            "Source signal peak: "+num("video-params/sig-peak"),
            "Output format: "+str("video-out-params/pixelformat"),
            "Output levels: "+str("video-out-params/colorlevels"),
            "Video size: "+count("video-params/w")+" x "+count("video-params/h"),
            "Decoded output size: "+count("video-out-params/w")+" x "+count("video-out-params/h"),
            "Display aspect ratio: "+str("video-params/aspect"),
            "Video bitrate (bps): "+count("video-bitrate"),
            "Demuxer video bitrate (bps): "+count("demuxer-via-bitrate"),
            "Video codec profile: "+str("video-codec-profile"),
            "Video track FPS: "+num("container-fps"),
            "Output colour matrix: "+str("video-out-params/colormatrix"),
            "Output colour primaries: "+str("video-out-params/primaries"),
            "Output transfer: "+str("video-out-params/gamma"),
            "Output signal peak: "+num("video-out-params/sig-peak"),
            "Target transfer: "+str("target-trc"),
            "Target primaries: "+str("target-prim"),
            "Tone mapping: "+str("tone-mapping"),
            "HDR compute peak: "+str("hdr-compute-peak"),
            "Active picture brightness: "+count("brightness"),
            "Active picture contrast: "+count("contrast"),
            "Active picture saturation: "+count("saturation"),
            "Active zoom: "+num("video-zoom"),
            "Video FPS: "+num("estimated-vf-fps"),
            "Container: "+str("file-format"),
            "Dropped frames: "+count("frame-drop-count"),
            "Decoder dropped frames: "+count("decoder-frame-drop-count"),
            "Video output: "+str("current-vo"),
            "GPU debanding: "+str("deband"),
            "Dither depth: "+str("dither-depth"),
            "Android surface pixel format: RGBA_8888",
            "Playback started: "+started,
            "Surface attached: "+attached
        ).joinToString("\n")
    }

    fun release() {
        if (closed) return
        closed = true
        generation++
        pendingUrl = null
        pendingHeaders = emptyMap()
        listener = null
        val engine = mpv
        mpv = null
        try { holder.removeCallback(this) } catch (_: Exception) {}
        if (engine != null) {
            try { engine.command(arrayOf("stop")) } catch (_: Exception) {}
            try {
                if (attached) {
                    engine.detachSurface()
                }
            } catch (_: Exception) {}
            attached = false
            try { engine.removeObserver(this) } catch (_: Exception) {}
            try { engine.destroy() } catch (_: Exception) {}
        }
    }
}
