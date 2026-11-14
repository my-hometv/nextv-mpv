package com.softppj.nextv

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

/**
 * Human-style acceptance scenarios for the Android TV policy layer.
 * These do not claim real device rendering, remote focus or native decoding.
 */
class HumanAcceptanceJourneyTest {
    @Test fun oneChannelSurfingDoesNotRestartAnAttachedPlayer() {
        assertFalse(HomeTvPolicies.shouldRetuneSurfingChannel(4, 4, true))
        assertTrue(HomeTvPolicies.shouldRetuneSurfingChannel(4, 4, false))
        assertTrue(HomeTvPolicies.shouldRetuneSurfingChannel(4, 5, true))
    }

    @Test fun viewerCanNavigateAndActivateWithCommonTvRemotes() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT)) {
            assertTrue(HomeTvPolicies.isRemoteSelectKey(key))
        }
        assertTrue(HomeTvPolicies.isFreshRemotePress(KeyEvent.ACTION_DOWN, 0, false))
        assertFalse(HomeTvPolicies.isFreshRemotePress(KeyEvent.ACTION_UP, 0, false))
        assertFalse(HomeTvPolicies.isFreshRemotePress(KeyEvent.ACTION_DOWN, 2, false))
        assertFalse(HomeTvPolicies.isFreshRemotePress(KeyEvent.ACTION_DOWN, 0, true))
        assertTrue(HomeTvPolicies.isRemoteBackKey(KeyEvent.KEYCODE_BACK))
        assertTrue(HomeTvPolicies.isRemoteBackKey(KeyEvent.KEYCODE_ESCAPE))
    }

    @Test fun viewerCanUseDrawerWithoutAccidentallySeekingLiveTv() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT,
            HomeTvPolicies.drawerNavigationKey(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT,
            HomeTvPolicies.drawerNavigationKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertTrue(HomeTvPolicies.shouldDeferDrawerNavigation(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(HomeTvPolicies.isRemoteSelectKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
    }

    @Test fun viewerDoesNotResumeLockedOrUnlicensedChannel() {
        assertFalse(HomeTvPolicies.mayAutoResume(false, false, true, true))
        assertFalse(HomeTvPolicies.mayAutoResume(true, false, false, true))
        assertFalse(HomeTvPolicies.mayAutoResume(true, true, true, true))
        assertFalse(HomeTvPolicies.mayAutoResume(true, false, true, false))
        assertTrue(HomeTvPolicies.mayAutoResume(true, false, true, true))
    }

    @Test fun rapidChannelChangesRejectOldAsyncPlaybackResults() {
        assertFalse(HomeTvPolicies.isCurrentPlaybackResult(12, 13, true))
        assertFalse(HomeTvPolicies.isCurrentPlaybackResult(13, 13, false))
        assertTrue(HomeTvPolicies.isCurrentPlaybackResult(13, 13, true))
    }

    @Test fun bufferingAndRecoveryAreBoundedDuringRepeatedStalls() {
        assertEquals(1, SmartBufferPolicy.nextOnStall(1, 1, 20000))
        assertEquals(2, SmartBufferPolicy.nextOnStall(1, 2, 20000))
        assertEquals(1, SmartBufferPolicy.nextOnHealthy(2, SmartBufferPolicy.HEALTHY_DOWNGRADE_MS))
    }

    @Test fun playlistSelectionFallsBackOnlyToAvailableSources() {
        val enabled=listOf("Main", "Backup")
        assertEquals("Backup", HomeTvPolicies.resolvePlaylistSource(
            "Unavailable", "Backup", enabled, setOf("Backup")))
        assertNull(HomeTvPolicies.resolvePlaylistSource(
            "Unavailable", "Missing", enabled, emptySet()))
    }

    @Test fun nativePlaybackConfirmationRequiresRealVideoEvidence() {
        assertFalse(HomeTvPolicies.shouldConfirmVlcPlayback(true, false, true, 20000))
        assertTrue(HomeTvPolicies.shouldConfirmVlcPlayback(true, true, true, 2000))
        assertFalse(HomeTvPolicies.shouldConfirmVlcPlayback(false, false, false, 3000))
        assertTrue(HomeTvPolicies.shouldConfirmVlcPlayback(false, false, true, 2000))
    }
}
