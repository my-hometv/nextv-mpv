package com.softppj.nextv

import java.security.MessageDigest

/** Per-viewer resume records identify a media entry, not just its stream URL. */
internal object MediaProgressPolicy {
    fun identityHash(c:Channel):String{
        val fields=listOf(c.source,c.mediaType,c.group,c.name,c.url)
        val input=fields.joinToString(""){it.length.toString()+":"+it}
        val digest=MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hex="0123456789abcdef"
        return buildString(digest.size*2){
            for(byte in digest){
                val value=byte.toInt() and 255
                append(hex[value ushr 4])
                append(hex[value and 15])
            }
        }
    }

    /** Old records were URL-only; copy them only if exactly one media identity owns the URL. */
    fun canMigrateLegacy(channels:List<Channel>,item:Channel):Boolean{
        if(item.mediaType !in setOf("movie","series","music"))return false
        val identity=identityHash(item)
        val other=channels.asSequence().filter{
            it.url==item.url && it.mediaType in setOf("movie","series","music")
        }.map(::identityHash).distinct().take(2).toList()
        return other.size==1 && other[0]==identity
    }
}
