package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test

class ChannelHealthTest {
    @Test fun noMisleadingPercentageForFewSamples() {
        assertNull(ChannelHealth.observedStartPercent(1, 0))
        assertNull(ChannelHealth.observedStartPercent(1, 1))
        assertEquals("Not enough startup observations", ChannelHealth.label(0, 0))
    }
    @Test fun observedRateUsesActualStartupAttempts() {
        assertEquals(67, ChannelHealth.observedStartPercent(2, 1))
        assertEquals(100, ChannelHealth.observedStartPercent(4, 0))
        assertEquals(0, ChannelHealth.observedStartPercent(0, 3))
        assertTrue(ChannelHealth.label(2, 1).contains("n=3"))
    }
    @Test fun invalidCountersDoNotProduceProbability() {
        assertNull(ChannelHealth.observedStartPercent(-1, 3))
        assertNull(ChannelHealth.observedStartPercent(3, -1))
    }
}
