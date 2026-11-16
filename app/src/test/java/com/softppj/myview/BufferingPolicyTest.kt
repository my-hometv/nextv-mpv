package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test

class BufferingPolicyTest {
    @Test fun onlyActiveLongBufferingTriggers() {
        assertFalse(BufferingPolicy.expired(true, true, 1000, 20000))
        assertTrue(BufferingPolicy.expired(true, true, 1000, 31000))
        assertFalse(BufferingPolicy.expired(false, true, 1000, 90000))
        assertFalse(BufferingPolicy.expired(true, false, 1000, 90000))
        assertFalse(BufferingPolicy.expired(true, true, -1, 90000))
        assertFalse(BufferingPolicy.expired(true, true, 50000, 1000))
    }
}
