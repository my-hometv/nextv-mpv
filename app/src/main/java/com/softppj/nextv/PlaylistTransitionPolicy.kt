package com.softppj.nextv

/** Order-preserving next enabled playlist selection after disable or deletion. */
internal object PlaylistTransitionPolicy {
    fun nextEnabledPlaylist(names:List<String>,enabledAndLoaded:List<Boolean>,removed:String):String? {
        if(names.size!=enabledAndLoaded.size)return null
        val anchor=names.indexOf(removed)
        if(anchor<0)return null
        for(step in 1 until names.size){
            val index=(anchor+step)%names.size
            if(enabledAndLoaded[index] && names[index]!=removed)return names[index]
        }
        return null
    }
}
