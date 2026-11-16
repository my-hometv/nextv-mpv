package com.softppj.nextv

/**
 * Pure selection policy for the stable Media3 DASH investigation.
 * This class does not activate or modify the MPV playback engine.
 */
internal object DashAudioFallbackPolicy {
    data class Candidate(
        val language: String?,
        val signature: String,
        val supported: Boolean,
        val selected: Boolean
    )

    /**
     * A saved override is only safe while the corresponding track is supported.
     * Prefer the saved exact track, then the requested language, then an already
     * selected supported track, then any supported track. Never choose unsupported.
     */
    fun choose(candidates: List<Candidate>, savedSignature: String?, preferredLanguage: String?): Int? {
        val supported = candidates.indices.filter { candidates[it].supported }
        if (supported.isEmpty()) return null
        if (!savedSignature.isNullOrBlank()) {
            supported.firstOrNull { candidates[it].signature == savedSignature }?.let { return it }
        }
        if (!preferredLanguage.isNullOrBlank()) {
            supported.firstOrNull {
                candidates[it].language?.lowercase()?.substringBefore('-') ==
                    preferredLanguage.lowercase().substringBefore('-')
            }?.let { return it }
        }
        return supported.firstOrNull { candidates[it].selected } ?: supported.first()
    }
}
