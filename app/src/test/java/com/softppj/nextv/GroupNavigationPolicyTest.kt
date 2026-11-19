package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class GroupNavigationPolicyTest {
    private val channels=listOf(
        Channel("News","https://example.com/news","News",source="Enabled"),
        Channel("Protected","https://example.com/locked","PIN Group",source="Enabled"),
        Channel("Disabled","https://example.com/no","Disabled Group",source="Disabled"),
        Channel("Playlist Locked","https://example.com/a","Secret Group",source="Locked")
    )
    @Test fun lockedGroupRemainsDiscoverableForPinEntry() {
        assertEquals(listOf("News","PIN Group"),GroupNavigationPolicy.visibleGroups(
            channels,channels.indices.toList(),setOf("Enabled","Locked"),
            setOf("Locked"),emptySet(),emptySet()))
    }
    @Test fun disabledSourceAndHiddenGroupDoNotLeakIntoNavigation() {
        assertEquals(listOf("News"),GroupNavigationPolicy.visibleGroups(
            channels,channels.indices.toList(),setOf("Enabled","Locked"),
            setOf("Locked"),emptySet(),setOf("PIN Group")))
    }
    @Test fun playlistUnlockAllowsItsGroupButNotDisabledSource() {
        assertEquals(listOf("News","PIN Group","Secret Group"),
            GroupNavigationPolicy.visibleGroups(
                channels,channels.indices.toList(),setOf("Enabled","Locked"),
                setOf("Locked"),setOf("Locked"),emptySet()))
    }
}
