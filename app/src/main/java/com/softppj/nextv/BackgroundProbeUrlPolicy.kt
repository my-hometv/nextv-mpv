package com.softppj.nextv

import java.net.URI
import java.util.Locale

/** Allow supported network stream protocols for independent LibVLC probes,
 * without mistaking YouTube pages for direct media sources. */
internal object BackgroundProbeUrlPolicy {
    /** Canonical comparison key, without rewriting the URL sent to the player. */
    fun probeIdentity(url: String): String = url.trim()

    /** A logical channel can have several alternative URLs. Never probe any
     * of those alternatives while that channel is the foreground selection. */
    fun shouldProbeOtherChannel(
        foregroundKey: String?, foregroundUrl: String?,
        candidateKey: String, candidateUrl: String
    ): Boolean {
        val normalizedCandidateUrl = candidateUrl.trim()
        if (normalizedCandidateUrl.isEmpty()) return false
        // Playlist imports may pad URLs or channel identifiers with whitespace.
        // Never accidentally probe the foreground stream because of that padding.
        if (foregroundUrl != null && normalizedCandidateUrl == foregroundUrl.trim()) return false
        if (foregroundKey != null && candidateKey.trim() == foregroundKey.trim()) return false
        return true
    }

    /** Only safe direct HTTP media URLs are eligible for the decoder-free check.
     * Other schemes are unknown, never automatically treated as failed. */
    fun isHttpProbeCandidate(url:String):Boolean {
        if(!isNativeProbeCandidate(url))return false
        val scheme=runCatching{URI(url).scheme?.lowercase(Locale.ROOT)}.getOrNull()
        return scheme=="http" || scheme=="https"
    }

    /** A bounded HTTP range check can legitimately return 206 Partial Content.
     * Reject HTML landing pages and redirects that did not resolve to media. */
    fun isReachableHttpResponse(status: Int, contentType: String?): Boolean =
        (status == 200 || status == 206) &&
            !contentType.orEmpty().contains("text/html", ignoreCase = true)

    /** Background HTTP checks are only transport reachability, not decoder validation.
     * Reject obvious non-stream responses even if a server replies with HTTP 200. */
    fun isLikelyMediaResponse(status:Int,contentType:String?):Boolean {
        if(!isReachableHttpResponse(status,contentType))return false
        val mime=contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return mime !in setOf(
            "application/json","application/problem+json","application/xml",
            "text/xml","text/plain","text/css","text/javascript",
            "application/javascript","application/x-www-form-urlencoded"
        )
    }

    /** Media3's standard HTTP progressive/HLS/DASH and RTSP sources are
     * supported without an optional RTMP extension. Keep RTMP on LibVLC. */
    fun isMedia3ProbeCandidate(rawUrl: String): Boolean {
        if (!isNativeProbeCandidate(rawUrl)) return false
        val scheme = runCatching { URI(rawUrl).scheme?.lowercase(Locale.ROOT) }.getOrNull()
        return scheme == "http" || scheme == "https" || scheme == "rtsp"
    }

    fun isNativeProbeCandidate(rawUrl: String): Boolean {
        return try {
            val uri = URI(rawUrl)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
            if (host.isEmpty()) return false
            // RTSP and RTMP families are common IPTV camera/live sources that LibVLC
            // can probe directly. Do not admit file, content or script URIs.
            if (scheme !in setOf("http", "https", "rtsp", "rtsps", "rtmp", "rtmps", "rtmpt")) return false
            // URI.host may accept an authority with user-info. Never pass a
            // credential-bearing URL to a background native probe, where the
            // stream checker has no reason to handle embedded credentials.
            if (uri.rawUserInfo != null) return false
            // Reject explicit empty ports rather than silently using defaults.
            if (uri.rawAuthority?.endsWith(":") == true) return false
            if (uri.port !in -1..65535 || uri.port == 0) return false
            // URI.port can return -1 for malformed explicit ports, which is
            // indistinguishable from an omitted port unless authority is checked.
            val authority = uri.rawAuthority ?: return false
            val hostPort = authority.substringAfterLast('@')
            val suffix = if (hostPort.startsWith("[")) {
                val bracket = hostPort.indexOf(']')
                if (bracket < 0) return false
                hostPort.substring(bracket + 1)
            } else {
                val colon = hostPort.lastIndexOf(':')
                if (colon < 0) "" else hostPort.substring(colon)
            }
            if (suffix.isNotEmpty()) {
                if (!suffix.startsWith(":")) return false
                val explicitPort = suffix.drop(1).toIntOrNull() ?: return false
                if (explicitPort !in 1..65535) return false
            }
            if (host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtu.be" || host == "youtube-nocookie.com" ||
                host.endsWith(".youtube-nocookie.com")) return false
            true
        } catch (_: Exception) {
            false
        }
    }
}
