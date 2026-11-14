package com.softppj.nextv

/** Decoder policy: copy-back default; software escape hatch for broken TV drivers. */
internal object MpvPlaybackPolicy {
    fun hardwareDecoder(mode: String): String = when(mode) {
        "Software" -> "no"
        else -> "mediacodec-copy"
    }
    fun isValidMode(mode: String): Boolean = mode in setOf("Automatic", "Hardware copy", "Software")
}
