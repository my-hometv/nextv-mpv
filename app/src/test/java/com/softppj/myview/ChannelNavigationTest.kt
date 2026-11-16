package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test

class ChannelNavigationTest {
    @Test fun numericSelectionIsOneBasedAndBounded() {
        assertEquals(0, ChannelNavigation.selectNumber(1, 5))
        assertEquals(4, ChannelNavigation.selectNumber(5, 5))
        assertNull(ChannelNavigation.selectNumber(0, 5))
        assertNull(ChannelNavigation.selectNumber(6, 5))
    }

    @Test fun navigationNeverWrapsAcrossPlaylists() {
        assertEquals(0, ChannelNavigation.next(2, 3, 1))
        assertEquals(2, ChannelNavigation.next(0, 3, -1))
        assertNull(ChannelNavigation.next(0, 0, 1))
        assertNull(ChannelNavigation.next(4, 3, 1))
    }

    @Test fun navigationMiddleChannelsWorksInBothDirections() {
        assertEquals(1, ChannelNavigation.next(2, 5, -1))
        assertEquals(3, ChannelNavigation.next(2, 5, 1))
    }

    @Test fun hiddenChannelsAreSkippedAndCanBeRestored() {
        val urls = listOf("one", "two", "three")
        assertEquals(2, ChannelNavigation.nextVisible(0, urls, setOf("two"), 1))
        assertEquals(1, ChannelNavigation.nextVisible(0, urls, emptySet(), 1))
        assertEquals(2, ChannelNavigation.nextVisible(0, urls, setOf("two"), -1))
        assertNull(ChannelNavigation.nextVisible(0, urls, urls.toSet(), 1))
    }

    @Test fun lastChannelFallsBackOnlyToVisibleEntry() {
        val urls = listOf("first", "second", "third")
        assertEquals(2, ChannelNavigation.preferredVisible(2, urls, setOf("first")))
        assertEquals(1, ChannelNavigation.preferredVisible(2, urls, setOf("first", "third")))
        assertEquals(1, ChannelNavigation.preferredVisible(-1, urls, setOf("first")))
        assertNull(ChannelNavigation.preferredVisible(0, urls, urls.toSet()))
        assertNull(ChannelNavigation.preferredVisible(0, emptyList(), emptySet()))
    }

    @Test fun favoritesAndSearchMatchWithinSelectedPlaylist() {
        val channels = listOf(
            PlaylistParser.Channel("Malayalam News", "https://example.org/1", "News"),
            PlaylistParser.Channel("Movies", "https://example.org/2", "Entertainment"))
        assertEquals(listOf(0), ChannelNavigation.filteredIndices(channels, "malayalam", false, emptySet()))
        assertEquals(listOf(1), ChannelNavigation.filteredIndices(channels, "",
            true, setOf("https://example.org/2")))
    }
}
