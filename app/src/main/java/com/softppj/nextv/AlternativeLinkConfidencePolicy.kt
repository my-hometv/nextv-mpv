package com.softppj.nextv

/**
 * Evidence-based, deliberately conservative confidence indicators.
 * These are heuristic scores, NOT calibrated probabilities.
 * Never manufacture a percentage for a source with no observations.
 */
internal object AlternativeLinkConfidencePolicy {
    fun isFresh(observedAt:Long?,now:Long,maxAgeMs:Long):Boolean =
        observedAt!=null && observedAt>=0L && now>=0L && maxAgeMs>=0L &&
            now>=observedAt && now-observedAt<=maxAgeMs

    /** A failed URL is not a valid recommendation, even with older success evidence. */
    fun eligibleScore(failedThisSession:Boolean,media3:Int?,libVlc:Int?):Int? =
        if(failedThisSession)null else bestScore(media3,libVlc)

    fun bestScore(media3:Int?,libVlc:Int?):Int? = when {
        media3==null -> libVlc
        libVlc==null -> media3
        else -> maxOf(media3,libVlc)
    }

    /** Recommend only a source with positive reachability or playback evidence. */
    fun recommendedIndex(scores:List<Int?>, excludedIndex:Int=-1):Int? {
        // A search for an alternative must not recommend the stream already playing.
        // Preserve the original stable ordering when scores tie.
        val best=scores.withIndex()
            .filter { it.index!=excludedIndex }
            .mapNotNull { it.value }
            .maxOrNull() ?: return null
        if(best<80)return null
        return scores.indices.firstOrNull { it!=excludedIndex && scores[it]==best }
    }

    fun score(confirmedPlayback:Boolean, latestProbe:Boolean?):Int? = when {
        confirmedPlayback && latestProbe == false -> 40 // conflicting recent evidence
        confirmedPlayback -> 95
        latestProbe == true -> 80
        latestProbe == false -> 15
        else -> null
    }
}
