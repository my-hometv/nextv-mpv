package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class PlaylistBrowsePolicyTest {
    @Test fun combinedListIncludesOtherPlaylistWithoutChangingScopedList() {
        val visible=listOf(0,1,4,5)
        val scoped=listOf(0,1)
        assertEquals(visible,PlaylistBrowsePolicy.displayedIndices(true,visible,scoped))
        assertEquals(scoped,PlaylistBrowsePolicy.displayedIndices(false,visible,scoped))
        assertFalse(PlaylistBrowsePolicy.displayedIndices(true,visible,scoped).contains(3))
    }
}
