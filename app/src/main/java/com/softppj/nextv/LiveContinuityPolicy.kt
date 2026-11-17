package com.softppj.nextv

/**
 * Live TV must not confuse one frozen decoder with a permanently dead source.
 * A single same-channel retry is allowed per short window; after that the
 * existing engine/URL recovery policy is allowed to decide.
 *
 * An audio underrun alone is NOT evidence of a lip-sync offset.
 */
internal object LiveContinuityPolicy {
    const val REARM_AFTER_MS = 120_000L

    fun shouldRetrySameChannel(
        isLive: Boolean,
        decoderFrozen: Boolean,
        currentSession: Boolean,
        autoReconnect: Boolean,
        nowMs: Long,
        previousRecoveryMs: Long?
    ): Boolean {
        if (!isLive || !decoderFrozen || !currentSession || !autoReconnect) return false
        if (previousRecoveryMs == null) return true
        if (nowMs < previousRecoveryMs) return false
        return nowMs - previousRecoveryMs >= REARM_AFTER_MS
    }

    /** Require sustained problems before reporting an audio stability concern. */
    fun audioUnderrunConcern(recentUnderruns: Int, windowMs: Long): Boolean =
        recentUnderruns >= 3 && windowMs in 1L..60_000L
}
