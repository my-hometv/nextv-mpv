package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class RecoveryAttemptSessionTest {
    private fun session() = RecoveryAttemptSession<String>({ it }, { _, _ -> true })

    @Test fun paddedDuplicateUrlsAreTriedOnlyOncePerEngine() {
        val s = session()
        s.beginChannel(listOf(" stream ", "stream", "next", " next "),
            listOf("Media3", "LibVLC"))
        val attempts = mutableListOf<String>()
        while (true) {
            val attempt = s.next() ?: break
            attempts += attempt.engine + "/" + attempt.url.trim()
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(listOf("Media3/stream", "Media3/next",
            "LibVLC/stream", "LibVLC/next"), attempts)
    }

    @Test fun traversesAllThreeUrlsPerEngineWithoutRepeatingPairs() {
        val s = session()
        s.beginChannel(listOf("url1", "url2", "url3", "url1"), listOf("Media3", "LibVLC", "MPV"))
        val actual = mutableListOf<Pair<String, String>>()
        while (true) {
            val next = s.next() ?: break
            actual.add(next.url to next.engine)
            assertTrue(s.finish(next, false))
        }
        assertEquals(listOf(
            "url1" to "Media3", "url2" to "Media3", "url3" to "Media3",
            "url1" to "LibVLC", "url2" to "LibVLC", "url3" to "LibVLC",
            "url1" to "MPV", "url2" to "MPV", "url3" to "MPV"
        ), actual)
    }

    @Test fun blocksDuplicateConcurrentForegroundAndBackgroundReservations() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3"))
        val first = s.next()!!
        assertNull(s.next())
        assertTrue(s.finish(first, false))
        assertEquals("u2", s.next()!!.url)
    }

    @Test fun switchingChannelInvalidatesOldCallbacks() {
        val s = session()
        s.beginChannel(listOf("old"), listOf("Media3"))
        val old = s.next()!!
        s.beginChannel(listOf("new"), listOf("LibVLC"))
        assertFalse(s.finish(old, true))
        val fresh = s.next()!!
        assertEquals("new", fresh.url)
        assertEquals("LibVLC", fresh.engine)
        assertTrue(s.finish(fresh, true))
        assertFalse(s.finish(fresh, false))
    }

    @Test fun cancelInvalidatesPendingProbeAndDoesNotRepeatFailedPair() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3"))
        val pending = s.next()!!
        s.cancelPending()
        assertFalse(s.finish(pending, true))
        assertEquals("u2", s.next()!!.url)
    }

    @Test fun delimiterContainingUrlsDoNotCollideWithEngineKeys() {
        val s = session()
        s.beginChannel(listOf("a|b", "a"), listOf("c", "b|c"))
        val pairs = mutableSetOf<Pair<String, String>>()
        while (true) {
            val attempt = s.next() ?: break
            pairs.add(attempt.url to attempt.engine)
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(4, pairs.size)
    }

    @Test fun unsupportedAndDuplicateEnginesAreSkipped() {
        val s = RecoveryAttemptSession<String>({ it }, { url, engine -> url != "u1" || engine == "LibVLC" })
        s.beginChannel(listOf("u1", "u2"), listOf("Media3", "Media3", "LibVLC"))
        assertEquals("u2" to "Media3", s.next()!!.let { it.url to it.engine })
    }
    @Test fun successStopsTraversalUntilNewChannelOrRecoveryCycle() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3", "LibVLC"))
        val first = s.next()!!
        assertTrue(s.finish(first, true))
        assertNull(s.next())
        assertEquals(first, s.confirmedAttempt())
        s.beginChannel(listOf("u1", "u2"), listOf("Media3", "LibVLC"))
        assertNotNull(s.next())
    }

    @Test fun structurallyEqualForgedCallbackCannotCompleteReservedAttempt() {
        val s = session()
        s.beginChannel(listOf("u1"), listOf("Media3"))
        val reserved = s.next()!!
        val forged = RecoveryAttemptSession.Attempt(reserved.generation, reserved.url, reserved.engine)
        assertFalse(s.finish(forged, true))
        assertNull(s.next())
        assertTrue(s.finish(reserved, false))
    }

    @Test fun cancelledConfirmedSessionRejectsLateSuccessAndAllowsNextPair() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3"))
        val first = s.next()!!
        s.cancelPending()
        assertFalse(s.finish(first, true))
        assertEquals("u2", s.next()!!.url)
    }

    @Test fun brokenUrlExtractorDoesNotPreventHealthyFallback() {
        val s = RecoveryAttemptSession<String>(
            { if (it == "bad") error("malformed URL") else it },
            { _, _ -> true }
        )
        s.beginChannel(listOf("bad", "good"), listOf("Media3"))
        val attempt = s.next()!!
        assertEquals("good", attempt.url)
        assertTrue(s.finish(attempt, true))
    }

    @Test fun engineCapabilityExceptionSkipsOnlyTheBrokenPair() {
        val s = RecoveryAttemptSession<String>(
            { it },
            { url, engine -> if (url == "u1" && engine == "Media3") error("backend missing") else true }
        )
        s.beginChannel(listOf("u1", "u2"), listOf("Media3", "LibVLC"))
        val attempts = mutableListOf<Pair<String, String>>()
        while (true) {
            val attempt = s.next() ?: break
            attempts.add(attempt.url to attempt.engine)
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(listOf(
            "u2" to "Media3", "u1" to "LibVLC", "u2" to "LibVLC"
        ), attempts)
    }

    @Test fun blankEngineNamesCannotReservePlaybackOrBlockValidEngine() {
        val s = session()
        s.beginChannel(listOf("url1", "url2"), listOf("", "  ", "Media3", "", "Media3"))
        val seen = mutableListOf<Pair<String, String>>()
        while (true) {
            val attempt = s.next() ?: break
            seen += attempt.url to attempt.engine
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(listOf("url1" to "Media3", "url2" to "Media3"), seen)
        s.beginChannel(listOf("url1"), listOf("", "  "))
        assertNull(s.next())
    }

    @Test fun blankUrlIsNotDispatchedToNativeEngine() {
        val s = session()
        s.beginChannel(listOf("", "  ", "valid"), listOf("LibVLC"))
        assertEquals("valid", s.next()!!.url)
    }

    @Test fun timeoutAdvancesWithoutReusingPairOrAcceptingLateSuccess() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3"))
        val timedOut = s.next()!!
        assertTrue(s.expire(timedOut))
        assertFalse(s.finish(timedOut, true))
        assertFalse(s.expire(timedOut))
        val second = s.next()!!
        assertEquals("u2", second.url)
        assertTrue(s.finish(second, true))
    }

    @Test fun staleTimeoutCannotCancelAnotherChannelOrActiveProbe() {
        val s = session()
        s.beginChannel(listOf("old"), listOf("Media3"))
        val old = s.next()!!
        s.beginChannel(listOf("new"), listOf("LibVLC"))
        val current = s.next()!!
        assertFalse(s.expire(old))
        assertNull(s.next())
        assertTrue(s.finish(current, true))
        assertEquals(current, s.confirmedAttempt())
    }

    @Test fun forgedTimeoutCannotCancelReservedAttempt() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3"))
        val current = s.next()!!
        val forged = RecoveryAttemptSession.Attempt(current.generation, current.url, current.engine)
        assertFalse(s.expire(forged))
        assertNull(s.next())
        assertTrue(s.finish(current, false))
    }
    @Test fun recoveryCursorDoesNotReevaluatePreviouslyRejectedPairs() {
        val calls = mutableListOf<String>()
        val s = RecoveryAttemptSession<String>({ it }, { url, engine ->
            calls += engine + "/" + url
            true
        })
        s.beginChannel(listOf("u1", "u2", "u3"), listOf("Media3", "LibVLC"))
        val seen = mutableListOf<String>()
        while (true) {
            val attempt = s.next() ?: break
            seen += attempt.engine + "/" + attempt.url
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(listOf(
            "Media3/u1", "Media3/u2", "Media3/u3",
            "LibVLC/u1", "LibVLC/u2", "LibVLC/u3"
        ), seen)
        assertEquals(seen, calls)
        assertNull(s.next())
        assertEquals(seen, calls)
    }

    @Test fun recoveryCursorResetsOnChannelRestartButNotPendingCancellation() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3", "LibVLC"))
        val first = s.next()!!
        s.cancelPending()
        assertFalse(s.finish(first, true))
        assertEquals("u2" to "Media3", s.next()!!.let { it.url to it.engine })
        s.beginChannel(listOf("u1", "u2"), listOf("LibVLC"))
        val restarted = s.next()!!
        assertEquals("u1" to "LibVLC", restarted.url to restarted.engine)
        assertFalse(s.finish(first, true))
        assertTrue(s.finish(restarted, true))
    }

    @Test fun recoveryCursorSkipsMalformedAndDuplicateSourcesWithoutRevisitingThem() {
        var malformedCalls = 0
        val s = RecoveryAttemptSession<String>(
            { url ->
                if (url == "bad") {
                    malformedCalls++
                    error("malformed source")
                }
                url
            },
            { _, _ -> true }
        )
        s.beginChannel(listOf("bad", "u1", "u1", "u2"), listOf("Media3", "LibVLC"))
        val pairs = mutableListOf<String>()
        while (true) {
            val attempt = s.next() ?: break
            pairs += attempt.engine + "/" + attempt.url
            assertTrue(s.expire(attempt))
        }
        assertEquals(listOf("Media3/u1", "Media3/u2", "LibVLC/u1", "LibVLC/u2"), pairs)
        // Source resolution is cached for the entire recovery cycle, including failed URLs.
        assertEquals(1, malformedCalls)
    }

    @Test fun duplicateSourcesAreResolvedOnlyOncePerEntryAndNeverRetriedAsPairs() {
        val calls = mutableMapOf<String, Int>()
        val s = RecoveryAttemptSession<String>(
            { url ->
                calls[url] = (calls[url] ?: 0) + 1
                url
            },
            { _, _ -> true }
        )
        s.beginChannel(listOf("u1", "u1", "u2"), listOf("Media3", "LibVLC"))
        val pairs = mutableListOf<Pair<String, String>>()
        while (true) {
            val attempt = s.next() ?: break
            pairs += attempt.url to attempt.engine
            assertTrue(s.expire(attempt))
        }
        assertEquals(listOf(
            "u1" to "Media3", "u2" to "Media3",
            "u1" to "LibVLC", "u2" to "LibVLC"
        ), pairs)
        assertEquals(mapOf("u1" to 2, "u2" to 1), calls)
        assertNull(s.next())
        assertEquals(mapOf("u1" to 2, "u2" to 1), calls)
    }

    @Test fun sourceUrlsAreResolvedOnlyOnceAcrossMultipleEngines() {
        val counts = mutableMapOf<String, Int>()
        val s = RecoveryAttemptSession<String>(
            { url ->
                counts[url] = (counts[url] ?: 0) + 1
                url
            },
            { _, _ -> true }
        )
        s.beginChannel(listOf("u1", "u2", "u3"), listOf("Media3", "LibVLC", "MPV"))
        var attempts = 0
        while (true) {
            val attempt = s.next() ?: break
            attempts++
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(9, attempts)
        assertEquals(mapOf("u1" to 1, "u2" to 1, "u3" to 1), counts)
    }

    @Test fun failedUrlResolutionIsCachedUntilNextChannelCycle() {
        var calls = 0
        val s = RecoveryAttemptSession<String>(
            { url ->
                if (url == "broken") {
                    calls++
                    error("bad source")
                }
                url
            },
            { _, _ -> true }
        )
        val urls = listOf("broken", "working")
        s.beginChannel(urls, listOf("Media3", "LibVLC"))
        val seen = mutableListOf<String>()
        while (true) {
            val attempt = s.next() ?: break
            seen += attempt.engine + "/" + attempt.url
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(listOf("Media3/working", "LibVLC/working"), seen)
        assertEquals(1, calls)
        s.beginChannel(urls, listOf("Media3"))
        assertEquals("working", s.next()!!.url)
        assertEquals(2, calls)
    }

    @Test fun cancelledPendingAttemptPreservesCachedSourceAndAdvances() {
        var calls = 0
        val s = RecoveryAttemptSession<String>(
            { url -> calls++; url },
            { _, _ -> true }
        )
        s.beginChannel(listOf("same"), listOf("Media3", "LibVLC"))
        val old = s.next()!!
        s.cancelPending()
        assertFalse(s.finish(old, true))
        val fresh = s.next()!!
        assertEquals("LibVLC", fresh.engine)
        assertEquals(1, calls)
        assertTrue(s.finish(fresh, true))
    }

    @Test fun whitespacePaddedEngineNamesAreNormalizedAndDeduplicated() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf(" Media3 ", "Media3", " LibVLC ", " "))
        val seen = mutableListOf<Pair<String, String>>()
        while (true) {
            val attempt = s.next() ?: break
            seen += attempt.url to attempt.engine
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(listOf(
            "u1" to "Media3", "u2" to "Media3",
            "u1" to "LibVLC", "u2" to "LibVLC"
        ), seen)
    }

    @Test fun whitespacePaddedEngineSettingsDoNotInvalidateActivePlayback() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3", "LibVLC"))
        val active = s.next()!!
        assertFalse(s.retainEnabledEngines(listOf(" LibVLC ", " Media3 ")))
        assertTrue(s.finish(active, true))
        assertEquals(active, s.confirmedAttempt())
    }

    @Test fun disablingEngineUsingPaddedSettingsStillPreservesFallback() {
        val s = session()
        s.beginChannel(listOf("u1", "u2"), listOf("Media3", "LibVLC"))
        val old = s.next()!!
        assertTrue(s.retainEnabledEngines(listOf(" LibVLC ")))
        assertFalse(s.finish(old, true))
        val fallback = s.next()!!
        assertEquals("u1" to "LibVLC", fallback.url to fallback.engine)
        assertTrue(s.finish(fallback, true))
    }

    @Test fun disablingUnvisitedEnginePreservesCursorAndDoesNotReplayMedia3() {
        val s = session()
        s.beginChannel(listOf("one", "two"), listOf("Media3", "LibVLC", "MPV"))
        val first = s.next()!!
        assertTrue(s.finish(first, false))
        assertTrue(s.retainEnabledEngines(listOf("Media3", "MPV")))
        val actual = mutableListOf<Pair<String, String>>()
        while (true) {
            val next = s.next() ?: break
            actual += next.url to next.engine
            assertTrue(s.finish(next, false))
        }
        assertEquals(listOf(
            "two" to "Media3", "one" to "MPV", "two" to "MPV"
        ), actual)
    }

    @Test fun disablingActiveEngineInvalidatesItsCallbackAndContinuesFallback() {
        val s = session()
        s.beginChannel(listOf("one", "two"), listOf("Media3", "LibVLC"))
        val old = s.next()!!
        assertTrue(s.retainEnabledEngines(listOf("LibVLC")))
        assertFalse(s.finish(old, true))
        val next = s.next()!!
        assertEquals("LibVLC", next.engine)
        assertEquals("one", next.url)
        assertTrue(s.finish(next, true))
    }

    @Test fun retainingExistingEngineSetDoesNotInvalidatePendingAttempt() {
        val s = session()
        s.beginChannel(listOf("one"), listOf("Media3", "LibVLC"))
        val active = s.next()!!
        assertFalse(s.retainEnabledEngines(listOf("LibVLC", "Media3")))
        assertTrue(s.finish(active, true))
        assertEquals(active, s.confirmedAttempt())
    }

    @Test fun disablingConfirmedEngineClearsConfirmationWithoutReplayingAttempt() {
        val s = session()
        s.beginChannel(listOf("one"), listOf("Media3", "LibVLC"))
        val first = s.next()!!
        assertTrue(s.finish(first, true))
        assertTrue(s.retainEnabledEngines(listOf("LibVLC")))
        assertNull(s.confirmedAttempt())
        val fallback = s.next()!!
        assertEquals("LibVLC", fallback.engine)
        assertTrue(s.finish(fallback, true))
    }

    @Test fun disablingEveryEngineExhaustsRecoveryAndRejectsLateSuccess() {
        val s = session()
        s.beginChannel(listOf("one", "two"), listOf("Media3", "LibVLC"))
        val pending = s.next()!!
        assertTrue(s.retainEnabledEngines(emptyList()))
        assertFalse(s.finish(pending, true))
        assertNull(s.next())
        assertNull(s.confirmedAttempt())
    }

    @Test fun removingLaterEngineAfterPartialTraversalKeepsOnlyUnvisitedPairs() {
        val s = session()
        s.beginChannel(listOf("a", "b", "c"), listOf("Media3", "LibVLC", "MPV"))
        repeat(4) {
            val attempt = s.next()!!
            assertTrue(s.finish(attempt, false))
        }
        assertTrue(s.retainEnabledEngines(listOf("Media3", "LibVLC")))
        val remaining = mutableListOf<Pair<String, String>>()
        while (true) {
            val attempt = s.next() ?: break
            remaining += attempt.url to attempt.engine
            assertTrue(s.finish(attempt, false))
        }
        assertEquals(listOf("b" to "LibVLC", "c" to "LibVLC"), remaining)
    }

}
