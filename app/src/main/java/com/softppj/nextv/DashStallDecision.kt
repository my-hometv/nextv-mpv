package com.softppj.nextv

/** Side-effect-free recovery decision; does not bypass or alter DRM. */
internal object DashStallDecision {
    enum class Action { WAIT, DIAGNOSE, RECOVER }
    data class Snapshot(
        val bufferingMs: Long,
        val hasPlayedSuccessfully: Boolean,
        val bufferedAheadMs: Long,
        val positionAdvancedMs: Long,
        val isProtectedStream: Boolean,
        val hasDrmError: Boolean
    )

    fun decide(s: Snapshot): Action {
        // DRM failures need explicit diagnostics and authorized license configuration;
        // retrying other URLs or changing decoders cannot resolve missing entitlement.
        if (s.hasDrmError) return Action.DIAGNOSE
        if (s.bufferingMs < 20_000L) return Action.WAIT
        // A live stream with advancing position or buffered media is not a confirmed
        // permanent failure. Do not interrupt an active recovery/decoder attempt.
        if (s.positionAdvancedMs > 0L || s.bufferedAheadMs > 0L) return Action.DIAGNOSE
        if (s.isProtectedStream) return Action.DIAGNOSE
        return if (s.hasPlayedSuccessfully) Action.DIAGNOSE else Action.RECOVER
    }
}
