package com.softppj.nextv

import android.view.Surface

/**
 * Serializes the ownership of a single isolated LibVLC runtime session.
 * Generation IDs prevent stale recovery callbacks from stopping a newer session.
 * Does not claim any native runtime is packaged or verified.
 */
internal class IsolatedVlcSessionController(private val registry: IsolatedVlcRegistry) {
    private var active: IsolatedVlcRuntime? = null
    private var generation: Long = Long.MIN_VALUE
    private var released = false

    @Synchronized
    fun start(engineId: String, url: String, requestGeneration: Long): Boolean {
        if (released || url.isBlank() || requestGeneration <= generation) return false
        val next = registry.resolve(engineId) ?: return false
        val previous = active
        if (previous != null) {
            active = null
            try { previous.stop(generation) } catch (_: Exception) { /* best-effort cleanup */ }
        }
        // Advance the high-water mark even when native startup fails. An
        // asynchronous callback from the abandoned session must stay stale.
        generation = requestGeneration
        return try {
            if (next.start(url, requestGeneration)) {
                active = next
                true
            } else {
                try { next.stop(requestGeneration) } catch (_: Exception) { }
                false
            }
        } catch (failure: Exception) {
            try { next.stop(requestGeneration) } catch (_: Exception) { }
            false
        }
    }

    /** Surface-backed session entry point. The controller must not claim
     * playback ownership until the isolated native runtime accepts the
     * Surface. Older requests cannot replace or stop a newer session. */
    @Synchronized
    fun startWithSurface(
        engineId: String, url: String, requestGeneration: Long, surface: Surface
    ): Boolean {
        if (released || url.isBlank() || !surface.isValid ||
            requestGeneration <= generation) return false
        val next = registry.resolve(engineId) ?: return false
        val previous = active
        active = null
        try { previous?.stop(generation) } catch (_: Exception) { }
        generation = requestGeneration
        return try {
            if (next.startWithSurface(url, requestGeneration, surface)) {
                active = next
                true
            } else {
                try { next.stop(requestGeneration) } catch (_: Exception) { }
                false
            }
        } catch (_: Exception) {
            try { next.stop(requestGeneration) } catch (_: Exception) { }
            false
        }
    }

    /** Expose only the currently owned engine, never an attempted but failed start. */
    @Synchronized
    fun activeEngineId():String? = if (released) null else active?.engineId

    /** Monotonic generation of the latest attempted native playback session.
     * Useful for diagnostics; it is not proof that playback has started. */
    @Synchronized
    fun latestRequestGeneration():Long = generation

    @Synchronized
    fun detachStop(requestGeneration: Long): (() -> Unit)? {
        if (released || requestGeneration != generation) return null
        val previous = active ?: return null
        active = null
        return {
            try { previous.stop(requestGeneration) }
            catch (_: Exception) { /* stale native stop must not crash playback */ }
        }
    }

    fun stop(requestGeneration: Long) {
        detachStop(requestGeneration)?.invoke()
    }

    /**
     * Immediately invalidate old ownership under the controller lock without
     * making a potentially blocking Binder call. The returned task captures
     * the exact OLD generation; executing it after a new start cannot stop a
     * newer service generation, because the remote service checks that token.
     */
    @Synchronized
    fun detachActiveStop(): (() -> Unit)? {
        if (released) return null
        val previous = active ?: return null
        val oldGeneration = generation
        active = null
        return {
            try { previous.stop(oldGeneration) }
            catch (_: Exception) { /* best-effort detached native teardown */ }
        }
    }

    /** Synchronous compatibility entry point for existing callers/tests. */
    fun stopActive() {
        detachActiveStop()?.invoke()
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        val previous = active
        active = null
        try { previous?.stop(generation) } catch (_: Exception) { }
        registry.releaseAll()
    }
}
