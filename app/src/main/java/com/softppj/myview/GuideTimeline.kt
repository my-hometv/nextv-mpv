package com.softppj.myview

/** Chronological, bounded programmes for one channel; expired and unrelated entries are excluded. */
internal object GuideTimeline {
    data class Event(val id: String, val start: Long, val end: Long, val title: String)

    fun upcoming(all: List<Event>, channelId: String, afterMs: Long,
        windowMs: Long = 6 * 60 * 60 * 1000L, limit: Int = 5): List<Event> {
        if (channelId.isBlank() || windowMs <= 0 || limit <= 0) return emptyList()
        val cutoff = afterMs + windowMs
        if (cutoff < afterMs) return emptyList()
        return all.asSequence()
            .filter { it.id == channelId && it.start < cutoff && it.end > afterMs && it.end > it.start }
            .sortedBy { it.start }
            .take(limit.coerceAtMost(20))
            .toList()
    }
}
