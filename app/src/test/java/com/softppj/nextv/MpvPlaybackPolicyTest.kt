package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class MpvPlaybackPolicyTest {
 @Test fun defaultIsCopyBack() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("Automatic")) }
 @Test fun hardwareIsCopyBack() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("Hardware copy")) }
 @Test fun softwareIsSoftware() { assertEquals("no", MpvPlaybackPolicy.hardwareDecoder("Software")) }
 @Test fun staleModeIsSafe() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("legacy")); assertFalse(MpvPlaybackPolicy.isValidMode("legacy")) }
 @Test fun hdr10RequiresCapableDisplay() {
     assertEquals("Auto (HDR10 unsupported)", MpvPlaybackPolicy.effectiveOutputMode("Force HDR10", false))
     assertEquals("Force HDR10", MpvPlaybackPolicy.effectiveOutputMode("Force HDR10", true))
 }
 @Test fun sdrAndAutomaticDoNotRequireHdrDisplay() {
     for (hdr in listOf(true, false)) {
         assertEquals("Force SDR", MpvPlaybackPolicy.effectiveOutputMode("Force SDR", hdr))
         assertEquals("Auto", MpvPlaybackPolicy.effectiveOutputMode("Auto", hdr))
     }
 }
 @Test fun staleOutputModeIsSafe() {
     assertEquals("Auto", MpvPlaybackPolicy.effectiveOutputMode("Media3", true))
     assertEquals("Auto", MpvPlaybackPolicy.effectiveOutputMode("", false))
 }
 @Test fun softwareFallbackOnlyForFirstUnconfirmedAutomaticFailure() {
     assertTrue(MpvPlaybackPolicy.shouldTrySoftwareAfterStartupFailure("Automatic", false, false, false))
     assertFalse(MpvPlaybackPolicy.shouldTrySoftwareAfterStartupFailure("Automatic", true, false, false))
     assertFalse(MpvPlaybackPolicy.shouldTrySoftwareAfterStartupFailure("Automatic", false, true, false))
     assertFalse(MpvPlaybackPolicy.shouldTrySoftwareAfterStartupFailure("Automatic", false, false, true))
     assertFalse(MpvPlaybackPolicy.shouldTrySoftwareAfterStartupFailure("Hardware copy", false, false, false))
     assertFalse(MpvPlaybackPolicy.shouldTrySoftwareAfterStartupFailure("Software", false, false, false))
 }
 @Test fun explicitTransportStreamIsDetected() {
     assertTrue(MpvPlaybackPolicy.isExplicitTransportStream("https://example.test/live/channel.ts"))
     assertTrue(MpvPlaybackPolicy.isExplicitTransportStream("https://example.test/live/CHANNEL.TS?token=redacted"))
     assertTrue(MpvPlaybackPolicy.isExplicitTransportStream("https://example.test/channel.ts#fragment"))
 }
 @Test fun hlsAndOtherResourcesKeepAutodetection() {
     assertFalse(MpvPlaybackPolicy.isExplicitTransportStream("https://example.test/index.m3u8"))
     assertFalse(MpvPlaybackPolicy.isExplicitTransportStream("https://example.test/channel.ts.m3u8"))
     assertFalse(MpvPlaybackPolicy.isExplicitTransportStream("https://example.test/live.ts/playlist.m3u8"))
     assertFalse(MpvPlaybackPolicy.isExplicitTransportStream("https://example.test/movie.mp4"))
 }
}
