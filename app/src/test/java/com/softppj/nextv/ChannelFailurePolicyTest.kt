package com.softppj.nextv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelFailurePolicyTest {
    @Test fun establishedChannelKeepsWorkingUrlThroughShortOutage() {
        assertFalse(ChannelFailurePolicy.shouldSearchAlternatives(1, true))
        assertFalse(ChannelFailurePolicy.shouldSearchAlternatives(2, true))
        assertTrue(ChannelFailurePolicy.shouldSearchAlternatives(3, true))
        assertTrue(ChannelFailurePolicy.shouldSearchAlternatives(10, true))
    }

    @Test fun alternativesRemainAvailableWithoutAutoReconnect() {
        assertTrue(ChannelFailurePolicy.shouldSearchAlternatives(1, false))
        assertTrue(ChannelFailurePolicy.shouldSearchAlternatives(0, false))
    }

    @Test fun savedUrlPreventsSkippingEvenWithoutSavedEngine() {
        assertTrue(ChannelFailurePolicy.hasEstablishedPlayback(true, false))
        assertFalse(ChannelFailurePolicy.shouldSkipUnestablishedChannel(true, true, true))
    }

    @Test fun enabledSavedEnginePreventsSkippingEvenWithoutSavedUrl() {
        assertTrue(ChannelFailurePolicy.hasEstablishedPlayback(false, true))
        assertFalse(ChannelFailurePolicy.shouldSkipUnestablishedChannel(true, true, true))
    }

    @Test fun unestablishedChannelSkipsOnlyAfterExhaustionWhenEnabled() {
        assertFalse(ChannelFailurePolicy.hasEstablishedPlayback(false, false))
        assertFalse(ChannelFailurePolicy.shouldSkipUnestablishedChannel(false, true, false))
        assertFalse(ChannelFailurePolicy.shouldSkipUnestablishedChannel(false, false, true))
        assertTrue(ChannelFailurePolicy.shouldSkipUnestablishedChannel(false, true, true))
    }

    @Test fun forwardAndReverseSkipRespectSurfDirection() {
        val sequence=listOf(10,20,30,40)
        val available=setOf(10,30,40)
        val key:(Int)->String={it.toString()}
        assertTrue(ChannelFailurePolicy.nextSurfingIndex(sequence,available,20,1,key,"20")==30)
        assertTrue(ChannelFailurePolicy.nextSurfingIndex(sequence,available,20,-1,key,"20")==10)
    }

    @Test fun alternateUrlResolvesLogicalChannelPosition() {
        val sequence=listOf(10,20,30)
        val key:(Int)->String={if(it==21) "20" else it.toString()}
        assertTrue(ChannelFailurePolicy.nextSurfingIndex(sequence,setOf(10,30),21,1,key,"20")==30)
    }

    @Test fun staleChannelIndexDoesNotCrashAutomaticSurfingRecovery() {
        val sequence = listOf(10, 20, 30, 40)
        val key: (Int) -> String = { index ->
            if (index == 30) error("playlist changed")
            index.toString()
        }
        assertTrue(ChannelFailurePolicy.nextSurfingIndex(
            sequence, setOf(10, 30, 40), 20, 1, key, "20") == 40)
        assertTrue(ChannelFailurePolicy.nextSurfingIndex(
            sequence, setOf(10, 30, 40), 20, -1, key, "20") == 10)
    }

    @Test fun failedChannelKeyLookupCanSurviveStaleEntries() {
        val sequence = listOf(10, 20, 30, 40)
        val key: (Int) -> String = { index ->
            if (index == 10) error("removed channel")
            if (index == 20) "failed" else "healthy"
        }
        assertTrue(ChannelFailurePolicy.nextSurfingIndex(
            sequence, setOf(30, 40), 99, 1, key, "failed") == 30)
    }

    @Test fun exhaustedOrMissingPositionDoesNotLoopToFailedChannel() {
        val sequence=listOf(10,20,30)
        val key:(Int)->String={it.toString()}
        assertTrue(ChannelFailurePolicy.nextSurfingIndex(sequence,setOf(20),20,1,key,"20")==null)
        assertTrue(ChannelFailurePolicy.nextSurfingIndex(sequence,setOf(10,30),99,1,key,"99")==null)
    }
}
