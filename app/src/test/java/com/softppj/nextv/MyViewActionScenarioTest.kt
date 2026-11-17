package com.softppj.nextv

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

/**
 * Headless realistic action journeys using the same policies as MainActivity.
 * These verify selection, authorization and concurrency decisions, not real
 * HDR, Surface rendering or native decoder timing on a television.
 */
class MyViewActionScenarioTest {
    private data class Channel(val key:String,val url:String,val source:String)

    @Test fun remotePressedRepeatedlyWhileStartupIsBuffering(){
        val remoteSequence=listOf(KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_CHANNEL_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_DPAD_CENTER)
        remoteSequence.forEach { key ->
            assertTrue(RemotePreemptionPolicy.isNavigationKey(key))
            assertEquals("No background decoder while foreground is starting",0,
                VideoQualityPolicy.backgroundProbeLimit(true,true,false,true))
        }
    }

    @Test fun foregroundBecomesReadyYetBackgroundProbeStillCannotStealDecoder(){
        assertEquals(0,VideoQualityPolicy.backgroundProbeLimit(true,false,false,false))
        assertEquals(1,VideoQualityPolicy.backgroundProbeLimit(false,false,false,false))
    }

    @Test fun switchingChannelDuringPlaylistRefreshKeepsMostRecentChoice(){
        val old=Channel("A","https://source.example/a","Home")
        val now=Channel("B","https://source.example/b","Home")
        val refreshed=listOf(now,old)
        val relocated=ActiveChannelRefreshPolicy.findRelocated(
            refreshed,now.key,now.url,now.source,
            {it.key},{it.url},{it.source})
        assertEquals(0,relocated)
        assertEquals("B",refreshed[relocated].key)
    }

    @Test fun refreshWithSameUrlInAnotherPlaylistNeverCrossesProvider(){
        val playing=Channel("news","https://source.example/live","Provider A")
        val refreshed=listOf(Channel("news","https://source.example/live","Provider B"))
        assertEquals(-1,ActiveChannelRefreshPolicy.findRelocated(
            refreshed,playing.key,playing.url,playing.source,
            {it.key},{it.url},{it.source}))
    }

    @Test fun lostSurfaceThenNewTuneDoesNotReplayOldChannel(){
        val saved="provider:channel-3"
        assertFalse(RemotePreemptionPolicy.shouldReplaySavedChannelSkip(
            true,saved,KeyEvent.KEYCODE_CHANNEL_UP))
        assertTrue(RemotePreemptionPolicy.shouldReplaySavedChannelSkip(
            false,saved,KeyEvent.KEYCODE_CHANNEL_UP))
    }

    @Test fun readyPrebufferIsRejectedWhenLockedOrReplaced(){
        assertFalse(LivePrebufferPolicy.mayAdopt(
            licenseReady=true,correctIdentity=true,ready=true,
            engineIsMedia3=true,unlocked=false))
        assertFalse(LivePrebufferPolicy.mayAdopt(
            licenseReady=true,correctIdentity=false,ready=true,
            engineIsMedia3=true,unlocked=true))
        assertFalse(LivePrebufferPolicy.mayAdopt(
            licenseReady=true,correctIdentity=true,ready=false,
            engineIsMedia3=true,unlocked=true))
        assertTrue(LivePrebufferPolicy.mayAdopt(
            licenseReady=true,correctIdentity=true,ready=true,
            engineIsMedia3=true,unlocked=true))
    }

    @Test fun providerCapacityErrorDoesNotTriggerImmediateChannelSwitch(){
        val kind=ProviderFailurePolicy.classify(
            httpStatus=429,drmFailure=false,decoderFailure=false,networkFailure=false)
        assertEquals(ProviderFailurePolicy.Kind.PROVIDER_BUSY,kind)
        assertEquals(4000L,ProviderFailurePolicy.retryDelayMs(kind,0))
        assertEquals(9000L,ProviderFailurePolicy.retryDelayMs(kind,1))
        assertNull(ProviderFailurePolicy.retryDelayMs(kind,2))
        assertNull(ProviderFailurePolicy.retryDelayMs(
            ProviderFailurePolicy.Kind.AUTHORIZATION,0))
    }

    @Test fun fourWayMultiViewCannotUseHiddenOrLockedSource(){
        assertFalse(MultiViewAccessPolicy.canEnter(
            true,false,true,false,true,false,true,true))
        assertFalse(MultiViewAccessPolicy.canEnter(
            true,true,false,false,true,false,true,false))
        assertFalse(MultiViewAccessPolicy.canEnter(
            false,false,true,false,true,false,true,false))
        assertTrue(MultiViewAccessPolicy.canEnter(
            true,false,true,false,true,false,true,false))
        val ordered=MultiViewSelectionPolicy.select(listOf(1,1,2,3,4),1,4){it.toString()}
        assertEquals(listOf(1,2,3,4),ordered)
    }

    @Test fun customChannelOrderSurvivesProviderReordering(){
        val saved=ChannelOrderPolicy.move(listOf("A","B","C"),"C",1)
        val refreshed=ChannelOrderPolicy.apply(listOf("B","D","C","A"),saved){it}
        assertEquals(listOf("C","A","B","D"),refreshed)
    }

    @Test fun backupCannotImportLicenseOrTokens(){
        for(key in listOf("license_token","signed_license_cache",
            "service_api_key","playlist_password","authorization_header")){
            assertFalse("Do not transfer "+key,BackupSafety.mayTransferSetting(key))
        }
        assertTrue(BackupSafety.mayTransferSetting("myview_clock_enabled"))
    }

    @Test fun hdrAndPictureSettingsDoNotForceUnsustainablePeakBitrate(){
        assertFalse(VideoQualityPolicy.forceHighestBitrate("Best Available"))
        assertFalse(VideoQualityPolicy.forceHighestBitrate("Auto"))
        // 24fps VOD matching is opt-in; live TV must never change TV mode.
        val current=FrameRateMatchPolicy.Mode(1,1920,1080,60f)
        val modes=listOf(current,FrameRateMatchPolicy.Mode(2,1920,1080,24f))
        assertNull(FrameRateMatchPolicy.select(false,true,24f,current,modes))
        assertNull(FrameRateMatchPolicy.select(true,false,24f,current,modes))
    }
}
