package com.softppj.nextv

/** Manual operations must remain available when their automatic counterpart is OFF. */
internal object FeatureTogglePolicy {
    // New installations default to user-initiated network/hidden-channel scans.
    // SharedPreferences values already saved by existing viewers are preserved.
    const val DEFAULT_AUTO_PLAYLIST_REFRESH = false
    const val DEFAULT_AUTO_HIDDEN_CHANNEL_CHECK = false
    const val DEFAULT_BACKGROUND_HIDDEN_CHANNEL_CHECK = false

    // Restore uses the same feature lifecycle transitions as Settings.
    // Values are the actual first-install defaults, not restored user values.
    val runtimeToggles:Map<String,Boolean> = linkedMapOf(
        "auto_hidden_channel_check" to DEFAULT_AUTO_HIDDEN_CHANNEL_CHECK,
        "background_hidden_channel_check" to DEFAULT_BACKGROUND_HIDDEN_CHANNEL_CHECK,
        "auto_playlist_check" to DEFAULT_AUTO_PLAYLIST_REFRESH,
        "background_url_checks" to false,
        "live_channel_preview" to false,
        "myview_instant_preload_enabled" to false,
        "myview_external_logos_enabled" to false,
        "myview_multiview_enabled" to true,
        "nextv_smartplay_enabled" to true,
        "auto_skip_nonworking" to false,
        "auto_reconnect" to true,
        "nextv_epg_enabled" to true,
        "nextv_profiles_enabled" to true,
        "nextv_home_enabled" to true,
        "nextv_backup_enabled" to true,
        "nextv_stream_health_enabled" to true
    )

    fun shouldRunAutomaticRefresh(automatic: Boolean, enabled: Boolean): Boolean =
        !automatic || enabled
}
