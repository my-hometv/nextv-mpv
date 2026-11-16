package com.softppj.nextv

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Counts decompressed guide bytes; does not trust source filenames or headers. */
internal class BoundedEpgInput(source: InputStream, private val maxBytes: Long) :
    FilterInputStream(source) {
    private var used: Long = 0
    init { require(maxBytes > 0) }

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) checkBytes(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) checkBytes(count.toLong())
        return count
    }

    private fun checkBytes(amount: Long) {
        if (amount > maxBytes - used) throw IOException("XMLTV exceeds decompressed size limit")
        used += amount
    }
}
