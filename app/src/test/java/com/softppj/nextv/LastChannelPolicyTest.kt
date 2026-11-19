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
    @Test fun automaticHistorySkipsHiddenAndLockedChannels(){
        assertEquals(-1,LastChannelPolicy.findEligibleSavedChannel(
            channels,"Two","https://stream/shared",{it.name},{it.name!="Two"}))
        assertEquals(0,LastChannelPolicy.findEligibleSavedChannel(
            channels,null,"https://stream/shared",{it.name},{it.name=="One"}))
    }
    @Test fun deletedIdentityCannotReopenSameUrlInAnotherPlaylist(){
        assertEquals(-1,LastChannelPolicy.findEligibleSavedChannel(
            channels,"Deleted","https://stream/shared",{it.name},{true}))
    }
    @Test fun perPlaylistHistoryUsesExactIdentity(){
        assertEquals(1,LastChannelPolicy.findEligibleSavedChannel(
            channels,"Two","https://stream/shared",{it.name},{it.source=="B"}))
        assertEquals(-1,LastChannelPolicy.findEligibleSavedChannel(
            channels,"Two","https://stream/shared",{it.name},{it.source=="A"}))
    }
    @Test fun legacyUrlHistoryRespectsEligibility(){
        assertEquals(1,LastChannelPolicy.findEligibleSavedChannel(
            channels,null,"https://stream/shared",{it.name},{it.source=="B"}))
        assertEquals(-1,LastChannelPolicy.findEligibleSavedChannel(
            channels,null,"https://stream/shared",{it.name},{false}))
    }
}

