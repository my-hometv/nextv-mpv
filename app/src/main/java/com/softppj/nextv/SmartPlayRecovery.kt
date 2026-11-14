package com.softppj.nextv

/**
 * Pure recovery policy: never overrides a manually selected engine.
 * Rotate away from the confirmed engine after two retries, allowing two
 * attempts per alternate engine before advancing to the next candidate.
 */
internal object SmartPlayRecovery {
    // Bound native decoder resets when a confirmed stream remains unhealthy.
    const val MAX_AUTOMATIC_RECOVERY_ATTEMPTS = 12

    fun mayRetry(failedAttempts: Int): Boolean =
        failedAttempts in 1..MAX_AUTOMATIC_RECOVERY_ATTEMPTS

    fun nextEngine(
        enabled: Boolean,
        manualEngine: String?,
        confirmedEngine: String,
        availableEngines: List<String>,
        failedAttempts: Int
    ): String {
        // A saved engine can become unavailable when the user disables an
        // engine or a native backend fails to load. Never retry that backend.
        val engines = availableEngines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (engines.isEmpty()) return confirmedEngine
        val safeConfirmed = confirmedEngine.trim().takeIf { it in engines } ?: engines.first()
        val manualId = manualEngine?.trim()
        if (manualId != null && manualId in engines) return manualId
        if (!enabled || failedAttempts <= 2 || engines.size < 2) return safeConfirmed
        val base = engines.indexOf(safeConfirmed)
        val offset = 1 + (failedAttempts - 3) / 2
        return engines[(base + offset) % engines.size]
    }
}
