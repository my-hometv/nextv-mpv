package com.softppj.nextv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlayRecoveryTest {
    private val engines = listOf("Media3", "LibVLC", "MPV")

    @Test fun staysOnConfirmedEngineForFirstTwoFailures() {
        for (attempt in 0..2) {
            assertEquals("Media3", SmartPlayRecovery.nextEngine(true, null, "Media3", engines, attempt))
        }
    }

    @Test fun rotatesAlternativesEveryTwoFailures() {
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, null, "Media3", engines, 3))
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, null, "Media3", engines, 4))
        assertEquals("MPV", SmartPlayRecovery.nextEngine(true, null, "Media3", engines, 5))
        assertEquals("MPV", SmartPlayRecovery.nextEngine(true, null, "Media3", engines, 6))
        assertEquals("Media3", SmartPlayRecovery.nextEngine(true, null, "Media3", engines, 7))
    }

    @Test fun neverOverridesManualSelection() {
        assertEquals("Media3", SmartPlayRecovery.nextEngine(true, "Media3", "Media3", engines, 100))
    }

    @Test fun toggleDisablesEngineRotation() {
        assertEquals("Media3", SmartPlayRecovery.nextEngine(false, null, "Media3", engines, 100))
    }

    @Test fun handlesUnavailableAndDuplicateEngines() {
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, null, "Media3", listOf("Media3", "LibVLC", "LibVLC"), 3))
        assertEquals("Media3", SmartPlayRecovery.nextEngine(true, null, "Media3", listOf("Media3"), 10))
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, null, "Unknown", engines, 3))
    }

    @Test fun rotatesCorrectlyWhenConfirmedEngineIsNotFirst() {
        assertEquals("MPV", SmartPlayRecovery.nextEngine(true, null, "LibVLC", engines, 3))
        assertEquals("Media3", SmartPlayRecovery.nextEngine(true, null, "LibVLC", engines, 5))
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, null, "LibVLC", engines, 7))
    }

    @Test fun keepsManualEnginePinnedEvenIfNotInAvailableList() {
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, "MPV", "MPV", listOf("Media3", "LibVLC"), 20))
    }

    @Test fun handlesEmptyAndSingleEngineLists() {
        assertEquals("Media3", SmartPlayRecovery.nextEngine(true, null, "Media3", emptyList(), 10))
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, null, "Media3", listOf("LibVLC"), 10))
    }
    @Test fun disabledConfirmedEngineFallsBackToAvailableBackend() {
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, null, "MPV", listOf("LibVLC"), 1))
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(false, null, "MPV", listOf("LibVLC"), 8))
        assertEquals("Media3", SmartPlayRecovery.nextEngine(true, null, "MPV", listOf("Media3", "LibVLC"), 1))
    }

    @Test fun manualSelectionNeverForcesDisabledBackend() {
        assertEquals("Media3", SmartPlayRecovery.nextEngine(true, "MPV", "MPV", listOf("Media3"), 5))
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(true, "LibVLC", "Media3", engines, 8))
    }

    @Test fun blankAndDuplicateEnginesAreNeverSelectedForRecovery() {
        val available = listOf("", " ", "Media3", "Media3", "LibVLC", "")
        assertEquals("Media3", SmartPlayRecovery.nextEngine(
            true, null, "Media3", available, 1))
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(
            true, null, "Media3", available, 3))
        assertEquals("Media3", SmartPlayRecovery.nextEngine(
            true, " ", "Media3", available, 1))
        assertEquals("Media3", SmartPlayRecovery.nextEngine(
            true, null, "Media3", listOf("", " "), 10))
    }

    @Test fun paddedManualEngineRemainsPinnedAndCannotBeDuplicated() {
        val available = listOf(" Media3 ", "LibVLC", " LibVLC ", " ")
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(
            true, " LibVLC ", " Media3 ", available, 7))
        assertEquals("LibVLC", SmartPlayRecovery.nextEngine(
            true, null, " Media3 ", available, 3))
        assertEquals("Media3", SmartPlayRecovery.nextEngine(
            true, "Unknown", " Media3 ", available, 1))
    }

    @Test fun paddedUnavailableManualEngineCannotOverrideSafeFallback() {
        assertEquals("Media3", SmartPlayRecovery.nextEngine(
            true, " LibVLC-3.7.7 ", " Media3 ", listOf(" Media3 "), 5))
        assertEquals("Media3", SmartPlayRecovery.nextEngine(
            false, "Missing", " LibVLC ", listOf(" Media3 "), 3))
    }

    @Test fun automaticRecoveryStopsAtBoundedLimit() {
        assertFalse(SmartPlayRecovery.mayRetry(0))
        assertTrue(SmartPlayRecovery.mayRetry(1))
        assertTrue(SmartPlayRecovery.mayRetry(SmartPlayRecovery.MAX_AUTOMATIC_RECOVERY_ATTEMPTS))
        assertFalse(SmartPlayRecovery.mayRetry(SmartPlayRecovery.MAX_AUTOMATIC_RECOVERY_ATTEMPTS + 1))
    }
}
