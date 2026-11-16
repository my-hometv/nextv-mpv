package com.softppj.myview

/** Pure selection logic: no playback side effects or cross-playlist jumps. */
internal object ChannelNavigation {
    fun selectNumber(number: Int, size: Int): Int? =
        if (number in 1..size) number - 1 else null

    fun next(current: Int, size: Int, direction: Int): Int? {
        if (size <= 0 || current !in 0 until size) return null
        val step = if (direction < 0) -1 else 1
        return ((current + step) % size + size) % size
    }

    /** Scan within the selected playlist only; never cross into other playlists. */
    fun nextVisible(current: Int, urls: List<String>, hidden: Set<String>, direction: Int): Int? {
        if (current !in urls.indices) return null
        for (step in 1..urls.size) {
            val proposed = ((current + step * (if (direction < 0) -1 else 1)) % urls.size + urls.size) % urls.size
            if (urls[proposed] !in hidden) return proposed
        }
        return null
    }

    /** Honor playlist-local visibility and resume only a currently visible channel. */
    fun preferredVisible(preferred: Int, urls: List<String>, hidden: Set<String>): Int? {
        if (urls.isEmpty()) return null
        if (preferred in urls.indices && urls[preferred] !in hidden) return preferred
        return urls.indices.firstOrNull { urls[it] !in hidden }
    }

    fun filteredIndices(
        channels: List<PlaylistParser.Channel>,
        query: String,
        favoritesOnly: Boolean,
        favorites: Set<String>
    ): List<Int> {
        val needle = query.trim()
        return channels.indices.filter { i ->
            (!favoritesOnly || channels[i].url in favorites) &&
                (needle.isEmpty() || channels[i].name.contains(needle, ignoreCase = true) ||
                    channels[i].group.contains(needle, ignoreCase = true))
        }
    }
}
