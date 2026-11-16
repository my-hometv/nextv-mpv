package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class IsolatedVlcRequestGateTest {
    @Test fun onlyKnownVersionIdsAreAccepted() {
        val gate=IsolatedVlcRequestGate()
        for (engine in listOf("LibVLC-3.5.1","LibVLC-3.7.7","LibVLC-4.0.0-eap29"))
            assertTrue(gate.acceptsEngine(engine))
        for (engine in listOf(null,"","LibVLC","Media3","LibVLC-unknown","libvlc-3.5.1"))
            assertFalse(gate.acceptsEngine(engine))
    }
    @Test fun staleStartsAndStopsCannotTakeOverNewerRequests() {
        val gate=IsolatedVlcRequestGate()
        assertTrue(gate.reserveStart("LibVLC-3.5.1","https://example.invalid/a",10))
        assertFalse(gate.reserveStart("LibVLC-3.7.7","https://example.invalid/b",9))
        assertFalse(gate.reserveStart("LibVLC-3.7.7","https://example.invalid/b",10))
        assertTrue(gate.reserveStart("LibVLC-3.7.7","https://example.invalid/b",11))
        assertFalse(gate.stop(10))
        assertTrue(gate.stop(11))
        assertFalse(gate.stop(11)) // A stopped generation is no longer active.
        assertFalse(gate.reserveStart("LibVLC-3.5.1","https://example.invalid/a",11))
    }
    @Test fun malformedRequestsDoNotConsumeGenerations() {
        val gate=IsolatedVlcRequestGate()
        assertFalse(gate.reserveStart("Media3","https://example.invalid/a",20))
        assertFalse(gate.reserveStart("LibVLC-3.5.1"," ",20))
        assertTrue(gate.reserveStart("LibVLC-3.5.1","https://example.invalid/a",20))
    }
    @Test fun newStartSupersedesPriorActiveStop() {
        val gate = IsolatedVlcRequestGate()
        assertTrue(gate.reserveStart("LibVLC-3.5.1", "https://example.invalid/a", 100))
        assertTrue(gate.reserveStart("LibVLC-3.7.7", "https://example.invalid/b", 101))
        assertFalse(gate.stop(100))
        assertTrue(gate.stop(101))
        assertFalse(gate.stop(101))
        assertTrue(gate.reserveStart("LibVLC-4.0.0-eap29", "https://example.invalid/c", 102))
        assertTrue(gate.stop(102))
    }

    @Test fun releaseIsTerminalAndIdempotent() {
        val gate=IsolatedVlcRequestGate()
        assertTrue(gate.reserveStart("LibVLC-4.0.0-eap29","https://example.invalid/a",1))
        gate.release()
        gate.release()
        assertFalse(gate.acceptsEngine("LibVLC-4.0.0-eap29"))
        assertFalse(gate.stop(1))
        assertFalse(gate.reserveStart("LibVLC-4.0.0-eap29","https://example.invalid/b",2))
    }
    @Test fun invalidRequestsCannotInvalidateActiveSession() {
        val gate = IsolatedVlcRequestGate()
        assertTrue(gate.reserveStart("LibVLC-3.5.1", "https://example.invalid/one", 40))
        assertFalse(gate.reserveStart("Media3", "https://example.invalid/two", 100))
        assertFalse(gate.reserveStart("LibVLC-3.7.7", "   ", 100))
        assertTrue(gate.stop(40))
        assertTrue(gate.reserveStart("LibVLC-3.7.7", "https://example.invalid/three", 41))
    }

    @Test fun concurrentStartRequestsCannotReuseTheSameGeneration() {
        val gate = IsolatedVlcRequestGate()
        val accepted = java.util.concurrent.atomic.AtomicInteger()
        val ready = java.util.concurrent.CountDownLatch(8)
        val start = java.util.concurrent.CountDownLatch(1)
        val workers = (1..8).map {
            Thread {
                ready.countDown()
                start.await()
                if (gate.reserveStart("LibVLC-3.5.1", "https://example.invalid/live", 50)) {
                    accepted.incrementAndGet()
                }
            }.also { it.start() }
        }
        assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
        start.countDown()
        workers.forEach { it.join(5000) }
        assertTrue(workers.none { it.isAlive })
        assertEquals(1, accepted.get())
        assertTrue(gate.stop(50))
        assertFalse(gate.stop(50))
    }

    @Test fun releaseInvalidatesActiveGenerationAndFutureStarts() {
        val gate = IsolatedVlcRequestGate()
        assertTrue(gate.reserveStart("LibVLC-4.0.0-eap29", "https://example.invalid/live", 70))
        gate.release()
        assertFalse(gate.stop(70))
        assertFalse(gate.reserveStart("LibVLC-4.0.0-eap29", "https://example.invalid/new", 71))
    }

    @Test fun gateRejectsOutOfOrderGenerationsAcrossEngineSwitches() {
        val gate = IsolatedVlcRequestGate()
        assertTrue(gate.reserveStart("LibVLC-3.5.1", "https://example.invalid/a", 500))
        assertTrue(gate.reserveStart("LibVLC-3.7.7", "https://example.invalid/b", 501))
        assertFalse(gate.stop(500))
        assertFalse(gate.reserveStart("LibVLC-4.0.0-eap29", "https://example.invalid/c", 501))
        assertTrue(gate.stop(501))
        assertFalse(gate.stop(501))
        assertTrue(gate.reserveStart("LibVLC-4.0.0-eap29", "https://example.invalid/c", 502))
    }

}
