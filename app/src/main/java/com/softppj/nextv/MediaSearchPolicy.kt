package com.softppj.nextv

/** Search only pre-authorized, locally indexed media; never fetch remote catalogues. */
internal object MediaSearchPolicy {
    fun matches(channel:Channel,query:String):Boolean{
        val q=query.trim()
        if(q.isEmpty())return false
        return sequenceOf(channel.name,channel.group,channel.artist,channel.album,
            channel.genre,channel.description,channel.year).any{it.contains(q,ignoreCase=true)}
    }
}
