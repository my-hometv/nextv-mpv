package com.softppj.nextv

/** All media libraries use logical representatives for every media type.
 * Live channel surfing intentionally uses only live representatives.
 */
internal object MediaLibraryPolicy {
    fun allowedRepresentatives(
        channels:List<Channel>,representatives:List<Int>,
        enabledSources:Set<String>,lockedSources:Set<String>,
        unlockedSources:Set<String>,lockedGroups:Set<String>,
        unlockedGroups:Set<String>
    ):List<Int> = representatives.filter{index->
        val c=channels.getOrNull(index) ?:return@filter false
        (c.source.isBlank() || c.source in enabledSources) &&
            (c.source !in lockedSources || c.source in unlockedSources) &&
            (c.group !in lockedGroups || c.group in unlockedGroups)
    }
}
