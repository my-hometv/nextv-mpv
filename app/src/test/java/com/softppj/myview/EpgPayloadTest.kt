package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class EpgPayloadTest {
    @Test fun readsPlainXml() {
        val bytes = "<tv/>".toByteArray()
        assertArrayEquals(bytes, EpgPayload.readBounded(ByteArrayInputStream(bytes)))
    }

    @Test fun readsGzipByMagicBytes() {
        val expected = "<tv><programme/></tv>".toByteArray()
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(expected) }
        assertArrayEquals(expected, EpgPayload.readBounded(ByteArrayInputStream(output.toByteArray())))
    }

    @Test fun rejectsExcessiveDecompressedData() {
        val bytes = ByteArray(3000) { 65 }
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        try {
            EpgPayload.readBounded(ByteArrayInputStream(output.toByteArray()), 200)
            fail("Expected size limit")
        } catch (_: IllegalArgumentException) { }
    }
}
