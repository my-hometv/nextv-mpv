package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NativeVlcCleanupQueueTest {
    @Test fun busyNativeStopDoesNotBlockNextRemoteAction(){
        val queue=NativeVlcCleanupQueue()
        val started=CountDownLatch(1)
        val allowStop=CountDownLatch(1)
        val cleaned=CountDownLatch(1)
        try {
            assertTrue(queue.submit{
                started.countDown()
                allowStop.await(4,TimeUnit.SECONDS)
                cleaned.countDown()
            })
            assertTrue("native work must start",started.await(3,TimeUnit.SECONDS))
            // Simulates DPAD/BACK arriving while the native stop is blocked.
            val start=System.nanoTime()
            var dispatched=false
            dispatched=true
            val elapsedMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)
            assertTrue(dispatched)
            assertTrue("remote path must not wait on blocked native stop",elapsedMs<100)
            assertEquals("native stop remains blocked",1L,cleaned.count)
        }finally {
            allowStop.countDown()
            queue.shutdown()
        }
        assertTrue(cleaned.await(3,TimeUnit.SECONDS))
    }

    @Test fun rapidChannelReplacementReleasesOldSessionsInOrder(){
        val queue=NativeVlcCleanupQueue()
        val log=java.util.Collections.synchronizedList(mutableListOf<String>())
        val ended=CountDownLatch(3)
        try {
            listOf("A","B","C").forEach { channel ->
                assertTrue(queue.submit { log.add(channel); ended.countDown() })
            }
            assertTrue(ended.await(3,TimeUnit.SECONDS))
            assertEquals(listOf("A","B","C"),log.toList())
        } finally { queue.shutdown() }
    }

    @Test fun nativeStopFailureDoesNotLoseSubsequentCleanup(){
        val errors=AtomicInteger()
        val queue=NativeVlcCleanupQueue(onError={ errors.incrementAndGet() })
        val cleaned=CountDownLatch(1)
        try {
            assertTrue(queue.submit { throw IllegalStateException("decoder stop failed") })
            assertTrue(queue.submit { cleaned.countDown() })
            assertTrue(cleaned.await(3,TimeUnit.SECONDS))
            assertEquals(1,errors.get())
        }finally { queue.shutdown() }
    }

    @Test fun shutdownRejectsNewNativeSessionsAndDrainsAlreadyQueuedCleanup(){
        val queue=NativeVlcCleanupQueue()
        val released=CountDownLatch(1)
        assertTrue(queue.submit{released.countDown()})
        queue.shutdown()
        assertFalse(queue.submit { fail("cleanup should not run after shutdown") })
        assertTrue(released.await(3,TimeUnit.SECONDS))
    }
}
