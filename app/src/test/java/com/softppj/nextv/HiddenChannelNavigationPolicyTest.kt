package com.softppj.nextv

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenChannelNavigationPolicyTest {
    @Test fun movesForwardFromAHiddenChannelWithoutJumpingToFirst() {
        assertEquals(4, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(0, 1, 2, 3, 4, 5), listOf(0, 1, 4, 5), 3, 1))
    }

    @Test fun movesBackwardFromAHiddenChannelWithoutJumpingToLast() {
        assertEquals(1, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(0, 1, 2, 3, 4, 5), listOf(0, 1, 4, 5), 3, -1))
    }

    @Test fun wrapsOnlyAtTheActualEndOfThePlaylist() {
        assertEquals(0, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(0, 1, 2, 3), listOf(0, 1, 2), 3, 1))
        assertEquals(2, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(0, 1, 2, 3), listOf(0, 1, 2), 3, -1))
    }

    @Test fun respectsTheCustomDisplayOrder() {
        assertEquals(2, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(5, 3, 2, 0, 1, 4), listOf(5, 2, 0, 4), 3, 1))
        assertEquals(5, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(5, 3, 2, 0, 1, 4), listOf(5, 2, 0, 4), 3, -1))
    }

    @Test fun returnsNoTargetWhenNothingIsVisible() {
        assertEquals(-1, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(0, 1, 2), emptyList(), 1, 1))
    }

    @Test fun excludedAlternativeWithoutAnchorFallsBackSafely() {
        assertEquals(1, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(1, 2, 3), listOf(1, 3), 42, 1))
        assertEquals(3, HiddenChannelNavigationPolicy.nextFromExcluded(
            listOf(1, 2, 3), listOf(1, 3), 42, -1))
    }
}
