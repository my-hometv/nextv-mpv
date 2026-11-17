package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class ProviderFailurePolicyTest {
    @Test fun temporaryProviderCapacityNeverBecomesAuthorizationFailure() {
        for (code in listOf(429, 458, 500, 502, 503, 509)) {
            assertEquals(ProviderFailurePolicy.Kind.PROVIDER_BUSY,
                ProviderFailurePolicy.classify(code, false, false, false))
        }
    }

    @Test fun authenticationAndDrmErrorsNeverGetBusyRetries() {
        for (code in listOf(401, 403)) {
            val kind = ProviderFailurePolicy.classify(code, false, false, false)
            assertEquals(ProviderFailurePolicy.Kind.AUTHORIZATION, kind)
            assertNull(ProviderFailurePolicy.retryDelayMs(kind, 0))
        }
        val drm = ProviderFailurePolicy.classify(503, true, false, false)
        assertEquals(ProviderFailurePolicy.Kind.DRM, drm)
        assertNull(ProviderFailurePolicy.retryDelayMs(drm, 0))
    }

    @Test fun boundedRetryKeepsTemporaryFailuresOnSameSource() {
        val busy = ProviderFailurePolicy.Kind.PROVIDER_BUSY
        assertEquals(4000L, ProviderFailurePolicy.retryDelayMs(busy, 0))
        assertEquals(9000L, ProviderFailurePolicy.retryDelayMs(busy, 1))
        assertNull(ProviderFailurePolicy.retryDelayMs(busy, 2))
        assertNull(ProviderFailurePolicy.retryDelayMs(busy, -1))
        val net = ProviderFailurePolicy.Kind.NETWORK
        assertEquals(2000L, ProviderFailurePolicy.retryDelayMs(net, 0))
        assertEquals(5000L, ProviderFailurePolicy.retryDelayMs(net, 1))
        assertNull(ProviderFailurePolicy.retryDelayMs(net, 2))
    }

    @Test fun decoderAndPermanentSourceFailureDoNotTriggerBusyRetry() {
        for (kind in listOf(ProviderFailurePolicy.Kind.DECODER,
            ProviderFailurePolicy.Kind.SOURCE, ProviderFailurePolicy.Kind.AUTHORIZATION)) {
            assertNull(ProviderFailurePolicy.retryDelayMs(kind, 0))
        }
        assertEquals(ProviderFailurePolicy.Kind.DECODER,
            ProviderFailurePolicy.classify(null, false, true, false))
        assertEquals(ProviderFailurePolicy.Kind.SOURCE,
            ProviderFailurePolicy.classify(404, false, false, false))
    }
}
