package com.softppj.nextv

/**
 * Merge XMLTV programmes in source-priority order.
 *
 * Existing entries belong to higher-priority sources. Incoming entries are
 * processed in source order. An entry conflicting with any accepted interval
 * is ignored, including partial overlaps; lower-priority sources fill only
 * genuinely unoccupied slots. No title or time range is fabricated.
 */
internal object EpgMergePolicy {
    fun <T> merge(
        existing: List<T>,
        incoming: List<T>,
        start: (T) -> Long,
        end: (T) -> Long,
        limit: Int = 1000
    ): List<T> {
        if (limit <= 0) return emptyList()
        // Accepted intervals never overlap, so only the immediate preceding
        // and following intervals need checking. Avoid quadratic scans when
        // XMLTV feeds contain hundreds of entries per channel.
        val accepted = java.util.TreeMap<Long, T>()
        for (candidate in existing + incoming) {
            val from = start(candidate)
            val to = end(candidate)
            if (from <= 0L || to <= from) continue
            if (accepted.size >= limit) break
            val previous = accepted.floorEntry(from)
            if (previous != null && end(previous.value) > from) continue
            val next = accepted.ceilingEntry(from)
            if (next != null && next.key < to) continue
            accepted[from] = candidate
        }
        return accepted.values.toList()
    }
}
