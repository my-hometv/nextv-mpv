package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class NativeVlcPlaybackTrackerTest {
    @Test fun startupAndTerminalStatesCannotResurrect() {
        val t = NativeVlcPlaybackTracker()
        assertEquals(0, t.state)
        t.start()
        assertEquals(1, t.state)
        t.playing()
        assertEquals(2, t.state)
        t.error()
        assertEquals(3, t.state)
        t.playing()
        assertEquals(3, t.state)
        t.reset()
        t.start()
        t.ended()
        t.playing()
        assertEquals(4, t.state)
    }

    @Test fun progressRequiresForwardValidSamples() {
        val t = NativeVlcPlaybackTracker()
        t.start()
        t.timeChanged(100)
        assertEquals(0L, t.progressCount)
        t.playing()
        t.timeChanged(-1)
        t.timeChanged(100)
        t.timeChanged(100)
        t.timeChanged(99)
        t.positionChanged(Float.NaN)
        t.positionChanged(-0.1f)
        t.positionChanged(0.2f)
        t.positionChanged(0.2f)
        assertEquals(2L, t.progressCount)
        t.error()
        t.timeChanged(101)
        assertEquals(2L, t.progressCount)
    }

    @Test fun timelineDiscontinuityCanRecoverForwardProgress() {
        val t = NativeVlcPlaybackTracker()
        t.start()
        t.playing()
        t.timeChanged(100_000L)
        t.timeChanged(1_000L)
        t.timeChanged(1_100L)
        assertEquals(1L, t.progressCount)
        t.timeChanged(1_200L)
        assertEquals(2L, t.progressCount)
    }

    @Test fun invalidFractionalPositionDoesNotCount() {
        val t = NativeVlcPlaybackTracker()
        t.start()
        t.playing()
        t.positionChanged(Float.POSITIVE_INFINITY)
        t.positionChanged(1.5f)
        t.positionChanged(-0.1f)
        assertEquals(0L, t.progressCount)
        t.positionChanged(0.5f)
        assertEquals(1L, t.progressCount)
    }

    @Test fun repeatedRegressingTimestampCannotForgeNewTimeline() {
        val t = NativeVlcPlaybackTracker()
        t.start()
        t.playing()
        t.timeChanged(100_000L)
        t.timeChanged(1_000L)
        t.timeChanged(1_000L)
        t.timeChanged(1_000L)
        assertEquals(1L, t.progressCount)
        t.timeChanged(1_100L)
        assertEquals(1L, t.progressCount)
        t.timeChanged(1_200L)
        assertEquals(2L, t.progressCount)
    }

    @Test fun errorAfterEndCannotOverwriteTerminalState() {
        val t = NativeVlcPlaybackTracker()
        t.start()
        t.playing()
        t.ended()
        t.error()
        assertEquals(4, t.state)
    }

    @Test fun errorIsStickyAgainstEndAndPlaying() {
        val t = NativeVlcPlaybackTracker()
        t.start()
        t.error()
        t.ended()
        t.playing()
        assertEquals(3, t.state)
    }

    @Test fun resetClearsProgressAndAllowsNewSession() {
        val t = NativeVlcPlaybackTracker()
        t.start()
        t.playing()
        t.timeChanged(200)
        t.reset()
        assertEquals(0, t.state)
        assertEquals(0L, t.progressCount)
        t.start()
        t.playing()
        t.timeChanged(100)
        assertEquals(1L, t.progressCount)
    }
}
