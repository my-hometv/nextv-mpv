package com.softppj.nextv

import android.view.Surface

/**
 * Version-specific adapter from the session controller to the Android AIDL client.
 * Availability is established by the remote service, never by a version label.
 *
 * A future relocated-native service may advertise a runtime only after native
 * loading and playback capability checks succeed.
 */
internal class RemoteIsolatedVlcRuntime(
    override val version: String,
    private val client: IsolatedVlcServiceClient
) : IsolatedVlcRuntime {
    override val engineId: String =
        requireNotNull(PlaybackEnginePolicy.versionEngineId(version)) {
            "Unsupported LibVLC version: $version"
        }

    override fun isAvailable(): Boolean = client.isReady(engineId)

    override fun start(url: String, generation: Long): Boolean =
        client.start(engineId, url, generation)

    override fun startWithSurface(url: String, generation: Long, surface: Surface): Boolean =
        client.startWithSurface(engineId, url, generation, surface)

    override fun stop(generation: Long) = client.stop(generation)

    // Client is shared by all version adapters; ownership stays with the
    // registry/controller, whose release is terminal.
    override fun release() = Unit
}
