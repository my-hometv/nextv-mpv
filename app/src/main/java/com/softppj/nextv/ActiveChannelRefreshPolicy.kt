package com.softppj.nextv

/**
 * Match the *currently playing* logical channel against a new playlist.
 *
 * The caller must capture its key/url/source immediately before publishing
 * the refreshed list. An index captured before the network request is stale.
 */
internal object ActiveChannelRefreshPolicy {
    fun <T> findRelocated(
        refreshed:List<T>,
        currentKey:String?,
        currentUrl:String?,
        currentSource:String?,
        key:(T)->String,
        url:(T)->String,
        source:(T)->String
    ):Int {
        if(currentKey==null || currentUrl==null || currentSource==null)return -1
        val exact=refreshed.indexOfFirst {
            key(it)==currentKey && url(it)==currentUrl && source(it)==currentSource
        }
        if(exact>=0)return exact
        // Providers may edit the title (and therefore the derived key)
        // without changing the source URL. Never cross playlists.
        return refreshed.indexOfFirst {
            url(it)==currentUrl && source(it)==currentSource
        }
    }
}
