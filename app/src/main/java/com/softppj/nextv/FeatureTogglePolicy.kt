package com.softppj.nextv

/** Manual operations must remain available when their automatic counterpart is OFF. */
internal object FeatureTogglePolicy {
    // New installations default to user-initiated network/hidden-channel scans.
    // SharedPreferences values already saved by existing viewers are preserved.
    const val DEFAULT_AUTO_PLAYLIST_REFRESH = false
    const val DEFAULT_AUTO_HIDDEN_CHANNEL_CHECK = false
    const val DEFAULT_BACKGROUND_HIDDEN_CHANNEL_CHECK = false

    fun shouldRunAutomaticRefresh(automatic: Boolean, enabled: Boolean): Boolean =
        !automatic || enabled
}
