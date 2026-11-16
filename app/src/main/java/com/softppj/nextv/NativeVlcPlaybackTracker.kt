package com.softppj.nextv

/**
 * Pure playback-event state machine. Native events are advisory: a terminal
 * state cannot be overwritten by a late Playing callback, and duplicate or
 * regressing progress timestamps do not count as forward playback.
 */
internal class NativeVlcPlaybackTracker {
    var state = 0
        private set
    var progressCount = 0L
        private set
    private var lastTime = Long.MIN_VALUE
    private var consecutiveTimeRegressions = 0
    private var lastRegressedTime = Long.MIN_VALUE
    private var lastPosition = Float.NaN

    fun start() {
        state = 1
        progressCount = 0L
        lastTime = Long.MIN_VALUE
        consecutiveTimeRegressions = 0
        lastRegressedTime = Long.MIN_VALUE
        lastPosition = Float.NaN
    }

    fun playing() {
        if (state == 1 || state == 2) state = 2
    }

    fun timeChanged(value: Long) {
        if (state != 2 || value < 0L) return
        if (value > lastTime) {
            lastTime = value
            consecutiveTimeRegressions = 0
            lastRegressedTime = Long.MIN_VALUE
            progressCount++
        } else if (value < lastTime) {
            // Live HLS/discontinuity streams can reset the timeline.
            // Require two distinct regressing samples before adopting the
            // new timeline; a single stale callback is not progress.
            if (value > lastRegressedTime) {
                consecutiveTimeRegressions++
                lastRegressedTime = value
            } else {
                // Repeated or backwards callbacks are not proof of a
                // forward-moving replacement timeline.
                consecutiveTimeRegressions = 1
                lastRegressedTime = value
            }
            if (consecutiveTimeRegressions >= 2) {
                lastTime = value
                consecutiveTimeRegressions = 0
                lastRegressedTime = Long.MIN_VALUE
            }
        }
    }

    fun positionChanged(value: Float) {
        if (state != 2 || !value.isFinite() || value < 0f || value > 1f) return
        if (lastPosition.isNaN() || value > lastPosition) {
            lastPosition = value
            progressCount++
        } else if (value < lastPosition) {
            // Do not use fractional position regression as proof of health.
            // It may indicate seeking, playlist rotation, or stale events.
            lastPosition = value
        }
    }

    fun error() {
        if (state == 1 || state == 2) state = 3
    }

    fun ended() {
        if (state == 1 || state == 2) state = 4
    }

    fun reset() {
        state = 0
        progressCount = 0L
        lastTime = Long.MIN_VALUE
        consecutiveTimeRegressions = 0
        lastRegressedTime = Long.MIN_VALUE
        lastPosition = Float.NaN
    }
}
