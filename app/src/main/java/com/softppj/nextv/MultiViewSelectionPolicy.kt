package com.softppj.nextv

/**
 * Select distinct logical channels without leaving the caller's permitted
 * playlist/favorites scope. Alternative URLs for the same channel count once.
 */
internal object MultiViewSelectionPolicy {
    fun select(options:List<Int>, first:Int, requested:Int, identity:(Int)->String):List<Int> {
        if(requested !in setOf(2,4) || options.isEmpty())return emptyList()
        val start=options.indexOf(first).let{if(it>=0)it else 0}
        val seen=HashSet<String>()
        val result=ArrayList<Int>(requested)
        for(step in options.indices){
            val candidate=options[(start+step)%options.size]
            if(seen.add(identity(candidate)))result.add(candidate)
            if(result.size>=requested)break
        }
        return result
    }
}
