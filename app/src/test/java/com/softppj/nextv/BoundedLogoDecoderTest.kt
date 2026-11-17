package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedLogoDecoderTest {
    @Test fun smallResponseIsRetained() {
        val original = ByteArray(1024) { (it and 255).toByte() }
        assertArrayEquals(original, BoundedLogoDecoder.readBounded(ByteArrayInputStream(original)))
    }

    @Test fun exactLimitIsAccepted() {
        val response = ByteArray(BoundedLogoDecoder.MAX_BYTES)
        assertEquals(response.size, BoundedLogoDecoder.readBounded(ByteArrayInputStream(response))?.size)
    }

    @Test fun overLimitIsRejectedWithoutFullRead() {
        val response = ByteArray(BoundedLogoDecoder.MAX_BYTES + 4096)
        assertNull(BoundedLogoDecoder.readBounded(ByteArrayInputStream(response)))
    }

    @Test fun emptyResponseIsNotAnImage() {
        assertEquals(0, BoundedLogoDecoder.readBounded(ByteArrayInputStream(byteArrayOf()))?.size)
    }
}
