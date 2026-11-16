package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class SmartLogoPolicyTest {
    @Test fun matchesSameNameWithHdSuffix() {
        val data=SmartLogoPolicy.build(listOf(
            SmartLogoPolicy.Entry("Surya HD","surya.tv","https://example.org/surya.png")))
        assertEquals("https://example.org/surya.png",SmartLogoPolicy.resolve("Surya","",data))
    }
    @Test fun prefersExactEpgIdAndRejectsAmbiguousNames() {
        val data=SmartLogoPolicy.build(listOf(
            SmartLogoPolicy.Entry("News HD","news.one","https://example.org/one.png"),
            SmartLogoPolicy.Entry("News SD","news.two","https://example.org/two.png")))
        assertNull(SmartLogoPolicy.resolve("News","",data))
        assertEquals("https://example.org/two.png",SmartLogoPolicy.resolve("News","NEWS.TWO",data))
    }
    @Test fun doesNotInventLogo() {
        assertNull(SmartLogoPolicy.resolve("Missing","",SmartLogoPolicy.build(emptyList())))
    }
}
