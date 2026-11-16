package com.softppj.myview

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Inspect compressed XMLTV by content signature, not untrusted filename. */
internal object EpgPayload {
    const val MAX_BYTES = 8_000_000

    fun open(input: InputStream): InputStream {
        val source = BufferedInputStream(input)
        source.mark(2)
        val first = source.read()
        val second = source.read()
        source.reset()
        return if (first == 0x1f && second == 0x8b) GZIPInputStream(source) else source
    }

    /** Limit decompressed XMLTV before passing bytes to the XML parser. */
    fun readBounded(input: InputStream, limit: Int = MAX_BYTES): ByteArray {
        require(limit in 1..MAX_BYTES)
        return open(input).use { stream ->
            val buffer = ByteArray(8192)
            val data = java.io.ByteArrayOutputStream()
            while (true) {
                val count = stream.read(buffer)
                if (count == -1) break
                require(data.size() + count <= limit) { "XMLTV exceeds size limit" }
                data.write(buffer, 0, count)
            }
            data.toByteArray()
        }
    }
}
