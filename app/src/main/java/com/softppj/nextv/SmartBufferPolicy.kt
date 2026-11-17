package com.softppj.nextv

/**
 * Bounded, per-channel live-buffer learning. A stall is counted only after
 * playback was previously ready; startup buffering is not a rebuffer event.
 * The level is stored per channel, never shared across unrelated streams.
 */
internal object SmartBufferPolicy {
    // Start with a responsive live buffer; sustained stalls promote it safely.
    const val DEFAULT_LEVEL = 0
    const val MAX_LEVEL = 3
    const val STALL_WINDOW_MS = 90_000L
    const val CHANGE_COOLDOWN_MS = 12_000L
    const val HEALTHY_DOWNGRADE_MS = 15 * 60_000L

    fun normalize(level: Int) = level.coerceIn(0, MAX_LEVEL)

    fun nextOnStall(level: Int, recentStalls: Int, sinceChangeMs: Long): Int =
        if (recentStalls >= 2 && sinceChangeMs >= CHANGE_COOLDOWN_MS)
            (normalize(level) + 1).coerceAtMost(MAX_LEVEL)
        else normalize(level)

    fun nextOnHealthy(level: Int, healthyMs: Long): Int =
        if (healthyMs >= HEALTHY_DOWNGRADE_MS)
            (normalize(level) - 1).coerceAtLeast(0)
        else normalize(level)

    fun durations(level: Int): IntArray = when (normalize(level)) {
        0 -> intArrayOf(2000, 15000, 1000, 2000)
        1 -> intArrayOf(4000, 25000, 1500, 3500)
        2 -> intArrayOf(6000, 35000, 2500, 5000)
        else -> intArrayOf(8000, 45000, 3000, 6500)
    }
}
