package com.softppj.myview

/** Pure selection logic: no playback side effects or cross-playlist jumps. */
internal object ChannelNavigation {
    fun selectNumber(number: Int, size: Int): Int? =
        if (number in 1..size) number - 1 else null

    fun next(current: Int, size: Int, direction: Int): Int? {
        if (size <= 0 || current !in 0 until size) return null
        return (current + if (direction < 0) -1 else 1 + size).let {
            ((it % size) + size) % size
        }
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
