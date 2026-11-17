package com.softppj.nextv

import android.view.KeyEvent

/**
 * Navigation takes priority over optional background stream checks.
 * Volume and unrelated keys do not cause decoder churn.
 */
internal object RemotePreemptionPolicy {
    fun shouldReplaySavedChannelSkip(activeChannelAvailable:Boolean,
        savedChannelKey:String?,key:Int):Boolean =
        !activeChannelAvailable && !savedChannelKey.isNullOrBlank() &&
        key in setOf(KeyEvent.KEYCODE_CHANNEL_UP,KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_NEXT,KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun skipDirection(key:Int):Int =
        if(key==KeyEvent.KEYCODE_CHANNEL_UP || key==KeyEvent.KEYCODE_MEDIA_NEXT)1 else -1

    fun isNavigationKey(key:Int):Boolean =
        key in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
        key in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ||
        key in setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_SELECT
        )
}
