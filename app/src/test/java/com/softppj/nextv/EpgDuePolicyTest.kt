package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class EpgDuePolicyTest {
    data class Scheduled(val name:String,val auto:Boolean)

    @Test fun allSimultaneousRemindersArePreserved(){
        val a=Scheduled("News",false)
        val b=Scheduled("Cinema",false)
        val c=Scheduled("Music",false)
        val batch=EpgDuePolicy.partition(listOf(a,b,c)){it.auto}
        assertNull(batch.automatic)
        assertEquals(0,batch.automaticCount)
        assertEquals(listOf(a,b,c),batch.reminders)
    }

    @Test fun latestAutoWinsWithoutLosingIndependentReminders(){
        val reminderA=Scheduled("Reminder A",false)
        val autoA=Scheduled("Auto A",true)
        val reminderB=Scheduled("Reminder B",false)
        val autoB=Scheduled("Auto B",true)
        val batch=EpgDuePolicy.partition(listOf(reminderA,autoA,reminderB,autoB)){it.auto}
        assertEquals(autoB,batch.automatic)
        assertEquals(2,batch.automaticCount)
        assertEquals(listOf(reminderA,reminderB),batch.reminders)
    }

    @Test fun emptyAndSingleScheduleHavePredictableOutcome(){
        val empty=EpgDuePolicy.partition(emptyList<Scheduled>()){it.auto}
        assertNull(empty.automatic)
        assertTrue(empty.reminders.isEmpty())
        val one=Scheduled("Single",true)
        val batch=EpgDuePolicy.partition(listOf(one)){it.auto}
        assertEquals(one,batch.automatic)
        assertEquals(1,batch.automaticCount)
        assertTrue(batch.reminders.isEmpty())
    }
}
