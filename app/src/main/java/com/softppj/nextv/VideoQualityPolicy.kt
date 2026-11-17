package com.softppj.nextv

/**
 * Preserve the full source quality range while letting adaptive streaming
 * choose a sustainable representation. Forcing peak bitrate causes stalls on
 * constrained networks and cannot improve fixed-bitrate sources.
 */
internal object VideoQualityPolicy {
    fun forceHighestBitrate(mode: String): Boolean = false

    /** Do not override YouTube's adaptive quality for Auto/Best available. */
    fun shouldRequestYoutubeWebQuality(preference: String): Boolean =
        preference == "720p" || preference == "1080p"

    /** Optional scans resume only after foreground work has fully stopped. */
    fun mayResumeIdleChecks(
        appForeground:Boolean, licensed:Boolean, foregroundActive:Boolean,
        teardown:Boolean, enabled:Boolean, safeStartup:Boolean
    ):Boolean = appForeground && licensed && !foregroundActive && !teardown &&
        enabled && !safeStartup

    /** Leave decoder capacity for the on-screen player. */
    @Suppress("UNUSED_PARAMETER")
    fun backgroundProbeLimit(
        foregroundActive: Boolean,
        foregroundBuffering: Boolean = false,
        foregroundNativeVlc: Boolean = false,
        foregroundStarting: Boolean = false
    ): Int =
        // No speculative decoder may compete with foreground video, even after
        // the initial buffering phase ends. Limit idle checks to one decoder.
        if (foregroundActive) 0 else 1
}
