package com.softppj.nextv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashAudioFallbackPolicyTest {
    private fun track(lang: String?, id: String, supported: Boolean, selected: Boolean = false) =
        DashAudioFallbackPolicy.Candidate(lang, id, supported, selected)

    @Test fun neverSelectUnsupportedSavedAudio() {
        val tracks = listOf(track("ml", "old", false), track("en", "working", true))
        assertEquals(1, DashAudioFallbackPolicy.choose(tracks, "old", "ml"))
    }

    @Test fun preferSavedSupportedAudio() {
        val tracks = listOf(track("en", "a", true), track("ml", "b", true))
        assertEquals(1, DashAudioFallbackPolicy.choose(tracks, "b", "en"))
    }

    @Test fun preserveAlreadySelectedSupportedTrackWhenLanguageMissing() {
        val tracks = listOf(track("en", "a", true), track("ta", "b", true, true))
        assertEquals(1, DashAudioFallbackPolicy.choose(tracks, "stale", "ml"))
    }

    @Test fun noSupportedAudioReturnsNull() {
        assertNull(DashAudioFallbackPolicy.choose(listOf(track("ml", "a", false)), "a", "ml"))
    }

    @Test fun noTracksReturnsNull() {
        assertNull(DashAudioFallbackPolicy.choose(emptyList(), null, "ml"))
    }
}
