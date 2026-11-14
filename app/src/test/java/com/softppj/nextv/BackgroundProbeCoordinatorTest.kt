package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class BackgroundProbeCoordinatorTest {
    private fun scan() = BackgroundRecoveryScan<String>({ it }, { _, _ -> true })

    @Test fun manualTimeoutCancelsScheduledLeaseWithoutDoubleNotification() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            var pending: BackgroundRecoveryScan.Probe<String>? = null
            var late: ((Boolean) -> Unit)? = null
            val results = mutableListOf<Boolean>()
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 5000,
                { probe, callback -> pending = probe; late = callback },
                { results += it }))
            assertTrue(coordinator.timeout(pending!!))
            late!!(true)
            assertTrue(results.isEmpty())
            assertNull(s.discoveredChannelId())
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 5000,
                { probe, callback ->
                    assertEquals("u2", probe.attempt.url)
                    callback(true)
                }, { results += it }))
            assertEquals(listOf(true), results)
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun coordinatorRestartSuppressesOldTimerAndNativeCallback() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            val coordinator = BackgroundProbeCoordinator(s)
            coordinator.restart(listOf(
                BackgroundRecoveryScan.Channel("old", listOf("oldUrl"))),
                listOf("Media3"))
            val oldResults = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            var late: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 40,
                { _, callback -> late = callback }, { oldResults.add(it) }))
            coordinator.restart(listOf(
                BackgroundRecoveryScan.Channel("new", listOf("newUrl"))),
                listOf("Media3"))
            late!!(true)
            Thread.sleep(100)
            assertTrue(oldResults.isEmpty())
            assertNull(s.discoveredChannelId())
            val currentResults = mutableListOf<Boolean>()
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { probe, callback ->
                    assertEquals("new", probe.channelId)
                    callback(true)
                }, { currentResults.add(it) }))
            assertEquals(listOf(true), currentResults)
            assertEquals("new", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun coordinatorRestartExcludesNewForegroundChannel() {
        val s = scan()
        val coordinator = BackgroundProbeCoordinator(s)
        coordinator.restart(listOf(
            BackgroundRecoveryScan.Channel("playing", listOf("u1")),
            BackgroundRecoveryScan.Channel("next", listOf("u2"))),
            listOf("Media3"), playingChannelId = "playing")
        assertTrue(coordinator.dispatch { probe, complete ->
            assertEquals("next", probe.channelId)
            complete(true)
        })
        assertEquals("next", s.discoveredChannelId())
    }

    @Test fun rejectedManualTimeoutDoesNotCancelCurrentTimedProbe() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            var current: BackgroundRecoveryScan.Probe<String>? = null
            var completion: ((Boolean) -> Unit)? = null
            val results = mutableListOf<Boolean>()
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 5000,
                { probe, callback -> current = probe; completion = callback },
                { results += it }))
            val forged = BackgroundRecoveryScan.Probe(current!!.channelId, current!!.attempt)
            assertFalse(coordinator.timeoutWithResult(forged) { assertFalse(it) })
            completion!!(true)
            assertEquals(listOf(true), results)
            assertEquals("next", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun paddedEngineSettingsDoNotCancelAnActiveTimedProbe() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
                listOf("Media3", "LibVLC"))
            val coordinator = BackgroundProbeCoordinator(s)
            var completion: ((Boolean) -> Unit)? = null
            val results = mutableListOf<Boolean>()
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 5000,
                { _, callback -> completion = callback }, { results += it }))
            assertFalse(coordinator.retainEnabledEngines(listOf(" Media3 ", " LibVLC ")))
            completion!!(true)
            assertEquals(listOf(true), results)
            assertEquals("next", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun disablingTimedEngineWithPaddedSettingsCancelsItsLateCallback() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3", "LibVLC"))
            val coordinator = BackgroundProbeCoordinator(s)
            var completion: ((Boolean) -> Unit)? = null
            val results = mutableListOf<Boolean>()
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 5000,
                { _, callback -> completion = callback }, { results += it }))
            assertTrue(coordinator.retainEnabledEngines(listOf(" LibVLC ")))
            completion!!(true)
            assertTrue(results.isEmpty())
            assertNull(s.discoveredChannelId())
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 5000,
                { probe, callback ->
                    assertEquals("LibVLC", probe.attempt.engine)
                    callback(true)
                }, { results += it }))
            assertEquals(listOf(true), results)
            assertEquals("next", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun asyncCallbackAdvancesThroughMedia3UrlsBeforeLibVlc() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2", "u3"))),
            listOf("Media3", "LibVLC"))
        val coordinator = BackgroundProbeCoordinator(s)
        val seen = mutableListOf<String>()
        var complete: ((Boolean) -> Unit)? = null
        repeat(4) {
            assertTrue(coordinator.dispatch { probe, callback ->
                seen.add(probe.attempt.engine + ":" + probe.attempt.url)
                complete = callback
            })
            assertFalse(coordinator.dispatch { _, _ -> error("overlapping probe") })
            complete!!(false)
        }
        assertEquals(listOf("Media3:u1", "Media3:u2", "Media3:u3", "LibVLC:u1"), seen)
    }

    @Test fun legacyDispatchRejectsDuplicateSuccessAfterFailure() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        var complete: ((Boolean) -> Unit)? = null
        assertTrue(coordinator.dispatch { _, callback -> complete = callback })
        complete!!(false)
        complete!!(true)
        assertNull(s.discoveredChannelId())
        assertTrue(coordinator.dispatch { probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        })
        assertEquals("next", s.discoveredChannelId())
    }

    @Test fun legacyDispatchConcurrentCompletionsCannotClaimAnotherProbe() {
        repeat(30) {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            var complete: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatch { _, callback -> complete = callback })
            val gate = java.util.concurrent.CountDownLatch(1)
            val workers = listOf(
                Thread { gate.await(); complete!!(false) },
                Thread { gate.await(); complete!!(true) }
            )
            workers.forEach { it.start() }
            gate.countDown()
            workers.forEach { it.join(5000); assertFalse(it.isAlive) }
            if (s.discoveredChannelId() == null) {
                assertEquals("u2", s.next()!!.attempt.url)
            } else {
                assertEquals("next", s.discoveredChannelId())
                assertNull(s.next())
            }
        }
    }

    @Test fun synchronousCheckerExceptionAdvancesWithoutCrashing() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        assertTrue(coordinator.dispatch { _, _ -> error("checker failed") })
        assertTrue(coordinator.dispatch { probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        })
        assertEquals("next", s.discoveredChannelId())
        assertFalse(coordinator.dispatch { _, _ -> error("should not dispatch") })
    }

    @Test fun staleCallbackAfterRestartCannotMarkOldChannelWorking() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("old", listOf("u1"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        var stale: ((Boolean) -> Unit)? = null
        assertTrue(coordinator.dispatch { _, callback -> stale = callback })
        s.start(listOf(BackgroundRecoveryScan.Channel("new", listOf("u2"))),
            listOf("Media3"))
        stale!!(true)
        assertNull(s.discoveredChannelId())
        assertTrue(coordinator.dispatch { probe, callback ->
            assertEquals("new", probe.channelId)
            callback(true)
        })
        assertEquals("new", s.discoveredChannelId())
    }

    @Test fun timeoutRejectsLateCompletionAndAdvances() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        var pending: BackgroundRecoveryScan.Probe<String>? = null
        var late: ((Boolean) -> Unit)? = null
        assertTrue(coordinator.dispatch { probe, callback ->
            pending = probe
            late = callback
        })
        assertTrue(coordinator.timeout(pending!!))
        late!!(true)
        assertNull(s.discoveredChannelId())
        assertTrue(coordinator.dispatch { probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        })
        assertEquals("next", s.discoveredChannelId())
    }
    @Test fun completionAwareDispatchRejectsDuplicateAndStaleResults() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        val accepted = mutableListOf<Boolean>()
        var complete: ((Boolean) -> Unit)? = null
        assertTrue(coordinator.dispatchWithResult({ _, callback -> complete = callback }) {
            accepted.add(it)
        })
        complete!!(false)
        complete!!(true)
        assertEquals(listOf(true), accepted)
        assertNull(s.discoveredChannelId())
        assertTrue(coordinator.dispatchWithResult({ probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        }) { accepted.add(it) })
        assertEquals("next", s.discoveredChannelId())
        assertEquals(listOf(true, true), accepted)
    }

    @Test fun completionAwareDispatchRejectsCallbackAfterCancellation() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        val accepted = mutableListOf<Boolean>()
        var late: ((Boolean) -> Unit)? = null
        assertTrue(coordinator.dispatchWithResult({ _, callback -> late = callback }) {
            accepted.add(it)
        })
        coordinator.cancel()
        late!!(true)
        assertEquals(listOf(false), accepted)
        assertNull(s.discoveredChannelId())
    }

    @Test fun acceptedTimeoutAdvancesAndLateTimeoutCannotExpireNextProbe() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))), listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        var first: BackgroundRecoveryScan.Probe<String>? = null
        var second: BackgroundRecoveryScan.Probe<String>? = null
        assertTrue(coordinator.dispatch { probe, _ -> first = probe })
        val results = mutableListOf<Boolean>()
        assertTrue(coordinator.timeoutWithResult(first!!) { results.add(it) })
        assertTrue(coordinator.dispatch { probe, _ -> second = probe })
        assertFalse(coordinator.timeoutWithResult(first!!) { results.add(it) })
        assertEquals(listOf(true, false), results)
        assertEquals("u2", second!!.attempt.url)
        assertTrue(coordinator.timeout(second!!))
    }

    @Test fun staleTimeoutAfterChannelSwitchDoesNotInterruptNewProbe() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("old", listOf("oldUrl"))), listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        var old: BackgroundRecoveryScan.Probe<String>? = null
        assertTrue(coordinator.dispatch { probe, _ -> old = probe })
        s.start(listOf(BackgroundRecoveryScan.Channel("new", listOf("newUrl"))), listOf("Media3"))
        var fresh: ((Boolean) -> Unit)? = null
        assertTrue(coordinator.dispatch { _, callback -> fresh = callback })
        assertFalse(coordinator.timeoutWithResult(old!!) { assertFalse(it) })
        fresh!!(true)
        assertEquals("new", s.discoveredChannelId())
    }

    @Test fun synchronousHostCallbackExceptionDoesNotCauseSecondCompletion() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        val accepted = mutableListOf<Boolean>()
        try {
            coordinator.dispatchWithResult({ _, callback -> callback(false) }) {
                accepted.add(it)
                error("host callback failed")
            }
            fail("host callback exception must remain visible")
        } catch (expected: IllegalStateException) {
            assertEquals("host callback failed", expected.message)
        }
        assertEquals(listOf(true), accepted)
        assertTrue(coordinator.dispatchWithResult({ probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        }) { accepted.add(it) })
        assertEquals(listOf(true, true), accepted)
        assertEquals("next", s.discoveredChannelId())
    }

    @Test fun checkerThrowBeforeCallbackIsReportedAsOneFailure() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        val accepted = mutableListOf<Boolean>()
        assertTrue(coordinator.dispatchWithResult({ _, _ ->
            error("network checker unavailable")
        }) { accepted.add(it) })
        assertEquals(listOf(true), accepted)
        assertNull(s.discoveredChannelId())
        assertTrue(coordinator.dispatchWithResult({ probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        }) { accepted.add(it) })
        assertEquals(listOf(true, true), accepted)
    }

    @Test fun crossThreadCompletionIsVisibleBeforeCheckerThrows() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        val accepted = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        try {
            coordinator.dispatchWithResult({ _, callback ->
                val worker = Thread { callback(false) }
                worker.start()
                worker.join()
                error("checker threw after asynchronous completion")
            }) { accepted.add(it) }
            fail("checker exception after completion must propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("checker threw after asynchronous completion", expected.message)
        }
        assertEquals(listOf(true), accepted.toList())
        assertTrue(coordinator.dispatchWithResult({ probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        }) { accepted.add(it) })
        assertEquals("next", s.discoveredChannelId())
        assertEquals(listOf(true, true), accepted.toList())
    }

    @Test fun legacyDispatchDoesNotMaskCheckerErrorAfterSynchronousCompletion() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        try {
            coordinator.dispatch { _, callback ->
                callback(false)
                error("post-completion checker error")
            }
            fail("post-completion exception must propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("post-completion checker error", expected.message)
        }
        assertTrue(coordinator.dispatch { probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        })
        assertEquals("next", s.discoveredChannelId())
    }

    @Test fun legacyDispatchStillTreatsPreCompletionCheckerExceptionAsFailure() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        assertTrue(coordinator.dispatch { _, _ -> error("checker unavailable") })
        assertTrue(coordinator.dispatch { probe, callback ->
            assertEquals("u2", probe.attempt.url)
            callback(true)
        })
        assertEquals("next", s.discoveredChannelId())
    }

    @Test fun racingTimeoutAndSuccessAcceptExactlyOneOutcome() {
        repeat(40) {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            var pending: BackgroundRecoveryScan.Probe<String>? = null
            var completion: ((Boolean) -> Unit)? = null
            val accepted = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            assertTrue(coordinator.dispatchWithResult({ probe, callback ->
                pending = probe
                completion = callback
            }) { accepted.add(it) })
            val ready = java.util.concurrent.CountDownLatch(1)
            val start = java.util.concurrent.CountDownLatch(1)
            val successThread = Thread {
                ready.countDown()
                start.await()
                completion!!(true)
            }
            val timeoutThread = Thread {
                ready.countDown()
                start.await()
                coordinator.timeoutWithResult(pending!!) { accepted.add(it) }
            }
            successThread.start()
            timeoutThread.start()
            start.countDown()
            successThread.join(5000)
            timeoutThread.join(5000)
            assertFalse(successThread.isAlive)
            assertFalse(timeoutThread.isAlive)
            // The timeout API reports rejection explicitly when success won;
            // only one completion may be accepted, regardless of callback order.
            assertTrue(accepted.size in 1..2)
            assertEquals(1, accepted.count { it })
            if (s.discoveredChannelId() == null) {
                assertEquals("u2", s.next()!!.attempt.url)
            } else {
                assertEquals("next", s.discoveredChannelId())
                assertNull(s.next())
            }
        }
    }

    @Test fun repeatedFailuresCannotRetrySameUrlEnginePairAcrossDuplicateSources() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next",
            listOf("url1", "url1", "url2", "url3", "url2"))),
            listOf("Media3", "LibVLC", "Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        val seen = mutableListOf<String>()
        while (coordinator.dispatch { probe, callback ->
            seen.add(probe.attempt.engine + ":" + probe.attempt.url)
            callback(false)
        }) { /* all attempts fail */ }
        assertEquals(listOf(
            "Media3:url1", "Media3:url2", "Media3:url3",
            "LibVLC:url1", "LibVLC:url2", "LibVLC:url3"
        ), seen)
        assertNull(s.discoveredChannelId())
    }

    @Test fun concurrentDuplicateCompletionsCannotClaimTwoResults() {
        repeat(30) {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            val accepted = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            var complete: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithResult({ _, callback -> complete = callback }) {
                accepted.add(it)
            })
            val gate = java.util.concurrent.CountDownLatch(1)
            val workers = listOf(Thread { gate.await(); complete!!(true) },
                Thread { gate.await(); complete!!(false) })
            workers.forEach { it.start() }
            gate.countDown()
            workers.forEach { it.join(5000); assertFalse(it.isAlive) }
            // Duplicate checker callbacks are deliberately suppressed rather
            // than notifying the host twice (even with accepted=false).
            assertEquals(1, accepted.size)
            assertEquals(1, accepted.count { it })
            if (s.discoveredChannelId() == null) {
                assertEquals("u2", s.next()!!.attempt.url)
            } else {
                assertEquals("next", s.discoveredChannelId())
                assertNull(s.next())
            }
        }
    }

    @Test fun cancellationRaceCannotConfirmOrAdvanceSupersededScan() {
        repeat(40) {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("old", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            val accepted = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            var complete: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithResult({ _, callback -> complete = callback }) {
                accepted.add(it)
            })
            val gate = java.util.concurrent.CountDownLatch(1)
            val cancelThread = Thread { gate.await(); coordinator.cancel() }
            val callbackThread = Thread { gate.await(); complete!!(true) }
            cancelThread.start()
            callbackThread.start()
            gate.countDown()
            cancelThread.join(5000)
            callbackThread.join(5000)
            assertFalse(cancelThread.isAlive)
            assertFalse(callbackThread.isAlive)
            assertEquals(1, accepted.size)
            assertNull(s.discoveredChannelId())
            assertNull(s.discoveredProbe())
            assertNull(s.next())
            s.start(listOf(BackgroundRecoveryScan.Channel("new", listOf("fresh"))),
                listOf("Media3"))
            assertTrue(coordinator.dispatchWithResult({ probe, callback ->
                assertEquals("new", probe.channelId)
                callback(true)
            }) { assertTrue(it) })
            assertEquals("new", s.discoveredChannelId())
        }
    }

    @Test fun restartedSameChannelSameUrlRejectsOldCompletionAndTimeout() {
        val s = scan()
        val channels = listOf(BackgroundRecoveryScan.Channel("next", listOf("url1", "url2")))
        val coordinator = BackgroundProbeCoordinator(s)
        s.start(channels, listOf("Media3"))
        var oldProbe: BackgroundRecoveryScan.Probe<String>? = null
        var oldCompletion: ((Boolean) -> Unit)? = null
        val accepted = mutableListOf<Boolean>()
        assertTrue(coordinator.dispatchWithResult({ probe, callback ->
            oldProbe = probe
            oldCompletion = callback
        }) { accepted.add(it) })
        s.start(channels, listOf("Media3"))
        var currentCompletion: ((Boolean) -> Unit)? = null
        assertTrue(coordinator.dispatchWithResult({ probe, callback ->
            assertEquals(oldProbe!!.channelId, probe.channelId)
            assertEquals(oldProbe!!.attempt.url, probe.attempt.url)
            currentCompletion = callback
        }) { accepted.add(it) })
        oldCompletion!!(true)
        assertFalse(coordinator.timeoutWithResult(oldProbe!!) { accepted.add(it) })
        assertEquals(listOf(false, false), accepted)
        assertNull(s.discoveredChannelId())
        currentCompletion!!(true)
        assertEquals(listOf(false, false, true), accepted)
        assertEquals("next", s.discoveredChannelId())
    }

    @Test fun duplicateCompletionAndTimeoutCannotSkipNextEngineUrlPair() {
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("url1", "url2", "url3"))),
            listOf("Media3", "LibVLC"))
        val coordinator = BackgroundProbeCoordinator(s)
        val accepted = mutableListOf<Boolean>()
        var first: BackgroundRecoveryScan.Probe<String>? = null
        var complete: ((Boolean) -> Unit)? = null
        assertTrue(coordinator.dispatchWithResult({ probe, callback ->
            first = probe
            complete = callback
        }) { accepted.add(it) })
        complete!!(false)
        complete!!(false)
        assertFalse(coordinator.timeoutWithResult(first!!) { accepted.add(it) })
        assertEquals(listOf(true, false), accepted)
        val remaining = mutableListOf<String>()
        while (coordinator.dispatchWithResult({ probe, callback ->
            remaining += probe.attempt.engine + "/" + probe.attempt.url
            callback(false)
        }) { assertTrue(it) }) { /* complete all remaining pairs */ }
        assertEquals(listOf(
            "Media3/url2", "Media3/url3",
            "LibVLC/url1", "LibVLC/url2", "LibVLC/url3"
        ), remaining)
        assertNull(s.discoveredChannelId())
    }

    @Test fun scheduledTimeoutAdvancesAndRejectsLateCompletion() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            val done = java.util.concurrent.CountDownLatch(1)
            val accepted = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            var late: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 20,
                { _, callback -> late = callback },
                { accepted.add(it); done.countDown() }))
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
            late!!(true)
            assertEquals(listOf(true), accepted.toList())
            assertNull(s.discoveredChannelId())
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { probe, callback ->
                    assertEquals("u2", probe.attempt.url)
                    callback(true)
                }, { accepted.add(it) }))
            assertEquals("next", s.discoveredChannelId())
            assertEquals(listOf(true, true), accepted.toList())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun synchronousSuccessCancelsTimeoutAndDuplicateCallbacks() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            val accepted = mutableListOf<Boolean>()
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { _, callback -> callback(true); callback(false) },
                { accepted.add(it) }))
            assertEquals(listOf(true), accepted)
            assertEquals("next", s.discoveredChannelId())
            assertNull(s.next())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun timedProbeCheckerExceptionAfterSuccessDoesNotFabricateFailure() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            val accepted = mutableListOf<Boolean>()
            try {
                coordinator.dispatchWithTimeout(scheduler, 1000,
                    { _, callback ->
                        callback(true)
                        error("checker failed after accepted completion")
                    }, { accepted.add(it) })
                fail("post-completion checker error must propagate")
            } catch (expected: IllegalStateException) {
                assertEquals("checker failed after accepted completion", expected.message)
            }
            assertEquals(listOf(true), accepted)
            assertEquals("next", s.discoveredChannelId())
            assertNull(s.next())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun timedProbeDuplicateCompletionCannotAdvanceNextUrl() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            val accepted = mutableListOf<Boolean>()
            var complete: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { _, callback -> complete = callback }, { accepted.add(it) }))
            complete!!(false)
            complete!!(true)
            assertEquals(listOf(true), accepted)
            assertNull(s.discoveredChannelId())
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { probe, callback ->
                    assertEquals("u2", probe.attempt.url)
                    callback(true)
                }, { accepted.add(it) }))
            assertEquals(listOf(true, true), accepted)
            assertEquals("next", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun timeoutDispatchRejectsInvalidDeadlineWithoutConsumingProbe() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            try {
                coordinator.dispatchWithTimeout(scheduler, 0, { _, _ -> }, { })
                fail("non-positive timeout must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertEquals("timeoutMillis must be positive", expected.message)
            }
            assertEquals("u1", s.next()!!.attempt.url)
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun timedProbeSuccessAndScheduledTimeoutCannotBothAdvanceScan() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            repeat(25) {
                val s = scan()
                s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                    listOf("Media3", "LibVLC"))
                val coordinator = BackgroundProbeCoordinator(s)
                val accepted = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
                val completed = java.util.concurrent.CountDownLatch(1)
                var completion: ((Boolean) -> Unit)? = null
                assertTrue(coordinator.dispatchWithTimeout(scheduler, 15,
                    { _, callback -> completion = callback },
                    { accepted.add(it); completed.countDown() }))
                val worker = Thread { Thread.sleep(15); completion!!(true) }
                worker.start()
                assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                worker.join(5000)
                assertFalse(worker.isAlive)
                assertEquals(listOf(true), accepted.toList())
                if (s.discoveredChannelId() == null) {
                    assertEquals("u2", s.next()!!.attempt.url)
                } else {
                    assertEquals("next", s.discoveredChannelId())
                    assertNull(s.next())
                }
            }
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun rejectedTimeoutSchedulerFailsProbeAndAllowsNextUrl() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        scheduler.shutdownNow()
        val s = scan()
        s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
            listOf("Media3"))
        val coordinator = BackgroundProbeCoordinator(s)
        val results = mutableListOf<Boolean>()
        var checkerInvoked = false
        assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
            { _, _ -> checkerInvoked = true }, { results.add(it) }))
        assertFalse(checkerInvoked)
        assertEquals(listOf(true), results)
        assertNull(s.discoveredChannelId())
        assertEquals("u2", s.next()!!.attempt.url)
    }

    @Test fun timedCheckerFailureBeforeCallbackAdvancesExactlyOnce() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            val results = mutableListOf<Boolean>()
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { _, _ -> error("checker failed before completion") },
                { results.add(it) }))
            assertEquals(listOf(true), results)
            assertNull(s.discoveredChannelId())
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { probe, callback ->
                    assertEquals("u2", probe.attempt.url)
                    callback(true)
                }, { results.add(it) }))
            assertEquals(listOf(true, true), results)
            assertEquals("next", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun cancellationSuppressesScheduledTimeoutAndLateSuccess() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("old", listOf("u1"))),
                listOf("Media3"))
            val coordinator = BackgroundProbeCoordinator(s)
            val accepted = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            var late: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 100,
                { _, callback -> late = callback },
                { accepted.add(it) }))
            coordinator.cancel()
            s.start(listOf(BackgroundRecoveryScan.Channel("new", listOf("u2"))),
                listOf("Media3"))
            late!!(true)
            Thread.sleep(180)
            assertTrue(accepted.isEmpty())
            assertNull(s.discoveredChannelId())
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { probe, callback ->
                    assertEquals("new", probe.channelId)
                    callback(true)
                }, { accepted.add(it) }))
            assertEquals(listOf(true), accepted.toList())
            assertEquals("new", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun cancelledTimedProbeCannotCompleteRestartedChannelWithSameUrl() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            val coordinator = BackgroundProbeCoordinator(s)
            val channels = listOf(BackgroundRecoveryScan.Channel("same", listOf("url1", "url2")))
            val oldResults = mutableListOf<Boolean>()
            var stale: ((Boolean) -> Unit)? = null
            s.start(channels, listOf("Media3"))
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { _, callback -> stale = callback }, { oldResults.add(it) }))
            coordinator.cancel()
            s.start(channels, listOf("Media3"))
            val currentResults = mutableListOf<Boolean>()
            var current: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { probe, callback ->
                    assertEquals("url1", probe.attempt.url)
                    current = callback
                }, { currentResults.add(it) }))
            stale!!(true)
            stale!!(false)
            assertTrue(oldResults.isEmpty())
            assertTrue(currentResults.isEmpty())
            assertNull(s.discoveredChannelId())
            current!!(true)
            assertEquals(listOf(true), currentResults)
            assertEquals("same", s.discoveredChannelId())
            assertEquals("url1", s.discoveredProbe()!!.attempt.url)
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun cancellingMultipleScheduledProbesDoesNotCompleteEitherGeneration() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            val coordinator = BackgroundProbeCoordinator(s)
            val accepted = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            val callbacks = mutableListOf<(Boolean) -> Unit>()
            repeat(2) { index ->
                s.start(listOf(BackgroundRecoveryScan.Channel("channel$index", listOf("u$index"))),
                    listOf("Media3"))
                assertTrue(coordinator.dispatchWithTimeout(scheduler, 100,
                    { _, callback -> callbacks.add(callback) },
                    { accepted.add(it) }))
            }
            coordinator.cancel()
            callbacks.forEach { it(true) }
            Thread.sleep(180)
            assertTrue(accepted.isEmpty())
            assertNull(s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun disablingTimedEngineSuppressesLateResultAndAllowsFallback() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1", "u2"))),
                listOf("Media3", "LibVLC"))
            val coordinator = BackgroundProbeCoordinator(s)
            val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            var stale: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 100,
                { probe, callback ->
                    assertEquals("Media3", probe.attempt.engine)
                    stale = callback
                }, { results.add(it) }))
            assertTrue(coordinator.retainEnabledEngines(listOf("LibVLC")))
            stale!!(true)
            Thread.sleep(180)
            assertTrue(results.isEmpty())
            assertNull(s.discoveredChannelId())
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { probe, callback ->
                    assertEquals("LibVLC", probe.attempt.engine)
                    assertEquals("u1", probe.attempt.url)
                    callback(true)
                }, { results.add(it) }))
            assertEquals(listOf(true), results.toList())
            assertEquals("next", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun retainingSameEnginesKeepsTimedProbeAlive() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val s = scan()
            s.start(listOf(BackgroundRecoveryScan.Channel("next", listOf("u1"))),
                listOf("Media3", "LibVLC"))
            val coordinator = BackgroundProbeCoordinator(s)
            val results = mutableListOf<Boolean>()
            var completion: ((Boolean) -> Unit)? = null
            assertTrue(coordinator.dispatchWithTimeout(scheduler, 1000,
                { _, callback -> completion = callback }, { results.add(it) }))
            assertFalse(coordinator.retainEnabledEngines(listOf("LibVLC", "Media3")))
            completion!!(true)
            assertEquals(listOf(true), results)
            assertEquals("next", s.discoveredChannelId())
        } finally {
            scheduler.shutdownNow()
        }
    }

}
