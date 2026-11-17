package com.softppj.nextv

/** A single secondary decoder may be rotated once to the opposite neighbour. */
internal object AdjacentPreloadPolicy {
    fun mayRotate(
        alreadyRotated: Boolean,
        currentGeneration: Boolean,
        foregroundPlaying: Boolean,
        appInForeground: Boolean,
        enabled: Boolean,
        previewSelected: Boolean,
        multiViewActive: Boolean
    ): Boolean = !alreadyRotated && currentGeneration && foregroundPlaying &&
        appInForeground && enabled && !previewSelected && !multiViewActive
}
