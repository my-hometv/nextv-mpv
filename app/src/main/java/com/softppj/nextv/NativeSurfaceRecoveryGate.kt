package com.softppj.nextv

/**
 * One-shot ownership of an Android video Surface and its native session.
 * Once destroyed, the same playback generation cannot reattach.
 */
internal class NativeSurfaceRecoveryGate {
    private var recoveryRequested = false
    private var surfaceDestroyed = false

    fun canStart(): Boolean = !surfaceDestroyed && !recoveryRequested

    fun onSurfaceDestroyed(wasAttached: Boolean): Boolean {
        surfaceDestroyed = true
        return if (wasAttached) requestRecovery() else false
    }

    fun requestRecovery(): Boolean {
        if (recoveryRequested) return false
        recoveryRequested = true
        return true
    }
}
