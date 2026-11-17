package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class ExternalLogoMatchPolicyTest {
    private fun image(channel:String,url:String="https://example.org/logo.png",format:String="PNG",inUse:Boolean=true)=
        ExternalLogoMatchPolicy.Entry(channel,url,format,inUse)

    @Test fun exactEpgIdBeatsNameAndNameIsCaseInsensitive() {
        val entries=listOf(
            image("SuryaTV.in","https://example.org/surya.png"),
            image("SuryaTV.us","https://example.org/other.png"))
        val map=ExternalLogoMatchPolicy.build(entries)
        assertEquals("https://example.org/surya.png",
            ExternalLogoMatchPolicy.resolve("Surya TV","SuryaTV.in",map))
        assertNull(ExternalLogoMatchPolicy.resolve("Surya TV","",map))
    }

    @Test fun unknownAndAmbiguousNamesMustNotGuess() {
        val map=ExternalLogoMatchPolicy.build(listOf(
            image("AlphaTV.in","https://example.org/a.png"),
            image("AlphaTV.us","https://example.org/b.png")))
        assertNull(ExternalLogoMatchPolicy.resolve("Alpha TV","",map))
        assertNull(ExternalLogoMatchPolicy.resolve("Unknown","",map))
    }

    @Test fun unsafeVectorAndOldEntriesCannotBeDownloaded() {
        for (entry in listOf(
            image("A.in","http://example.org/a.png"),
            image("A.in","https://example.org/a.svg","SVG"),
            image("A.in","https://example.org/a.gif","GIF"),
            image("A.in","https://example.org/a.png","PNG",false))) {
            assertFalse(ExternalLogoMatchPolicy.isSupported(entry))
        }
    }

    @Test fun onlyRelevantSourcesAreSelected() {
        val target=listOf(ExternalLogoMatchPolicy.Target("Surya TV","surya.in"))
        assertTrue(ExternalLogoMatchPolicy.matchesAny(image("Surya.in"),target))
        assertTrue(ExternalLogoMatchPolicy.matchesAny(image("suryaTV.us"),target))
        assertFalse(ExternalLogoMatchPolicy.matchesAny(image("Another.in"),target))
    }
}
