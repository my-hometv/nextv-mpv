package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test

class PlaybackPoliciesTest {
    @Test fun normalPlaybackDoesNotTriggerClockRecovery() {
        val guard = PlaybackClockGuard()
        assertFalse(guard.sample(1000L, 100L, true, 1f))
        for (n in 1..8) assertFalse(guard.sample(1000L + n * 5000L, 100L + n * 5000L, true, 1f))
    }

    @Test fun sustainedFastProgressionRequiresThreeSamples() {
        val guard = PlaybackClockGuard()
        assertFalse(guard.sample(0, 0, true, 1f))
        assertFalse(guard.sample(5000, 9000, true, 1f))
        assertFalse(guard.sample(10000, 18000, true, 1f))
        assertTrue(guard.sample(15000, 27000, true, 1f))
        guard.reset()
        assertFalse(guard.sample(20000, 30000, true, 1f))
    }

    @Test fun bufferingAndDiscontinuitiesDoNotTrigger() {
        val guard = PlaybackClockGuard()
        guard.sample(0, 0, true, 1f)
        assertFalse(guard.sample(5000, 9000, false, 1f))
        assertFalse(guard.sample(10000, 18000, true, 1f))
        assertFalse(guard.sample(15000, 1000, true, 1f))
    }

    @Test fun providerCapacityAndAuthorizationAreDifferent() {
        assertEquals(ProviderFailurePolicy.Kind.PROVIDER_BUSY, ProviderFailurePolicy.classify(429, false, false, false))
        assertEquals(ProviderFailurePolicy.Kind.PROVIDER_BUSY, ProviderFailurePolicy.classify(503, false, false, false))
        assertEquals(ProviderFailurePolicy.Kind.AUTHORIZATION, ProviderFailurePolicy.classify(403, false, false, false))
        assertEquals(ProviderFailurePolicy.Kind.DRM, ProviderFailurePolicy.classify(403, true, false, false))
        assertEquals(4000L, ProviderFailurePolicy.retryDelayMs(ProviderFailurePolicy.Kind.PROVIDER_BUSY, 0))
        assertNull(ProviderFailurePolicy.retryDelayMs(ProviderFailurePolicy.Kind.AUTHORIZATION, 0))
        assertNull(ProviderFailurePolicy.retryDelayMs(ProviderFailurePolicy.Kind.PROVIDER_BUSY, 2))
    }

    @Test fun frameRateMatchingIsOptInAndVodOnly() {
        val current = FrameRateMatchPolicy.Mode(1, 1920, 1080, 60f)
        val modes = listOf(current, FrameRateMatchPolicy.Mode(2, 1920, 1080, 24f),
            FrameRateMatchPolicy.Mode(3, 3840, 2160, 24f))
        assertNull(FrameRateMatchPolicy.select(false, true, 24f, current, modes))
        assertNull(FrameRateMatchPolicy.select(true, false, 24f, current, modes))
        assertEquals(2, FrameRateMatchPolicy.select(true, true, 24f, current, modes)?.id)
        assertNull(FrameRateMatchPolicy.select(true, true, 60f, current, modes))
    }
}
