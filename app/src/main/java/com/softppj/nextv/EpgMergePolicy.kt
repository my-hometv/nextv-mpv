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
        val accepted = ArrayList<T>(minOf(limit, existing.size + incoming.size))
        for (candidate in existing + incoming) {
            val from = start(candidate)
            val to = end(candidate)
            if (from <= 0L || to <= from) continue
            // Half-open ranges: adjacent programmes do not overlap.
            if (accepted.any { start(it) < to && from < end(it) }) continue
            if (accepted.size >= limit) break
            accepted.add(candidate)
        }
        return accepted.sortedBy(start)
    }
}
