package com.softppj.nextv

/**
 * Apply a saved user order to one playlist's logical channels.
 * Missing/new channels retain their source order after saved channels.
 */
internal object ChannelOrderPolicy {
    fun <T> apply(items: List<T>, savedKeys: List<String>, identity: (T)->String): List<T> {
        if(savedKeys.isEmpty()) return items
        val positions=savedKeys.withIndex().associate { it.value to it.index }
        return items.withIndex().sortedWith(compareBy<IndexedValue<T>> {
            positions[identity(it.value)] ?: Int.MAX_VALUE
        }.thenBy { it.index }).map { it.value }
    }

    fun move(keys: List<String>, movingKey: String, requestedPosition: Int): List<String> {
        val unique=keys.distinct().toMutableList()
        val current=unique.indexOf(movingKey)
        if(current < 0 || requestedPosition !in 1..unique.size)return unique
        unique.removeAt(current)
        unique.add(requestedPosition-1,movingKey)
        return unique
    }
}
