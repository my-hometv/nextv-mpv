package com.softppj.nextv

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide native Binder request IDs. The isolated service may outlive
 * individual Activities; their request sequences must never start at zero.
 *
 * The allocator's clock is process monotonic and the high-water mark is
 * shared across all Activity instances in this app process.
 */
internal class NativePlaybackGenerationAllocator(
    private val clock: () -> Long = System::nanoTime,
    private val highWater: AtomicLong = sharedHighWater
) {
    companion object {
        private val sharedHighWater = AtomicLong(Long.MIN_VALUE)
    }

    fun next(): Long {
        while (true) {
            val previous = highWater.get()
            check(previous != Long.MAX_VALUE) { "Native request generation exhausted" }
            val candidate = clock()
            val next = if (candidate > previous) candidate else previous + 1L
            if (highWater.compareAndSet(previous, next)) return next
        }
    }
}
