package com.softppj.nextv

/** Page within a channel list without tuning or leaving the current playlist. */
internal object ChannelPagingPolicy {
    fun target(current:Int,visibleRows:Int,direction:Int,total:Int):Int {
        if(total<=0)return -1
        val selected=current.coerceIn(0,total-1)
        val step=visibleRows.coerceAtLeast(1)
        return (selected+if(direction>0)step else -step).coerceIn(0,total-1)
    }
}
