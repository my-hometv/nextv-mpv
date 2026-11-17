package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class ClockOverlayPolicyTest {
    private fun date(elapsed: Long, timeEnabled: Boolean = true, dateEnabled: Boolean = true) =
        ClockOverlayPolicy.showDate(elapsed, 10_000L, 5_000L, timeEnabled, dateEnabled)

    @Test fun startsWithTimeForTenSeconds() {
        assertFalse(date(0L))
        assertFalse(date(9999L))
        assertTrue(date(10_000L))
    }

    @Test fun switchesToDateForFiveSecondsThenLoops() {
        assertTrue(date(14_999L))
        assertFalse(date(15_000L))
        assertTrue(date(25_000L))
        assertFalse(date(30_000L))
    }

    @Test fun disablingComponentsKeepsRemainingOneVisible() {
        assertTrue(date(0L, timeEnabled = false))
        assertFalse(date(12_000L, dateEnabled = false))
    }

    @Test fun invalidDurationsAreClamped() {
        assertFalse(ClockOverlayPolicy.showDate(-100L, 0L, -1L, true, true))
        assertTrue(ClockOverlayPolicy.showDate(1000L, 0L, -1L, true, true))
    }
}
