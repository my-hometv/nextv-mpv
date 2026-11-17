package com.softppj.nextv

/**
 * Keep the TV Guide's channel rows independent of XMLTV coverage.
 * Missing programme metadata must not make a playable channel disappear.
 */
internal object EpgGuidePolicy {
    fun rows(visibleIndices:List<Int>, channelCount:Int):List<Int> =
        visibleIndices.filter { it >= 0 && it < channelCount }

    enum class ProgrammeAction { WATCH_NOW, SCHEDULE }

    fun action(startTimeMs:Long, nowMs:Long):ProgrammeAction =
        if(startTimeMs<=nowMs) ProgrammeAction.WATCH_NOW else ProgrammeAction.SCHEDULE
}
