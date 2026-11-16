package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test

class PlaylistParserTest {
    @Test fun parsesNamesGroupsAndHttpsStreams() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Fallback" group-title="Malayalam" tvg-logo="https://example.org/logo.png",Surya TV
            https://example.org/channel.m3u8
            #EXTINF:-1,Second TV
            https://example.org/second.mpd
        """.trimIndent()
        val channels = PlaylistParser.parse(m3u)
        assertEquals(2, channels.size)
        assertEquals("Surya TV", channels[0].name)
        assertEquals("Malayalam", channels[0].group)
        assertEquals("https://example.org/logo.png", channels[0].logo)
        assertEquals("Second TV", channels[1].name)
    }

    @Test fun rejectsUnsafeOrUnsupportedUrls() {
        assertFalse(PlaylistParser.validHttpsUrl("http://example.org/live.m3u8"))
        assertFalse(PlaylistParser.validHttpsUrl("https://user:pass@example.org/live"))
        assertFalse(PlaylistParser.validHttpsUrl("javascript:alert(1)"))
        assertFalse(PlaylistParser.validHttpsUrl("https://example.org/live\\path"))
        assertEquals(1, PlaylistParser.parse("#EXTM3U\nhttp://bad.example/live\nhttps://good.example/live").size)
    }

    @Test fun boundsChannelCountAndRejectsOversizedInput() {
        assertEquals(2, PlaylistParser.parse("https://one.example/live\nhttps://two.example/live\nhttps://three.example/live", 2).size)
        assertTrue(PlaylistParser.parse("x".repeat(2_000_001)).isEmpty())
    }
}
