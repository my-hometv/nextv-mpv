package com.softppj.myview

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.Date

/** Small, bounded, on-device XMLTV importer. Never contains a bundled EPG or provider URL. */
internal object XmlTvGuide {
    data class Programme(val channelId: String, val startMs: Long, val endMs: Long, val title: String)

    private fun parseTime(value: String): Long? = XmlTvTime.parse(value)


    fun parse(bytes: ByteArray, maximum: Int = 6000): List<Programme> {
        if (bytes.size > 8_000_000 || maximum <= 0) return emptyList()
        // Reject DTDs and entity declarations rather than allowing entity expansion.
        if (String(bytes, Charsets.UTF_8).contains(
                Regex("""<!\s*(?:DOCTYPE|ENTITY)""", RegexOption.IGNORE_CASE))) return emptyList()
        val reader = Xml.newPullParser()
        reader.setInput(ByteArrayInputStream(bytes), "UTF-8")
        val result = ArrayList<Programme>()
        var event = reader.eventType
        var inProgramme = false
        var inTitle = false
        var channel = ""
        var start = 0L
        var end = 0L
        var title = ""
        while (event != XmlPullParser.END_DOCUMENT && result.size < maximum) {
            when (event) {
                XmlPullParser.START_TAG -> when (reader.name) {
                    "programme" -> {
                        inProgramme = true
                        channel = reader.getAttributeValue(null, "channel").orEmpty().take(120)
                        start = parseTime(reader.getAttributeValue(null, "start").orEmpty()) ?: 0L
                        end = parseTime(reader.getAttributeValue(null, "stop").orEmpty()) ?: 0L
                        title = ""
                    }
                    "title" -> if (inProgramme) inTitle = true
                }
                XmlPullParser.TEXT -> if (inTitle) title = (title + reader.text).take(200)
                XmlPullParser.END_TAG -> when (reader.name) {
                    "title" -> inTitle = false
                    "programme" -> {
                        if (channel.isNotEmpty() && start > 0L && end > start && title.isNotBlank()) {
                            result.add(Programme(channel, start, end, title.trim()))
                        }
                        inProgramme = false
                        inTitle = false
                    }
                }
            }
            event = reader.next()
        }
        return result
    }

    fun nowNext(programmes: List<Programme>, id: String, nowMs: Long = Date().time): Pair<Programme?, Programme?> {
        if (id.isBlank()) return null to null
        val items = programmes.filter { it.channelId == id && it.endMs > nowMs }
            .sortedBy { it.startMs }
        return items.firstOrNull { it.startMs <= nowMs && it.endMs > nowMs } to
            items.firstOrNull { it.startMs > nowMs }
    }
}
