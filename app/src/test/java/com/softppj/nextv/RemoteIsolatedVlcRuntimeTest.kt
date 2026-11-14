package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class RemoteIsolatedVlcRuntimeTest {
    @Test fun versionEngineIdsAreStable() {
        assertEquals("LibVLC-3.5.1", PlaybackEnginePolicy.versionEngineId("3.5.1"))
        assertEquals("LibVLC-3.7.7", PlaybackEnginePolicy.versionEngineId("3.7.7"))
        assertEquals("LibVLC-4.0.0-eap29", PlaybackEnginePolicy.versionEngineId("4.0.0-eap29"))
    }

    @Test fun unverifiedVersionsAreNotSelectable() {
        val engines = PlaybackEnginePolicy.selectableWithRuntimeVerification(
            "3.5.1", emptySet(), emptySet()
        )
        assertFalse(engines.contains("LibVLC-3.7.7"))
        assertFalse(engines.contains("LibVLC-4.0.0-eap29"))
    }
}
