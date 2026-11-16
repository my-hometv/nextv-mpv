package com.softppj.myview

/**
 * Reports historical startup outcomes, not a prediction or guaranteed playback chance.
 * Hides percentages when the device has fewer than three observed starts.
 */
internal object ChannelHealth {
    fun observedStartPercent(success: Int, failures: Int): Int? {
        if (success < 0 || failures < 0) return null
        val total = success.toLong() + failures.toLong()
        if (total < 3L) return null
        return ((success.toDouble() / total) * 100.0 + 0.5).toInt().coerceIn(0, 100)
    }

    fun label(success: Int, failures: Int): String {
        val rate = observedStartPercent(success, failures) ?: return "Not enough startup observations"
        return "Observed starts: $rate% (n=${success + failures})"
    }
}
