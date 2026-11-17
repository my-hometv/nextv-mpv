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

    @Test fun idleScanNeverResumesDuringForegroundOrAfterLicenseLoss(){
        fun allowed(active:Boolean=false,licensed:Boolean=true,
            visible:Boolean=true,teardown:Boolean=false,
            enabled:Boolean=true,safe:Boolean=false)=
            VideoQualityPolicy.mayResumeIdleChecks(
                visible,licensed,active,teardown,enabled,safe)
        assertTrue(allowed())
        assertFalse(allowed(active=true))
        assertFalse(allowed(licensed=false))
        assertFalse(allowed(visible=false))
        assertFalse(allowed(teardown=true))
        assertFalse(allowed(enabled=false))
        assertFalse(allowed(safe=true))
    }

    @Test fun foregroundAlwaysHasExclusiveDecoderPriority(){
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true))
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true,true))
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true,false,true))
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true,false,false,true))
        assertEquals(0, VideoQualityPolicy.backgroundProbeLimit(true,false,false,false))
    }

    @Test fun idleBackgroundChecksNeverAllocateMoreThanOneDecoder(){
        assertEquals(1, VideoQualityPolicy.backgroundProbeLimit(false))
        assertEquals(1, VideoQualityPolicy.backgroundProbeLimit(false,true))
        assertEquals(1, VideoQualityPolicy.backgroundProbeLimit(false,false,true))
        assertEquals(1, VideoQualityPolicy.backgroundProbeLimit(false,false,false,true))
    }
}
