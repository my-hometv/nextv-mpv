package com.softppj.nextv

/** Fail-safe playback selection: a corrupted/all-disabled preference set must
 * not start a disabled engine. Media3 is the recovery engine in that case. */
internal object PlaybackEnginePolicy {
    private val supported = listOf("Media3", "LibVLC")
    // Keep legacy version IDs recognized for migration, but only the packaged
    // LibVLC engine is selectable in the production APK.
    private val versionIds = mapOf(
        "3.5.1" to "LibVLC-3.5.1",
        "3.7.7" to "LibVLC-3.7.7",
        "4.0.0-eap29" to "LibVLC-4.0.0-eap29"
    )
    fun versionEngineId(version: String): String? = versionIds[version]
    fun selectableEngines(packagedVersion: String): List<String> =
        if (packagedVersion == "3.5.1") supported else listOf("Media3")
    fun packagedLibVlcVersion(version: String): String? =
        version.takeIf { it == "3.5.1" }
    fun verifiedVersionEngines(
        packagedRuntimeIds: Set<String>, verifiedRuntimeIds: Set<String>
    ): List<String> = versionIds.values.filter {
        it in packagedRuntimeIds && it in verifiedRuntimeIds
    }
    fun isVersionRuntimeReady(version: String, verifiedRuntimeIds: Set<String>): Boolean =
        versionEngineId(version)?.let { it in verifiedRuntimeIds } ?: false
    fun isolatedSelectableEngines(verifiedRuntimeIds: Set<String>): List<String> =
        listOf("Media3") + versionIds.values.filter { it in verifiedRuntimeIds }
    fun selectableWithRuntimeVerification(
        packagedVersion: String,
        packagedRuntimeIds: Set<String>,
        verifiedRuntimeIds: Set<String>
    ): List<String> = selectableEngines(packagedVersion) +
        verifiedVersionEngines(packagedRuntimeIds, verifiedRuntimeIds)

    /** Map legacy preference to the selected flavor's packaged LibVLC only.
     * Never silently remap a requested isolated engine to a different version. */
    fun resolveRequestedEngine(requested: String, available: List<String>): String {
        // An untrusted preference or availability list must never activate an
        // unknown backend. Versioned runtimes are allowed only by stable ID.
        val requestedId = requested.trim()
        val availableIds = available.map { it.trim() }.toSet()
        if (requestedId in availableIds && (requestedId in supported || requestedId in versionIds.values)) return requestedId
        return "Media3"
    }
    fun normalized(enabled: List<String>): List<String> {
        val requested = enabled.map { it.trim() }.toSet()
        val valid = supported.filter { it in requested }
        return if (valid.isEmpty()) listOf("Media3") else valid
    }
    /** Runtime-scoped preferences: only currently packaged and verified IDs
     * can be enabled, including versioned LibVLC engines.
     */
    fun normalized(enabled: List<String>, available: List<String>): List<String> {
        val recognized = supported + versionIds.values
        val requested = enabled.map { it.trim() }.toSet()
        val availableIds = available.map { it.trim() }.filter { it in recognized }.distinct()
        val valid = availableIds.filter { it in requested }
        return if (valid.isEmpty() && "Media3" in availableIds) listOf("Media3") else valid
    }
    fun isEnabled(name: String, enabled: List<String>, available: List<String>): Boolean =
        name.trim() in normalized(enabled, available)

    fun isEnabled(name: String, enabled: List<String>): Boolean =
        name.trim() in normalized(enabled)
    fun firstEnabled(preferred: List<String>): String =
        preferred.map { it.trim() }.firstOrNull { it in supported || it in versionIds.values } ?: "Media3"
}
