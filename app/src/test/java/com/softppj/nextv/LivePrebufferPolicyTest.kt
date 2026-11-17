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
}
