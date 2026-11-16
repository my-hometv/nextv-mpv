package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test

class PlaylistReplacementTest {
    private fun channel(name: String, id: Int) =
        PlaylistParser.Channel(name, "https://stream.example/$id.m3u8")

    @Test fun retainsConfirmedStreamAcrossPlaylistReordering() {
        val previous = listOf(channel("A", 1), channel("B", 2))
        val updated = listOf(channel("B", 2), channel("C", 3), channel("A", 1))
        assertEquals(0, PlaylistReplacement.confirmedIndex(previous, 1, updated))
    }

    @Test fun neverAssumesNewChannelIsConfirmed() {
        assertEquals(-1, PlaylistReplacement.confirmedIndex(
            listOf(channel("A", 1)), 0, listOf(channel("Other", 9))))
        assertEquals(-1, PlaylistReplacement.confirmedIndex(emptyList(), 0,
            listOf(channel("Other", 9))))
        assertEquals(-1, PlaylistReplacement.confirmedIndex(listOf(channel("A", 1)), -1,
            listOf(channel("A", 1))))
    }
}
