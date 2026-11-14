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

    @Test fun recoveryCanOnlyBeRequestedOnce() {
        val gate = NativeSurfaceRecoveryGate()
        assertTrue(gate.requestRecovery())
        assertFalse(gate.requestRecovery())
        assertFalse(gate.onSurfaceDestroyed(true))
    }
}
