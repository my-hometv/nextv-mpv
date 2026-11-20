package com.softppj.nextv

/**
 * Keep the TV Guide's channel rows independent of XMLTV coverage.
 * Missing programme metadata must not make a playable channel disappear.
 */
internal object EpgGuidePolicy {
    /** Compare XMLTV display names despite inconsistent case and spacing. */
    fun normalizeDisplayName(value:String):String =
        value.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")

    /**
     * IPTV display names often append a quality suffix absent from XMLTV.
     * Prefer exact tvg-id and display-name matches; use these conservative
     * aliases only when exact matching has failed.
     */
    fun matchNames(value:String):List<String> {
        val exact=normalizeDisplayName(value)
        if(exact.isEmpty())return emptyList()
        val punctuation=normalizeDisplayName(exact.replace(Regex("[._|]+")," ").replace('-', ' '))
        val suffix=Regex("""(?:\s+(?:hd|fhd|uhd|sd|4k|1080p|720p|hevc|h265|h264))+$""")
        val withoutQuality=punctuation.replace(suffix,"").trim()
        return listOf(exact,punctuation,withoutQuality).filter{it.isNotBlank()}.distinct()
    }

    /** An XMLTV document with no useful programmes must never replace
     * a previously populated guide or claim a one-hour successful cache. */
    fun shouldPublishFeed(usableProgrammeCount:Int):Boolean =
        usableProgrammeCount>0

    fun rows(visibleIndices:List<Int>, channelCount:Int):List<Int> =
        visibleIndices.filter { it >= 0 && it < channelCount }

    /**
     * A due reminder must not spin at zero delay when foreground delivery is
     * temporarily impossible (licence checking or app in background).
     */
    fun nextReminderDelayMs(
        startTimeMs: Long, nowMs: Long, canDeliver: Boolean
    ): Long {
        if (!canDeliver && startTimeMs <= nowMs) return 60_000L
        return (startTimeMs - nowMs).coerceAtLeast(0L).coerceAtMost(60_000L)
    }

    /**
     * Select the visible programme and next future programme using half-open
     * [start, end) intervals. Exact end/start transitions must never retain
     * the previous programme.
     */
    fun <T> nowNext(
        rows:List<T>,nowMs:Long,start:(T)->Long,end:(T)->Long
    ):Pair<T?,T?> {
        val current=rows.firstOrNull{start(it)<=nowMs && end(it)>nowMs}
        val upcoming=rows.asSequence().filter{start(it)>nowMs}
            .minByOrNull{start(it)}
        return current to upcoming
    }

    /** Stale guides are refreshed while watching, without polling XMLTV continuously. */
    fun needsRefresh(loadedAtMs:Long,nowMs:Long):Boolean =
        loadedAtMs<=0L || nowMs<loadedAtMs || nowMs-loadedAtMs>=60*60*1000L

    /** Returns null when there is no future EPG change to schedule. */
    fun <T> nextBoundaryDelayMs(
        rows:List<T>,nowMs:Long,start:(T)->Long,end:(T)->Long
    ):Long? = rows.asSequence()
        .flatMap{sequenceOf(start(it),end(it))}
        .filter{it>nowMs}
        .minOrNull()?.let{(it-nowMs).coerceAtLeast(1L)}

    enum class ProgrammeAction { WATCH_NOW, SCHEDULE }

    fun action(startTimeMs:Long, nowMs:Long):ProgrammeAction =
        if(startTimeMs<=nowMs) ProgrammeAction.WATCH_NOW else ProgrammeAction.SCHEDULE
}
