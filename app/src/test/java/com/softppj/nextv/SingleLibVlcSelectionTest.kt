package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class SingleLibVlcSelectionTest {
    @Test fun onlyPackagedLibVlcVersionIsSelectable() {
        assertEquals(listOf("Media3", "LibVLC"),
            PlaybackEnginePolicy.selectableEngines("3.5.1"))
        assertEquals(listOf("Media3"),
            PlaybackEnginePolicy.selectableEngines("3.7.7"))
    }

    @Test fun missingRuntimeNeverGetsInvented() {
        assertEquals(listOf("Media3"),
            PlaybackEnginePolicy.selectableWithRuntimeVerification(
                "3.7.7", emptySet(), emptySet()))
    }

    @Test fun unverifiedVersionedEnginesAreNotSelectable() {
        assertEquals(listOf("Media3", "LibVLC"),
            PlaybackEnginePolicy.selectableWithRuntimeVerification(
                "3.5.1", setOf("LibVLC-3.7.7"), emptySet()))
    }

    @Test fun isolatedVersionRequiresBothPackagingAndVerification() {
        assertEquals(listOf("Media3", "LibVLC", "LibVLC-3.7.7"),
            PlaybackEnginePolicy.selectableWithRuntimeVerification(
                "3.5.1", setOf("LibVLC-3.7.7"), setOf("LibVLC-3.7.7")))
    }
}
