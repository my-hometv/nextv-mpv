package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class NativePlaybackWatchdogTest {

    @Test fun startupTimeoutIsTimeBasedNotPollBased() {
        val w = NativePlaybackWatchdog(1000L)
        assertEquals(NativePlaybackWatchdog.Result.WAITING, w.check(1, 0, 14999L))
        assertEquals(NativePlaybackWatchdog.Result.STARTUP_TIMEOUT, w.check(1, 0, 16000L))
    }

    @Test fun playingWithoutProgressEventuallyRecovers() {
        val w = NativePlaybackWatchdog(0L)
        assertEquals(NativePlaybackWatchdog.Result.PLAYING, w.check(2, 0, 1000L))
        assertEquals(NativePlaybackWatchdog.Result.PLAYING, w.check(2, 0, 20999L))
        assertEquals(NativePlaybackWatchdog.Result.FIRST_PROGRESS_TIMEOUT, w.check(2, 0, 21000L))
    }

    @Test fun progressRefreshesStallDeadline() {
        val w = NativePlaybackWatchdog(0L)
        assertEquals(NativePlaybackWatchdog.Result.PLAYING, w.check(2, 0, 1000L))
        assertEquals(NativePlaybackWatchdog.Result.PLAYING, w.check(2, 1, 5000L))
        assertEquals(NativePlaybackWatchdog.Result.PLAYING, w.check(2, 1, 34999L))
        assertEquals(NativePlaybackWatchdog.Result.STALLED, w.check(2, 1, 35000L))
    }

    @Test fun repeatedOrRegressedProgressCannotHideStall() {
        val w = NativePlaybackWatchdog(0L)
        w.check(2, 0, 1000L)
        w.check(2, 8, 2000L)
        w.check(2, 7, 31000L)
        assertEquals(NativePlaybackWatchdog.Result.STALLED, w.check(2, 8, 32000L))
    }

    @Test fun timeoutCannotBeResurrectedByLateProgress() {
        val w = NativePlaybackWatchdog(0L)
        assertEquals(NativePlaybackWatchdog.Result.STARTUP_TIMEOUT, w.check(1, 0L, 15000L))
        assertEquals(NativePlaybackWatchdog.Result.STARTUP_TIMEOUT, w.check(2, 999L, 16000L))
        assertFalse(w.hasObservedProgress)
    }

    @Test fun terminalNativeEventCannotBeResurrected() {
        val w = NativePlaybackWatchdog(0L)
        assertEquals(NativePlaybackWatchdog.Result.FAILED, w.check(3, 0L, 10L))
        assertEquals(NativePlaybackWatchdog.Result.FAILED, w.check(2, 1L, 20L))
    }

    @Test fun invalidStateFailsClosed() {
        val w = NativePlaybackWatchdog(0L)
        assertEquals(NativePlaybackWatchdog.Result.FAILED, w.check(99, 0L, 100L))
    }

    @Test fun staleClockSampleDoesNotMoveWatchdogBackward() {
        val w = NativePlaybackWatchdog(100L)
        assertEquals(NativePlaybackWatchdog.Result.WAITING, w.check(1, 0L, 1000L))
        assertEquals(NativePlaybackWatchdog.Result.WAITING, w.check(1, 0L, 500L))
        assertEquals(NativePlaybackWatchdog.Result.STARTUP_TIMEOUT, w.check(1, 0L, 15100L))
    }

    @Test fun constructorRejectsNonPositiveTimeouts() {
        try {
            NativePlaybackWatchdog(0L, startupTimeoutMs = 0L)
            fail("Expected invalid timeout to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun terminalStateAlwaysWins() {
        val w = NativePlaybackWatchdog(0L)
        assertEquals(NativePlaybackWatchdog.Result.FAILED, w.check(3, 9, 0L))
        assertEquals(NativePlaybackWatchdog.Result.ENDED, w.check(4, 9, 0L))
        assertEquals(NativePlaybackWatchdog.Result.ENDED, w.check(0, -1L, 0L))
    }
}
