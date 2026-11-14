package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class NativeLibVlcProbeTest {
    @Test fun absentNativeRuntimeDoesNotCrashJvm() {
        val result = NativeLibVlcProbe.runtimeVersion()
        assertTrue(result == null || result.isNotBlank())
    }

    @Test fun blankLibraryIsRejectedWithoutNativeCall() {
        assertNull(NativeLibVlcProbe.runtimeVersion(""))
        assertNull(NativeLibVlcProbe.runtimeVersion(" "))
    }

    @Test fun probeIsNotAPlaybackReadinessRequirement() {
        // Probe result is optional telemetry. Native LibVLC construction is
        // the authoritative readiness check in IsolatedVlcPlaybackService.
        val version = NativeLibVlcProbe.runtimeVersion()
        assertEquals(version != null, NativeLibVlcProbe.isNativeApiPresent())
    }
}
