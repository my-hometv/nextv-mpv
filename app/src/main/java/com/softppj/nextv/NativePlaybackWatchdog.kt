package com.softppj.nextv

/**
 * Deterministic native playback watchdog. Progress means advancing media time
 * or position, not merely receiving a native Playing event.
 *
 * Terminal outcomes are sticky for the lifetime of a session; a late callback
 * cannot turn an already-failed generation into a healthy one. A definitive
 * native Ended event may refine an earlier generic Failed event.
 */
internal class NativePlaybackWatchdog(
    private val startedAtMs: Long,
    private val startupTimeoutMs: Long = 15_000L,
    private val firstProgressTimeoutMs: Long = 20_000L,
    private val stallTimeoutMs: Long = 30_000L
) {
    enum class Result { WAITING, PLAYING, STARTUP_TIMEOUT, FIRST_PROGRESS_TIMEOUT, STALLED, ENDED, FAILED }

    init {
        require(startupTimeoutMs > 0L && firstProgressTimeoutMs > 0L && stallTimeoutMs > 0L)
    }

    private var firstPlayingAtMs: Long? = null
    private var lastProgressAtMs: Long? = null
    private var lastProgressCount = 0L
    private var lastObservedAtMs = startedAtMs
    private var terminal: Result? = null

    val hasObservedProgress: Boolean get() = lastProgressAtMs != null

    fun check(state: Int, progress: Long, nowMs: Long): Result {
        // elapsedRealtime should never go backwards. Ignore a stale sample
        // rather than moving a timeout deadline or terminal state backward.
        if (nowMs < lastObservedAtMs) return terminal ?: Result.WAITING
        lastObservedAtMs = nowMs

        terminal?.let { existing ->
            // Native Ended is a more definitive terminal outcome than a
            // generic Failed callback. Never allow a terminal session to
            // return to a playing/waiting state.
            if (existing == Result.FAILED && state == 4) return finish(Result.ENDED)
            return existing
        }

        if (state == 0 || state == 3) return finish(Result.FAILED)
        if (state == 4) return finish(Result.ENDED)
        if (state != 1 && state != 2) return finish(Result.FAILED)

        if (state == 2 && firstPlayingAtMs == null) firstPlayingAtMs = nowMs
        if (state == 2 && progress > lastProgressCount) {
            lastProgressCount = progress
            lastProgressAtMs = nowMs
        }

        val firstPlaying = firstPlayingAtMs
        if (firstPlaying == null) {
            return if (elapsedSince(startedAtMs, nowMs) >= startupTimeoutMs)
                finish(Result.STARTUP_TIMEOUT) else Result.WAITING
        }
        val lastProgress = lastProgressAtMs
        if (lastProgress == null) {
            return if (elapsedSince(firstPlaying, nowMs) >= firstProgressTimeoutMs)
                finish(Result.FIRST_PROGRESS_TIMEOUT) else Result.PLAYING
        }
        return if (elapsedSince(lastProgress, nowMs) >= stallTimeoutMs)
            finish(Result.STALLED) else Result.PLAYING
    }

    private fun finish(result: Result): Result {
        terminal = result
        return result
    }

    // Saturates rather than overflowing for extreme elapsed-time values.
    private fun elapsedSince(start: Long, now: Long): Long =
        if (now < start) 0L else {
            val delta = now - start
            if (delta < 0L) Long.MAX_VALUE else delta
        }
}
