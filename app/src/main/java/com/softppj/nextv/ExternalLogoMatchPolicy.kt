package com.softppj.nextv

/**
 * Match only exact EPG IDs or unambiguous canonical channel names.
 * Never guess across similarly named feeds or use vector formats that
 * Android's BitmapFactory cannot decode on all supported TV versions.
 */
internal object ExternalLogoMatchPolicy {
    data class Entry(val channelId: String, val url: String, val format: String, val inUse: Boolean)
    data class Target(val channelName: String, val epgId: String)

    private val bracketedQuality = Regex(
        """\\s*[\\[(](?:sd|hd|fhd|uhd|4k|1080p|720p)[\\])]\\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val spacedQuality = Regex(
        """(?:\\s+|_)(?:sd|hd|fhd|uhd|4k|1080p|720p)$""",
        RegexOption.IGNORE_CASE
    )

    /** Remove only separate quality labels; never strip part of a channel name. */
    fun canonical(text: String): String {
        val trimmed = text.trim().replace(bracketedQuality, "")
            .replace(spacedQuality, "").trim()
        return trimmed.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
    }

    fun isSupported(entry: Entry): Boolean {
        val format = entry.format.uppercase(java.util.Locale.ROOT)
        return entry.inUse && entry.url.startsWith("https://", true) &&
            entry.url.length <= 2048 &&
            format in setOf("PNG", "JPEG", "JPG", "WEBP")
    }

    fun matchesAny(entry: Entry, targets: List<Target>): Boolean {
        if (!isSupported(entry)) return false
        val id = entry.channelId.trim().lowercase(java.util.Locale.ROOT)
        val name = canonical(entry.channelId.substringBeforeLast('.'))
        return targets.any { target ->
            (target.epgId.isNotBlank() &&
                target.epgId.trim().lowercase(java.util.Locale.ROOT) == id) ||
                (target.channelName.isNotBlank() &&
                    canonical(target.channelName) == name)
        }
    }

    /** Returns id/name-keyed results; conflicting URLs suppress name matches. */
    fun build(entries: List<Entry>): Map<String, String> {
        val seen = mutableMapOf<String, MutableSet<String>>()
        for (entry in entries) {
            if (!isSupported(entry)) continue
            val id = entry.channelId.trim().lowercase(java.util.Locale.ROOT)
            if (id.isEmpty()) continue
            val name = canonical(entry.channelId.substringBeforeLast('.'))
            seen.getOrPut("id:" + id) { mutableSetOf() }.add(entry.url)
            if (name.isNotBlank())
                seen.getOrPut("name:" + name) { mutableSetOf() }.add(entry.url)
        }
        return seen.mapNotNull { (key, urls) ->
            if (urls.size == 1) key to urls.single() else null
        }.toMap()
    }

    fun resolve(name: String, epgId: String, index: Map<String, String>): String? {
        if (epgId.isNotBlank()) index["id:" + epgId.trim().lowercase(java.util.Locale.ROOT)]?.let { return it }
        return index["name:" + canonical(name)]
    }
}
