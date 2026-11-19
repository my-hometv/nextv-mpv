package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class MediaSearchPolicyTest {
    private val song=Channel("എന്റെ ഗാനം","https://example.org/song","Devotional",
        source="Music Playlist",mediaType="music",artist="K. J. Yesudas",album="Malayalam Hits")
    @Test fun searchesMalayalamTitles(){
        assertTrue(MediaSearchPolicy.matches(song,"ഗാനം"))
        assertTrue(MediaSearchPolicy.matches(song,"  എന്റെ  "))
    }
    @Test fun searchesArtistAndAlbumWithoutCaseSensitivity(){
        assertTrue(MediaSearchPolicy.matches(song,"yesudas"))
        assertTrue(MediaSearchPolicy.matches(song,"MALAYALAM HITS"))
    }
    @Test fun youtubeClassificationIsExclusive(){
        assertEquals("youtube",MediaSearchPolicy.category("movie",true))
        assertEquals("youtube",MediaSearchPolicy.category("series",true))
        assertEquals("movie",MediaSearchPolicy.category("movie",false))
        assertEquals("music",MediaSearchPolicy.category("music",false))
        assertEquals("live",MediaSearchPolicy.category("live",false))
    }
    @Test fun playlistDeletionCannotRedirectAStaleSearchResult(){
        val old=Channel("Old","https://example.org/old","News",source="Removed")
        val current=Channel("New","https://example.org/new","News",source="Enabled")
        val identity={c:Channel->c.source+"|"+c.name+"|"+c.url}
        assertEquals(-1,MediaSearchPolicy.resolveIdentity(listOf(current),identity(old),identity){true})
        assertEquals(0,MediaSearchPolicy.resolveIdentity(listOf(current),identity(current),identity){true})
        assertEquals(-1,MediaSearchPolicy.resolveIdentity(listOf(current),identity(current),identity){false})
    }
    @Test fun emptyQueriesDoNotMatchEverything(){
        assertFalse(MediaSearchPolicy.matches(song,"   "))
        assertFalse(MediaSearchPolicy.matches(song,"unrelated"))
    }
}
