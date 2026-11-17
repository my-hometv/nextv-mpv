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
    @Test fun userOrderSurvivesPlaylistRefreshWithNewItems(){
        val original=listOf("A","B","C")
        val moved=ChannelOrderPolicy.move(original,"C",1)
        assertEquals(listOf("C","A","B"),moved)
        // A playlist refresh can replace every Channel instance and add entries.
        val refreshed=listOf("B","D","A","C","E")
        assertEquals(listOf("C","A","B","D","E"),
            ChannelOrderPolicy.apply(refreshed,moved){it})
    }
    @Test fun independentPlaylistOrdersNeverCrossOver(){
        val alphaOrder=ChannelOrderPolicy.move(listOf("alpha:A","alpha:B"),"alpha:B",1)
        val betaOrder=ChannelOrderPolicy.move(listOf("beta:A","beta:B"),"beta:B",1)
        assertEquals(listOf("alpha:B","alpha:A"),
            ChannelOrderPolicy.apply(listOf("alpha:A","alpha:B"),alphaOrder){it})
        assertEquals(listOf("beta:B","beta:A"),
            ChannelOrderPolicy.apply(listOf("beta:A","beta:B"),betaOrder){it})
        assertEquals(listOf("alpha:A","alpha:B"),
            ChannelOrderPolicy.apply(listOf("alpha:A","alpha:B"),betaOrder){it})
    }
    @Test fun removedChannelsDoNotReappearWhenPlaylistChanges(){
        val saved=listOf("A","B","C")
        assertEquals(listOf("A","C","D"),
            ChannelOrderPolicy.apply(listOf("D","C","A"),saved){it})
    }
    @Test fun duplicatesDoNotCreateExtraChannelNumbers(){
        assertEquals(listOf("B","A"),ChannelOrderPolicy.move(listOf("A","A","B"),"B",1))
    }
}
