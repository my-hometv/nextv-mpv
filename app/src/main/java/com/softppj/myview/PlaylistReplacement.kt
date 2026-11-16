package com.softppj.myview

/**
 * Preserve the last confirmed URL when replacing a playlist, never guess an
 * unverified replacement if the old stream no longer exists.
 */
internal object PlaylistReplacement {
    fun confirmedIndex(previous: List<PlaylistParser.Channel>, lastConfirmedIndex: Int,
        incoming: List<PlaylistParser.Channel>): Int {
        val confirmedUrl = previous.getOrNull(lastConfirmedIndex)?.url ?: return -1
        return incoming.indexOfFirst { it.url == confirmedUrl }
    }
}
