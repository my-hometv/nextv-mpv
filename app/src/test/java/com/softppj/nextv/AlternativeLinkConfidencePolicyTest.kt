package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class AlternativeLinkConfidencePolicyTest {
    @org.junit.Test fun failedSourceCannotReceiveBestCheckedRecommendation() {
        val scores=listOf(
            AlternativeLinkConfidencePolicy.eligibleScore(true,95,80),
            AlternativeLinkConfidencePolicy.eligibleScore(false,80,null),
            AlternativeLinkConfidencePolicy.eligibleScore(false,null,null)
        )
        org.junit.Assert.assertNull(scores[0])
        org.junit.Assert.assertEquals(1,AlternativeLinkConfidencePolicy.recommendedIndex(scores))
    }

    @org.junit.Test fun allFailedOrUntestedSourcesHaveNoRecommendation() {
        val scores=listOf(
            AlternativeLinkConfidencePolicy.eligibleScore(true,95,null),
            AlternativeLinkConfidencePolicy.eligibleScore(false,null,null)
        )
        org.junit.Assert.assertNull(AlternativeLinkConfidencePolicy.recommendedIndex(scores))
    }


    @Test fun expiredFutureAndMissingObservationsCannotCountAsFresh() {
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(null,1000L,500L))
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(1001L,1000L,500L))
        assertTrue(AlternativeLinkConfidencePolicy.isFresh(500L,1000L,500L))
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(499L,1000L,500L))
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(500L,1000L,-1L))
    }
    @Test fun negativeMonotonicTimestampsAreNeverFresh() {
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(-1L,1000L,2000L))
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(0L,-1L,2000L))
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(-1L,-1L,2000L))
        assertTrue(AlternativeLinkConfidencePolicy.isFresh(0L,0L,0L))
    }
    @Test fun freshnessIsInclusiveAtExpiryAndRejectsClockRollback() {
        assertTrue(AlternativeLinkConfidencePolicy.isFresh(100L,600L,500L))
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(100L,601L,500L))
        assertFalse(AlternativeLinkConfidencePolicy.isFresh(600L,100L,500L))
    }
    @Test fun bestScoreUsesOnlyAvailableEngineEvidence() {
        assertNull(AlternativeLinkConfidencePolicy.bestScore(null,null))
        assertEquals(80,AlternativeLinkConfidencePolicy.bestScore(80,null))
        assertEquals(95,AlternativeLinkConfidencePolicy.bestScore(15,95))
        assertEquals(80,AlternativeLinkConfidencePolicy.bestScore(80,15))
    }
    @Test fun failedOnlyAlternativesNeverReceiveBestCheckedBadge() {
        assertNull(AlternativeLinkConfidencePolicy.recommendedIndex(listOf(null,null)))
        assertNull(AlternativeLinkConfidencePolicy.recommendedIndex(listOf(15,40,null)))
        assertEquals(1,AlternativeLinkConfidencePolicy.recommendedIndex(listOf(15,80,40)))
        assertEquals(0,AlternativeLinkConfidencePolicy.recommendedIndex(listOf(95,95,80)))
    }
    @Test fun currentlyPlayingStreamCannotBeRecommendedAsItsOwnAlternative() {
        assertEquals(1,AlternativeLinkConfidencePolicy.recommendedIndex(listOf(95,80,15),0))
        assertEquals(2,AlternativeLinkConfidencePolicy.recommendedIndex(listOf(95,15,95),0))
        assertNull(AlternativeLinkConfidencePolicy.recommendedIndex(listOf(95,40,null),0))
        assertNull(AlternativeLinkConfidencePolicy.recommendedIndex(listOf(95),0))
    }
    @Test fun invalidExclusionPreservesStableRecommendationOrdering() {
        assertEquals(0,AlternativeLinkConfidencePolicy.recommendedIndex(listOf(95,95),-1))
        assertEquals(0,AlternativeLinkConfidencePolicy.recommendedIndex(listOf(95,95),99))
        assertEquals(1,AlternativeLinkConfidencePolicy.recommendedIndex(listOf(95,95),0))
    }
    @Test fun unknownSourceDoesNotDisplayInventedProbability() {
        assertNull(AlternativeLinkConfidencePolicy.score(false,null))
    }
    @Test fun engineProbeResultsProduceIndependentConfidenceScores() {
        assertEquals(80,AlternativeLinkConfidencePolicy.score(false,true))
        assertEquals(15,AlternativeLinkConfidencePolicy.score(false,false))
    }
    @Test fun confirmedPlaybackIsStrongEvidenceButRecentFailureReducesConfidence() {
        assertEquals(95,AlternativeLinkConfidencePolicy.score(true,null))
        assertEquals(95,AlternativeLinkConfidencePolicy.score(true,true))
        assertEquals(40,AlternativeLinkConfidencePolicy.score(true,false))
    }

    @Test fun alternativeUiDoesNotRecommendUnverifiedOrDisabledEngines() {
        val untested = AlternativeLinkConfidencePolicy.bestScore(null, null)
        assertNull(untested)
        val checked = AlternativeLinkConfidencePolicy.bestScore(80, null)
        assertEquals(80, checked)
        assertEquals(1, AlternativeLinkConfidencePolicy.recommendedIndex(
            listOf(95, checked, untested), excludedIndex = 0
        ))
    }

    @Test fun recentProbeFailureOverridesPreviousConfirmationForRecommendation() {
        val conflict = AlternativeLinkConfidencePolicy.score(true, false)
        assertEquals(40, conflict)
        assertNull(AlternativeLinkConfidencePolicy.recommendedIndex(listOf(conflict, null)))
    }
}
