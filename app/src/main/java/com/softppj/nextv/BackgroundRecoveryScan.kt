package com.softppj.nextv

/**
 * Independent background scan: foreground playback success must not stop discovery of
 * the next playable channel. Each channel owns its own recovery generation.
 *
 * This class only selects probes. Callers must perform playback checks and report
 * results; no native player or Media3 implementation is changed here.
 */
internal class BackgroundRecoveryScan<T>(
    private val urlOf: (T) -> String,
    private val supports: (T, String) -> Boolean
) {
    data class Channel<T>(val id: String, val urls: List<T>)
    data class Probe<T>(val channelId: String, val attempt: RecoveryAttemptSession.Attempt<T>)

    private val session = RecoveryAttemptSession(urlOf, supports)
    private var channels: List<Channel<T>> = emptyList()
    private var engines: List<String> = emptyList()
    private var index = 0
    private var active: Probe<T>? = null
    private var found: String? = null
    private var foundProbe: Probe<T>? = null

    /** Begin searching after the current channel, never probing the playing channel. */
    @Synchronized
    fun start(nextChannels: List<Channel<T>>, enabledEngines: List<String>, playingChannelId: String? = null) {
        val foregroundId = playingChannelId?.trim()
        channels = nextChannels.asSequence()
            .filter { it.id.isNotBlank() && it.id.trim() != foregroundId }
            .distinctBy { it.id.trim() }
            .map { it.copy(urls = it.urls.toList()) }
            .toList()
        engines = enabledEngines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        index = 0
        active = null
        found = null
        foundProbe = null
        session.beginChannel(emptyList(), emptyList())
        enterChannel()
    }

    @Synchronized
    private fun enterChannel() {
        if (index < channels.size) session.beginChannel(channels[index].urls, engines)
    }

    @Synchronized
    fun next(): Probe<T>? {
        if (found != null || active != null) return null
        while (index < channels.size) {
            val attempt = session.next()
            if (attempt != null) return Probe(channels[index].id, attempt).also { active = it }
            index++
            enterChannel()
        }
        return null
    }

    /** A late or forged completion cannot mark another channel as working. */
    @Synchronized
    fun finish(probe: Probe<T>, success: Boolean): Boolean {
        if (active !== probe || !session.finish(probe.attempt, success)) return false
        active = null
        if (success) {
            found = probe.channelId
            foundProbe = probe
        }
        return true
    }

    @Synchronized
    fun expire(probe: Probe<T>): Boolean {
        if (active !== probe || !session.expire(probe.attempt)) return false
        active = null
        return true
    }

    @Synchronized
    fun cancel() {
        session.beginChannel(emptyList(), emptyList())
        channels = emptyList()
        engines = emptyList()
        active = null
        found = null
        foundProbe = null
        index = 0
    }

    /**
     * Apply engine-disable changes to an active background discovery without
     * restarting from the first URL. An in-flight probe on a disabled engine
     * is invalidated; its eventual native callback will be rejected.
     *
     * A previously discovered result from a now-disabled engine is withdrawn
     * so the host cannot navigate to an unusable playback backend.
     */
    @Synchronized
    fun retainEnabledEngines(enabledEngines: Collection<String>): Boolean {
        val allowed = enabledEngines.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val retained = engines.filter { it in allowed }
        if (retained == engines) return false
        engines = retained
        val oldActive = active
        session.retainEnabledEngines(retained)
        if (oldActive != null && oldActive.attempt.engine !in allowed) {
            active = null
        }
        if (foundProbe?.attempt?.engine !in allowed) {
            found = null
            foundProbe = null
        }
        return true
    }

    @Synchronized
    fun discoveredChannelId(): String? = found

    /** Retain the proven URL/engine pair so callers need not guess or retry it. */
    @Synchronized
    fun discoveredProbe(): Probe<T>? = foundProbe
}
