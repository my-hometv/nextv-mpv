package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class EpgLoadBudgetTest {
    @Test fun totalTimeIsBounded() {
        assertEquals(60000L,EpgLoadBudget.remainingMs(1000L,1000L))
        assertEquals(40000L,EpgLoadBudget.remainingMs(1000L,21000L))
        assertEquals(0L,EpgLoadBudget.remainingMs(1000L,61000L))
        assertEquals(0L,EpgLoadBudget.remainingMs(1000L,90000L))
    }
    @Test fun deadlineAlsoAppliesWhileAReadHasStalled() {
        assertEquals(1L,EpgLoadBudget.remainingMs(2000L,61999L))
        assertEquals(0L,EpgLoadBudget.remainingMs(2000L,62000L))
        assertEquals(0L,EpgLoadBudget.remainingMs(2000L,62500L))
    }
    @Test fun networkTimeoutsRespectRemainingTime() {
        assertEquals(8000,EpgLoadBudget.connectionTimeoutMs(60000))
        assertEquals(20000,EpgLoadBudget.readTimeoutMs(60000))
        assertEquals(1250,EpgLoadBudget.connectionTimeoutMs(1250))
        assertEquals(1250,EpgLoadBudget.readTimeoutMs(1250))
        assertEquals(1,EpgLoadBudget.connectionTimeoutMs(0))
    }
}
