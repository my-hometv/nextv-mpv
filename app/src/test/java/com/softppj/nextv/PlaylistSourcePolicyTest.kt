package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class PlaylistSourcePolicyTest {
    @Test fun noPreinstalledEntitlementPlaylistReturnsFromStorage(){
        assertFalse(PlaylistSourcePolicy.shouldKeepSavedSource("hometv://licensed-playlist"))
        assertTrue(PlaylistSourcePolicy.shouldClearSelection("hometv://licensed-playlist"))
    }
    @Test fun externallyConfiguredSourcesAreNeverRemovedByTheirNameOrUrl(){
        assertTrue(PlaylistSourcePolicy.shouldKeepSavedSource("https://example.com/TV.m3u"))
        assertTrue(PlaylistSourcePolicy.shouldKeepSavedSource("https://example.com/Master.m3u"))
        assertTrue(PlaylistSourcePolicy.shouldKeepSavedSource("file:///storage/emulated/0/imported.m3u"))
        assertFalse(PlaylistSourcePolicy.shouldKeepSavedSource(""))
        assertFalse(PlaylistSourcePolicy.shouldClearSelection("TV"))
        assertFalse(PlaylistSourcePolicy.shouldClearSelection("Master"))
    }
}
