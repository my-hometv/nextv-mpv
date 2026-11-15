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

    fun isValidMode(mode: String): Boolean = mode in setOf("Automatic", "Hardware copy", "Software")
}
