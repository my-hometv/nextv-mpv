package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class ActiveChannelRefreshPolicyTest {
    private data class Entry(val key:String,val url:String,val source:String)
    private fun relocate(updated:List<Entry>, current:Entry):Int =
        ActiveChannelRefreshPolicy.findRelocated(
            updated,current.key,current.url,current.source,
            {it.key},{it.url},{it.source}
        )

    @Test fun currentRemoteSelectionWinsOverOldNetworkSnapshot(){
        val originalFirst=Entry("alpha-1","https://a.test/1","Alpha")
        val newRemoteSelection=Entry("alpha-2","https://a.test/2","Alpha")
        val afterRefresh=listOf(originalFirst,newRemoteSelection)
        assertEquals(1,relocate(afterRefresh,newRemoteSelection))
    }

    @Test fun channelMovesWithoutSwitchingToOldIndex(){
        val playing=Entry("channel-c","https://a.test/c","Alpha")
        val updated=listOf(playing,Entry("channel-a","https://a.test/a","Alpha"))
        assertEquals(0,relocate(updated,playing))
    }

    @Test fun sameUrlOnDifferentPlaylistMustNotCrossSources(){
        val playing=Entry("a","https://shared.test/live","Alpha")
        assertEquals(-1,relocate(listOf(
            Entry("b","https://shared.test/live","Beta")
        ),playing))
    }

    @Test fun providerRenameMayPreserveUrlAndSource(){
        val playing=Entry("old","https://a.test/live","Alpha")
        assertEquals(0,relocate(listOf(
            Entry("new","https://a.test/live","Alpha")
        ),playing))
    }

    @Test fun removedChannelDoesNotBecomeAnotherChannel(){
        val playing=Entry("old","https://a.test/old","Alpha")
        assertEquals(-1,relocate(listOf(
            Entry("other","https://a.test/new","Alpha")
        ),playing))
    }

    @Test fun noPlayingChannelCannotBeReplaced(){
        val updated=listOf(Entry("a","https://a.test/live","Alpha"))
        assertEquals(-1,ActiveChannelRefreshPolicy.findRelocated(
            updated,null,null,null,{it.key},{it.url},{it.source}
        ))
    }
}
