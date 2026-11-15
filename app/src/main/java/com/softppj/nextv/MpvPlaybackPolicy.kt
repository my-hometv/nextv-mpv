package com.softppj.nextv

/** Decoder policy: copy-back default; software escape hatch for broken TV drivers. */
internal object MpvPlaybackPolicy {
    fun hardwareDecoder(mode: String): String = when(mode) {
        "Software" -> "no"
        else -> "mediacodec-copy"
    }
    /** Explicit transport-stream resources only; never force HLS manifests. */
    fun isExplicitTransportStream(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?')
        return path.substringAfterLast('/').endsWith(".ts", ignoreCase = true)
    }

    /**
     * Never request a PQ output target when Android does not advertise HDR10.
     * Keep the saved user preference intact so moving to an HDR-capable TV
     * can enable it without silently rewriting settings.
     */
    fun effectiveOutputMode(requested: String, hdr10Supported: Boolean): String = when {
        requested == "Force HDR10" && !hdr10Supported -> "Auto (HDR10 unsupported)"
        requested in setOf("Auto", "Force HDR10", "Force SDR") -> requested
        else -> "Auto"
    }

    fun isValidMode(mode: String): Boolean = mode in setOf("Automatic", "Hardware copy", "Software")
}
