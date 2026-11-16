package com.softppj.myview

/** Parses user-supplied M3U; no bundled providers, streams, or credentials. */
internal object PlaylistParser {
    data class Channel(val name: String, val url: String, val group: String = "", val logo: String = "")

    private fun attr(info: String, name: String): String {
        val found = Regex("""(?:^|\s)${Regex.escape(name)}="([^"]*)"""").find(info)
        return found?.groupValues?.get(1).orEmpty()
    }

    fun validHttpsUrl(raw: String): Boolean {
        if (raw.length !in 9..4096 || !raw.startsWith("https://", true) || raw.any { it == '\\' || it.isWhitespace() }) return false
        val hostPort = raw.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPort.isBlank() || '@' in hostPort || ' ' in hostPort || '\\' in hostPort) return false
        return hostPort.none { it == '\n' || it == '\r' || it == '\t' }
    }

    fun parse(input: String, maxChannels: Int = 2000): List<Channel> {
        if (input.length > 2_000_000 || maxChannels <= 0) return emptyList()
        val channels = ArrayList<Channel>()
        var title = ""
        var group = ""
        var logo = ""
        for (line in input.lineSequence()) {
            val item = line.trim()
            if (item.startsWith("#EXTINF:", true)) {
                title = item.substringAfter(',', "").trim().take(150)
                group = attr(item, "group-title").take(80)
                logo = attr(item, "tvg-logo").takeIf(::validHttpsUrl).orEmpty()
                if (title.isBlank()) title = attr(item, "tvg-name").take(150)
            } else if (item.isNotBlank() && !item.startsWith('#')) {
                if (validHttpsUrl(item)) {
                    channels.add(Channel(title.ifBlank { "Channel ${channels.size + 1}" }, item, group, logo))
                    if (channels.size == maxChannels) break
                }
                title = ""
                group = ""
                logo = ""
            }
        }
        return channels
    }
}
