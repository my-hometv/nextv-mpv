package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class HiddenChannelReviewPolicyTest {
    @Test fun skippedChannelIsExcludedFromSubsequentReviews() {
        val skipped = HiddenChannelReviewPolicy.skipForever(emptySet(), "news")
        assertEquals(listOf("sports"), HiddenChannelReviewPolicy.eligible(listOf("news", "sports", "news"), skipped))
    }

    @Test fun manuallyUnhidingResetsSkipForeverForNextHide() {
        val skipped = HiddenChannelReviewPolicy.skipForever(emptySet(), "news")
        val reset = HiddenChannelReviewPolicy.manuallyUnhidden(skipped, "news")
        assertEquals(listOf("news"), HiddenChannelReviewPolicy.eligible(listOf("news"), reset))
    }

    @Test fun skippingOneChannelDoesNotSuppressOtherChannels() {
        val skipped = HiddenChannelReviewPolicy.skipForever(setOf("news"), "movies")
        assertEquals(listOf("sports"), HiddenChannelReviewPolicy.eligible(listOf("news", "movies", "sports"), skipped))
    }
    @Test fun restoringGroupClearsOnlyItsOwnSuppressedChannels() {
        val skipped = setOf("group-a-news", "group-a-sports", "group-b-movies")
        val restored = HiddenChannelReviewPolicy.manuallyRestoredGroup(
            skipped, setOf("group-a-news", "group-a-sports")
        )
        assertEquals(setOf("group-b-movies"), restored)
        assertEquals(
            listOf("group-a-news", "group-a-sports"),
            HiddenChannelReviewPolicy.eligible(listOf("group-a-news", "group-a-sports"), restored)
        )
    }

    @Test fun restoringEmptyGroupPreservesAllSuppression() {
        val skipped = setOf("news", "sports")
        assertEquals(skipped, HiddenChannelReviewPolicy.manuallyRestoredGroup(skipped, emptySet()))
    }

}
