package com.softppj.nextv

/** Offer a PIN for locked groups without revealing locked-playlist contents. */
internal object GroupNavigationPolicy {
    fun visibleGroups(
        channels:List<Channel>, liveIndices:List<Int>, enabledSources:Set<String>,
        lockedSources:Set<String>, unlockedSources:Set<String>,
        hiddenGroups:Set<String>
    ):List<String> = liveIndices.asSequence().mapNotNull { i ->
        val c=channels.getOrNull(i) ?:return@mapNotNull null
        if((c.source.isNotBlank() && c.source !in enabledSources) ||
            (c.source in lockedSources && c.source !in unlockedSources) ||
            c.group in hiddenGroups) null else c.group
    }.distinct().toList()
}
