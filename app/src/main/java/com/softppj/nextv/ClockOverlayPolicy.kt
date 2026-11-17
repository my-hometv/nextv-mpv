package com.softppj.nextv

/**
 * Clock display rotation uses monotonic elapsed time so time/date pages do not
 * jump backward if the wall clock changes. Time is the first page by default.
 */
internal object ClockOverlayPolicy {
    fun showDate(
        elapsedMs: Long,
        timeDurationMs: Long,
        dateDurationMs: Long,
        timeEnabled: Boolean,
        dateEnabled: Boolean
    ): Boolean {
        if (!dateEnabled) return false
        if (!timeEnabled) return true
        val time = timeDurationMs.coerceAtLeast(1000L)
        val date = dateDurationMs.coerceAtLeast(1000L)
        return elapsedMs.coerceAtLeast(0L) % (time + date) >= time
    }
}
