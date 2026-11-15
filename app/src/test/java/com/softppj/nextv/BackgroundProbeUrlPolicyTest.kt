package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class BackgroundProbeUrlPolicyTest {
    @Test fun mpvHttpCheckerAcceptsDirectStreamsButNotNativeOnlyProtocols() {
        assertTrue(BackgroundProbeUrlPolicy.isHttpProbeCandidate(
            "https://iptv.example/live/channel.m3u8?token=AbC"))
        assertTrue(BackgroundProbeUrlPolicy.isHttpProbeCandidate(
            "http://iptv.example/live/channel.ts"))
        assertFalse(BackgroundProbeUrlPolicy.isHttpProbeCandidate(
            "rtsp://iptv.example/live"))
        assertFalse(BackgroundProbeUrlPolicy.isHttpProbeCandidate(
            "rtmp://iptv.example/live"))
        assertFalse(BackgroundProbeUrlPolicy.isHttpProbeCandidate(
            "file:///sdcard/video.ts"))
        assertFalse(BackgroundProbeUrlPolicy.isHttpProbeCandidate(
            "https://user:password@iptv.example/live.ts"))
        assertFalse(BackgroundProbeUrlPolicy.isHttpProbeCandidate(
            "https://youtube.com/watch?v=abc"))
    }

    @Test fun boundedHttpChecksAcceptPartialContentAndRejectHtml() {
        assertTrue(BackgroundProbeUrlPolicy.isReachableHttpResponse(200,"video/mp2t"))
        assertTrue(BackgroundProbeUrlPolicy.isReachableHttpResponse(206,"application/octet-stream"))
        assertFalse(BackgroundProbeUrlPolicy.isReachableHttpResponse(204,null))
        assertFalse(BackgroundProbeUrlPolicy.isReachableHttpResponse(205,"video/mp2t"))
        assertFalse(BackgroundProbeUrlPolicy.isReachableHttpResponse(201,"video/mp2t"))
        assertFalse(BackgroundProbeUrlPolicy.isReachableHttpResponse(302,"video/mp2t"))
        assertFalse(BackgroundProbeUrlPolicy.isReachableHttpResponse(403,"video/mp2t"))
        assertFalse(BackgroundProbeUrlPolicy.isReachableHttpResponse(500,null))
        assertFalse(BackgroundProbeUrlPolicy.isReachableHttpResponse(200,"TEXT/HTML; charset=utf-8"))
        assertFalse(BackgroundProbeUrlPolicy.isReachableHttpResponse(206,"text/html"))
    }

    @Test fun backgroundHttpProbesRejectCredentialBearingAndMalformedUrls() {
        for(url in listOf(
            "https://user:password@iptv.example/live.m3u8",
            "https://iptv.example:0/live.ts",
            "https://youtube.com/watch?v=abc",
            "https:///missing-host"
        )) assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "https://iptv.example:8443/live.m3u8"))
    }

    @Test fun rejectsApiAndErrorPagesFromBackgroundMediaChecks() {
        for(type in listOf(
            "application/json","application/problem+json; charset=utf-8",
            "application/xml","text/plain","text/javascript","text/css",
            "text/html","application/x-www-form-urlencoded"
        )) assertFalse(BackgroundProbeUrlPolicy.isLikelyMediaResponse(200,type))
    }

    @Test fun acceptsCommonMediaManifestAndTransportMimeTypes() {
        for(type in listOf(
            "application/vnd.apple.mpegurl","application/x-mpegURL",
            "application/dash+xml","video/mp2t","video/mp4",
            "application/octet-stream"
        )) assertTrue(BackgroundProbeUrlPolicy.isLikelyMediaResponse(206,type))
        assertFalse(BackgroundProbeUrlPolicy.isLikelyMediaResponse(204,"video/mp2t"))
        assertFalse(BackgroundProbeUrlPolicy.isLikelyMediaResponse(403,"video/mp2t"))
    }

    @Test fun probeIdentityDeduplicatesPaddedUrlsWithoutChangingCaseOrQuery() {
        assertEquals("https://iptv.example/live?token=AbC",
            BackgroundProbeUrlPolicy.probeIdentity("  https://iptv.example/live?token=AbC  "))
        assertEquals(BackgroundProbeUrlPolicy.probeIdentity(" stream "),
            BackgroundProbeUrlPolicy.probeIdentity("stream"))
        assertFalse(BackgroundProbeUrlPolicy.probeIdentity("stream") ==
            BackgroundProbeUrlPolicy.probeIdentity("STREAM"))
    }

    @Test fun acceptsNormalHttpStreamsEvenWhenQueryMentionsYoutube() {
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "https://iptv.example/live/channel.m3u8?ref=youtube.com"))
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "http://iptv.example/youtu.be/channel.ts"))
    }

    @Test fun rejectsYoutubePagesAndSubdomains() {
        for (url in listOf(
            "https://youtube.com/watch?v=abc",
            "https://www.youtube.com/live/abc",
            "https://m.youtube.com/watch?v=abc",
            "https://youtu.be/abc",
            "https://www.youtube-nocookie.com/embed/abc"
        )) assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
    }

    @Test fun rejectsMalformedOrNonNetworkUrls() {
        for (url in listOf("", "not a url", "file:///movie.mp4",
            "javascript:alert(1)", "https:///missing-host", "ftp://iptv.example/a"))
            assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
    }

    @Test fun doesNotMistakeOtherHostsForYoutube() {
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "https://notyoutube.com/live.m3u8"))
    }
    @Test fun rejectsCredentialBearingStreamUrlsFromNativeBackgroundChecks() {
        for (url in listOf(
            "https://user:password@iptv.example/live.m3u8",
            "http://token@iptv.example/live.ts",
            "https://user%40domain:secret@iptv.example/stream"
        )) assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
    }

    @Test fun rejectsZeroPortAndAcceptsOrdinaryNonDefaultStreamPorts() {
        assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "http://iptv.example:0/live.m3u8"))
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "http://iptv.example:8080/live.m3u8"))
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "https://iptv.example:8443/live.ts"))
    }

    @Test fun acceptsRtspCameraAndRtmpLiveStreamSources() {
        for (url in listOf(
            "rtsp://camera.example:554/live/ch1",
            "RTSP://camera.example/live",
            "rtmp://live.example:1935/app/stream",
            "https://iptv.example/live.m3u8"
        )) assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
    }

    @Test fun rejectsUnsafeOrUnsupportedSchemesEvenWithValidHost() {
        for (url in listOf(
            "file://iptv.example/movie.mp4",
            "javascript://iptv.example/live",
            "content://iptv.example/live",
            "ftp://iptv.example/live",
            "rtsp://user:password@camera.example/live",
            "rtmp://live.example:0/app/stream"
        )) assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
    }

    @Test fun libVlcCanDiscoverSecureRtspAndRtmpProtocolVariants() {
        for (url in listOf(
            "rtsps://camera.example:322/live",
            "rtmps://live.example/app/channel",
            "rtmpt://live.example/app/channel"
        )) {
            assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
        }
    }

    @Test fun secureProtocolVariantsRetainCredentialAndHostRestrictions() {
        for (url in listOf(
            "rtsps://user:secret@camera.example/live",
            "rtmps://user:secret@live.example/app",
            "rtmpt:///missing-host",
            "rtmps://live.example:0/app"
        )) assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
    }

    @Test fun trailingDotCannotBypassYoutubeHostExclusion() {
        for (url in listOf(
            "https://youtube.com./watch?v=123",
            "https://www.youtube.com./live/abc",
            "https://youtu.be./abc",
            "https://www.youtube-nocookie.com./embed/abc"
        )) {
            assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
        }
    }

    @Test fun trailingDotOnOrdinaryStreamHostRemainsAllowed() {
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "https://iptv.example./live.m3u8"))
        assertTrue(BackgroundProbeUrlPolicy.isMpvProbeCandidate(
            "https://iptv.example./live.m3u8"))
    }

    @Test fun invalidExplicitPortsAreRejectedInsteadOfTreatedAsMissing() {
        for (url in listOf(
            "http://iptv.example:65536/live.ts",
            "https://iptv.example:999999/live.m3u8",
            "rtsp://camera.example:abc/live",
            "rtmp://live.example:-1/app/stream",
            "https://[::1]:65536/live"
        )) {
            assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
        }
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "https://[::1]:8443/live"))
        assertTrue(BackgroundProbeUrlPolicy.isNativeProbeCandidate(
            "http://iptv.example:8080/live.ts"))
        assertTrue(BackgroundProbeUrlPolicy.isMpvProbeCandidate(
            "http://iptv.example:8080/live.ts"))
    }

    @Test fun explicitEmptyPortIsRejectedForAllBackgroundEngines() {
        for (url in listOf(
            "http://iptv.example:/live.ts",
            "rtsp://camera.example:/live",
            "rtmp://live.example:/app/stream"
        )) {
            assertFalse(BackgroundProbeUrlPolicy.isNativeProbeCandidate(url))
        }
    }

    @Test fun backgroundDiscoverySkipsEveryAlternativeOfForegroundChannel() {
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "sports-one", "https://a.example/live", "sports-one", "https://b.example/live"))
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "sports-one", "https://a.example/live", "sports-two", "https://a.example/live"))
        assertTrue(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "sports-one", "https://a.example/live", "sports-two", "https://b.example/live"))
    }

    @Test fun paddedForegroundUrlsAndChannelKeysCannotBeProbed() {
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "sports", " https://a.example/live ", "news", "https://a.example/live"))
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "sports", "https://a.example/live", "news", " https://a.example/live "))
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            " sports ", "https://a.example/live", "sports", "https://b.example/live"))
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "sports", "https://a.example/live", " sports ", "https://b.example/live"))
        assertTrue(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "sports", "https://a.example/live", "news", "https://b.example/live"))
    }

    @Test fun backgroundDiscoveryWorksWithoutForegroundAndSkipsEmptyUrls() {
        assertTrue(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            null, null, "next", "rtsp://camera.example/live"))
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            null, null, "next", ""))
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "playing", "http://playing.example/live", "next", " "))
    }

    @Test fun queuedProbeMustBeRecheckedAfterForegroundChannelSwitch() {
        val queuedKey = "news"
        val queuedUrl = "https://news.example/live"
        assertTrue(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "sports", "https://sports.example/live", queuedKey, queuedUrl))
        // The user changes to the queued channel before dispatch.
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "news", "https://news.example/backup", queuedKey, queuedUrl))
    }

    @Test fun completedProbeCannotUpdateReachabilityForNewForeground() {
        val probedKey = "movies"
        val probedUrl = "rtsp://movies.example/live"
        // The channel was background when its asynchronous check began.
        assertTrue(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "news", "https://news.example/live", probedKey, probedUrl))
        // Its result is stale for background reachability after selection.
        assertFalse(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            probedKey, "https://movies.example/alternative", probedKey, probedUrl))
        // A different channel's result remains eligible.
        assertTrue(BackgroundProbeUrlPolicy.shouldProbeOtherChannel(
            "news", "https://news.example/live", probedKey, probedUrl))
    }

}
