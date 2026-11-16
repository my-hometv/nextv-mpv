package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BoundedEpgInputTest {
    @Test fun acceptsGuideWithinLimit() {
        val data = "guide".toByteArray()
        assertArrayEquals(data, BoundedEpgInput(ByteArrayInputStream(data), 5).readBytes())
    }

    @Test fun rejectsOversizedGuideWithBufferedReads() {
        val source = BoundedEpgInput(ByteArrayInputStream(ByteArray(16)), 8)
        try {
            source.readBytes()
            fail("Oversized XMLTV should be rejected")
        } catch (_: IOException) { }
    }

    @Test fun rejectsOversizedGuideWithSingleByteReads() {
        val source = BoundedEpgInput(ByteArrayInputStream(byteArrayOf(1, 2)), 1)
        assertEquals(1, source.read())
        try {
            source.read()
            fail("Expected size limit")
        } catch (_: IOException) { }
    }
}
