package com.softppj.nextv

/** Combined browsing must not change the current playlist channel-surfing scope. */
internal object PlaylistBrowsePolicy {
    fun displayedIndices(combined:Boolean,visible:List<Int>,scoped:List<Int>):List<Int> =
        if(combined)visible else scoped
}
