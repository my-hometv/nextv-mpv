package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class EpgGuidePolicyTest {
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
