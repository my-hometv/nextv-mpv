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

    @Test fun largeGuideHasNoOverlapsAndMaintainsSourcePriority() {
        val first = (1..1000).map { n -> Slot(n * 200L, n * 200L + 100L, "Priority $n") }
        val fallback = (1..1000).map { n ->
            Slot(n * 200L + 100L, n * 200L + 200L, "Gap $n")
        }
        val merged = merge(first, fallback, limit = 2000)
        assertEquals(2000, merged.size)
        assertEquals("Priority 1", merged.first().title)
        assertEquals("Gap 1000", merged.last().title)
        assertEquals(true, merged.zipWithNext().all { (a, b) -> a.end <= b.start })
    }

    @Test fun nestedAndSameStartIntervalsKeepFirstPriority() {
        val existing = listOf(Slot(100, 500, "Keep"))
        val additions = listOf(
            Slot(100, 200, "Same start"),
            Slot(200, 300, "Nested"),
            Slot(400, 550, "Partial"),
            Slot(500, 600, "Adjacent")
        )
        assertEquals(listOf("Keep", "Adjacent"),
            merge(existing, additions).map { it.title })
    }

    @Test fun programmeLimitIsPreserved() {
        val slots = (1..5).map { Slot(it * 100L, it * 100L + 50, "P$it") }
        assertEquals(3, merge(emptyList(), slots, limit = 3).size)
    }
}
