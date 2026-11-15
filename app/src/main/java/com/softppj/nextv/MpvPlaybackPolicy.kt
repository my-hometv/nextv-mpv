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

    /**
     * Software decode is a one-time escape hatch for a first-tune failure.
     * Never override an explicit user decoder choice, or switch a previously
     * working channel after a brief signal loss.
     */
    fun shouldTrySoftwareAfterStartupFailure(
        selectedMode: String,
        previouslyConfirmed: Boolean,
        alreadyTriedSoftware: Boolean,
        playbackStarted: Boolean
    ): Boolean = selectedMode == "Automatic" && !previouslyConfirmed &&
        !alreadyTriedSoftware && !playbackStarted

    /**
     * Source metadata describes the decoded stream, not the Android display
     * pipeline. Never infer actual HDR output solely from HDR source metadata.
     */
    fun sourceDynamicRange(transfer: String, primaries: String): String {
        val t=transfer.trim().lowercase()
        val p=primaries.trim().lowercase()
        return when {
            t in setOf("pq", "smpte2084", "st2084", "smpte-st-2084") -> "HDR10/PQ source"
            t in setOf("hlg", "arib-std-b67", "arib-std-b67 (hlg)") -> "HLG HDR source"
            t in setOf("unknown", "", "auto", "n/a") -> "Unknown (no reliable transfer metadata)"
            t in setOf("bt.1886", "bt1886", "bt.709", "srgb", "gamma2.2", "gamma2.4") ->
                "SDR source"
            else -> "Unclassified transfer: " + transfer.trim().take(40)
        } + if(p == "bt.2020" || p == "bt2020") " / BT.2020 primaries" else ""
    }

    fun outputInterpretation(effectiveMode: String, hdr10Supported: Boolean): String = when {
        effectiveMode == "Force SDR" -> "SDR target requested; verify TV picture"
        effectiveMode == "Force HDR10" && hdr10Supported ->
            "HDR10 target requested; actual HDR output not verified"
        effectiveMode == "Auto (HDR10 unsupported)" ->
            "HDR10 override disabled; display did not report HDR10"
        else -> "Automatic output; actual HDR signalling not verified"
    }

    /**
     * A saved working URL is sufficient to suppress first-tune prompts.
     * Older installations may have a URL pin without a matching engine pin;
     * MPV is the only native engine in this edition.
     */
    fun isEstablishedStream(
        workingUrl: String?,
        manualUrl: String?,
        playedThisSession: Boolean
    ): Boolean = !workingUrl.isNullOrBlank() ||
        !manualUrl.isNullOrBlank() || playedThisSession

    fun isValidMode(mode: String): Boolean = mode in setOf("Automatic", "Hardware copy", "Software")
}
