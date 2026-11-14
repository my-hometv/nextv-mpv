package com.softppj.nextv

/** Pure fallback selection. A broken URL or unavailable backend must not abort traversal. */
internal object AlternativeRecoveryPolicy {
    /** Engine-major ordering: E1/U1, E1/U2, E1/U3, then E2/U1, etc. */
    fun <T> nextEngineFirst(
        urls: List<T>,
        engines: List<String>,
        urlOf: (T) -> String,
        supports: (T, String) -> Boolean,
        attempted: Set<String>
    ): Pair<T, String>? {
        for (engine in engines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) for (candidate in urls) {
            val url = runCatching { urlOf(candidate) }.getOrNull() ?: continue
            if (url.isBlank()) continue
            val canonicalUrl = url.trim()
            if (attemptKey(canonicalUrl, engine) in attempted ||
                "$canonicalUrl|$engine" in attempted ||
                attemptKey(url, engine) in attempted || "$url|$engine" in attempted) continue
            if (!runCatching { supports(candidate, engine) }.getOrDefault(false)) continue
            return candidate to engine
        }
        return null
    }

    /**
     * Create a collision-free key for recovery sessions. The selector also accepts
     * legacy URL|engine attempt sets for compatibility with existing callers.
     * New callers should use this key to avoid delimiter collisions.
     */
    fun attemptKey(url: String, engine: String): String =
        "${url.length}:$url${engine.length}:$engine"

    /** A queued alternative may execute only for the exact playback snapshot. */
    fun mayApplyQueuedSwitch(
        sameActiveIndex: Boolean,
        sameScreen: Boolean,
        sameGeneration: Boolean,
        samePlaylist: Boolean,
        sameSourceUrl: Boolean,
        sameTargetUrl: Boolean
    ): Boolean = sameActiveIndex && sameScreen && sameGeneration &&
        samePlaylist && sameSourceUrl && sameTargetUrl

    /** Circular source navigation, skipping URLs that already failed this session. */
    fun <T> nextCircularUsable(
        candidates:List<T>,
        currentIndex:Int,
        failedUrls:Set<String>,
        urlOf:(T)->String
    ):Int? {
        if(candidates.size<2 || currentIndex !in candidates.indices)return null
        for(offset in 1 until candidates.size){
            val index=(currentIndex+offset)%candidates.size
            val url=runCatching { urlOf(candidates[index]).trim() }.getOrNull()
            if(!url.isNullOrEmpty() && url !in failedUrls)return index
        }
        return null
    }

    /** Ignore malformed or empty sources without allowing them to block healthy alternatives. */
    fun <T> nextUntried(
        candidates: List<T>,
        attemptedUrls: Set<String>,
        urlOf: (T) -> String
    ): T? = candidates.firstOrNull { candidate ->
        val url = runCatching { urlOf(candidate) }.getOrNull()
        url != null && url.isNotBlank() && url !in attemptedUrls && url.trim() !in attemptedUrls
    }
}
