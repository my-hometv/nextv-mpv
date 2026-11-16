package com.softppj.nextv

/**
 * Only match the display refresh rate for explicit VOD and with opt-in.
 * Never change the display resolution, never adjust refresh for live TV.
 */
internal object FrameRateMatchPolicy {
    data class Mode(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

    fun select(
        enabled: Boolean,
        videoOnDemand: Boolean,
        videoFps: Float,
        current: Mode,
        available: List<Mode>
    ): Mode? {
        if (!enabled || !videoOnDemand || !videoFps.isFinite() || videoFps !in 23f..60f) return null
        val candidates = available.filter {
            it.width == current.width && it.height == current.height &&
                it.refreshRate.isFinite() && it.refreshRate >= 23f
        }
        if (candidates.isEmpty()) return null
        fun cadenceError(rate: Float): Double {
            val ratio = rate / videoFps
            val nearest = ratio.toInt().coerceAtLeast(1)
            val candidates = listOf(nearest, nearest + 1)
            return candidates.minOf { kotlin.math.abs(ratio - it) }.toDouble()
        }
        val selected = candidates.minWithOrNull(
            compareBy<Mode> { cadenceError(it.refreshRate) }
                .thenBy { kotlin.math.abs(it.refreshRate - current.refreshRate) }
        ) ?: return null
        if (selected.id == current.id) return null
        // Avoid display blackouts for merely marginal improvements.
        if (cadenceError(selected.refreshRate) >= 0.03 || 
            cadenceError(selected.refreshRate) >= cadenceError(current.refreshRate) - 0.02) return null
        return selected
    }
}
