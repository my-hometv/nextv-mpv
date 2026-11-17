package com.softppj.nextv

/** Public builds never provision a playlist automatically.
 * Persisted user sources are retained regardless of their name or URL.
 */
internal object PlaylistSourcePolicy {
    const val REMOVED_INTERNAL_SOURCE = "hometv://licensed-playlist"

    fun shouldKeepSavedSource(url:String):Boolean =
        url.isNotBlank() && url != REMOVED_INTERNAL_SOURCE

    fun shouldClearSelection(selected:String?):Boolean =
        selected == REMOVED_INTERNAL_SOURCE
}
