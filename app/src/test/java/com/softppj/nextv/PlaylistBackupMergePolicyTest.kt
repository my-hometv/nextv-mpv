package com.softppj.nextv

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PlaylistBackupMergePolicyTest {
    @Test fun caseSensitivePathsAndTokensStayDistinct(){
        val existing="""[{"name":"First","url":"https://example.invalid/Live/TokenA.m3u"}]"""
        val incoming="""[{"name":"Second","url":"https://example.invalid/live/TokenA.m3u"}]"""
        val result=JSONArray(PlaylistBackupMergePolicy.merge(existing,incoming))
        assertEquals(2,result.length())
        assertEquals("First",result.getJSONObject(0).getString("name"))
        assertEquals("Second",result.getJSONObject(1).getString("name"))
    }
    @Test fun exactDuplicateReplacesSettingsWithoutChangingOrder(){
        val existing="""[{"name":"Old","url":"https://example.invalid/Secure.m3u","enabled":false},{"name":"Other","url":"https://example.invalid/Other.m3u","enabled":true}]"""
        val incoming="""[{"name":"New","url":"https://example.invalid/Secure.m3u","enabled":true}]"""
        val result=JSONArray(PlaylistBackupMergePolicy.merge(existing,incoming))
        assertEquals(2,result.length())
        assertEquals("New",result.getJSONObject(0).getString("name"))
        assertTrue(result.getJSONObject(0).getBoolean("enabled"))
        assertEquals("Other",result.getJSONObject(1).getString("name"))
    }
    @Test fun blanksDoNotCreatePlaylists(){
        val result=JSONArray(PlaylistBackupMergePolicy.merge("[]","""[{"name":"Blank","url":"  "}]"""))
        assertEquals(0,result.length())
    }
}
