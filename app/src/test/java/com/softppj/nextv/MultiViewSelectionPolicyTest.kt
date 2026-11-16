package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class MultiViewSelectionPolicyTest {
    private val keys=mapOf(0 to "A",1 to "A",2 to "B",3 to "C",4 to "D")
    private fun select(options:List<Int>,first:Int,count:Int)=
        MultiViewSelectionPolicy.select(options,first,count){keys.getValue(it)}

    @Test fun skipsAlternativeUrlsOfSameChannel(){
        assertEquals(listOf(0,2),select(listOf(0,1,2),0,2))
    }
    @Test fun wrapsFromCurrentPlayingChannel(){
        assertEquals(listOf(3,4,0,2),select(listOf(0,1,2,3,4),3,4))
    }
    @Test fun neverSelectsOutsideCallerScope(){
        assertEquals(listOf(2,4),select(listOf(2,4),2,4))
    }
    @Test fun invalidCountOrEmptyListCannotStartDecoders(){
        assertTrue(select(listOf(0,1,2),0,8).isEmpty())
        assertTrue(select(emptyList(),0,2).isEmpty())
    }
}
