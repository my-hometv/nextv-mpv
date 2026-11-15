package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class MpvPlaybackPolicyTest {
 @Test fun defaultIsCopyBack() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("Automatic")) }
 @Test fun hardwareIsCopyBack() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("Hardware copy")) }
 @Test fun softwareIsSoftware() { assertEquals("no", MpvPlaybackPolicy.hardwareDecoder("Software")) }
 @Test fun staleModeIsSafe() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("legacy")); assertFalse(MpvPlaybackPolicy.isValidMode("legacy")) }
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
