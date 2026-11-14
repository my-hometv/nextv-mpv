package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class NativePlaybackConfirmationTest {

    @Test fun playingWithoutProgressIsNotConfirmed() {
        val w = NativePlaybackWatchdog(0L)
        assertEquals(NativePlaybackWatchdog.Result.PLAYING, w.check(2, 0L, 100L))
        assertFalse(w.hasObservedProgress)
        assertEquals(NativePlaybackWatchdog.Result.PLAYING, w.check(2, 1L, 200L))
        assertTrue(w.hasObservedProgress)
    }

    @Test fun startingStateCannotClaimProgress() {
        val w = NativePlaybackWatchdog(0L)
        assertEquals(NativePlaybackWatchdog.Result.WAITING, w.check(1, 5L, 100L))
        assertFalse(w.hasObservedProgress)
        w.check(2, 5L, 200L)
        assertTrue(w.hasObservedProgress)
    }

    @Test fun firstProgressTimeoutWithoutConfirmation() {
        val w = NativePlaybackWatchdog(0L)
        w.check(2, 0L, 1000L)
        assertEquals(NativePlaybackWatchdog.Result.FIRST_PROGRESS_TIMEOUT, w.check(2, 0L, 21000L))
        assertFalse(w.hasObservedProgress)
    }
}
