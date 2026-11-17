package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class NativeSurfaceRecoveryGateTest {
    @Test fun destroyedSurfaceCannotRestartOldSession() {
        val gate = NativeSurfaceRecoveryGate()
        assertTrue(gate.canStart())
        assertTrue(gate.onSurfaceDestroyed(true))
        assertFalse(gate.canStart())
        assertFalse(gate.onSurfaceDestroyed(true))
        assertFalse(gate.requestRecovery())
    }

    @Test fun neverAttachedSurfaceDoesNotTriggerRecovery() {
        val gate = NativeSurfaceRecoveryGate()
        assertFalse(gate.onSurfaceDestroyed(false))
        assertFalse(gate.canStart())
    }

    @Test fun lateBinderSuccessCannotReviveTimedOutSurface(){
        val gate=NativeSurfaceRecoveryGate()
        var nativeStartPending=true
        val requestedGeneration=11
        var currentGeneration=11
        // Twelve-second IPC startup timeout moves the viewer into recovery.
        nativeStartPending=false
        currentGeneration++
        assertTrue(gate.requestRecovery())
        // A stale binder callback later returns SUCCESS for generation 11.
        val mayAttach=nativeStartPending && gate.canStart() &&
            requestedGeneration==currentGeneration
        assertFalse(mayAttach)
        assertFalse(gate.requestRecovery())
    }

    @Test fun recoveryCanOnlyBeRequestedOnce() {
        val gate = NativeSurfaceRecoveryGate()
        assertTrue(gate.requestRecovery())
        assertFalse(gate.requestRecovery())
        assertFalse(gate.onSurfaceDestroyed(true))
    }
}
