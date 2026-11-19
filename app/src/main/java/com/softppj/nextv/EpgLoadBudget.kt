package com.softppj.nextv

/** Monotonic overall budget for prioritized, sequential XMLTV downloads. */
internal object EpgLoadBudget {
    const val MAX_LOAD_MS=60_000L
    fun remainingMs(startMs:Long, nowMs:Long):Long =
        (MAX_LOAD_MS-(nowMs-startMs).coerceAtLeast(0L)).coerceAtLeast(0L)
    fun connectionTimeoutMs(remainingMs:Long):Int =
        remainingMs.coerceIn(1L,8000L).toInt()
    fun readTimeoutMs(remainingMs:Long):Int =
        remainingMs.coerceIn(1L,20000L).toInt()
}
