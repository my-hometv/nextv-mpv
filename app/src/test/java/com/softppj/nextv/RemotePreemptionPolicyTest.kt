package com.softppj.nextv

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

/** Regression checks for cancel-on-navigation without cancelling volume. */
class RemotePreemptionPolicyTest {
    @Test fun channelNavigationPreemptsBackgroundChecks() {
        listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_CHANNEL_UP,KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_LAST_CHANNEL,
            KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_SELECT
        ).forEach { assertTrue("Navigation key $it should preempt background checks",
            RemotePreemptionPolicy.isNavigationKey(it)) }
    }
    @Test fun allNumericChannelKeysPreemptBackgroundChecks(){
        for (key in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9)
            assertTrue(RemotePreemptionPolicy.isNavigationKey(key))
        for (key in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9)
            assertTrue(RemotePreemptionPolicy.isNavigationKey(key))
    }
    @Test fun teardownReplaysChannelSkipOnlyWithSavedIdentity(){
        assertTrue(RemotePreemptionPolicy.shouldReplaySavedChannelSkip(
            false,"playlist-channel",KeyEvent.KEYCODE_CHANNEL_UP))
        assertTrue(RemotePreemptionPolicy.shouldReplaySavedChannelSkip(
            false,"playlist-channel",KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertFalse(RemotePreemptionPolicy.shouldReplaySavedChannelSkip(
            true,"playlist-channel",KeyEvent.KEYCODE_CHANNEL_UP))
        assertFalse(RemotePreemptionPolicy.shouldReplaySavedChannelSkip(
            false,null,KeyEvent.KEYCODE_CHANNEL_UP))
        assertFalse(RemotePreemptionPolicy.shouldReplaySavedChannelSkip(
            false,"playlist-channel",KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(1,RemotePreemptionPolicy.skipDirection(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(1,RemotePreemptionPolicy.skipDirection(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(-1,RemotePreemptionPolicy.skipDirection(KeyEvent.KEYCODE_CHANNEL_DOWN))
        assertEquals(-1,RemotePreemptionPolicy.skipDirection(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
    }
    @Test fun audioAndUnrelatedButtonsDoNotCancelBackgroundChecks(){
        listOf(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,KeyEvent.KEYCODE_UNKNOWN
        ).forEach { assertFalse(RemotePreemptionPolicy.isNavigationKey(it)) }
    }
}
