package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test

class GuideTimelineTest {
    @Test fun filtersSortsAndBoundsEvents() {
        val all = listOf(
            GuideTimeline.Event("a", 9000, 10000, "Late"),
            GuideTimeline.Event("b", 1000, 5000, "Other"),
            GuideTimeline.Event("a", 2000, 5000, "Now"),
            GuideTimeline.Event("a", 5000, 9000, "Next"),
            GuideTimeline.Event("a", 0, 1000, "Expired"))
        val selected = GuideTimeline.upcoming(all, "a", 3000, 9000, 2)
        assertEquals(listOf("Now", "Next"), selected.map { it.title })
    }

    @Test fun rejectsUnboundedAndInvalidWindows() {
        val event = GuideTimeline.Event("a", 5, 10, "Title")
        assertTrue(GuideTimeline.upcoming(listOf(event), "", 0).isEmpty())
        assertTrue(GuideTimeline.upcoming(listOf(event), "a", 0, -1).isEmpty())
        assertTrue(GuideTimeline.upcoming(listOf(event), "a", 0, 100, 0).isEmpty())
        assertTrue(GuideTimeline.upcoming(listOf(event), "a", Long.MAX_VALUE - 10, 100).isEmpty())
    }
}
