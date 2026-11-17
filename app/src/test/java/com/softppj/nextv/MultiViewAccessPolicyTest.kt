package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class MultiViewAccessPolicyTest {
    private fun permitted(
        licensed:Boolean=true,
        channelLocked:Boolean=false,
        channelUnlocked:Boolean=false,
        playlistLocked:Boolean=false,
        playlistUnlocked:Boolean=false,
        groupLocked:Boolean=false,
        groupUnlocked:Boolean=false,
        manuallyHidden:Boolean=false
    ) = MultiViewAccessPolicy.canEnter(
        licensed,channelLocked,channelUnlocked,
        playlistLocked,playlistUnlocked,groupLocked,groupUnlocked,manuallyHidden
    )

    @Test fun normalChannelMayBeUsed(){assertTrue(permitted())}
    @Test fun expiredLicenseAndHiddenChannelsCannotBeUsed(){
        assertFalse(permitted(licensed=false))
        assertFalse(permitted(manuallyHidden=true))
    }
    @Test fun lockedChannelRequiresSessionUnlock(){
        assertFalse(permitted(channelLocked=true))
        assertTrue(permitted(channelLocked=true,channelUnlocked=true))
    }
    @Test fun playlistAndGroupLocksEnforcedIndependently(){
        assertFalse(permitted(playlistLocked=true))
        assertFalse(permitted(groupLocked=true))
        assertTrue(permitted(playlistLocked=true,playlistUnlocked=true))
        assertTrue(permitted(groupLocked=true,groupUnlocked=true))
        assertFalse(permitted(playlistLocked=true,playlistUnlocked=true,groupLocked=true))
    }
}
