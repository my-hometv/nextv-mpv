package com.softppj.nextv

/** Keep previously established playback out of automatic channel surfing skips. */
internal object ChannelFailurePolicy {
    fun hasEstablishedPlayback(savedUrl: Boolean, enabledSavedEngine: Boolean): Boolean =
        savedUrl || enabledSavedEngine

    /**
     * A transient network/decoder interruption on an already-working channel
     * must not immediately advertise an alternative URL. Allow two normal
     * reconnect attempts first. If reconnect is switched OFF, offer alternatives
     * immediately rather than leaving the viewer with no recovery action.
     */
    fun shouldSearchAlternatives(
        consecutiveFailures: Int,
        autoReconnectEnabled: Boolean
    ): Boolean = !autoReconnectEnabled || consecutiveFailures >= 3

    /** Advance from the failed logical channel, never restarting at channel one. */
    fun nextSurfingIndex(
        previousSequence: List<Int>,
        available: Set<Int>,
        failedIndex: Int,
        direction: Int,
        keyOf: (Int) -> String,
        failedKey: String
    ): Int? {
        if(previousSequence.isEmpty()) return null
        // Resolve the failed position before filtering: the failed entry may
        // already be hidden, and its numeric index may have been replaced.
        val position = previousSequence.indexOf(failedIndex).takeIf { it >= 0 }
            ?: previousSequence.indexOfFirst {
                runCatching { keyOf(it) == failedKey }.getOrDefault(false)
            }
        if (position < 0) return null
        val stepDirection = if (direction >= 0) 1 else -1
        for (step in 1 until previousSequence.size) {
            val offset = if (stepDirection > 0) step else previousSequence.size - step
            val candidate = previousSequence[(position + offset) % previousSequence.size]
            // Playlist refreshes may invalidate indices between building the
            // surfing sequence and selecting the next channel. Skip them.
            if (candidate in available &&
                runCatching { keyOf(candidate) != failedKey }.getOrDefault(false)
            ) return candidate
        }
        return null
    }

    fun shouldSkipUnestablishedChannel(
        established: Boolean,
        autoSkipEnabled: Boolean,
        combinationsExhausted: Boolean
    ): Boolean = !established && autoSkipEnabled && combinationsExhausted
}
