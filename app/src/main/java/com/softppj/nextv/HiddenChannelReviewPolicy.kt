package com.softppj.nextv

/** A suppressed channel is not offered again until the viewer manually unhides it. */
internal object HiddenChannelReviewPolicy {
    fun eligible(reachable: Collection<String>, suppressed: Set<String>): List<String> =
        reachable.filter { it !in suppressed }.distinct()

    fun skipForever(suppressed: Set<String>, channelKey: String): Set<String> =
        suppressed + channelKey

    fun manuallyUnhidden(suppressed: Set<String>, channelKey: String): Set<String> =
        suppressed - channelKey

    fun manuallyRestoredGroup(suppressed: Set<String>, groupChannelKeys: Set<String>): Set<String> =
        suppressed - groupChannelKeys
}
