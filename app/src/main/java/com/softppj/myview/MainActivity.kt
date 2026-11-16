package com.softppj.myview

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import android.content.Intent
import android.text.format.DateFormat
import android.view.Gravity
import java.util.Date
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private var player: ExoPlayer? = null
    private var browser: WebView? = null
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var mode = "menu"
    private var playlistChannels: List<PlaylistParser.Channel> = emptyList()
    private var channelIndex = -1
    private var favoritesOnly = false
    private var channelQuery = ""
    private var selectedGroup = ""
    private var showClock = true
    private var channelPage = 0
    private val channelPageSize = 80
    private val exportRequest = 1001
    private val importRequest = 1002
    private var clockView: TextView? = null
    private var drawerView: View? = null
    private var numericView: TextView? = null
    private var numericBuffer = ""
    private var epgProgrammes: List<XmlTvGuide.Programme> = emptyList()
    private var guideGeneration = 0
    private var playbackGeneration = 0
    private var showDiagnostics = false
    private var originalDisplayModeId: Int? = null
    private var requestedDisplayModeId: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("myview_test", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        setContentView(root)
        loadPlaylist()
        val guideUrl = prefs.getString("xmltv_url", "").orEmpty()
        if (guideUrl.isNotBlank()) refreshGuide(guideUrl)
        showMenu()
    }

    private fun playlistCount(): Int = prefs.getInt("playlist_count", 1).coerceIn(1, 5)
    private fun activePlaylist(): Int = prefs.getInt("active_playlist", 0).coerceIn(0, playlistCount() - 1)
    private fun playlistKey(index: Int) = "playlist_data_$index"
    private fun playlistName(index: Int): String =
        prefs.getString("playlist_name_$index", if (index == 0) "My Playlist" else "Playlist ${index + 1}")
            .orEmpty()

    private fun loadPlaylist() {
        val index = activePlaylist()
        val raw = prefs.getString(playlistKey(index),
            if (index == 0) prefs.getString("playlist_text", "") else "").orEmpty()
        playlistChannels = PlaylistParser.parse(raw)
        channelIndex = prefs.getInt("last_channel_$index", -1)
            .takeIf { it in playlistChannels.indices } ?: -1
    }

    private fun setActivePlaylist(index: Int) {
        if (index !in 0 until playlistCount()) return
        prefs.edit().putInt("active_playlist", index).apply()
        loadPlaylist()
        favoritesOnly = false
        showMenu()
    }

    private fun favoriteListCount(): Int =
        prefs.getInt("favorites_count_${activePlaylist()}", 1).coerceIn(1, 5)

    private fun activeFavoriteList(): Int =
        prefs.getInt("favorites_active_${activePlaylist()}", 0).coerceIn(0, favoriteListCount() - 1)

    private fun favoriteName(index: Int): String =
        prefs.getString("favorites_name_${activePlaylist()}_$index",
            if (index == 0) "Favorites" else "Favorites ${index + 1}").orEmpty()

    private fun favoriteKey(): String =
        if (activeFavoriteList() == 0) "fav_${activePlaylist()}"
        else "fav_${activePlaylist()}_${activeFavoriteList()}"

    private fun favoriteSet(): Set<String> =
        prefs.getStringSet(favoriteKey(), emptySet())?.toSet() ?: emptySet()

    private fun manageFavorites() {
        val count = favoriteListCount()
        val labels = (0 until count).map { favoriteName(it) }.toMutableList()
        if (count < 5) labels.add("Add favorite list")
        labels.add("Rename active favorite list")
        AlertDialog.Builder(this).setTitle("Favorite lists")
            .setItems(labels.toTypedArray()) { _, option ->
                when {
                    option < count -> {
                        prefs.edit().putInt("favorites_active_${activePlaylist()}", option).apply()
                        favoritesOnly = true
                        showChannelList()
                    }
                    option == count && count < 5 -> {
                        prefs.edit().putInt("favorites_count_${activePlaylist()}", count + 1)
                            .putInt("favorites_active_${activePlaylist()}", count).apply()
                        favoritesOnly = true
                        showChannelList()
                    }
                    else -> {
                        val active = activeFavoriteList()
                        val input = EditText(this).apply {
                            setSingleLine(true)
                            setText(favoriteName(active))
                        }
                        AlertDialog.Builder(this).setTitle("Rename favorite list").setView(input)
                            .setPositiveButton("Save") { _, _ ->
                                prefs.edit().putString("favorites_name_${activePlaylist()}_$active",
                                    input.text.toString().take(45).ifBlank { "Favorites" }).apply()
                                showMenu()
                            }.setNegativeButton("Cancel", null).show()
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun hiddenSet(): Set<String> =
        prefs.getStringSet("hidden_${activePlaylist()}", emptySet())?.toSet() ?: emptySet()

    private fun toggleHidden() {
        val item = playlistChannels.getOrNull(channelIndex) ?: return
        val updated = hiddenSet().toMutableSet()
        if (!updated.add(item.url)) updated.remove(item.url)
        prefs.edit().putStringSet("hidden_${activePlaylist()}", updated).apply()
        Toast.makeText(this, if (item.url in updated) "Channel hidden" else "Channel visible",
            Toast.LENGTH_SHORT).show()
    }

    private fun showHiddenChannels() {
        val hidden = hiddenSet()
        val indices = playlistChannels.indices.filter { playlistChannels[it].url in hidden }
        val names = indices.map { playlistChannels[it].name }.toTypedArray()
        if (indices.isEmpty()) {
            Toast.makeText(this, "No hidden channels", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Restore hidden channels")
            .setItems(names) { _, position ->
                val index = indices[position]
                val update = hiddenSet().toMutableSet()
                update.remove(playlistChannels[index].url)
                prefs.edit().putStringSet("hidden_${activePlaylist()}", update).apply()
                showMenu()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun toggleFavorite() {
        val item = playlistChannels.getOrNull(channelIndex) ?: return
        val updated = favoriteSet().toMutableSet()
        if (!updated.add(item.url)) updated.remove(item.url)
        prefs.edit().putStringSet(favoriteKey(), updated).apply()
        Toast.makeText(this, if (item.url in updated) "Favorite saved" else "Favorite removed",
            Toast.LENGTH_SHORT).show()
    }

    private fun choosePlaylist() {
        val labels = (0 until playlistCount()).map { playlistName(it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose playlist")
            .setSingleChoiceItems(labels, activePlaylist()) { dialog, which ->
                dialog.dismiss()
                setActivePlaylist(which)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun addPlaylist() {
        if (playlistCount() >= 5) {
            Toast.makeText(this, "Maximum five local playlists", Toast.LENGTH_LONG).show()
            return
        }
        val index = playlistCount()
        prefs.edit().putInt("playlist_count", index + 1).putInt("active_playlist", index)
            .putString("playlist_name_$index", "Playlist ${index + 1}").apply()
        loadPlaylist()
        askPlaylist()
    }

    private fun renamePlaylist() {
        val index = activePlaylist()
        val field = EditText(this).apply { setText(playlistName(index)); setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Rename playlist").setView(field)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("playlist_name_$index",
                    field.text.toString().trim().take(50).ifBlank { "Playlist ${index + 1}" }).apply()
                showMenu()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setChannel(index: Int) {
        if (index !in playlistChannels.indices) return
        channelIndex = index
        prefs.edit().putInt("last_channel_${activePlaylist()}", index).apply()
        openMedia(playlistChannels[index].url)
    }

    private fun refreshGuide(url: String) {
        if (!PlaylistParser.validHttpsUrl(url)) return
        val request = ++guideGeneration
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 7000
                connection.readTimeout = 9000
                connection.instanceFollowRedirects = false
                val data = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val block = ByteArray(8192)
                    while (true) {
                        val n = input.read(block)
                        if (n < 0) break
                        if (data.size() + n > 8_000_000) error("EPG too large")
                        data.write(block, 0, n)
                    }
                }
                val guide = XmlTvGuide.parse(data.toByteArray())
                runOnUiThread {
                    if (request == guideGeneration) {
                        epgProgrammes = guide
                        Toast.makeText(this, "Guide loaded: ${guide.size} programmes", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (request == guideGeneration)
                        Toast.makeText(this, "Unable to load XMLTV guide", Toast.LENGTH_LONG).show()
                }
            } finally { connection?.disconnect() }
        }.start()
    }

    private fun askGuide() {
        val field = EditText(this).apply {
            setSingleLine(true)
            hint = "https://provider.example/guide.xml"
            setText(prefs.getString("xmltv_url", ""))
        }
        AlertDialog.Builder(this).setTitle("XMLTV guide URL (HTTPS)").setView(field)
            .setPositiveButton("Load") { _, _ ->
                val url = field.text.toString().trim()
                if (PlaylistParser.validHttpsUrl(url)) {
                    prefs.edit().putString("xmltv_url", url).apply()
                    refreshGuide(url)
                } else Toast.makeText(this, "Invalid HTTPS guide URL", Toast.LENGTH_LONG).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun guideLine(index: Int): String {
        val id = playlistChannels.getOrNull(index)?.tvgId.orEmpty()
        val (now, next) = XmlTvGuide.nowNext(epgProgrammes, id)
        if (now == null && next == null) return "No guide data"
        return "Now: ${now?.title ?: "—"}  ·  Next: ${next?.title ?: "—"}"
    }


    private fun askChannelSearch() {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(channelQuery)
            hint = "Search channel name or group"
        }
        AlertDialog.Builder(this).setTitle("Find channel").setView(input)
            .setPositiveButton("Search") { _, _ ->
                channelQuery = input.text.toString().trim().take(90)
                showChannelList()
            }.setNeutralButton("Clear filters") { _, _ ->
                channelQuery = ""
                selectedGroup = ""
                showChannelList()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseChannelGroup() {
        val groups = playlistChannels.map { it.group }.filter { it.isNotBlank() }
            .distinct().sorted()
        AlertDialog.Builder(this).setTitle("Channel groups")
            .setItems((listOf("All groups") + groups).toTypedArray()) { _, item ->
                selectedGroup = if (item == 0) "" else groups[item - 1]
                showChannelList()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showGuideScreen() {
        releasePlayback()
        mode = "guide"
        root.removeAllViews()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            setBackgroundColor(android.graphics.Color.rgb(18, 26, 38))
        }
        panel.addView(Button(this).apply { text = "Back"; setOnClickListener { showMenu() } })
        panel.addView(TextView(this).apply {
            text = "TV Guide • Now / Next"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
        })
        for (index in playlistChannels.indices) {
            if (playlistChannels[index].url in hiddenSet()) continue
            panel.addView(Button(this).apply {
                text = "${index + 1}. ${playlistChannels[index].name}\n${guideLine(index)}"
                isFocusable = true
                setOnClickListener { setChannel(index) }
            })
        }
        root.addView(ScrollView(this).apply { addView(panel) },
            FrameLayout.LayoutParams(-1, -1))
        panel.getChildAt(0).requestFocus()
    }


    private fun confirmExport() {
        AlertDialog.Builder(this).setTitle("Export MyView settings")
            .setMessage("The exported file contains your playlist addresses and may include provider tokens. Keep it private and never publish it to GitHub.")
            .setPositiveButton("Choose destination") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "MyView-settings.json")
                }, exportRequest)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmImport() {
        AlertDialog.Builder(this).setTitle("Restore MyView settings")
            .setMessage("This replaces your local playlist, hidden-channel and favorite-list settings. Continue?")
            .setPositiveButton("Choose backup") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }, importRequest)
            }.setNegativeButton("Cancel", null).show()
    }

    @Deprecated("Uses Android's Storage Access Framework result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                exportRequest -> {
                    val output = contentResolver.openOutputStream(uri, "wt")
                        ?: error("Export location cannot be written")
                    output.use { it.write(SettingsBackup.export(prefs).toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(this, "MyView settings exported", Toast.LENGTH_SHORT).show()
                }
                importRequest -> {
                    val input = contentResolver.openInputStream(uri)
                        ?: error("Backup cannot be opened")
                    val buffer = ByteArrayOutputStream()
                    input.use { stream ->
                        val chunk = ByteArray(8192)
                        while (true) {
                            val count = stream.read(chunk)
                            if (count < 0) break
                            require(buffer.size() + count <= 12_000_000)
                            buffer.write(chunk, 0, count)
                        }
                    }
                    SettingsBackup.restore(prefs, buffer.toString("UTF-8"))
                    guideGeneration++
                    epgProgrammes = emptyList()
                    val guideUrl = prefs.getString("xmltv_url", "").orEmpty()
                    if (guideUrl.isNotBlank()) refreshGuide(guideUrl)
                    channelQuery = ""
                    selectedGroup = ""
                    favoritesOnly = false
                    channelPage = 0
                    loadPlaylist()
                    showMenu()
                    Toast.makeText(this, "MyView settings restored", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Settings transfer failed. Check the backup file.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun showMenu() {
        releasePlayback()
        mode = "menu"
        root.removeAllViews()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.rgb(18, 26, 38))
        }
        panel.addView(TextView(this).apply {
            text = "MyView • Live TV"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
        })
        panel.addView(TextView(this).apply {
            text = "Test an authorized stream in Media3, or a provider webpage in WebView. DRM-protected streams need provider-authorized licenses."
            setTextColor(android.graphics.Color.LTGRAY)
        })
        fun addButton(label: String, action: () -> Unit) {
            panel.addView(Button(this).apply {
                text = label
                isFocusable = true
                setOnClickListener { action() }
            })
        }
        addButton("Recover sustained fast-forward: " + if (prefs.getBoolean("clock_recovery", true)) "ON" else "OFF") {
            prefs.edit().putBoolean("clock_recovery", !prefs.getBoolean("clock_recovery", true)).apply()
            showMenu()
        }
        addButton("VOD refresh match: " + if (prefs.getBoolean("vod_refresh", false)) "ON" else "OFF") {
            prefs.edit().putBoolean("vod_refresh", !prefs.getBoolean("vod_refresh", false)).apply()
            showMenu()
        }
        addButton("Playback diagnostics: " + if (showDiagnostics) "ON" else "OFF") {
            showDiagnostics = !showDiagnostics
            showMenu()
        }
        addButton("Playlist: ${playlistName(activePlaylist())} (switch)") { choosePlaylist() }
        addButton("Add playlist") { addPlaylist() }
        addButton("Rename playlist") { renamePlaylist() }
        addButton("Export MyView settings") { confirmExport() }
        addButton("Import MyView settings") { confirmImport() }
        addButton("Manage favorite lists") { manageFavorites() }
        addButton("Search channels") { askChannelSearch() }
        addButton("Choose channel group") { chooseChannelGroup() }
        addButton("Live clock: " + if (showClock) "ON" else "OFF") {
            showClock = !showClock
            showMenu()
        }
        addButton("Set XMLTV guide URL") { askGuide() }
        addButton("Restore hidden channels") { showHiddenChannels() }
        addButton("TV Guide") { showGuideScreen() }
        addButton("Paste M3U playlist (HTTPS)") { askPlaylist() }
        if (playlistChannels.isNotEmpty()) {
            addButton("Watch last channel") {
                setChannel(channelIndex.takeIf { it in playlistChannels.indices } ?: 0)
            }
            addButton("Browse ${playlistChannels.size} channels") { favoritesOnly = false; showChannelList() }
            addButton("Favorites") { favoritesOnly = true; showChannelList() }
        }
        addButton("Play stream (Media3)") { askUrl("stream", "HTTPS .mpd / .m3u8 / .mp4 URL") }
        addButton("Open provider webpage (WebView)") { askUrl("web", "HTTPS webpage URL") }
        addButton("Exit") { finish() }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(panel)
        }, FrameLayout.LayoutParams(-1, -1))
        panel.getChildAt(2).requestFocus()
    }

    private fun askPlaylist() {
        val field = EditText(this).apply {
            minLines = 7
            maxLines = 14
            setText(prefs.getString(playlistKey(activePlaylist()),
                if (activePlaylist() == 0) prefs.getString("playlist_text", "") else ""))
            hint = "#EXTM3U\\n#EXTINF:-1,Example channel\\nhttps://example.org/live.m3u8"
        }
        val dialog = AlertDialog.Builder(this).setTitle("Paste your authorized HTTPS M3U")
            .setView(field).setPositiveButton("Load", null)
            .setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = field.text.toString()
                val parsed = PlaylistParser.parse(raw)
                if (parsed.isEmpty()) {
                    field.error = "No valid HTTPS channel URLs found"
                } else {
                    playlistChannels = parsed
                    // User-provided playlist is device-local, never bundled into a public repository.
                    prefs.edit().putString(playlistKey(activePlaylist()), raw)
                        .putInt("last_channel_${activePlaylist()}", 0).apply()
                    channelIndex = 0
                    dialog.dismiss()
                    showChannelList()
                }
            }
        }
        dialog.show()
    }

    private fun showChannelList() {
        releasePlayback()
        mode = "channels"
        root.removeAllViews()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
            setBackgroundColor(android.graphics.Color.rgb(18, 26, 38))
        }
        list.addView(TextView(this).apply {
            text = "${playlistName(activePlaylist())} • ${if (favoritesOnly) "Favorites" else "Channels"}"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
        })
        list.addView(Button(this).apply { text = "Back"; setOnClickListener { showMenu() } })
        list.addView(Button(this).apply {
            text = "Search / group: ${channelQuery.ifBlank { "All channels" }} / ${selectedGroup.ifBlank { "All groups" }}"
            setOnClickListener { askChannelSearch() }
        })
        list.addView(Button(this).apply {
            text = if (favoritesOnly) "Show all channels" else "Favorites only"
            setOnClickListener { favoritesOnly = !favoritesOnly; showChannelList() }
        })
        val indices = ChannelNavigation.filteredIndices(playlistChannels, channelQuery, favoritesOnly, favoriteSet())
            .filter { playlistChannels[it].url !in hiddenSet() &&
                (selectedGroup.isEmpty() || playlistChannels[it].group == selectedGroup) }
        if (favoritesOnly) list.addView(Button(this).apply {
            text = "Favorite list: ${favoriteName(activeFavoriteList())} (change)"
            setOnClickListener { manageFavorites() }
        })
        if (indices.isEmpty()) list.addView(TextView(this).apply {
            text = "No channels in this view"
            setTextColor(android.graphics.Color.WHITE)
        })
        val totalPages = ((indices.size + channelPageSize - 1) / channelPageSize).coerceAtLeast(1)
        channelPage = channelPage.coerceIn(0, totalPages - 1)
        if (totalPages > 1) list.addView(Button(this).apply {
            text = "Page ${channelPage + 1} of $totalPages • next page"
            setOnClickListener { channelPage = (channelPage + 1) % totalPages; showChannelList() }
        })
        for (index in indices.drop(channelPage * channelPageSize).take(channelPageSize)) {
            val channel = playlistChannels[index]
            list.addView(Button(this).apply {
                text = "${index + 1}. ${channel.name}  ${if (channel.url in favoriteSet()) "★" else ""}" +
                    "\n${guideLine(index)}"
                isFocusable = true
                setOnClickListener { setChannel(index) }
            })
        }
        root.addView(ScrollView(this).apply { addView(list) }, FrameLayout.LayoutParams(-1, -1))
        list.getChildAt(1).requestFocus()
    }

    private fun askUrl(type: String, title: String) {
        val field = EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("last_" + type, ""))
            hint = "https://"
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(field)
            .setPositiveButton("Open", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = field.text.toString().trim()
                if (!PlaylistParser.validHttpsUrl(url)) {
                    field.error = "Use a valid HTTPS URL"
                } else {
                    prefs.edit().putString("last_" + type, url).apply()
                    dialog.dismiss()
                    if (type == "web") openWeb(url) else openMedia(url)
                }
            }
        }
        dialog.show()
    }

    private fun classifyFailure(error: PlaybackException): ProviderFailurePolicy.Kind {
        var cause: Throwable? = error
        var status: Int? = null
        var networkFailure = false
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                status = cause.responseCode
                break
            }
            if (cause is IOException) networkFailure = true
            cause = cause.cause
        }
        val isDrm = error.errorCode == PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR ||
            error.errorCode == PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED
        return ProviderFailurePolicy.classify(status, isDrm,
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED, networkFailure)
    }

    // Explicit VOD only: a direct MP4/M4V file, not HLS/DASH live streams.
    private fun isVodUrl(url: String): Boolean {
        val path = android.net.Uri.parse(url).path.orEmpty().lowercase()
        return path.endsWith(".mp4") || path.endsWith(".m4v")
    }

    private fun maybeMatchVodRefresh(exo: ExoPlayer, url: String) {
        if (!prefs.getBoolean("vod_refresh", false) || !isVodUrl(url) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = windowManager.defaultDisplay
        val current = display.mode
        val fps = exo.videoFormat?.frameRate ?: return
        val selected = FrameRateMatchPolicy.select(true, true, fps,
            FrameRateMatchPolicy.Mode(current.modeId, current.physicalWidth,
                current.physicalHeight, current.refreshRate),
            display.supportedModes.map {
                FrameRateMatchPolicy.Mode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
            }) ?: return
        if (requestedDisplayModeId == selected.id) return
        if (originalDisplayModeId == null) originalDisplayModeId = current.modeId
        requestedDisplayModeId = selected.id
        window.attributes = window.attributes.apply { preferredDisplayModeId = selected.id }
    }

    private fun restoreRefresh() {
        if (requestedDisplayModeId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
        }
        requestedDisplayModeId = null
        originalDisplayModeId = null
    }

    private fun openMedia(url: String, attempt: Int = 0) {
        releasePlayback()
        mode = "media"
        root.removeAllViews()
        if (!PlaylistParser.validHttpsUrl(url)) { showMenu(); return }
        val generation = playbackGeneration
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        val clockGuard = PlaybackClockGuard()
        var recoveryScheduled = false
        var readyEver = false
        var failureLabel = "none"
        var bufferCount = 0
        clockView = if (showClock) TextView(this).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(12, 8, 12, 8)
        } else null
        val view = PlayerView(this).apply {
            player = exo
            useController = true
            isFocusable = true
        }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        clockView?.let { root.addView(it,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)) }
        val status = TextView(this).apply {
            setPadding(18, 12, 18, 12)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xA0000000.toInt())
            text = if (attempt == 0) "Loading stream…" else "Retrying this stream ($attempt/2)…"
        }
        root.addView(status, FrameLayout.LayoutParams(-2, -2,
            android.view.Gravity.TOP or android.view.Gravity.START))
        val diagnostics = TextView(this).apply {
            setPadding(18, 12, 18, 12)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xB0000000.toInt())
            textSize = 12f
            visibility = if (showDiagnostics) View.VISIBLE else View.GONE
        }
        root.addView(diagnostics, FrameLayout.LayoutParams(-2, -2,
            android.view.Gravity.BOTTOM or android.view.Gravity.START))

        fun retryCurrentStream(kind: ProviderFailurePolicy.Kind) {
            if (recoveryScheduled || player !== exo || generation != playbackGeneration) return
            val delay = ProviderFailurePolicy.retryDelayMs(kind, attempt)
            if (delay == null) {
                status.text = "${ProviderFailurePolicy.description(kind)}. Press BACK."
                status.visibility = View.VISIBLE
                return
            }
            recoveryScheduled = true
            failureLabel = kind.name
            status.text = "${ProviderFailurePolicy.description(kind)}; retrying same URL…"
            status.visibility = View.VISIBLE
            handler.postDelayed({
                if (generation == playbackGeneration && player === exo) {
                    openMedia(url, attempt + 1)
                }
            }, delay)
        }

        fun clockTick() {
            if (player !== exo || generation != playbackGeneration) return
            clockView?.text = DateFormat.format("EEE dd MMM yy  HH:mm:ss", Date())
            handler.postDelayed({ clockTick() }, 1000L)
        }

        fun healthTick() {
            if (player !== exo || generation != playbackGeneration) return
            val isPlaying = exo.isPlaying
            if (!isPlaying) clockGuard.reset()
            else if (!recoveryScheduled && prefs.getBoolean("clock_recovery", true) &&
                clockGuard.sample(SystemClock.elapsedRealtime(), exo.currentPosition,
                    true, exo.playbackParameters.speed)) {
                // This identifies sustained abnormal progression, not actual audio/video lip-sync.
                // One bounded same-source recovery only; never switch to another provider URL.
                retryCurrentStream(ProviderFailurePolicy.Kind.NETWORK)
            }
            if (showDiagnostics) {
                val video = exo.videoFormat
                val audio = exo.audioFormat
                val dimension = exo.videoSize
                diagnostics.text = "Media3 | ${if (isPlaying) "playing" else "waiting"}" +
                    "\nVideo: ${dimension.width}x${dimension.height}  " +
                    "fps: ${video?.frameRate ?: "?"}" +
                    "\nCodec: ${video?.sampleMimeType ?: "?"} | " +
                    "Audio: ${audio?.sampleMimeType ?: "?"}" +
                    "\nPosition: ${exo.currentPosition / 1000}s | " +
                    "speed: ${exo.playbackParameters.speed}x" +
                    "\nBuffers: $bufferCount | retry: $attempt/2 | error: $failureLabel"
            }
            handler.postDelayed({ healthTick() }, 5000L)
        clockTick()
        }

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (player !== exo) return
                when (state) {
                    Player.STATE_READY -> {
                        status.text = "Playing • Media3"
                        readyEver = true
                        maybeMatchVodRefresh(exo, url)
                        handler.postDelayed({
                            if (player === exo && !recoveryScheduled) status.visibility = View.GONE
                        }, 3000L)
                    }
                    Player.STATE_BUFFERING -> {
                        if (readyEver) bufferCount++
                        clockGuard.reset()
                        status.text = "Buffering; keeping selected channel…"
                        status.visibility = View.VISIBLE
                    }
                    Player.STATE_ENDED -> {
                        clockGuard.reset()
                        status.text = "Stream ended"
                        status.visibility = View.VISIBLE
                    }
                    else -> clockGuard.reset()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (player !== exo) return
                retryCurrentStream(classifyFailure(error))
            }
        })
        val builder = MediaItem.Builder().setUri(url)
        val mediaPath = android.net.Uri.parse(url).path.orEmpty().lowercase()
        if (mediaPath.endsWith(".mpd")) builder.setMimeType(MimeTypes.APPLICATION_MPD)
        if (mediaPath.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        exo.setMediaItem(builder.build())
        exo.prepare()
        exo.playWhenReady = true
        handler.postDelayed({ healthTick() }, 5000L)
        view.requestFocus()
    }

    private fun showLiveDrawer() {
        if (mode != "media" || drawerView != null) return
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(0xDD101924.toInt())
            isFocusable = true
        }
        overlay.addView(TextView(this).apply {
            text = "MyView • ${playlistName(activePlaylist())}"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
        })
        val indices = ChannelNavigation.filteredIndices(playlistChannels, channelQuery, favoritesOnly, favoriteSet())
            .filter { playlistChannels[it].url !in hiddenSet() &&
                (selectedGroup.isEmpty() || playlistChannels[it].group == selectedGroup) }
        val selector = ScrollView(this)
        val items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // Keep the live drawer small; opening it must not allocate 2,000 buttons.
        for (index in indices.take(90)) {
            val channel = playlistChannels[index]
            items.addView(Button(this).apply {
                text = "${index + 1}. ${channel.name} ${if (channel.url in favoriteSet()) "★" else ""}\n${guideLine(index)}"
                isFocusable = true
                setOnClickListener { hideLiveDrawer(); setChannel(index) }
            })
        }
        selector.addView(items)
        overlay.addView(selector, LinearLayout.LayoutParams(-1, 0, 1f))
        overlay.addView(Button(this).apply {
            text = "Favorite / Unfavorite"
            setOnClickListener {
                toggleFavorite()
                hideLiveDrawer()
                showLiveDrawer()
            }
        })
        overlay.addView(Button(this).apply {
            text = "Hide / Unhide current channel"
            setOnClickListener { toggleHidden(); hideLiveDrawer(); showLiveDrawer() }
        })
        overlay.addView(Button(this).apply {
            text = "Close drawer"
            setOnClickListener { hideLiveDrawer() }
        })
        val params = FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.46f).toInt().coerceAtLeast(280),
            FrameLayout.LayoutParams.MATCH_PARENT, android.view.Gravity.START)
        root.addView(overlay, params)
        drawerView = overlay
        (items.getChildAt(indices.take(90).indexOf(channelIndex).takeIf { it >= 0 } ?: 0)
            ?: overlay.getChildAt(0)).requestFocus()
    }

    private fun hideLiveDrawer() {
        drawerView?.let { root.removeView(it) }
        drawerView = null
    }

    private fun numericInput(digit: Int) {
        if (mode != "media" || playlistChannels.isEmpty()) return
        numericBuffer = (numericBuffer + digit).takeLast(4)
        numericView?.let { root.removeView(it) }
        val target = ChannelNavigation.selectNumber(numericBuffer.toIntOrNull() ?: 0, playlistChannels.size)
        numericView = TextView(this).apply {
            setPadding(30, 20, 30, 20)
            textSize = 26f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xD9000000.toInt())
            text = "Channel $numericBuffer ${if (target == null) "(not found)" else playlistChannels[target].name}"
        }.also { root.addView(it, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.END)) }
        val pending = numericBuffer
        handler.postDelayed({
            if (mode == "media" && pending == numericBuffer) {
                val index = ChannelNavigation.selectNumber(pending.toIntOrNull() ?: 0, playlistChannels.size)
                numericBuffer = ""
                numericView?.let { root.removeView(it) }
                numericView = null
                if (index != null && playlistChannels[index].url !in hiddenSet()) setChannel(index)
            }
        }, 1800L)
    }

    private fun openWeb(url: String) {
        releasePlayback()
        mode = "web"
        root.removeAllViews()
        val web = WebView(this)
        browser = web
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.scheme != "https"
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreen != null) { callback.onCustomViewHidden(); return }
                fullscreen = view
                fullscreenCallback = callback
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                web.visibility = View.GONE
            }
            override fun onHideCustomView() { hideFullscreen() }
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        web.loadUrl(url)
        web.requestFocus()
    }

    private fun hideFullscreen() {
        val view = fullscreen ?: return
        root.removeView(view)
        fullscreen = null
        browser?.visibility = View.VISIBLE
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    private fun releasePlayback() {
        restoreRefresh()
        playbackGeneration++
        handler.removeCallbacksAndMessages(null)
        hideLiveDrawer()
        numericView = null
        numericBuffer = ""
        clockView = null
        hideFullscreen()
        player?.release()
        player = null
        browser?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
        browser = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (mode == "media") {
            if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                numericInput(keyCode - KeyEvent.KEYCODE_0)
                return true
            }
            if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
                numericInput(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                if (drawerView == null) showLiveDrawer() else hideLiveDrawer()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && drawerView == null) {
                showLiveDrawer()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                val next = ChannelNavigation.nextVisible(channelIndex, playlistChannels.map { it.url },
                    hiddenSet(), if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) -1 else 1)
                if (next != null) setChannel(next)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_PROG_RED) {
                toggleFavorite()
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && mode != "menu") {
            if (fullscreen != null) hideFullscreen()
            else if (drawerView != null) hideLiveDrawer()
            else if (mode == "media" && playlistChannels.isNotEmpty()) showChannelList()
            else showMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() { browser?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); browser?.onResume() }
    override fun onDestroy() { releasePlayback(); super.onDestroy() }
}
