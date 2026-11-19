package com.softppj.nextv

/** A saved Web TV item is distinct from a provider playlist's same URL. */
internal object SavedWebTvPolicy {
    fun isIndependentCopy(channel:Channel,url:String):Boolean =
        channel.source.isBlank() && channel.mediaType=="web" && channel.url==url
}
