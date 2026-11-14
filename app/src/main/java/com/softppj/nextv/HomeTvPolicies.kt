package com.softppj.nextv

import android.view.KeyEvent

internal data class ChannelIndexSnapshot(
    val indicesByKey: Map<String, List<Int>>,
    val representatives: List<Int>,
    val liveRepresentatives: List<Int>,
    val perPlaylistNumbers: Map<String, Int>,
    val continuousNumbers: Map<String, Int>
)

internal object HomeTvPolicies {
    fun isRemoteSelectKey(keyCode:Int):Boolean = keyCode in setOf(
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_SELECT
    )

    fun isRemoteLeftKey(keyCode:Int):Boolean =
        keyCode==KeyEvent.KEYCODE_DPAD_LEFT || keyCode==KeyEvent.KEYCODE_MEDIA_REWIND

    fun isRemoteRightKey(keyCode:Int):Boolean =
        keyCode==KeyEvent.KEYCODE_DPAD_RIGHT || keyCode==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD

    /** Map remote LEFT/RIGHT variants to DPAD events while a drawer owns navigation. */
    fun drawerNavigationKey(keyCode:Int):Int? = when(keyCode){
        KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT -> keyCode
        KeyEvent.KEYCODE_MEDIA_REWIND -> KeyEvent.KEYCODE_DPAD_LEFT
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> KeyEvent.KEYCODE_DPAD_RIGHT
        else -> null
    }

    /** Preserve repeat, long-press, device and key-up metadata when mapping TV transport keys. */
    fun translateDrawerEvent(event: KeyEvent, keyCode: Int): KeyEvent =
        if (event.keyCode == keyCode) event else KeyEvent(
            event.downTime, event.eventTime, event.action, keyCode,
            event.repeatCount, event.metaState, event.deviceId,
            event.scanCode, event.flags, event.source
        )

    /** DPAD events must reach the focused ListView so its selection and click state stay in sync. */
    fun shouldDeferDrawerNavigation(keyCode:Int):Boolean = keyCode in setOf(
        KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT
    )

    /** Keep BACK semantics separate from SELECT to avoid accidental channel activation. */
    fun isRemoteBackKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE

    /** Never treat key-up, repeated key-down or long-press as a fresh click. */
    fun isFreshRemotePress(action: Int, repeatCount: Int, isLongPress: Boolean): Boolean =
        action == KeyEvent.ACTION_DOWN && repeatCount == 0 && !isLongPress

    /** A single-channel surfing list must not tear down healthy playback on every UP/DOWN. */
    fun shouldRetuneSurfingChannel(currentIndex:Int,nextIndex:Int,playerAttached:Boolean):Boolean =
        currentIndex!=nextIndex || !playerAttached

    /** Resume is permitted only after entitlement and the parental gate pass. */
    fun mayAutoResume(
        licensed: Boolean,
        safeStartup: Boolean,
        parentalAllowed: Boolean,
        channelAvailable: Boolean
    ): Boolean = licensed && !safeStartup && parentalAllowed && channelAvailable

    /** Prevent stale asynchronous playback results from replacing a newer tune. */
    fun isCurrentPlaybackResult(
        requestGeneration: Int,
        activeGeneration: Int,
        activityActive: Boolean
    ): Boolean = activityActive && requestGeneration == activeGeneration

    /** Ignore Playing callbacks from a previous URL or a replaced playback session. */
    fun mayConfirmPlayback(
        reportedUrl:String,
        activeUrl:String?,
        screenActive:Boolean,
        teardownInProgress:Boolean
    ):Boolean = screenActive && !teardownInProgress && activeUrl==reportedUrl

    /** A probed alternative becomes a manual preference only on its own
     * confirmed foreground tune, never on probe success or an older callback. */
    fun mayCommitAlternative(
        pendingGeneration:Int,
        activeGeneration:Int,
        pendingKey:String?,
        activeKey:String,
        pendingUrl:String?,
        activeUrl:String,
        pendingEngine:String?,
        activeEngine:String
    ):Boolean = pendingGeneration>=0 && pendingGeneration==activeGeneration &&
        pendingKey==activeKey && pendingUrl==activeUrl && pendingEngine==activeEngine

    /** Network reconnect may resume only the still-selected playback session. */
    fun mayResumeNetworkRecovery(
        requestedIndex:Int,
        activeIndex:Int,
        requestedGeneration:Int,
        activeGeneration:Int,
        sameScreen:Boolean,
        taskOwned:Boolean,
        autoReconnect:Boolean
    ):Boolean = requestedIndex==activeIndex &&
        requestedGeneration==activeGeneration && sameScreen && taskOwned &&
        autoReconnect

    /** Audio-only confirmation is safe only after VLC has positively enumerated no video tracks. */
    fun isKnownAudioOnly(videoTrackCount:Int?,audioTrackCount:Int?):Boolean =
        videoTrackCount==0 && (audioTrackCount ?: 0)>0

    fun channelKey(channel: Channel): String =
        channel.mediaType + "|" +
            channel.source.trim().lowercase(java.util.Locale.ROOT) + "|" +
            channel.name.trim().lowercase(java.util.Locale.ROOT) + "|" +
            channel.group.trim().lowercase(java.util.Locale.ROOT)

    /** Build a logical-channel index in one ordered pass; alternate URLs share a number. */
    fun buildChannelIndex(channels: List<Channel>): ChannelIndexSnapshot {
        val groups = linkedMapOf<String, MutableList<Int>>()
        channels.forEachIndexed { index, channel ->
            groups.getOrPut(channelKey(channel)) { mutableListOf() }.add(index)
        }
        val representatives = groups.values.map { it.first() }
        val live = representatives.filter { channels[it].mediaType == "live" }

        val counts = mutableMapOf<String, Int>()
        val perPlaylist = LinkedHashMap<String, Int>(live.size)
        live.forEach { index ->
            val channel = channels[index]
            val playlistKey = channel.source.trim().lowercase(java.util.Locale.ROOT)
            val number = (counts[playlistKey] ?: 0) + 1
            counts[playlistKey] = number
            perPlaylist[channelKey(channel)] = number
        }
        val continuous = LinkedHashMap<String, Int>(live.size)
        live.forEachIndexed { position, index ->
            continuous[channelKey(channels[index])] = position + 1
        }
        return ChannelIndexSnapshot(groups, representatives, live, perPlaylist, continuous)
    }

    fun resolvePlaylistSource(
        selected:String?,
        current:String?,
        enabled:List<String>,
        available:Set<String>
    ):String?{
        val enabledSet=enabled.toHashSet()
        return selected?.takeIf{it in enabledSet && it in available}
            ?: current?.takeIf{it in enabledSet && it in available}
            ?: enabled.firstOrNull{it in available}
    }

    /** Shared playlist boundary for numeric tuning, preview and channel surfing. */
    fun scopedChannelIndices(channels:List<Channel>, indices:List<Int>, source:String?):List<Int> =
        if(source==null) emptyList()
        else indices.filter { it in channels.indices && channels[it].source==source }

    /** Favorite collections intentionally span playlists but never include non-members. */
    fun favoriteGroupIndices(channels:List<Channel>,indices:List<Int>,members:Set<String>):List<Int> =
        indices.filter{it in channels.indices && channelKey(channels[it]) in members}

    /** Give live IPTV a longer first-frame grace; HLS/DASH VOD also need segment startup time. */
    fun startupTimeoutMs(mediaType: String, streamKind: String): Long = when {
        mediaType == "live" -> 30_000L
        streamKind == "hls" || streamKind == "dash" || streamKind == "smooth" -> 25_000L
        else -> 15_000L
    }

    fun shouldAttemptNativeYoutube(
        eligibleVideo: Boolean,
        alreadyAttempted: Boolean,
        hasResolvedStream: Boolean,
        forceWeb: Boolean
    ): Boolean = eligibleVideo && !alreadyAttempted && !hasResolvedStream && !forceWeb

    fun shouldFallbackNativeYoutube(
        hasResolvedStream: Boolean,
        isCurrentChannel: Boolean,
        isSameScreen: Boolean
    ): Boolean = hasResolvedStream && isCurrentChannel && isSameScreen

    fun isReachableHttpResponse(statusCode: Int, contentType: String?): Boolean =
        statusCode in 200..299 && !contentType.orEmpty().contains("text/html", ignoreCase = true)

    fun shouldAutoUnhide(enabled: Boolean, isHidden: Boolean, playbackConfirmed: Boolean): Boolean =
        enabled && isHidden && playbackConfirmed

    fun <T> matchEpg(guide: Map<String, List<T>>, epgId: String, channelName: String): List<T> {
        val id = epgId.trim().lowercase(java.util.Locale.ROOT)
        val name = channelName.trim().lowercase(java.util.Locale.ROOT)
        return guide[id]?.takeIf { it.isNotEmpty() } ?: guide[name] ?: emptyList()
    }

    fun nextClockValue(values: List<String>, currentIndex: Int): Pair<String, Int>? {
        if (values.isEmpty()) return null
        val index = ((currentIndex % values.size) + values.size) % values.size
        return values[index] to ((index + 1) % values.size)
    }

    /** Keep Auto/Best Available uncapped; only explicit user quality presets set a height ceiling. */
    fun libVlcAdaptiveOptions(quality: String): List<String> = when (quality) {
        "Data Saver (360p)" -> listOf(":adaptive-maxheight=360", ":adaptive-logic=highest")
        "Standard (480p)" -> listOf(":adaptive-maxheight=480", ":adaptive-logic=highest")
        "HD (720p)" -> listOf(":adaptive-maxheight=720", ":adaptive-logic=highest")
        "Full HD (1080p)" -> listOf(":adaptive-maxheight=1080", ":adaptive-logic=highest")
        // Keep LibVLC's native adaptive selection for Auto. Forcing the highest
        // bitrate at startup made marginal live streams repeatedly rebuffer.
        "Auto", "Best Available" -> emptyList()
        else -> emptyList()
    }

    /** Preserve the V6-supported LibVLC HTTP headers; arbitrary Origin headers are not portable across VLC 3.x builds. */
    fun libVlcHttpHeaderOptions(headers:Map<String,String>):List<String> = buildList {
        fun value(vararg names:String):String? = headers.entries.firstOrNull{entry->
            names.any{entry.key.equals(it,true)}
        }?.value?.takeIf{it.isNotBlank() && '\r' !in it && '\n' !in it}
        value("User-Agent","http-user-agent")?.let{add(":http-user-agent="+it)}
        value("Referer","Referrer","http-referrer","http-referer")?.let{add(":http-referrer="+it)}
    }

    /** VLC's adjust filter is configured on the LibVLC instance, before the player opens a stream. */
    fun libVlcPictureOptions(
        mode: String,
        brightness: Float,
        contrast: Float,
        saturation: Float
    ): List<String> = if (mode == "Standard") emptyList() else listOf(
        "--video-filter=adjust",
        "--brightness=$brightness",
        "--contrast=$contrast",
        "--saturation=$saturation"
    )

    /** Video streams need a presented frame; audio-only streams need known tracks and startup progress. */
    fun shouldConfirmVlcPlayback(
        videoExpected: Boolean,
        framePresented: Boolean,
        videoTracksKnown: Boolean,
        playbackAgeMs: Long,
        minimumAudioOnlyAgeMs: Long = 1_500L
    ): Boolean = if (videoExpected) framePresented
        else videoTracksKnown && playbackAgeMs >= minimumAudioOnlyAgeMs

    fun isVideoOutputStalled(
        isPlaying: Boolean,
        videoExpected: Boolean,
        videoFrameAgeMs: Long,
        mediaProgressAgeMs: Long,
        timeoutMs: Long = 20_000L
    ): Boolean {
        if (!isPlaying) return false
        return if (videoExpected) videoFrameAgeMs >= timeoutMs
        else mediaProgressAgeMs >= timeoutMs
    }
}
