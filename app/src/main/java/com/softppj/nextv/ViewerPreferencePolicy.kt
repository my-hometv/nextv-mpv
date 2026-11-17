package com.softppj.nextv

/** A viewer owns favorites/history/order, not licence, playlists or PINs. */
internal object ViewerPreferencePolicy {
    private val keys = listOf(
        "favorites", "recent_channels", "favorites_surfing",
        "favorite_groups", "favorite_group_order", "active_favorite_group",
        "default_favorite_group", "channel_order_favorites",
        "channel_order_all", "channel_sort_mode"
    )

    fun activeViewer(requested:String, knownProfiles:Collection<String>):String =
        if(requested.matches(Regex("[A-Za-z0-9 _-]{1,24}")) &&
            requested in knownProfiles)requested else "default"

    // Never match a raw viewer-name prefix: "Ann" must not delete "Ann_Sue".
    fun profileKeys(name:String):List<String> = keys.map { "viewer_"+name+"_"+it }
}
