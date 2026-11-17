package com.softppj.nextv

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelPagingPolicyTest {
    @Test fun movesByOneVisiblePage(){
        assertEquals(18,ChannelPagingPolicy.target(10,8,1,50))
        assertEquals(2,ChannelPagingPolicy.target(10,8,-1,50))
    }
    @Test fun clampsAtPlaylistEdges(){
        assertEquals(19,ChannelPagingPolicy.target(17,8,1,20))
        assertEquals(0,ChannelPagingPolicy.target(2,8,-1,20))
        assertEquals(-1,ChannelPagingPolicy.target(0,8,1,0))
    }
    @Test fun handlesNoSelectedItemAndNoMeasuredRows(){
        assertEquals(1,ChannelPagingPolicy.target(-1,0,1,10))
        assertEquals(0,ChannelPagingPolicy.target(0,0,-1,10))
    }
}
