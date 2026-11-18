package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class EpgGuidePolicyTest {
    @Test fun displayNameMatchingIgnoresRepeatedWhitespaceAndCase() {
        assertEquals("surya tv hd",EpgGuidePolicy.normalizeDisplayName("  Surya   TV   HD  "))
        assertEquals("asianet news",EpgGuidePolicy.normalizeDisplayName("ASIANET NEWS"))
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

    @Test fun futureProgrammeMustUseSchedulingInstead() {
        assertEquals(EpgGuidePolicy.ProgrammeAction.SCHEDULE,
            EpgGuidePolicy.action(1001L,1000L))
    }
}
