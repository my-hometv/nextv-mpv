package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class BackgroundRecoveryScanTest {
    private fun scan() = BackgroundRecoveryScan<String>({ it }, { _, _ -> true })

    @Test fun checksAllUrlsOnMedia3BeforeTryingOtherEngineAndNextChannel() {
        val s = scan()
        s.start(listOf(
            BackgroundRecoveryScan.Channel("next1", listOf("u1", "u2", "u3")),
            BackgroundRecoveryScan.Channel("next2", listOf("v1"))
        ), listOf("Media3", "LibVLC"))
        val actual = mutableListOf<Pair<String, String>>()
        while (true) {
            val probe = s.next() ?: break
            actual.add(probe.channelId to (probe.attempt.engine + ":" + probe.attempt.url))
            assertTrue(s.finish(probe, false))
        }
        assertEquals(listOf(
            "next1" to "Media3:u1", "next1" to "Media3:u2", "next1" to "Media3:u3",
            "next1" to "LibVLC:u1", "next1" to "LibVLC:u2", "next1" to "LibVLC:u3",
            "next2" to "Media3:v1", "next2" to "LibVLC:v1"
        ), actual)
    }

    @Test fun discoversNextChannelEvenIfForegroundIsAlreadyPlaying() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("url"))), listOf("Media3"))
        val probe = s.next()!!
        assertTrue(s.finish(probe, true))
        assertEquals("next", s.discoveredChannelId())
        assertNull(s.next())
    }

    @Test fun channelSwitchRejectsStaleSuccessAndTimeout() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("old", listOf("oldUrl"))), listOf("Media3"))
        val old = s.next()!!
        s.start(listOf(BackgroundRecoveryScan.Channel("new", listOf("newUrl"))), listOf("Media3"))
        val fresh = s.next()!!
        assertFalse(s.finish(old, true))
        assertFalse(s.expire(old))
        assertNull(s.discoveredChannelId())
        assertTrue(s.finish(fresh, true))
        assertEquals("new", s.discoveredChannelId())
    }

    @Test fun timeoutAdvancesAndCancelledProbeCannotComplete() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))), listOf("Media3"))
        val old = s.next()!!
        assertTrue(s.expire(old))
        assertFalse(s.finish(old, true))
        val second = s.next()!!
        assertEquals("u2", second.attempt.url)
        s.cancel()
        assertFalse(s.finish(second, true))
        assertNull(s.discoveredChannelId())
        assertNull(s.next())
    }

    @Test fun duplicateChannelIdsAndInvalidUrlsDoNotCauseDuplicateProbes() {
        val s = scan()
        s.start(listOf(
            BackgroundRecoveryScan.Channel("a", listOf("", "good", "good")),
            BackgroundRecoveryScan.Channel("a", listOf("duplicate")),
            BackgroundRecoveryScan.Channel("b", listOf("next"))
        ), listOf("Media3", "Media3"))
        val seen = mutableListOf<String>()
        while (true) {
            val probe = s.next() ?: break
            seen.add(probe.channelId + ":" + probe.attempt.url)
            assertTrue(s.finish(probe, false))
        }
        assertEquals(listOf("a:good", "b:next"), seen)
    }
    @Test fun neverProbesCurrentlyPlayingChannelEvenIfIncludedByCaller() {
        val s = scan()
        s.start(listOf(
            BackgroundRecoveryScan.Channel("playing", listOf("current")),
            BackgroundRecoveryScan.Channel("next", listOf("nextUrl"))
        ), listOf("", "Media3", "Media3"), playingChannelId = "playing")
        val probe = s.next()!!
        assertEquals("next", probe.channelId)
        assertEquals("Media3", probe.attempt.engine)
        assertEquals("nextUrl", probe.attempt.url)
        assertTrue(s.finish(probe, true))
        assertEquals("next", s.discoveredChannelId())
    }

    @Test fun restartInvalidatesOutstandingProbeAndExcludesNewForeground() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("a", listOf("u1"))), listOf("Media3"))
        val old = s.next()!!
        s.start(listOf(
            BackgroundRecoveryScan.Channel("a", listOf("u1")),
            BackgroundRecoveryScan.Channel("b", listOf("u2"))
        ), listOf("Media3"), playingChannelId = "a")
        assertFalse(s.finish(old, true))
        assertFalse(s.expire(old))
        assertEquals("b", s.next()!!.channelId)
    }

    @Test fun malformedUrlAndUnavailableEngineDoNotStopNextChannelDiscovery() {
        val s = BackgroundRecoveryScan<String>(
            { if (it == "broken") error("bad URL") else it },
            { url, engine -> if (url == "unsupported" && engine == "Media3") error("backend unavailable") else true }
        )
        s.start(listOf(
            BackgroundRecoveryScan.Channel("first", listOf("broken", "unsupported")),
            BackgroundRecoveryScan.Channel("second", listOf("valid"))
        ), listOf("Media3"))
        val probe = s.next()!!
        assertEquals("second", probe.channelId)
        assertEquals("valid", probe.attempt.url)
        assertTrue(s.finish(probe, true))
        assertEquals("second", s.discoveredChannelId())
    }

    @Test fun forgedProbeCannotCompleteActiveReservation() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))), listOf("Media3"))
        val active = s.next()!!
        val forged = BackgroundRecoveryScan.Probe(active.channelId, active.attempt)
        assertFalse(s.finish(forged, true))
        assertFalse(s.expire(forged))
        assertNull(s.next())
        assertTrue(s.finish(active, false))
        assertEquals("u2", s.next()!!.attempt.url)
    }

    @Test fun cancellationClearsDiscoveryAndAllowsFreshScan() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("old", listOf("u1"))),
            listOf("Media3"))
        val stale = s.next()!!
        s.cancel()
        assertFalse(s.finish(stale, true))
        assertNull(s.discoveredProbe())
        assertNull(s.next())
        s.start(listOf(BackgroundRecoveryScan.Channel("new", listOf("u2"))),
            listOf("", "LibVLC"))
        val fresh = s.next()!!
        assertEquals("LibVLC", fresh.attempt.engine)
        assertTrue(s.finish(fresh, true))
        assertEquals("new", s.discoveredChannelId())
    }

    @Test fun noEnginesOrOnlyForegroundChannelProducesNoProbes() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("playing", listOf("u1"))),
            listOf("Media3"), playingChannelId = "playing")
        assertNull(s.next())
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u2"))), emptyList())
        assertNull(s.next())
        assertNull(s.discoveredChannelId())
    }

    @Test fun confirmedProbeRetainsExactEngineAndUrlUntilRestartOrCancel() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3", "LibVLC"))
        val failed = s.next()!!
        assertTrue(s.finish(failed, false))
        assertNull(s.discoveredProbe())
        val working = s.next()!!
        assertEquals("u2", working.attempt.url)
        assertEquals("Media3", working.attempt.engine)
        assertTrue(s.finish(working, true))
        assertSame(working, s.discoveredProbe())
        assertNull(s.next())
        s.start(listOf(BackgroundRecoveryScan.Channel("later", listOf("u3"))), listOf("Media3"))
        assertNull(s.discoveredProbe())
        val fresh = s.next()!!
        assertTrue(s.finish(fresh, true))
        assertEquals("later", s.discoveredProbe()!!.channelId)
        s.cancel()
        assertNull(s.discoveredProbe())
        assertNull(s.discoveredChannelId())
    }

    @Test fun staleCompletionCannotOverwriteConfirmedProbe() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("old", listOf("old"))), listOf("Media3"))
        val stale = s.next()!!
        s.start(listOf(BackgroundRecoveryScan.Channel("new", listOf("fresh"))), listOf("Media3"))
        val working = s.next()!!
        assertTrue(s.finish(working, true))
        assertFalse(s.finish(stale, true))
        assertSame(working, s.discoveredProbe())
    }

    @Test fun threeUrlsAreTriedOncePerEngineBeforeMovingToNextEngine() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("url1", "url2", "url3", "url2"))),
            listOf("Media3", "LibVLC-3.5.1", "MPV", "Media3"))
        val seen = mutableListOf<String>()
        while (true) {
            val probe = s.next() ?: break
            seen += probe.attempt.engine + "/" + probe.attempt.url
            assertTrue(s.finish(probe, false))
            assertFalse(s.finish(probe, true))
        }
        assertEquals(listOf(
            "Media3/url1", "Media3/url2", "Media3/url3",
            "LibVLC-3.5.1/url1", "LibVLC-3.5.1/url2", "LibVLC-3.5.1/url3",
            "MPV/url1", "MPV/url2", "MPV/url3"
        ), seen)
        assertNull(s.discoveredProbe())
    }

    @Test fun timedOutPairIsNotRetriedAndLateSuccessCannotReplaceDiscovery() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("url1", "url2", "url3"))),
            listOf("Media3", "LibVLC"))
        val timedOut = s.next()!!
        assertEquals("url1", timedOut.attempt.url)
        assertTrue(s.expire(timedOut))
        val failed = s.next()!!
        assertEquals("url2", failed.attempt.url)
        assertTrue(s.finish(failed, false))
        val working = s.next()!!
        assertEquals("url3", working.attempt.url)
        assertTrue(s.finish(working, true))
        assertFalse(s.finish(timedOut, true))
        assertFalse(s.expire(timedOut))
        assertSame(working, s.discoveredProbe())
        assertNull(s.next())
    }

    @Test fun engineSupportFailuresSkipOnlyTheUnsupportedPairAndPreserveTraversalOrder() {
        val s = BackgroundRecoveryScan<String>(
            { it },
            { url, engine ->
                if (url == "url2" && engine == "Media3") false
                else if (url == "url1" && engine == "LibVLC") error("engine rejected stream")
                else true
            }
        )
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("url1", "url2", "url3"))),
            listOf("Media3", "LibVLC"))
        val visited = mutableListOf<String>()
        while (true) {
            val probe = s.next() ?: break
            visited += probe.attempt.engine + "/" + probe.attempt.url
            assertTrue(s.finish(probe, false))
        }
        assertEquals(listOf(
            "Media3/url1", "Media3/url3",
            "LibVLC/url2", "LibVLC/url3"
        ), visited)
    }

    @Test fun restartingSameChannelAndUrlCannotAcceptPreviousGeneration() {
        val s = scan()
        val channels = listOf(BackgroundRecoveryScan.Channel("next", listOf("url1", "url2")))
        s.start(channels, listOf("Media3"))
        val previous = s.next()!!
        s.start(channels, listOf("Media3"))
        val current = s.next()!!
        assertEquals(previous.channelId, current.channelId)
        assertEquals(previous.attempt.url, current.attempt.url)
        assertFalse(s.finish(previous, true))
        assertFalse(s.expire(previous))
        assertNull(s.discoveredProbe())
        assertTrue(s.finish(current, true))
        assertSame(current, s.discoveredProbe())
    }

    @Test fun channelAndEngineInputsAreSnapshottedForInFlightScan() {
        val urls = mutableListOf("url1", "url2", "url3")
        val channels = mutableListOf(BackgroundRecoveryScan.Channel("next", urls))
        val engines = mutableListOf("Media3", "LibVLC")
        val s = scan()
        s.start(channels, engines)
        urls.clear()
        channels.clear()
        engines.clear()
        val seen = mutableListOf<String>()
        while (true) {
            val probe = s.next() ?: break
            seen += probe.attempt.engine + "/" + probe.attempt.url
            assertTrue(s.finish(probe, false))
        }
        assertEquals(listOf(
            "Media3/url1", "Media3/url2", "Media3/url3",
            "LibVLC/url1", "LibVLC/url2", "LibVLC/url3"
        ), seen)
    }

    @Test fun completedDiscoveryIsNotAffectedByInputMutationOrLateTimeout() {
        val urls = mutableListOf("url1", "url2")
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", urls)), listOf("Media3"))
        val expired = s.next()!!
        assertTrue(s.expire(expired))
        val working = s.next()!!
        assertTrue(s.finish(working, true))
        urls.clear()
        assertFalse(s.expire(expired))
        assertFalse(s.finish(expired, true))
        assertSame(working, s.discoveredProbe())
        assertEquals("url2", s.discoveredProbe()!!.attempt.url)
        assertNull(s.next())
    }

    @Test fun blankAndDuplicateChannelIdsCannotDisplaceLaterValidChannels() {
        val s = scan()
        s.start(listOf(
            BackgroundRecoveryScan.Channel("", listOf("invalid")),
            BackgroundRecoveryScan.Channel("playing", listOf("foreground")),
            BackgroundRecoveryScan.Channel("next", listOf("first")),
            BackgroundRecoveryScan.Channel("next", listOf("duplicate")),
            BackgroundRecoveryScan.Channel("later", listOf("second"))
        ), listOf("", "Media3", "Media3"), playingChannelId = "playing")
        val visited = mutableListOf<String>()
        while (true) {
            val probe = s.next() ?: break
            visited += probe.channelId + "/" + probe.attempt.url
            assertTrue(s.finish(probe, false))
        }
        assertEquals(listOf("next/first", "later/second"), visited)
        assertNull(s.discoveredChannelId())
    }

    @Test fun emptyFirstChannelDoesNotBlockDiscoveryOnLaterChannel() {
        val s = scan()
        s.start(listOf(
            BackgroundRecoveryScan.Channel("empty", emptyList()),
            BackgroundRecoveryScan.Channel("next", listOf("working"))
        ), listOf("Media3"))
        val probe = s.next()!!
        assertEquals("next", probe.channelId)
        assertEquals("working", probe.attempt.url)
        assertTrue(s.finish(probe, true))
        assertSame(probe, s.discoveredProbe())
        assertNull(s.next())
    }

    @Test fun restartAfterSuccessfulDiscoveryClearsOldResultAndRejectsOldProbe() {
        val s = scan()
        val channels = listOf(BackgroundRecoveryScan.Channel("same", listOf("url")))
        s.start(channels, listOf("Media3"))
        val old = s.next()!!
        assertTrue(s.finish(old, true))
        assertEquals("same", s.discoveredChannelId())
        s.start(channels, listOf("Media3"))
        assertNull(s.discoveredChannelId())
        assertNull(s.discoveredProbe())
        val fresh = s.next()!!
        assertFalse(s.finish(old, true))
        assertFalse(s.expire(old))
        assertNull(s.discoveredChannelId())
        assertTrue(s.finish(fresh, true))
        assertSame(fresh, s.discoveredProbe())
    }

    @Test fun activeProbeBlocksConcurrentReservationUntilFailureOrExpiry() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("url1", "url2", "url3"))),
            listOf("Media3"))
        val first = s.next()!!
        assertNull(s.next())
        assertTrue(s.finish(first, false))
        val second = s.next()!!
        assertEquals("url2", second.attempt.url)
        assertNull(s.next())
        assertTrue(s.expire(second))
        val third = s.next()!!
        assertEquals("url3", third.attempt.url)
        assertTrue(s.finish(third, true))
        assertEquals("next", s.discoveredChannelId())
        assertNull(s.next())
    }

    @Test fun disablingActiveEngineRejectsLateCallbackAndContinuesWithAllowedEngine() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3", "LibVLC"))
        val old = s.next()!!
        assertEquals("Media3", old.attempt.engine)
        assertTrue(s.retainEnabledEngines(listOf("LibVLC")))
        assertFalse(s.finish(old, true))
        assertFalse(s.expire(old))
        assertNull(s.discoveredChannelId())
        val fallback = s.next()!!
        assertEquals("LibVLC", fallback.attempt.engine)
        assertEquals("u1", fallback.attempt.url)
        assertTrue(s.finish(fallback, true))
        assertEquals("next", s.discoveredChannelId())
    }

    @Test fun disablingConfirmedEngineWithdrawsDiscoveryAndResumesFallback() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
            listOf("Media3", "LibVLC"))
        val old = s.next()!!
        assertTrue(s.finish(old, true))
        assertEquals("next", s.discoveredChannelId())
        assertTrue(s.retainEnabledEngines(listOf("LibVLC")))
        assertNull(s.discoveredChannelId())
        assertNull(s.discoveredProbe())
        val fallback = s.next()!!
        assertEquals("LibVLC", fallback.attempt.engine)
        assertTrue(s.finish(fallback, true))
        assertSame(fallback, s.discoveredProbe())
    }

    @Test fun paddedEngineNamesAreNormalizedForEveryChannel() {
        val s = scan()
        s.start(listOf(
            BackgroundRecoveryScan.Channel("one", listOf("u1", "u2")),
            BackgroundRecoveryScan.Channel("two", listOf("v1"))
        ), listOf(" Media3 ", "Media3", " LibVLC ", "LibVLC"))
        val visited = mutableListOf<String>()
        while (true) {
            val probe = s.next() ?: break
            visited += probe.channelId + "/" + probe.attempt.engine + "/" + probe.attempt.url
            assertTrue(s.finish(probe, false))
        }
        assertEquals(listOf(
            "one/Media3/u1", "one/Media3/u2",
            "one/LibVLC/u1", "one/LibVLC/u2",
            "two/Media3/v1", "two/LibVLC/v1"
        ), visited)
    }

    @Test fun paddedEngineAvailabilityKeepsActiveProbeAndConfirmedDiscovery() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
            listOf("Media3", "LibVLC"))
        val active = s.next()!!
        assertFalse(s.retainEnabledEngines(listOf(" Media3 ", " LibVLC ")))
        assertTrue(s.finish(active, true))
        assertSame(active, s.discoveredProbe())
        assertFalse(s.retainEnabledEngines(listOf("Media3", "LibVLC")))
        assertSame(active, s.discoveredProbe())
    }

    @Test fun disablingEngineWithPaddedNameWithdrawsDiscoveryAndContinues() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
            listOf("Media3", "LibVLC"))
        val old = s.next()!!
        assertTrue(s.finish(old, true))
        assertTrue(s.retainEnabledEngines(listOf(" LibVLC ")))
        assertNull(s.discoveredProbe())
        assertFalse(s.finish(old, true))
        val fallback = s.next()!!
        assertEquals("LibVLC", fallback.attempt.engine)
        assertTrue(s.finish(fallback, true))
        assertSame(fallback, s.discoveredProbe())
    }

    @Test fun paddedForegroundChannelIdIsExcludedFromBackgroundDiscovery() {
        val s = scan()
        s.start(listOf(
            BackgroundRecoveryScan.Channel(" sports ", listOf("playing")),
            BackgroundRecoveryScan.Channel("news", listOf("other"))
        ), listOf("Media3"), playingChannelId = "sports")
        val next = s.next()!!
        assertEquals("news", next.channelId)
        assertEquals("other", next.attempt.url)
        assertTrue(s.finish(next, true))
    }

    @Test fun paddedDuplicateChannelIdsAreNotProbedTwice() {
        val s = scan()
        s.start(listOf(
            BackgroundRecoveryScan.Channel("news", listOf("u1")),
            BackgroundRecoveryScan.Channel(" news ", listOf("duplicate")),
            BackgroundRecoveryScan.Channel("sports", listOf("u2"))
        ), listOf("Media3"))
        val visited = mutableListOf<String>()
        while (true) {
            val next = s.next() ?: break
            visited += next.attempt.url
            assertTrue(s.finish(next, false))
        }
        assertEquals(listOf("u1", "u2"), visited)
    }

    @Test fun unchangedEngineAvailabilityPreservesOutstandingProbe() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
            listOf("Media3", "LibVLC"))
        val active = s.next()!!
        assertFalse(s.retainEnabledEngines(listOf("LibVLC", "Media3")))
        assertTrue(s.finish(active, true))
    }

}
