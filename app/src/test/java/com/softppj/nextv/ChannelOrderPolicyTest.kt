package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class ChannelOrderPolicyTest {
    @Test fun movedChannelOverridesPlaylistOrder(){
        assertEquals(listOf("C","A","B"),
            ChannelOrderPolicy.move(listOf("A","B","C"),"C",1))
    }
    @Test fun insertedPlaylistChannelsStayAfterSavedPositions(){
        assertEquals(listOf("C","A","B","D"),
            ChannelOrderPolicy.apply(listOf("A","B","C","D"),listOf("C","A","B")){it})
    }
    @Test fun unknownAndOutOfRangeMovesDoNotChangeOrder(){
        val saved=listOf("A","B","C")
        assertEquals(saved,ChannelOrderPolicy.move(saved,"X",1))
        assertEquals(saved,ChannelOrderPolicy.move(saved,"B",0))
        assertEquals(saved,ChannelOrderPolicy.move(saved,"B",5))
    }
    @Test fun noSavedOrderPreservesSourceOrder(){
        assertEquals(listOf("B","A"),ChannelOrderPolicy.apply(listOf("B","A"),emptyList()){it})
    }
    @Test fun duplicatesDoNotCreateExtraChannelNumbers(){
        assertEquals(listOf("B","A"),ChannelOrderPolicy.move(listOf("A","A","B"),"B",1))
    }
}
