package com.softppj.myview

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Fully consumes XMLTV timestamp; timezone-less values are interpreted as UTC. */
internal object XmlTvTime {
    fun parse(raw: String): Long? {
        val value = raw.trim().replace(Regex("\\s+"), " ")
        if (value.length !in 12..20) return null
        val patterns = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmm Z",
            "yyyyMMddHHmmss", "yyyyMMddHHmm")
        for (pattern in patterns) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val position = ParsePosition(0)
            val date = formatter.parse(value, position)
            if (date != null && position.index == value.length) return date.time
        }
        return null
    }
}
