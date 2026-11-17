package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class LivePrebufferPolicyTest {
    private fun allowed(
        enabled: Boolean = true,
        licenseReady: Boolean = true,
        mainPlayerPlaying: Boolean = true,
        mainEngineIsMedia3: Boolean = true,
        previewEngineIsMedia3: Boolean = true,
        unlocked: Boolean = true,
        anotherDecoderSessionActive: Boolean = false,
        sameStream: Boolean = false
    ) = LivePrebufferPolicy.mayStart(
        enabled, licenseReady, mainPlayerPlaying, mainEngineIsMedia3,
        previewEngineIsMedia3, unlocked, anotherDecoderSessionActive, sameStream
    )

    @Test fun healthyOptedInMedia3CanPrebuffer() { assertTrue(allowed()) }
    @Test fun disabledPreviewMustAllocateNothing() { assertFalse(allowed(enabled = false)) }
    @Test fun licenseAndPinMustBeValidated() {
        assertFalse(allowed(licenseReady = false))
        assertFalse(allowed(unlocked = false))
    }
    @Test fun noCompetingDecoderDuringLiveStartupOrVlcPlayback() {
        assertFalse(allowed(mainPlayerPlaying = false))
        assertFalse(allowed(mainEngineIsMedia3 = false))
        assertFalse(allowed(previewEngineIsMedia3 = false))
        assertFalse(allowed(anotherDecoderSessionActive = true))
    }
    @Test fun sameUrlNeverGetsTwoConnections() { assertFalse(allowed(sameStream = true)) }
    @Test fun noSpeculativeDecoderIfAnyPreconditionFails() {
        val conditions = listOf(
            allowed(enabled = false),
            allowed(licenseReady = false),
            allowed(mainPlayerPlaying = false),
            allowed(mainEngineIsMedia3 = false),
            allowed(previewEngineIsMedia3 = false),
            allowed(unlocked = false),
            allowed(anotherDecoderSessionActive = true),
            allowed(sameStream = true)
        )
        assertTrue("Every unsafe situation must reject a second decoder",
            conditions.all { !it })
    }

    @Test fun validPreparationStillRequiresAnActualReadyPlayerForHandoff() {
        // A request that was admitted is not automatically a usable handoff.
        assertTrue(allowed())
        assertFalse(LivePrebufferPolicy.mayAdopt(true, true, false, true, true))
        assertTrue(LivePrebufferPolicy.mayAdopt(true, true, true, true, true))
    }

    @Test fun customPicturePresetMustUseFullRendererInsteadOfPreparedPlayer() {
        assertFalse(LivePrebufferPolicy.mayAdopt(
            licenseReady = true,
            correctIdentity = true,
            ready = true,
            engineIsMedia3 = true,
            unlocked = true,
            picturePresetIsStandard = false))
        assertTrue(LivePrebufferPolicy.mayAdopt(
            licenseReady = true,
            correctIdentity = true,
            ready = true,
            engineIsMedia3 = true,
            unlocked = true,
            picturePresetIsStandard = true))
    }

    @Test fun lockedOrMismatchedPreparedChannelCannotBeAdopted() {
        assertFalse(LivePrebufferPolicy.mayAdopt(true, false, true, true, true))
        assertFalse(LivePrebufferPolicy.mayAdopt(true, true, true, true, false))
        assertFalse(LivePrebufferPolicy.mayAdopt(true, true, true, false, true))
    }

    @Test fun onlyMatchingPreparedUnlockedMedia3CanBePromoted() {
        assertTrue(LivePrebufferPolicy.mayAdopt(true,true,true,true,true))
        assertFalse(LivePrebufferPolicy.mayAdopt(false,true,true,true,true))
        assertFalse(LivePrebufferPolicy.mayAdopt(true,false,true,true,true))
        assertFalse(LivePrebufferPolicy.mayAdopt(true,true,false,true,true))
        assertFalse(LivePrebufferPolicy.mayAdopt(true,true,true,false,true))
        assertFalse(LivePrebufferPolicy.mayAdopt(true,true,true,true,false))
    }
}
