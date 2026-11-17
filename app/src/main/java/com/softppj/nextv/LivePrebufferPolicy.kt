package com.softppj.nextv

/**
 * Secondary live preview admission policy. Keep previewing best-effort:
 * the active foreground player has priority over speculative decoders.
 * Never let a prebuffer fetch cross a PIN or playlist access boundary.
 */
internal object LivePrebufferPolicy {
    /** Adoption is allowed only after a fully prepared, access-checked match. */
    fun mayAdopt(
        licenseReady: Boolean,
        correctIdentity: Boolean,
        ready: Boolean,
        engineIsMedia3: Boolean,
        unlocked: Boolean
    ): Boolean = licenseReady && correctIdentity && ready && engineIsMedia3 && unlocked

    fun mayStart(
        enabled: Boolean,
        licenseReady: Boolean,
        mainPlayerPlaying: Boolean,
        mainEngineIsMedia3: Boolean,
        previewEngineIsMedia3: Boolean,
        unlocked: Boolean,
        anotherDecoderSessionActive: Boolean,
        sameStream: Boolean
    ): Boolean =
        enabled && licenseReady && mainPlayerPlaying &&
            mainEngineIsMedia3 && previewEngineIsMedia3 && unlocked &&
            !anotherDecoderSessionActive && !sameStream
}
