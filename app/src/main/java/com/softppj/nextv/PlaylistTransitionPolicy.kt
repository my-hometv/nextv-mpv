package com.softppj.nextv

/** Order-preserving next enabled playlist selection after disable or deletion. */
internal object PlaylistTransitionPolicy {
    fun nextEligiblePlaylist(
        names:List<String>,enabled:List<Boolean>,removed:String,
        hasPlayableChannel:(String)->Boolean
    ):String?{
        if(names.size!=enabled.size)return null
        val anchor=names.indexOf(removed)
        if(anchor<0)return null
        for(step in 1 until names.size){
            val index=(anchor+step)%names.size
            if(enabled[index] && names[index]!=removed &&
                hasPlayableChannel(names[index]))return names[index]
        }
        return null
    }
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
