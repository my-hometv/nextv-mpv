package com.softppj.nextv

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class FavoriteGroupBackupMergePolicyTest {
    @Test fun importedGroupsDoNotDiscardExistingGroups(){
        val saved="""{"Favorites":["news","movies"],"Kids":["cartoon"]}"""
        val imported="""{"Favorites":["movies","sport"],"Parents":["devotional"]}"""
        val merged=JSONObject(FavoriteGroupBackupMergePolicy.merge(saved,imported))
        assertEquals(listOf("news","movies","sport"),
            (0 until merged.getJSONArray("Favorites").length())
                .map{merged.getJSONArray("Favorites").getString(it)})
        assertEquals("cartoon",merged.getJSONArray("Kids").getString(0))
        assertEquals("devotional",merged.getJSONArray("Parents").getString(0))
    }

    @Test fun duplicateMembershipDoesNotGrowAfterRepeatedMerge(){
        val saved="""{"Favorites":["a"]}"""
        val imported="""{"Favorites":["a","b"]}"""
        val once=FavoriteGroupBackupMergePolicy.merge(saved,imported)
        val twice=JSONObject(FavoriteGroupBackupMergePolicy.merge(once,imported))
        assertEquals(2,twice.getJSONArray("Favorites").length())
    }
}
