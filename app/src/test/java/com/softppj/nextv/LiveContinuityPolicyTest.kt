package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class LiveContinuityPolicyTest {
    private fun retry(isLive:Boolean=true, frozen:Boolean=true,
        current:Boolean=true, reconnect:Boolean=true, now:Long=200000L,
        previous:Long?=null)=LiveContinuityPolicy.shouldRetrySameChannel(
            isLive,frozen,current,reconnect,now,previous)

    @Test fun oneLiveFrozenDecoderGetsSameChannelRetry(){
        assertTrue(retry())
        assertFalse(retry(previous=199999L))
        assertFalse(retry(previous=100000L))
        assertTrue(retry(previous=80000L))
    }

    @Test fun neverRecoverOnNormalPlaybackStaleSessionOrDisabledReconnect(){
        assertFalse(retry(frozen=false))
        assertFalse(retry(current=false))
        assertFalse(retry(reconnect=false))
        assertFalse(retry(isLive=false))
    }

    @Test fun clockRollbackCannotRearmRecovery(){
        assertFalse(retry(now=100L,previous=200L))
    }

    @Test fun individualAudioUnderrunsDoNotProveLipSyncDrift(){
        assertFalse(LiveContinuityPolicy.audioUnderrunConcern(1,30000L))
        assertFalse(LiveContinuityPolicy.audioUnderrunConcern(2,30000L))
        assertTrue(LiveContinuityPolicy.audioUnderrunConcern(3,30000L))
        assertFalse(LiveContinuityPolicy.audioUnderrunConcern(3,61000L))
    }
}
