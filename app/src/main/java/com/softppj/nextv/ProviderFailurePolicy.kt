package com.softppj.nextv

/**
 * Separates provider capacity limits from authorization and source errors.
 * Retries never change the selected channel, URL or playback engine.
 */
internal object ProviderFailurePolicy {
    enum class Kind { PROVIDER_BUSY, AUTHORIZATION, NETWORK, DECODER, DRM, SOURCE, UNKNOWN }

    fun classify(httpStatus: Int?, drmFailure: Boolean, decoderFailure: Boolean, networkFailure: Boolean): Kind {
        if (drmFailure) return Kind.DRM
        if (httpStatus == 401 || httpStatus == 403) return Kind.AUTHORIZATION
        if (httpStatus == 429 || httpStatus == 458 || httpStatus == 509 ||
            (httpStatus != null && httpStatus in 500..599)) return Kind.PROVIDER_BUSY
        if (decoderFailure) return Kind.DECODER
        if (networkFailure || httpStatus == 408) return Kind.NETWORK
        if (httpStatus != null && httpStatus in 400..499) return Kind.SOURCE
        return Kind.UNKNOWN
    }

    /** Null means no automatic retry. attempt is zero-based. */
    fun retryDelayMs(kind: Kind, attempt: Int): Long? {
        if (attempt !in 0..1) return null
        return when (kind) {
            Kind.PROVIDER_BUSY -> if (attempt == 0) 4000L else 9000L
            Kind.NETWORK, Kind.UNKNOWN -> if (attempt == 0) 2000L else 5000L
            else -> null
        }
    }

    fun description(kind: Kind): String = when (kind) {
        Kind.PROVIDER_BUSY -> "Provider busy; keeping this channel"
        Kind.AUTHORIZATION -> "Provider authorization required"
        Kind.NETWORK -> "Network interrupted"
        Kind.DECODER -> "This format may be unsupported"
        Kind.DRM -> "Protected content requires an authorized license"
        Kind.SOURCE -> "The source refused this request"
        Kind.UNKNOWN -> "Playback interrupted"
    }
}
