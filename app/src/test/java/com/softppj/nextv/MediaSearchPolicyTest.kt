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
    @Test fun emptyQueriesDoNotMatchEverything(){
        assertFalse(MediaSearchPolicy.matches(song,"   "))
        assertFalse(MediaSearchPolicy.matches(song,"unrelated"))
    }
}
