package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class ViewerPreferencePolicyTest {
    @Test fun unknownOrInvalidProfilesFallbackToDefault(){
        assertEquals("default",ViewerPreferencePolicy.activeViewer("Unlisted",listOf("Parent")))
        assertEquals("Parent",ViewerPreferencePolicy.activeViewer("Parent",listOf("Parent")))
        assertEquals("default",ViewerPreferencePolicy.activeViewer("../../other",listOf("Parent")))
    }

    @Test fun profileRemovalClearsOnlyOwnedDynamicVODProgress(){
        val stored=setOf(
            "viewer_Ann_myhometv_progress_v2_42",
            "viewer_Ann_myhometv_progress_7",
            "viewer_Ann_myhometv_progress_ambiguous_7",
            "viewer_Ann_Sue_myhometv_progress_v2_42",
            "myhometv_progress_v2_42",
            "viewer_Ann_continue_watching_ids",
            "license_token"
        )
        val deleted=ViewerPreferencePolicy.profileKeys("Ann",stored).toSet()
        assertTrue("viewer_Ann_myhometv_progress_v2_42" in deleted)
        assertTrue("viewer_Ann_myhometv_progress_7" in deleted)
        assertTrue("viewer_Ann_myhometv_progress_ambiguous_7" in deleted)
        assertTrue("viewer_Ann_continue_watching_ids" in deleted)
        assertFalse("viewer_Ann_Sue_myhometv_progress_v2_42" in deleted)
        assertFalse("myhometv_progress_v2_42" in deleted)
        assertFalse("license_token" in deleted)
    }

    @Test fun profileRemovalDoesNotTouchSharedSettingsOrSiblingProfiles(){
        val ann=ViewerPreferencePolicy.profileKeys("Ann").toSet()
        val sibling=ViewerPreferencePolicy.profileKeys("Ann_Sue").toSet()
        assertTrue(ann.intersect(sibling).isEmpty())
        assertTrue("viewer_Ann_favorite_groups" in ann)
        assertTrue("viewer_Ann_channel_sort_mode" in ann)
        assertFalse("license_token" in ann)
        assertFalse("playlists" in ann)
        assertFalse("pin" in ann)
        assertTrue("viewer_Ann_continue_watching_ids" in ann)
        assertEquals(11,ann.size)
    }
}
