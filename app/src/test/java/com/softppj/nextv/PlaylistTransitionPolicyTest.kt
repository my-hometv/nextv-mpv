package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class PlaylistTransitionPolicyTest {
    @Test fun switchesToNextEnabledLoadedSourceInPlaylistOrder() {
        assertEquals("Third",PlaylistTransitionPolicy.nextEnabledPlaylist(
            listOf("First","Second","Third"),listOf(true,false,true),"First"))
    }
    @Test fun wrapsFromLastPlaylistToFirst() {
        assertEquals("First",PlaylistTransitionPolicy.nextEnabledPlaylist(
            listOf("First","Second","Third"),listOf(true,true,false),"Third"))
    }
    @Test fun noAvailableSourceDoesNotReturnRemoved() {
        assertNull(PlaylistTransitionPolicy.nextEnabledPlaylist(
            listOf("First","Second"),listOf(false,false),"First"))
        assertNull(PlaylistTransitionPolicy.nextEnabledPlaylist(
            listOf("Only"),listOf(true),"Only"))
    }
    @Test fun ignoresMissingOrInvalidSourceLists() {
        assertNull(PlaylistTransitionPolicy.nextEnabledPlaylist(
            listOf("A","B"),listOf(true),"A"))
        assertNull(PlaylistTransitionPolicy.nextEnabledPlaylist(
            listOf("A","B"),listOf(true,true),"Unknown"))
    }
}
