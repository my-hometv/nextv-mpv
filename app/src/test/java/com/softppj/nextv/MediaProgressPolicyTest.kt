package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class MediaProgressPolicyTest {
    private fun movie(name:String,source:String="One",url:String="https://example.org/shared.mp4") =
        Channel(name,url,"Cinema",source=source,mediaType="movie")

    @Test fun sharedUrlsDoNotShareProgressIdentity(){
        assertNotEquals(MediaProgressPolicy.identityHash(movie("Movie A")),
            MediaProgressPolicy.identityHash(movie("Movie B")))
        assertNotEquals(MediaProgressPolicy.identityHash(movie("Movie A","One")),
            MediaProgressPolicy.identityHash(movie("Movie A","Two")))
        assertEquals(MediaProgressPolicy.identityHash(movie("Movie A")),
            MediaProgressPolicy.identityHash(movie("Movie A")))
    }

    @Test fun legacyMigrationRequiresUnambiguousMediaUrl(){
        val first=movie("Movie A")
        val second=movie("Movie B")
        assertTrue(MediaProgressPolicy.canMigrateLegacy(listOf(first),first))
        assertFalse(MediaProgressPolicy.canMigrateLegacy(listOf(first,second),first))
        assertFalse(MediaProgressPolicy.canMigrateLegacy(listOf(first,second),second))
    }

    @Test fun legacyMigrationDoesNotAssignAnotherEntryOrLiveTv(){
        val first=movie("Movie A")
        val removed=movie("Removed")
        assertFalse(MediaProgressPolicy.canMigrateLegacy(listOf(first),removed))
        val live=Channel("Live","https://example.org/shared.mp4","Cinema",source="One",mediaType="live")
        assertFalse(MediaProgressPolicy.canMigrateLegacy(listOf(live),live))
    }
}
