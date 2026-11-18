package com.softppj.nextv

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgMergePolicyTest {
    private data class Slot(val start: Long, val end: Long, val title: String)
    private fun merge(first: List<Slot>, second: List<Slot>, limit: Int = 1000) =
        EpgMergePolicy.merge(first, second, { it.start }, { it.end }, limit)

    @Test fun lowerPriorityExactAndPartialOverlapsAreRejected() {
        val manual = listOf(Slot(100, 200, "Manual"), Slot(300, 400, "Manual 2"))
        val playlist = listOf(
            Slot(100, 200, "Duplicate"),
            Slot(150, 250, "Partial"),
            Slot(250, 350, "Partial 2"),
            Slot(400, 450, "Next")
        )
        assertEquals(listOf("Manual", "Manual 2", "Next"),
            merge(manual, playlist).map { it.title })
    }

    @Test fun lowerPriorityFeedsFillRealGapsAndAdjacentBoundaries() {
        val manual = listOf(Slot(100, 200, "First"), Slot(400, 500, "Last"))
        val fallback = listOf(Slot(200, 300, "Gap A"), Slot(300, 400, "Gap B"))
        assertEquals(listOf("First", "Gap A", "Gap B", "Last"),
            merge(manual, fallback).map { it.title })
    }

    @Test fun invalidProgrammesAreIgnoredAndResultsSorted() {
        val source = listOf(
            Slot(300, 350, "Later"), Slot(100, 90, "Invalid"),
            Slot(0, 200, "Missing start"), Slot(100, 200, "Earlier")
        )
        assertEquals(listOf("Earlier", "Later"),
            merge(emptyList(), source).map { it.title })
    }

    @Test fun firstSourceWinsEvenIfItsProgrammesAreParsedOutOfOrder() {
        val first = listOf(Slot(300, 400, "Late"), Slot(100, 200, "Early"))
        val second = listOf(Slot(110, 190, "Override"), Slot(200, 300, "Gap"))
        assertEquals(listOf("Early", "Gap", "Late"),
            merge(first, second).map { it.title })
    }

    @Test fun programmeLimitIsPreserved() {
        val slots = (1..5).map { Slot(it * 100L, it * 100L + 50, "P$it") }
        assertEquals(3, merge(emptyList(), slots, limit = 3).size)
    }
}
