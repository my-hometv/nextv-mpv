package com.softppj.nextv

/**
 * Detects a real packaged GStreamer runtime without changing existing playback.
 * Detection is not equivalent to SDK initialization, pipeline creation or TV playback.
 */
internal object GstreamerNativeProbe {
    private val nativeLoaded by lazy {
        try {
            System.loadLibrary("nextv_gstreamer_probe")
            true
        } catch (_: LinkageError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    @JvmStatic private external fun nativeRuntimeVersion(): String?

    fun runtimeVersion(): String? {
        if (!nativeLoaded) return null
        return try { nativeRuntimeVersion() } catch (_: LinkageError) { null }
    }

    fun isRuntimePresent(): Boolean = runtimeVersion() != null
}
