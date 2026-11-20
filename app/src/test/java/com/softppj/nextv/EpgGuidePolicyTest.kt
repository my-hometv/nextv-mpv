package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class EpgGuidePolicyTest {
    @Test fun emptyOrExpiredFeedNeverOverwritesExistingGuide() {
        assertFalse(EpgGuidePolicy.shouldPublishFeed(0))
        assertFalse(EpgGuidePolicy.shouldPublishFeed(-1))
        assertTrue(EpgGuidePolicy.shouldPublishFeed(1))
        assertTrue(EpgGuidePolicy.shouldPublishFeed(500))
    }
    @Test fun displayNameMatchingIgnoresRepeatedWhitespaceAndCase() {
        assertEquals("surya tv hd",EpgGuidePolicy.normalizeDisplayName("  Surya   TV   HD  "))
        assertEquals("asianet news",EpgGuidePolicy.normalizeDisplayName("ASIANET NEWS"))
    }

    @Test fun xmltvNameAliasesMatchQualitySuffixButKeepExactFirst() {
        assertEquals(listOf("surya tv hd", "surya tv"),
            EpgGuidePolicy.matchNames("Surya TV HD"))
        assertEquals(listOf("sun.tv-fhd", "sun tv fhd", "sun tv"),
            EpgGuidePolicy.matchNames("Sun.TV-FHD"))
        assertEquals(listOf("asianet news"),
            EpgGuidePolicy.matchNames("Asianet News"))
    }

    @Test fun dueReminderBacksOffWhenLicenceNotReadyOrAppBackgrounded() {
        assertEquals(60_000L,EpgGuidePolicy.nextReminderDelayMs(1000L,2000L,false))
        assertEquals(0L,EpgGuidePolicy.nextReminderDelayMs(1000L,2000L,true))
    }

    @Test fun upcomingReminderRetainsBoundedTimerAndDoesNotMissDueTime() {
        assertEquals(30_000L,EpgGuidePolicy.nextReminderDelayMs(60_000L,30_000L,false))
        assertEquals(60_000L,EpgGuidePolicy.nextReminderDelayMs(900_000L,30_000L,true))
        assertEquals(0L,EpgGuidePolicy.nextReminderDelayMs(30_000L,30_000L,true))
        assertEquals(60_000L,EpgGuidePolicy.nextReminderDelayMs(30_000L,30_000L,false))
    }

    @Test fun visibleChannelsRemainEvenWithoutProgrammeData() {
        val visible=listOf(0,1,4,7)
        assertEquals(visible,EpgGuidePolicy.rows(visible,8))
    }

    @Test fun onlyInvalidChannelIndicesAreExcluded() {
        assertEquals(listOf(2,0,3),EpgGuidePolicy.rows(listOf(-1,2,0,5,3),4))
    }

    @Test fun alreadyStartedProgrammeMustTuneImmediately() {
        assertEquals(EpgGuidePolicy.ProgrammeAction.WATCH_NOW,
            EpgGuidePolicy.action(1000L,1000L))
        assertEquals(EpgGuidePolicy.ProgrammeAction.WATCH_NOW,
            EpgGuidePolicy.action(500L,1000L))
    }

    private data class Timed(val start:Long,val end:Long,val title:String)

    @Test fun exactBoundaryChangesNowAndNextWithoutDisplayingExpiredProgramme() {
        val rows=listOf(Timed(0,1000,"A"),Timed(1000,2000,"B"),Timed(2000,3000,"C"))
        fun labels(time:Long):Pair<String?,String?> {
            val (now,next)=EpgGuidePolicy.nowNext(rows,time,{it.start},{it.end})
            return now?.title to next?.title
        }
        assertEquals("A" to "B",labels(999))
        assertEquals("B" to "C",labels(1000))
        assertEquals("B" to "C",labels(1999))
        assertEquals("C" to null,labels(2000))
        assertEquals(null to null,labels(3000))
    }

    @Test fun programmeGapAndOverlapsKeepCorrectNowNext() {
        val gap=listOf(Timed(100,200,"A"),Timed(300,400,"B"))
        val (duringGap,upcoming)=EpgGuidePolicy.nowNext(gap,250,{it.start},{it.end})
        assertNull(duringGap)
        assertEquals("B",upcoming?.title)
        val overlapping=listOf(Timed(0,500,"Long"),Timed(200,300,"Short"),Timed(600,700,"Later"))
        val (current,next)=EpgGuidePolicy.nowNext(overlapping,250,{it.start},{it.end})
        assertEquals("Long",current?.title)
        assertEquals("Later",next?.title)
    }

    @Test fun nextBoundarySchedulingUsesRealStartsAndEnds() {
        val rows=listOf(Timed(0,1000,"A"),Timed(1000,2000,"B"),Timed(2000,3000,"C"))
        assertEquals(1L,EpgGuidePolicy.nextBoundaryDelayMs(rows,999,{it.start},{it.end}))
        assertEquals(1000L,EpgGuidePolicy.nextBoundaryDelayMs(rows,1000,{it.start},{it.end}))
        assertEquals(1L,EpgGuidePolicy.nextBoundaryDelayMs(rows,1999,{it.start},{it.end}))
        assertNull(EpgGuidePolicy.nextBoundaryDelayMs(rows,3000,{it.start},{it.end}))
    }

    @Test fun guideReplacementUsesNewTimingsInsteadOfStaleGuide() {
        val original=listOf(Timed(0,1000,"Old"),Timed(1000,2000,"Old next"))
        val replacement=listOf(Timed(0,1500,"Replacement"),Timed(1500,3000,"New next"))
        val (oldNow,oldNext)=EpgGuidePolicy.nowNext(original,1200,{it.start},{it.end})
        val (newNow,newNext)=EpgGuidePolicy.nowNext(replacement,1200,{it.start},{it.end})
        assertEquals("Old next",oldNow?.title)
        assertNull(oldNext)
        assertEquals("Replacement",newNow?.title)
        assertEquals("New next",newNext?.title)
        assertEquals(300L,EpgGuidePolicy.nextBoundaryDelayMs(replacement,1200,{it.start},{it.end}))
    }

    @Test fun longViewingSessionRefreshesOnlyAfterOneHour(){
        val hour=60*60*1000L
        assertTrue(EpgGuidePolicy.needsRefresh(0L,1000L))
        assertFalse(EpgGuidePolicy.needsRefresh(1000L,1000L+hour-1L))
        assertTrue(EpgGuidePolicy.needsRefresh(1000L,1000L+hour))
        assertTrue(EpgGuidePolicy.needsRefresh(5000L,1000L))
    }

    private data class GuideChannel(val identity:String,val enabled:Boolean=true)

    @Test fun guideDialogResolvesOriginalChannelAfterPlaylistReorder(){
        val original=GuideChannel("playlist-a|Movie A|https://a.test")
        val other=GuideChannel("playlist-b|Movie B|https://b.test")
        val updated=listOf(other,original)
        val resolved=EpgGuidePolicy.resolveDialogChannel(
            updated,original.identity,{it.identity},{it.enabled})
        assertEquals(1,resolved)
        assertEquals(original,updated[resolved])
    }

    @Test fun guideDialogNeverTunesReplacementAtOldIndex(){
        val removed=GuideChannel("playlist-a|Removed|https://gone.test")
        val replacement=GuideChannel("playlist-b|Replacement|https://new.test")
        assertEquals(-1,EpgGuidePolicy.resolveDialogChannel(
            listOf(replacement),removed.identity,{it.identity},{it.enabled}))
    }

    @Test fun guideDialogRejectsDisabledSourceEvenWhenIdentityStillMatches(){
        val original=GuideChannel("playlist-a|Channel|https://a.test",false)
        assertEquals(-1,EpgGuidePolicy.resolveDialogChannel(
            listOf(original),original.identity,{it.identity},{it.enabled}))
    }

    @Test fun futureProgrammeMustUseSchedulingInstead() {
        assertEquals(EpgGuidePolicy.ProgrammeAction.SCHEDULE,
            EpgGuidePolicy.action(1001L,1000L))
    }
}
