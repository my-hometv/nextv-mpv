package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class MpvPlaybackPolicyTest {
 @Test fun defaultIsCopyBack() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("Automatic")) }
 @Test fun hardwareIsCopyBack() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("Hardware copy")) }
 @Test fun softwareIsSoftware() { assertEquals("no", MpvPlaybackPolicy.hardwareDecoder("Software")) }
 @Test fun staleModeIsSafe() { assertEquals("mediacodec-copy", MpvPlaybackPolicy.hardwareDecoder("legacy")); assertFalse(MpvPlaybackPolicy.isValidMode("legacy")) }
}
