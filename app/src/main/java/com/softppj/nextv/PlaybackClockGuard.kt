package com.softppj.nextv

/**
 * Conservative timestamp-progression detector. It is NOT a lip-sync measurement.
 * Multiple abnormal consecutive samples are required before asking for one recovery.
 */
internal class PlaybackClockGuard {
    private var lastWallMs = -1L
    private var lastMediaMs = -1L
    private var consecutiveFast = 0

    fun reset() {
        lastWallMs = -1L
        lastMediaMs = -1L
        consecutiveFast = 0
    }

    fun sample(wallMs: Long, mediaMs: Long, playing: Boolean, speed: Float): Boolean {
        // Intentional slow/fast playback is a user preference, not a clock fault.
        if (!playing || mediaMs < 0 || !speed.isFinite() ||
            speed < 0.97f || speed > 1.03f) {
            reset()
            return false
        }
        if (lastWallMs < 0 || wallMs <= lastWallMs || mediaMs < lastMediaMs) {
            lastWallMs = wallMs
            lastMediaMs = mediaMs
            consecutiveFast = 0
            return false
        }
        val wallDelta = wallMs - lastWallMs
        val mediaDelta = mediaMs - lastMediaMs
        lastWallMs = wallMs
        lastMediaMs = mediaMs
        if (wallDelta !in 3000L..12000L) {
            consecutiveFast = 0
            return false
        }
        val excessive = mediaDelta.toDouble() / wallDelta > 1.65
        consecutiveFast = if (excessive) consecutiveFast + 1 else 0
        return consecutiveFast >= 3
    }
}
