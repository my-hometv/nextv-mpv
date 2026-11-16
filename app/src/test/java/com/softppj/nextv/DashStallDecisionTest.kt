package com.softppj.nextv

import org.junit.Assert.assertEquals
import org.junit.Test

class DashStallDecisionTest {
    private fun snapshot(
        duration: Long = 20_000L,
        played: Boolean = true,
        buffered: Long = 0L,
        advanced: Long = 0L,
        protected: Boolean = false,
        drmError: Boolean = false
    ) = DashStallDecision.Snapshot(duration, played, buffered, advanced, protected, drmError)

    @Test fun shortBufferingNeverTriggersUrlSwitch() =
        assertEquals(DashStallDecision.Action.WAIT,
            DashStallDecision.decide(snapshot(duration = 5_000L)))

    @Test fun confirmedWorkingChannelIsNotSkippedAfterStall() =
        assertEquals(DashStallDecision.Action.DIAGNOSE,
            DashStallDecision.decide(snapshot()))

    @Test fun movingPositionIsNotPermanentFailure() =
        assertEquals(DashStallDecision.Action.DIAGNOSE,
            DashStallDecision.decide(snapshot(advanced = 1_000L)))

    @Test fun drmErrorIsDiagnosedNotRetriedAsUrlFailure() =
        assertEquals(DashStallDecision.Action.DIAGNOSE,
            DashStallDecision.decide(snapshot(drmError = true)))

    @Test fun protectedStreamStallRequiresDrmDiagnostics() =
        assertEquals(DashStallDecision.Action.DIAGNOSE,
            DashStallDecision.decide(snapshot(protected = true)))

    @Test fun initialUnprotectedHardStallCanRecover() =
        assertEquals(DashStallDecision.Action.RECOVER,
            DashStallDecision.decide(snapshot(played = false)))
}
