package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class SmartLogoPolicyTest {
    @Test fun reusesLogoForHdVariant() {
        val channels = listOf(
            Channel("Surya TV HD", "https://example.com/one", "Malayalam", logo="https://example.com/surya.png", source="A"),
            Channel("Surya TV", "https://example.com/two", "Malayalam", source="A")
        )
        assertEquals("https://example.com/surya.png", SmartLogoPolicy.Index(channels).resolve(channels[1]))
    }

    @Test fun conflictingNamesDoNotGuessLogoAcrossSources() {
        val channels = listOf(
            Channel("News", "https://example.com/one", "News", logo="https://a.example/logo.png", source="A"),
            Channel("News", "https://example.com/two", "News", logo="https://b.example/logo.png", source="B"),
            Channel("News", "https://example.com/three", "News", source="C")
        )
        assertNull(SmartLogoPolicy.Index(channels).resolve(channels[2]))
    }

    @Test fun tvgIdMatchesEvenWhenNameDiffers() {
        val channels = listOf(
            Channel("Channel A", "https://example.com/a", "News", logo="https://a.example/a.png", epgId="a.tv"),
            Channel("A Live", "https://example.com/b", "News", epgId="A.TV")
        )
        assertEquals("https://a.example/a.png", SmartLogoPolicy.Index(channels).resolve(channels[1]))
    }

    @Test fun invalidUrlsAreNotUsed() {
        val channels = listOf(Channel("X", "https://example.com/x", "Other", logo="file:///unsafe.png"))
        assertNull(SmartLogoPolicy.Index(channels).resolve(channels[0]))
    }
}
