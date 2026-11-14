package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class LastChannelPolicyTest {
    private val channels = listOf(
        Channel("One", "https://stream/shared", "News", source = "A"),
        Channel("Two", "https://stream/shared", "Sports", source = "B"),
        Channel("Three", "https://stream/other", "News", source = "A")
    )
    private fun find(identity: String?, url: String?) =
        LastChannelPolicy.findSavedChannel(channels, identity, url) { it.name }

    @Test fun exactIdentityWinsWhenChannelsShareUrl() {
        assertEquals(1, find("Two", "https://stream/shared"))
    }

    @Test fun legacyUrlFallbackStillWorks() {
        assertEquals(0, find(null, "https://stream/shared"))
    }

    @Test fun removedIdentityFallsBackToSavedUrl() {
        assertEquals(2, find("Removed", "https://stream/other"))
    }

    @Test fun exactIdentityWinsEvenIfLegacyUrlPointsElsewhere() {
        assertEquals(1, find("Two", "https://stream/other"))
    }

    @Test fun blankIdentityFallsBackToLegacyUrl() {
        assertEquals(2, find(" ", "https://stream/other"))
    }

    @Test fun missingSavedChannelReturnsNotFound() {
        assertEquals(-1, find(null, null))
        assertEquals(-1, find("Removed", "https://stream/missing"))
    }
}
