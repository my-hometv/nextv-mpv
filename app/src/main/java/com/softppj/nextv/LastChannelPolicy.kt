package com.softppj.nextv

/** Select a saved channel without confusing two entries that share a stream URL. */
internal object LastChannelPolicy {
    /** Strict identity prevents URL collisions across channels and playlists.
     * Legacy URL matching is used only when no identity was ever stored.
     */
    fun findEligibleSavedChannel(
        channels:List<Channel>,savedIdentity:String?,savedUrl:String?,
        identity:(Channel)->String,eligible:(Channel)->Boolean
    ):Int{
        if(!savedIdentity.isNullOrBlank())
            return channels.indexOfFirst{identity(it)==savedIdentity && eligible(it)}
        if(savedUrl.isNullOrBlank())return -1
        return channels.indexOfFirst{it.url==savedUrl && eligible(it)}
    }

    fun findSavedChannel(
        channels: List<Channel>,
        savedIdentity: String?,
        savedUrl: String?,
        identity: (Channel) -> String
    ): Int {
        if (!savedIdentity.isNullOrBlank()) {
            val exact = channels.indexOfFirst { identity(it) == savedIdentity }
            if (exact >= 0) return exact
        }
        if (savedUrl.isNullOrBlank()) return -1
        // Legacy preferences stored only a URL.
        return channels.indexOfFirst { it.url == savedUrl }
    }
}
