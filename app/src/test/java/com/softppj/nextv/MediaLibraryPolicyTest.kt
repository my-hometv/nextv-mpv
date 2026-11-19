package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class MediaLibraryPolicyTest {
    @Test fun movieSeriesMusicAndSavedWebAppearInTheirLibraries(){
        val channels=listOf(
            Channel("Live","https://example.org/live","News",source="P",mediaType="live"),
            Channel("Movie","https://example.org/movie","Movies",source="P",mediaType="movie"),
            Channel("Series","https://example.org/series","Series",source="P",mediaType="series"),
            Channel("Music","https://example.org/music","Music",source="P",mediaType="music"),
            Channel("Web","https://example.org/web","Web TV",mediaType="web",webChannel="auto")
        )
        val indices=MediaLibraryPolicy.allowedRepresentatives(
            channels,channels.indices.toList(),setOf("P"),
            emptySet(),emptySet(),emptySet(),emptySet())
        assertEquals(listOf("live","movie","series","music","web"),
            indices.map{channels[it].mediaType})
    }
    @Test fun disabledSourcesAndProtectedGroupsStayHidden(){
        val channels=listOf(
            Channel("Movie","https://example.org/movie","Films",source="Disabled",mediaType="movie"),
            Channel("Series","https://example.org/series","Protected",source="Enabled",mediaType="series"),
            Channel("Web","https://example.org/web","Web TV",mediaType="web")
        )
        val indices=MediaLibraryPolicy.allowedRepresentatives(
            channels,channels.indices.toList(),setOf("Enabled"),
            emptySet(),emptySet(),setOf("Protected"),emptySet())
        assertEquals(listOf(2),indices)
    }
    @Test fun unlockedPlaylistAndGroupBecomeVisibleWithoutBypassingDisabledSource(){
        val channels=listOf(
            Channel("A","https://example.org/a","Protected",source="Enabled",mediaType="movie"),
            Channel("B","https://example.org/b","Protected",source="Disabled",mediaType="movie")
        )
        val result=MediaLibraryPolicy.allowedRepresentatives(
            channels,channels.indices.toList(),setOf("Enabled"),
            setOf("Enabled"),setOf("Enabled"),setOf("Protected"),setOf("Protected"))
        assertEquals(listOf(0),result)
    }
}
