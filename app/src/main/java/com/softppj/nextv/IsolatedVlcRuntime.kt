package com.softppj.nextv

import android.view.Surface

/**
 * Boundary between playback orchestration and future isolated LibVLC services.
 * Implementations must own their native runtime and must not share LibVLC JNI state.
 * This contract intentionally does not advertise any runtime as installed.
 */
internal interface IsolatedVlcRuntime {
    val engineId: String
    val version: String
    fun isAvailable(): Boolean
    fun start(url: String, generation: Long): Boolean
    /** Remote TV video output requires an explicit Surface. Implementations
     * without native surface support must reject this request. */
    fun startWithSurface(url: String, generation: Long, surface: Surface): Boolean = false
    fun stop(generation: Long)
    fun release()
}

internal class IsolatedVlcRegistry(runtimes: List<IsolatedVlcRuntime>) {
    private val entries: Map<String, IsolatedVlcRuntime>
    init {
        require(runtimes.all { PlaybackEnginePolicy.versionEngineId(it.version) == it.engineId }) {
            "Invalid isolated LibVLC runtime identity"
        }
        require(runtimes.map { it.engineId }.distinct().size == runtimes.size) {
            "Duplicate isolated LibVLC runtime identity"
        }
        entries = runtimes.associateBy { it.engineId }
    }

    @Volatile private var released = false

    @Synchronized
    fun releaseAll() {
        if (released) return
        released = true
        // JNI linkage failures are Errors, not Exceptions. A broken native
        // runtime must not prevent remaining registered runtimes from closing.
        entries.values.forEach { try { it.release() } catch (_: Throwable) { } }
    }

    private fun safeReady(runtime: IsolatedVlcRuntime): Boolean =
        try { runtime.isAvailable() } catch (_: Exception) { false }

    fun verifiedIds(): Set<String> =
        if (released) emptySet() else entries.filterValues(::safeReady).keys

    fun resolve(engineId: String): IsolatedVlcRuntime? =
        if (released) null else entries[engineId]?.takeIf(::safeReady)
}
