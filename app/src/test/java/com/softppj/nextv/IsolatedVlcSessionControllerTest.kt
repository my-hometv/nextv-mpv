package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class IsolatedVlcSessionControllerTest {
    private class Fake(val versionName: String) : IsolatedVlcRuntime {
        override val version = versionName
        override val engineId = PlaybackEnginePolicy.versionEngineId(version)!!
        var starts = 0
        var stops = 0
        var releases = 0
        override fun isAvailable() = true
        override fun start(url: String, generation: Long): Boolean { starts++; return true }
        override fun stop(generation: Long) { stops++ }
        override fun release() { releases++ }
    }

    @Test fun cancelledSurfaceStopCannotAffectNewerPlayback(){
        val runtime=Fake("3.5.1")
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(runtime)))
        assertTrue(controller.start(runtime.engineId,"https://example.invalid/a",8))
        assertNull(controller.detachStop(7))
        assertEquals(runtime.engineId,controller.activeEngineId())
        val old=controller.detachStop(8)
        assertNotNull(old)
        assertNull(controller.activeEngineId())
        assertTrue(controller.start(runtime.engineId,"https://example.invalid/b",9))
        old!!.invoke()
        assertEquals(runtime.engineId,controller.activeEngineId())
        assertNull(controller.detachStop(8))
        controller.stop(9)
        assertNull(controller.activeEngineId())
    }

    @Test fun detachedBinderStopCannotOverrideNewerGeneration(){
        val runtime=object:IsolatedVlcRuntime {
            override val version="3.5.1"
            override val engineId="LibVLC-3.5.1"
            var ownedGeneration=Long.MIN_VALUE
            override fun isAvailable()=true
            override fun start(url:String,generation:Long):Boolean {
                ownedGeneration=generation;return true
            }
            override fun stop(generation:Long){
                if(ownedGeneration==generation)ownedGeneration=Long.MIN_VALUE
            }
            override fun release()=Unit
        }
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(runtime)))
        assertTrue(controller.start(runtime.engineId,"https://example.invalid/first",100))
        val oldStop=controller.detachActiveStop()
        assertNull(controller.activeEngineId())
        // The viewer presses CH+ before the old native stop finishes.
        assertTrue(controller.start(runtime.engineId,"https://example.invalid/second",101))
        oldStop?.invoke()
        assertEquals(101L,runtime.ownedGeneration)
        assertEquals(runtime.engineId,controller.activeEngineId())
        assertEquals(101L,controller.latestRequestGeneration())
    }

    @Test fun blockedDetachedBinderStopDoesNotHoldControllerLock(){
        val entered=java.util.concurrent.CountDownLatch(1)
        val unblock=java.util.concurrent.CountDownLatch(1)
        val runtime=object:IsolatedVlcRuntime {
            override val version="3.5.1"
            override val engineId="LibVLC-3.5.1"
            override fun isAvailable()=true
            override fun start(url:String,generation:Long)=true
            override fun stop(generation:Long) {
                entered.countDown()
                unblock.await(3,java.util.concurrent.TimeUnit.SECONDS)
            }
            override fun release()=Unit
        }
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(runtime)))
        assertTrue(controller.start(runtime.engineId,"https://example.invalid/a",1))
        val old=controller.detachActiveStop()!!
        val worker=Thread{old.invoke()}
        worker.start()
        try{
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS))
            // This is the remote action while the native Binder stop is stuck.
            val pool=java.util.concurrent.Executors.newSingleThreadExecutor()
            try{
                val result=pool.submit<Boolean> {
                    controller.start(runtime.engineId,"https://example.invalid/b",2)
                }
                assertTrue(result.get(1,java.util.concurrent.TimeUnit.SECONDS))
            }finally{pool.shutdownNow()}
        }finally{
            unblock.countDown()
            worker.join(3000)
        }
    }

    @Test fun terminalReleaseDetachesOwnershipBeforeBlockingNativeWork(){
        val entered=java.util.concurrent.CountDownLatch(1)
        val unblock=java.util.concurrent.CountDownLatch(1)
        val runtime=object:IsolatedVlcRuntime {
            override val version="3.5.1"
            override val engineId="LibVLC-3.5.1"
            var releases=0
            override fun isAvailable()=true
            override fun start(url:String,generation:Long)=true
            override fun stop(generation:Long) {
                entered.countDown()
                unblock.await(3,java.util.concurrent.TimeUnit.SECONDS)
            }
            override fun release(){releases++}
        }
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(runtime)))
        assertTrue(controller.start(runtime.engineId,"https://example.invalid/live",41))
        val delayed=controller.detachRelease()
        assertNotNull(delayed)
        assertNull(controller.detachRelease())
        assertNull(controller.activeEngineId())
        assertFalse(controller.start(runtime.engineId,"https://example.invalid/stale",42))
        val worker=Thread{delayed!!.invoke()}
        worker.start()
        try {
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS))
            // Native stop is still waiting; the controller lock is available.
            val pool=java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                assertNull(pool.submit<String?>{controller.activeEngineId()}
                    .get(1,java.util.concurrent.TimeUnit.SECONDS))
            }finally{pool.shutdownNow()}
        }finally{
            unblock.countDown()
            worker.join(3000)
        }
        assertFalse(worker.isAlive)
        assertEquals(1,runtime.releases)
    }

    @Test fun terminalDetachReleasesIdleRegistryExactlyOnce(){
        val runtime=Fake("3.5.1")
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(runtime)))
        val cleanup=controller.detachRelease()
        assertNotNull(cleanup)
        assertNull(controller.detachRelease())
        cleanup!!.invoke()
        assertEquals(1,runtime.releases)
        assertFalse(controller.start(runtime.engineId,"https://example.invalid/a",1))
    }

    @Test fun activeEngineReflectsSuccessfulStartsAndStopsOnly() {
        val first=Fake("3.5.1")
        val second=Fake("3.7.7")
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(first,second)))
        assertNull(controller.activeEngineId())
        assertTrue(controller.start(first.engineId,"https://example.invalid/a",1))
        assertEquals(first.engineId,controller.activeEngineId())
        assertTrue(controller.start(second.engineId,"https://example.invalid/b",2))
        assertEquals(second.engineId,controller.activeEngineId())
        controller.stop(1)
        assertEquals(second.engineId,controller.activeEngineId())
        controller.stop(2)
        assertNull(controller.activeEngineId())
        controller.release()
        assertNull(controller.activeEngineId())
    }

    @Test fun unavailableReplacementPreservesActiveEngineOwnership() {
        val first=Fake("3.5.1")
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(first)))
        assertTrue(controller.start(first.engineId,"https://example.invalid/a",10))
        assertFalse(controller.start("LibVLC-4.0.0-eap29","https://example.invalid/b",11))
        assertEquals(first.engineId,controller.activeEngineId())
    }

    @Test fun failedReplacementCannotClaimActiveRuntime() {
        val original=Fake("3.5.1")
        val broken=object:IsolatedVlcRuntime {
            override val version="3.7.7"
            override val engineId="LibVLC-3.7.7"
            override fun isAvailable()=true
            override fun start(url:String,generation:Long)=false
            override fun stop(generation:Long)=Unit
            override fun release()=Unit
        }
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(original,broken)))
        assertTrue(controller.start(original.engineId,"https://example.invalid/first",20))
        assertFalse(controller.start(broken.engineId,"https://example.invalid/second",21))
        assertNull(controller.activeEngineId())
        assertEquals(1,original.stops)
        assertFalse(controller.start(original.engineId,"https://example.invalid/stale",20))
        assertTrue(controller.start(original.engineId,"https://example.invalid/retry",22))
        assertEquals(original.engineId,controller.activeEngineId())
    }

    @Test fun stopActiveClearsOwnershipWithoutReleasingController() {
        val runtime=Fake("4.0.0-eap29")
        val controller=IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(runtime)))
        assertTrue(controller.start(runtime.engineId,"https://example.invalid/first",5))
        controller.stopActive()
        assertNull(controller.activeEngineId())
        assertFalse(controller.start(runtime.engineId,"https://example.invalid/stale",5))
        assertTrue(controller.start(runtime.engineId,"https://example.invalid/new",6))
        assertEquals(runtime.engineId,controller.activeEngineId())
    }

    @Test fun switchingStopsPreviousRuntime() {
        val first = Fake("3.5.1")
        val second = Fake("3.7.7")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(first, second)))
        assertTrue(controller.start(first.engineId, "https://example.invalid/a", 1))
        assertTrue(controller.start(second.engineId, "https://example.invalid/b", 2))
        assertEquals(1, first.stops)
        assertEquals(1, second.starts)
    }

    @Test fun staleGenerationCannotReplaceOrStopCurrentSession() {
        val fake = Fake("3.5.1")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(fake)))
        assertTrue(controller.start(fake.engineId, "https://example.invalid/a", 5))
        assertFalse(controller.start(fake.engineId, "https://example.invalid/b", 4))
        controller.stop(4)
        assertEquals(0, fake.stops)
        controller.stop(5)
        assertEquals(1, fake.stops)
    }

    @Test fun missingRuntimeCannotInterruptActiveSession() {
        val fake = Fake("3.5.1")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(fake)))
        assertTrue(controller.start(fake.engineId, "https://example.invalid/a", 1))
        assertFalse(controller.start("LibVLC-4.0.0-eap29", "https://example.invalid/b", 2))
        assertEquals(0, fake.stops)
    }

    @Test fun failingStopDoesNotPreventSwitch() {
        val broken = object : IsolatedVlcRuntime {
            override val version = "3.5.1"
            override val engineId = "LibVLC-3.5.1"
            override fun isAvailable() = true
            override fun start(url: String, generation: Long) = true
            override fun stop(generation: Long) { throw IllegalStateException("stop failure") }
            override fun release() {}
        }
        val next = Fake("3.7.7")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(broken, next)))
        assertTrue(controller.start(broken.engineId, "https://example.invalid/a", 1))
        assertTrue(controller.start(next.engineId, "https://example.invalid/b", 2))
    }

    @Test fun failedStartCanBeRetriedWithNewGeneration() {
        val broken = object : IsolatedVlcRuntime {
            override val version = "3.5.1"
            override val engineId = "LibVLC-3.5.1"
            override fun isAvailable() = true
            override fun start(url: String, generation: Long): Boolean { throw IllegalStateException("start failure") }
            override fun stop(generation: Long) { throw IllegalStateException("cleanup failure") }
            override fun release() {}
        }
        val next = Fake("3.7.7")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(broken, next)))
        assertFalse(controller.start(broken.engineId, "https://example.invalid/a", 1))
        assertTrue(controller.start(next.engineId, "https://example.invalid/b", 2))
    }
    @Test fun stopActiveIsIdempotentAndRejectsStaleRestarts() {
        val fake = Fake("3.5.1")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(fake)))
        assertTrue(controller.start(fake.engineId, "https://example.invalid/a", 10))
        controller.stopActive()
        controller.stopActive()
        assertEquals(1, fake.stops)
        assertFalse(controller.start(fake.engineId, "https://example.invalid/old", 9))
        assertTrue(controller.start(fake.engineId, "https://example.invalid/new", 11))
        controller.stopActive()
        assertEquals(2, fake.stops)
    }
    @Test fun releasedControllerRejectsNewPlayback() {
        val fake = Fake("3.5.1")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(fake)))
        controller.release()
        assertFalse(controller.start(fake.engineId, "https://example.invalid/a", 1))
        assertEquals(0, fake.starts)
    }

    @Test fun releaseIsIdempotent() {
        val fake = Fake("3.5.1")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(fake)))
        assertTrue(controller.start(fake.engineId, "https://example.invalid/a", 1))
        controller.release()
        controller.release()
        assertEquals(1, fake.stops)
        assertEquals(1, fake.releases)
    }

    @Test fun releaseCleansUpAllRegisteredRuntimes() {
        val first = Fake("3.5.1")
        val second = Fake("3.7.7")
        val controller = IsolatedVlcSessionController(IsolatedVlcRegistry(listOf(first, second)))
        controller.start(first.engineId, "https://example.invalid/a", 1)
        controller.release()
        assertEquals(1, first.stops)
        assertEquals(1, first.releases)
        assertEquals(1, second.releases)
    }
}
