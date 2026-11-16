package com.softppj.nextv

import java.util.Locale

/**
 * Reuses trusted tvg-logo metadata already present in the user's playlists.
 * No external logo catalog, scraping, or untrusted third-party lookup.
 */
internal object SmartLogoPolicy {
    private fun normalized(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("""\b(?:hd|fhd|uhd|4k|sd|hevc|h265)\b"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim().replace(Regex("""\s+"""), " ")

    private fun logoUrl(value: String): String? =
        value.trim().takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }

    internal class Index(channels: List<Channel>) {
        private val byIdentity = HashMap<String, String>()
        private val ambiguous = HashSet<String>()

        init {
            channels.forEach { channel ->
                val url = logoUrl(channel.logo) ?: return@forEach
                val name = normalized(channel.name)
                if (name.isNotEmpty()) {
                    register("name:${name}", url)
                    register("source:${channel.source}|name:${name}", url)
                }
                if (channel.epgId.isNotBlank()) register("epg:${normalized(channel.epgId)}", url)
            }
        }

        private fun register(key: String, url: String) {
            if (key in ambiguous) return
            val previous = byIdentity.putIfAbsent(key, url)
            if (previous != null && previous != url) {
                byIdentity.remove(key)
                ambiguous.add(key)
            }
        }

        fun resolve(channel: Channel): String? {
            logoUrl(channel.logo)?.let { return it }
            val name = normalized(channel.name)
            return if (channel.epgId.isNotBlank()) {
                byIdentity["epg:${normalized(channel.epgId)}"]
                    ?: byIdentity["source:${channel.source}|name:${name}"]
                    ?: byIdentity["name:${name}"]
            } else {
                byIdentity["source:${channel.source}|name:${name}"]
                    ?: byIdentity["name:${name}"]
            }
        }
    }
}
