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

    /** Leave decoder capacity for the on-screen player. */
    fun backgroundProbeLimit(
        foregroundActive: Boolean,
        foregroundBuffering: Boolean = false,
        foregroundNativeVlc: Boolean = false,
        foregroundStarting: Boolean = false
    ): Int =
        when {
            foregroundActive && (foregroundBuffering || foregroundNativeVlc ||
                foregroundStarting) -> 0
            foregroundActive -> 1
            else -> 2
        }
}
