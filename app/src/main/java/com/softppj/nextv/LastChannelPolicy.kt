package com.softppj.nextv

/** Select a saved channel without confusing two entries that share a stream URL. */
internal object LastChannelPolicy {
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
