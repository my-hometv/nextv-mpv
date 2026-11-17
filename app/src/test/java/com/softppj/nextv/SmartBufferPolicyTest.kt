package com.softppj.nextv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartBufferPolicyTest {
    @Test fun defaultsToResponsiveAndClampsCorruptLevels() {
        assertEquals(0, SmartBufferPolicy.DEFAULT_LEVEL)
        assertArrayEquals(intArrayOf(2000, 15000, 1000, 2000), SmartBufferPolicy.durations(SmartBufferPolicy.DEFAULT_LEVEL))
        assertEquals(0, SmartBufferPolicy.normalize(-99))
        assertEquals(3, SmartBufferPolicy.normalize(99))
        assertArrayEquals(intArrayOf(4000, 25000, 1500, 3500), SmartBufferPolicy.durations(1))
    }

    @Test fun repeatedStallsIncreaseOnlyAfterCooldown() {
        assertEquals(1, SmartBufferPolicy.nextOnStall(1, 1, 20000))
        assertEquals(1, SmartBufferPolicy.nextOnStall(1, 2, 11999))
        assertEquals(2, SmartBufferPolicy.nextOnStall(1, 2, 12000))
        assertEquals(3, SmartBufferPolicy.nextOnStall(3, 4, 90000))
    }

    @Test fun stablePlaybackGraduallyReducesBuffer() {
        assertEquals(2, SmartBufferPolicy.nextOnHealthy(2, 899999))
        assertEquals(1, SmartBufferPolicy.nextOnHealthy(2, 900000))
        assertEquals(0, SmartBufferPolicy.nextOnHealthy(0, 1800000))
    }

    @Test fun durationsRemainValidAtEveryLevel() {
        for (level in -2..6) {
            val values = SmartBufferPolicy.durations(level)
            assertEquals(4, values.size)
            assert(values[0] <= values[1])
            assert(values[2] <= values[3])
        }
    }
}
