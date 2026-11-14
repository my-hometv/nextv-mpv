package com.softppj.nextv

/**
 * Session-scoped, engine-major recovery traversal shared by foreground/background callers.
 * All state transitions are synchronized: a callback from a superseded channel/probe cannot
 * change the current channel's result, even when probes finish on different threads.
 */
internal class RecoveryAttemptSession<T>(
    private val urlOf: (T) -> String,
    private val supports: (T, String) -> Boolean
) {
    data class Attempt<T>(val generation: Long, val url: T, val engine: String)
    private data class Key(val url: String, val engine: String)

    private var generation = 0L
    private var nextPairIndex = 0L
    private var resolvedUrls: MutableList<String?> = mutableListOf()
    private var urlResolved: BooleanArray = booleanArrayOf()
    private var candidates: List<T> = emptyList()
    private var engines: List<String> = emptyList()
    private val attempted = mutableSetOf<Key>()
    private var pending: Attempt<T>? = null
    private var confirmed: Attempt<T>? = null

    @Synchronized
    fun beginChannel(urls: List<T>, enabledEngines: List<String>): Long {
        generation += 1
        candidates = urls.toList()
        resolvedUrls = MutableList(candidates.size) { null }
        urlResolved = BooleanArray(candidates.size)
        engines = enabledEngines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        attempted.clear()
        nextPairIndex = 0L
        pending = null
        confirmed = null
        return generation
    }

    @Synchronized
    fun currentGeneration(): Long = generation

    /** Reserve one unique URL/engine pair. No new attempt follows a confirmed success. */
    @Synchronized
    fun next(): Attempt<T>? {
        if (pending != null || confirmed != null) return null
        // Resume from the next pair instead of rescanning all earlier pairs on
        // every failed probe. This keeps long IPTV playlists responsive.
        val count = candidates.size
        if (count == 0) return null
        val total = count.toLong() * engines.size
        while (nextPairIndex < total) {
            val pairIndex = nextPairIndex++
            val engine = engines[(pairIndex / count).toInt()]
            val candidateIndex = (pairIndex % count).toInt()
            val candidate = candidates[candidateIndex]
            // A malformed source or an unavailable native backend must not crash recovery.
            // Treat selector exceptions as unsupported and continue to the next pair.
            // Resolve each source once per channel, even when multiple engines
            // are tried. A malformed source cannot repeatedly throw on retries.
            if (!urlResolved[candidateIndex]) {
                resolvedUrls[candidateIndex] = runCatching { urlOf(candidate) }.getOrNull()
                urlResolved[candidateIndex] = true
            }
            val url = resolvedUrls[candidateIndex] ?: continue
            if (url.isBlank()) continue
            // Whitespace around playlist entries does not identify a distinct stream.
            val key = Key(url.trim(), engine)
            if (key in attempted) continue
            if (!runCatching { supports(candidate, engine) }.getOrDefault(false)) continue
            if (attempted.add(key)) {
                return Attempt(generation, candidate, engine).also { pending = it }
            }
        }
        return null
    }

    /** Only the exact reserved attempt may finish; replayed or stale callbacks are rejected. */
    @Synchronized
    fun finish(attempt: Attempt<T>, succeeded: Boolean): Boolean {
        if (attempt.generation != generation || pending !== attempt) return false
        pending = null
        if (succeeded) confirmed = attempt
        return true
    }

    /**
     * Expire a timed-out probe only if it is still the active reservation.
     * A late timeout from a previous channel must not cancel a newer probe.
     * The pair remains attempted so the next reservation advances to a different source.
     */
    @Synchronized
    fun expire(attempt: Attempt<T>): Boolean {
        if (attempt.generation != generation || pending !== attempt) return false
        pending = null
        return true
    }

    /** Cancel a pending probe and invalidate callbacks without retrying its pair. */
    @Synchronized
    fun cancelPending(): Long {
        generation += 1
        pending = null
        confirmed = null
        return generation
    }

    /**
     * Reconcile engine availability without restarting the current channel.
     * A disabled backend must never receive another reservation, even when
     * the user's engine preference changes during an active recovery cycle.
     * Re-enabling a backend does not replay previously attempted pairs.
     *
     * An in-flight reservation for a removed engine is invalidated, so a late
     * Playing callback cannot confirm a backend that is no longer permitted.
     */
    @Synchronized
    fun retainEnabledEngines(enabled: Collection<String>): Boolean {
        val allowed = enabled.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val retained = engines.filter { it in allowed }
        if (retained == engines) return false
        val oldPending = pending
        // Preserve the engine-major cursor by translating its next position
        // into the first unvisited pair of the retained engine ordering.
        val count = candidates.size
        val oldEngines = engines
        val oldCursor = nextPairIndex
        var translated = 0L
        if (count > 0) {
            for ((index, engine) in oldEngines.withIndex()) {
                if (engine !in allowed) continue
                val begin = index.toLong() * count
                val visited = (oldCursor - begin).coerceIn(0L, count.toLong())
                translated += visited
            }
        }
        engines = retained
        nextPairIndex = translated
        if (oldPending != null && oldPending.engine !in allowed) {
            generation += 1
            pending = null
        }
        if (confirmed?.engine !in allowed) confirmed = null
        return true
    }

    @Synchronized
    fun confirmedAttempt(): Attempt<T>? = confirmed
}
