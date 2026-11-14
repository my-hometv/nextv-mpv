package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class VideoQualityPolicyTest {
    @Test fun bestAvailableRetainsAdaptiveBitrateInsteadOfForcingPeak() {
        for (mode in listOf("Auto", "Best Available", "Best available",
            "Full HD (1080p)", "720p", "1080p")) {
            assertFalse(VideoQualityPolicy.forceHighestBitrate(mode))
        }
    }

    @Test fun youtubeAutoDoesNotOverrideAdaptivePlayer() {
        assertFalse(VideoQualityPolicy.shouldRequestYoutubeWebQuality("Auto"))
        assertFalse(VideoQualityPolicy.shouldRequestYoutubeWebQuality("Best available"))
        assertTrue(VideoQualityPolicy.shouldRequestYoutubeWebQuality("720p"))
        assertTrue(VideoQualityPolicy.shouldRequestYoutubeWebQuality("1080p"))
    }

    @Test fun foregroundPlaybackLimitsConcurrentBackgroundDecoders() {
        assertEquals(1, VideoQualityPolicy.backgroundProbeLimit(true))
        assertEquals(2, VideoQualityPolicy.backgroundProbeLimit(false))
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true, true))
        assertEquals(1, VideoQualityPolicy.backgroundProbeLimit(true, false))
        assertEquals(2, VideoQualityPolicy.backgroundProbeLimit(false, true))
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true, false, true))
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true, true, true))
        assertEquals(2, VideoQualityPolicy.backgroundProbeLimit(false, false, true))
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true, false, false, true))
        assertEquals(1, VideoQualityPolicy.backgroundProbeLimit(true, false, false, false))
        assertEquals(2, VideoQualityPolicy.backgroundProbeLimit(false, false, false, true))
        // Foreground startup must suppress optional background decoders.
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true, false, false, true))
        // A settled foreground player can coexist with one background check.
        assertEquals(1, VideoQualityPolicy.backgroundProbeLimit(true, false, false, false))
    }
}
