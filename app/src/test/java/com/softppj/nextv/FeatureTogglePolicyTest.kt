package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class FeatureTogglePolicyTest {
    @Test fun backgroundNetworkAndHiddenScansAreOptInByDefault() {
        assertFalse(FeatureTogglePolicy.DEFAULT_AUTO_PLAYLIST_REFRESH)
        assertFalse(FeatureTogglePolicy.DEFAULT_AUTO_HIDDEN_CHANNEL_CHECK)
        assertFalse(FeatureTogglePolicy.DEFAULT_BACKGROUND_HIDDEN_CHANNEL_CHECK)
        assertFalse(FeatureTogglePolicy.shouldRunAutomaticRefresh(
            automatic=true,enabled=FeatureTogglePolicy.DEFAULT_AUTO_PLAYLIST_REFRESH))
    }

    @Test fun manualRefreshRemainsAvailableWithNewDefaults() {
        assertTrue(FeatureTogglePolicy.shouldRunAutomaticRefresh(
            automatic=false,enabled=FeatureTogglePolicy.DEFAULT_AUTO_PLAYLIST_REFRESH))
    }

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
