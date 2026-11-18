package com.softppj.nextv

/**
 * When a viewer explicitly opens a hidden/skipped channel, its position is
 * absent from the normal surfing list. Move relative to its original ordered
 * position rather than wrapping to the first/last visible channel.
 */
internal object HiddenChannelNavigationPolicy {
    fun nextFromExcluded(
        orderedIncludingHidden: List<Int>,
        available: List<Int>,
        current: Int,
        delta: Int
    ): Int {
        if (available.isEmpty()) return -1
        val position = orderedIncludingHidden.indexOf(current)
        if (position < 0) return if (delta >= 0) available.first() else available.last()
        val allowed = available.toHashSet()
        val direction = if (delta >= 0) 1 else -1
        for (step in 1..orderedIncludingHidden.size) {
            val next = (position + direction * step + orderedIncludingHidden.size) % orderedIncludingHidden.size
            val candidate = orderedIncludingHidden[next]
            if (candidate in allowed) return candidate
        }
        return -1
    }
}
