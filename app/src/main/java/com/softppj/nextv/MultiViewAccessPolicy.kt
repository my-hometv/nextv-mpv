package com.softppj.nextv

/**
 * A multi-screen session must never resolve or open protected stream URLs
 * before the same source/group/channel gates as ordinary playback succeed.
 */
internal object MultiViewAccessPolicy {
    /** An entitlement loss requires immediate decoder shutdown, even in MultiView. */
    fun mustReleaseSession(licensed:Boolean):Boolean = !licensed

    fun canEnter(
        licensed: Boolean,
        channelLocked: Boolean,
        channelUnlocked: Boolean,
        playlistLocked: Boolean,
        playlistUnlocked: Boolean,
        groupLocked: Boolean,
        groupUnlocked: Boolean,
        manuallyHidden: Boolean
    ): Boolean =
        licensed && !manuallyHidden &&
            (!channelLocked || channelUnlocked) &&
            (!playlistLocked || playlistUnlocked) &&
            (!groupLocked || groupUnlocked)
}
