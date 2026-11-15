package com.softppj.nextv

import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class NativePlaybackGenerationAllocatorTest {
    @Test fun idsIncreaseWhenClockRepeats() {
        val ids = NativePlaybackGenerationAllocator({ 123L }, AtomicLong(Long.MIN_VALUE))
        assertEquals(123L, ids.next())
        assertEquals(124L, ids.next())
        assertEquals(125L, ids.next())
    }

    @Test fun idsNeverRegressWithClock() {
        val clock = listOf(100L, 90L, 105L, 104L).iterator()
        val ids = NativePlaybackGenerationAllocator({ clock.next() }, AtomicLong(Long.MIN_VALUE))
        assertEquals(listOf(100L, 101L, 105L, 106L), (1..4).map { ids.next() })
    }

    @Test fun separateActivityAllocatorsShareProcessHighWater() {
        val first = NativePlaybackGenerationAllocator(clock = { 500L })
        val second = NativePlaybackGenerationAllocator(clock = { 500L })
        val a = first.next()
        val b = second.next()
        assertTrue(b > a)
        assertTrue(first.next() > b)
    }

    @Test fun concurrentRequestsAreUnique() {
        val counter = AtomicLong(Long.MIN_VALUE)
        val ids = NativePlaybackGenerationAllocator({ 1000L }, counter)
        val values = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
        val threads = (1..16).map {
            Thread { repeat(50) { values.add(ids.next()) } }.also { it.start() }
        }
        threads.forEach { it.join(5000) }
        assertTrue(threads.none { it.isAlive })
        assertEquals(800, values.size)
    }

}
