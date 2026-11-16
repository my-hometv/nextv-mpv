package com.softppj.myview

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Explicit local-only backup; no secrets are ever stored in the repository. */
internal object SettingsBackup {
    private const val MAX_PLAYLISTS = 5
    private const val MAX_BYTES = 12_000_000

    private fun urlSet(prefs: SharedPreferences, key: String): JSONArray =
        JSONArray(prefs.getStringSet(key, emptySet())?.toList() ?: emptyList<String>())

    fun export(prefs: SharedPreferences): String {
        val count = prefs.getInt("playlist_count", 1).coerceIn(1, MAX_PLAYLISTS)
        val items = JSONArray()
        for (index in 0 until count) {
            val entry = JSONObject()
            entry.put("name", prefs.getString("playlist_name_$index", "Playlist ${index + 1}").orEmpty())
            entry.put("playlist", prefs.getString("playlist_data_$index",
                if (index == 0) prefs.getString("playlist_text", "") else "").orEmpty())
            entry.put("lastChannel", prefs.getInt("last_channel_$index", 0))
            entry.put("hidden", urlSet(prefs, "hidden_$index"))
            val numFavorites = prefs.getInt("favorites_count_$index", 1).coerceIn(1, 5)
            entry.put("favoriteCount", numFavorites)
            entry.put("activeFavorite", prefs.getInt("favorites_active_$index", 0))
            val favorites = JSONArray()
            for (list in 0 until numFavorites) {
                val group = JSONObject()
                group.put("name", prefs.getString("favorites_name_${index}_$list",
                    if (list == 0) "Favorites" else "Favorites ${list + 1}"))
                group.put("channels", urlSet(prefs,
                    if (list == 0) "fav_$index" else "fav_${index}_$list"))
                favorites.put(group)
            }
            entry.put("favoriteLists", favorites)
            items.put(entry)
        }
        return JSONObject().put("schema", 1)
            .put("playlistCount", count)
            .put("activePlaylist", prefs.getInt("active_playlist", 0))
            .put("playlists", items)
            .put("xmltvUrl", prefs.getString("xmltv_url", "").orEmpty())
            .put("clockRecovery", prefs.getBoolean("clock_recovery", true))
            .put("vodRefresh", prefs.getBoolean("vod_refresh", false))
            .toString(2)
    }

    fun restore(prefs: SharedPreferences, content: String) {
        require(content.length in 2..MAX_BYTES) { "Invalid backup length" }
        val json = JSONObject(content)
        require(json.getInt("schema") == 1) { "Unsupported schema" }
        val count = json.getInt("playlistCount")
        require(count in 1..MAX_PLAYLISTS) { "Invalid playlist count" }
        val playlists = json.getJSONArray("playlists")
        require(playlists.length() == count)
        val guideUrl = json.optString("xmltvUrl", "")
        require(guideUrl.isBlank() || PlaylistParser.validHttpsUrl(guideUrl))
        // Validate everything BEFORE the first prefs write; corrupt files cannot partially restore.
        val validated = ArrayList<ValidatedPlaylist>()
        for (index in 0 until count) {
            val entry = playlists.getJSONObject(index)
            val raw = entry.getString("playlist")
            require(raw.length <= 2_000_000)
            val channels = PlaylistParser.parse(raw)
            require(raw.isBlank() || channels.isNotEmpty())
            val validUrls = channels.map { it.url }.toSet()
            fun readUrls(array: JSONArray): Set<String> {
                require(array.length() <= 2000)
                return (0 until array.length()).map { array.getString(it) }
                    .filter { it in validUrls }.toSet()
            }
            val countFav = entry.getInt("favoriteCount")
            val list = entry.getJSONArray("favoriteLists")
            require(countFav in 1..5 && list.length() == countFav)
            val favs = (0 until countFav).map {
                val item = list.getJSONObject(it)
                item.getString("name").take(45) to readUrls(item.getJSONArray("channels"))
            }
            validated.add(ValidatedPlaylist(
                entry.getString("name").take(50),
                raw,
                entry.optInt("lastChannel", 0).coerceIn(0, (channels.size - 1).coerceAtLeast(0)),
                readUrls(entry.getJSONArray("hidden")),
                favs,
                entry.optInt("activeFavorite", 0).coerceIn(0, countFav - 1)))
        }
        // Replace the complete previous configuration atomically, including obsolete
        // playlists/favorite lists. Validation above finishes before clearing anything.
        val editor = prefs.edit().clear()
        for ((index, entry) in validated.withIndex()) {
            editor.putString("playlist_name_$index", entry.name)
                .putString("playlist_data_$index", entry.m3u)
                .putInt("last_channel_$index", entry.last)
                .putStringSet("hidden_$index", entry.hidden)
                .putInt("favorites_count_$index", entry.favoriteLists.size)
                .putInt("favorites_active_$index", entry.activeFavorite)
            for ((listIndex, group) in entry.favoriteLists.withIndex()) {
                editor.putString("favorites_name_${index}_$listIndex", group.first)
                    .putStringSet(if (listIndex == 0) "fav_$index" else "fav_${index}_$listIndex", group.second)
            }
        }
        editor.putInt("playlist_count", count)
            .putInt("active_playlist", json.optInt("activePlaylist", 0).coerceIn(0, count - 1))
            .putString("xmltv_url", guideUrl)
            .putBoolean("clock_recovery", json.optBoolean("clockRecovery", true))
            .putBoolean("vod_refresh", json.optBoolean("vodRefresh", false))
        check(editor.commit()) { "Settings could not be saved" }
    }

    private data class ValidatedPlaylist(
        val name: String,
        val m3u: String,
        val last: Int,
        val hidden: Set<String>,
        val favoriteLists: List<Pair<String, Set<String>>>,
        val activeFavorite: Int
    )
}
