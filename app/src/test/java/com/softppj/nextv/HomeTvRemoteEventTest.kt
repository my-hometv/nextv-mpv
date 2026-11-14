package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** KeyEvent construction requires a Robolectric Android runtime, not the mockable android.jar stubs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeTvRemoteEventTest {
    @Test fun drawerKeyTranslationPreservesRemoteEventMetadata() {
        val original = android.view.KeyEvent(
            123L, 145L, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 2, 0,
            17, 42, android.view.KeyEvent.FLAG_LONG_PRESS
        )
        val translated = HomeTvPolicies.translateDrawerEvent(
            original, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, translated.keyCode)
        assertEquals(original.action, translated.action)
        assertEquals(original.repeatCount, translated.repeatCount)
        assertEquals(original.deviceId, translated.deviceId)
        assertEquals(original.scanCode, translated.scanCode)
        assertEquals(original.flags, translated.flags)
        assertEquals(original.downTime, translated.downTime)
        assertEquals(original.eventTime, translated.eventTime)
        assertSame(original, HomeTvPolicies.translateDrawerEvent(
            original, original.keyCode))
    }

    @Test fun translatedKeyUpPreservesReleaseSemanticsAndInputSource() {
        val original = android.view.KeyEvent(
            1_000L, 1_150L, android.view.KeyEvent.ACTION_UP,
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND, 0,
            android.view.KeyEvent.META_SHIFT_ON, 23, 81,
            android.view.KeyEvent.FLAG_CANCELED,
            android.view.InputDevice.SOURCE_DPAD
        )
        val translated = HomeTvPolicies.translateDrawerEvent(
            original, android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(android.view.KeyEvent.ACTION_UP, translated.action)
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_LEFT, translated.keyCode)
        assertEquals(original.metaState, translated.metaState)
        assertEquals(original.source, translated.source)
        assertEquals(original.flags, translated.flags)
        assertEquals(original.downTime, translated.downTime)
        assertEquals(original.eventTime, translated.eventTime)
        assertEquals(original.deviceId, translated.deviceId)
        assertEquals(original.scanCode, translated.scanCode)
        assertFalse(HomeTvPolicies.isFreshRemotePress(
            translated.action, translated.repeatCount, false))
    }

    @Test fun repeatedAndLongPressTransportKeysNeverBecomeFreshDrawerClicks() {
        val repeated = android.view.KeyEvent(
            200L, 250L, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 3, 0,
            9, 12, android.view.KeyEvent.FLAG_LONG_PRESS
        )
        val translated = HomeTvPolicies.translateDrawerEvent(
            repeated, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(3, translated.repeatCount)
        assertEquals(android.view.KeyEvent.FLAG_LONG_PRESS,
            translated.flags and android.view.KeyEvent.FLAG_LONG_PRESS)
        assertFalse(HomeTvPolicies.isFreshRemotePress(
            translated.action, translated.repeatCount,
            translated.flags and android.view.KeyEvent.FLAG_LONG_PRESS != 0))
    }

}
