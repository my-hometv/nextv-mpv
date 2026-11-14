package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class FeatureTogglePolicyTest {
    @Test fun automaticRefreshStopsWhenToggleIsOff() {
        assertFalse(FeatureTogglePolicy.shouldRunAutomaticRefresh(true, false))
    }

    @Test fun automaticRefreshRunsWhenToggleIsOn() {
        assertTrue(FeatureTogglePolicy.shouldRunAutomaticRefresh(true, true))
    }

    @Test fun manualRefreshWorksRegardlessOfAutomaticToggle() {
        assertTrue(FeatureTogglePolicy.shouldRunAutomaticRefresh(false, false))
        assertTrue(FeatureTogglePolicy.shouldRunAutomaticRefresh(false, true))
    }
}
