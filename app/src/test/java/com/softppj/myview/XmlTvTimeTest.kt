package com.softppj.myview

import org.junit.Assert.*
import org.junit.Test

class XmlTvTimeTest {
    @Test fun offsetsDescribeSameInstant() {
        val a = XmlTvTime.parse("20261116150000 +0400")
        val b = XmlTvTime.parse("20261116110000 +0000")
        assertNotNull(a)
        assertEquals(a, b)
    }

    @Test fun rejectsMalformedOrPartialTimes() {
        assertNull(XmlTvTime.parse("20261116150000 +0400 extra"))
        assertNull(XmlTvTime.parse("20261316150000 +0400"))
        assertNull(XmlTvTime.parse("20261116150000 garbage"))
        assertNull(XmlTvTime.parse(""))
    }

    @Test fun supportsMinutePrecision() {
        assertNotNull(XmlTvTime.parse("202611161500 +0400"))
        assertNotNull(XmlTvTime.parse("202611161500"))
    }
}
