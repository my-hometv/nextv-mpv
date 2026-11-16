package com.softppj.nextv

/**
 * Native C-API diagnostics for the LibVLC library shipped in the active APK.
 *
 * A missing C-API probe is not proof that the upstream Java LibVLC bindings
 * cannot initialize: upstream versions can load or name their libraries
 * differently. Only successful construction of LibVLC proves Java readiness.
 */
internal object NativeLibVlcProbe {
    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("nextv_vlc_probe")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    @JvmStatic private external fun nativeRuntimeVersion(library: String): String?

    fun runtimeVersion(library: String = "libvlc.so"): String? {
        if (!loaded || library.isBlank()) return null
        return try {
            nativeRuntimeVersion(library)
        } catch (_: UnsatisfiedLinkError) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    fun isNativeApiPresent(): Boolean = !runtimeVersion().isNullOrBlank()
}
