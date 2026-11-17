package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class IsolatedVlcRegistryTest {
    private class Fake(
        override val version: String,
        private val ready: Boolean
    ) : IsolatedVlcRuntime {
        override val engineId = PlaybackEnginePolicy.versionEngineId(version)!!
        override fun isAvailable() = ready
        override fun start(url: String, generation: Long) = ready
        override fun stop(generation: Long) {}
        override fun release() {}
    }

    @Test fun absentRuntimesAreNeverAdvertised() {
        val registry = IsolatedVlcRegistry(emptyList())
        assertTrue(registry.verifiedIds().isEmpty())
        assertNull(registry.resolve("LibVLC-3.7.7"))
    }

    @Test fun onlyAvailableVersionResolves() {
        val registry = IsolatedVlcRegistry(listOf(Fake("3.5.1", true), Fake("3.7.7", false)))
        assertEquals(setOf("LibVLC-3.5.1"), registry.verifiedIds())
        assertNotNull(registry.resolve("LibVLC-3.5.1"))
        assertNull(registry.resolve("LibVLC-3.7.7"))
    }

    @Test fun failingRuntimeCheckCannotBreakEngineDiscovery() {
        val broken = object : IsolatedVlcRuntime {
            override val engineId = "LibVLC-3.7.7"
            override val version = "3.7.7"
            override fun isAvailable(): Boolean { throw IllegalStateException("service unavailable") }
            override fun start(url: String, generation: Long) = false
            override fun stop(generation: Long) {}
            override fun release() {}
        }
        val healthy = Fake("3.5.1", true)
        val registry = IsolatedVlcRegistry(listOf(broken, healthy))
        assertEquals(setOf("LibVLC-3.5.1"), registry.verifiedIds())
        assertNull(registry.resolve(broken.engineId))
        assertNotNull(registry.resolve(healthy.engineId))
    }

    @Test fun linkageFailureDuringReadinessDoesNotHideHealthyRuntime() {
        val broken = object : IsolatedVlcRuntime {
            override val engineId = "LibVLC-3.7.7"
            override val version = "3.7.7"
            override fun isAvailable(): Boolean { throw UnsatisfiedLinkError("native library missing") }
            override fun start(url: String, generation: Long) = false
            override fun stop(generation: Long) {}
            override fun release() {}
        }
        val healthy = Fake("3.5.1", true)
        val registry = IsolatedVlcRegistry(listOf(broken, healthy))
        assertEquals(setOf("LibVLC-3.5.1"), registry.verifiedIds())
        assertNull(registry.resolve(broken.engineId))
        assertNotNull(registry.resolve(healthy.engineId))
    }

    @Test fun linkageFailureDuringReleaseDoesNotLeakNextRuntime() {
        val first = object : IsolatedVlcRuntime {
            override val engineId = "LibVLC-3.5.1"
            override val version = "3.5.1"
            override fun isAvailable() = true
            override fun start(url: String, generation: Long) = true
            override fun stop(generation: Long) {}
            override fun release() { throw UnsatisfiedLinkError("native library missing") }
        }
        var secondReleased = 0
        val second = object : IsolatedVlcRuntime {
            override val engineId = "LibVLC-3.7.7"
            override val version = "3.7.7"
            override fun isAvailable() = true
            override fun start(url: String, generation: Long) = true
            override fun stop(generation: Long) {}
            override fun release() { secondReleased++ }
        }
        val registry = IsolatedVlcRegistry(listOf(first, second))
        registry.releaseAll()
        registry.releaseAll()
        assertEquals(1, secondReleased)
        assertTrue(registry.verifiedIds().isEmpty())
    }

    @Test fun releasedRegistryNeverAdvertisesEngines() {
        var releases = 0
        val runtime = object : IsolatedVlcRuntime {
            override val engineId = "LibVLC-3.5.1"
            override val version = "3.5.1"
            override fun isAvailable() = true
            override fun start(url: String, generation: Long) = true
            override fun stop(generation: Long) {}
            override fun release() { releases++ }
        }
        val registry = IsolatedVlcRegistry(listOf(runtime))
        assertNotNull(registry.resolve(runtime.engineId))
        registry.releaseAll()
        registry.releaseAll()
        assertEquals(1, releases)
        assertTrue(registry.verifiedIds().isEmpty())
        assertNull(registry.resolve(runtime.engineId))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateVersionIsRejected() {
        IsolatedVlcRegistry(listOf(Fake("3.5.1", true), Fake("3.5.1", true)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedIdentityIsRejected() {
        val bad = object : IsolatedVlcRuntime {
            override val engineId = "LibVLC-3.7.7"
            override val version = "3.5.1"
            override fun isAvailable() = true
            override fun start(url: String, generation: Long) = true
            override fun stop(generation: Long) {}
            override fun release() {}
        }
        IsolatedVlcRegistry(listOf(bad))
    }
}
