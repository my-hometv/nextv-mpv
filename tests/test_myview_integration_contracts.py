"""Source-level guardrails for MyView feature wiring and manual-only packaging.

These static contracts complement, but do not replace, Kotlin unit and TV tests.
"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "app/src/main/java/com/softppj/nextv/MainActivity.kt"
SOURCE_WORKFLOW = ROOT / ".github/workflows/myview-source-verification.yml"
APK_WORKFLOW = ROOT / ".github/workflows/myview-full-app.yml"


class MyViewIntegrationContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = ACTIVITY.read_text(encoding="utf-8")
        cls.source_workflow = SOURCE_WORKFLOW.read_text(encoding="utf-8")
        cls.apk_workflow = APK_WORKFLOW.read_text(encoding="utf-8")

    def test_user_facing_branding_does_not_regress(self):
        obsolete = (
            "HomeTV • License activation",
            "Enter your HomeTV License Key",
            "Activate HomeTV",
            "Personalize HomeTV",
            "HOMETV  •  PLAYLISTS",
            "Save to the NexTV app folder",
            "Clear HomeTV cache",
        )
        for value in obsolete:
            with self.subTest(value=value):
                self.assertNotIn(value, self.activity)

    def test_channel_info_epg_uses_all_configured_sources(self):
        # The old guard checked only epg_xmltv_url; new setups use
        # epg_xmltv_urls or the default XMLTV feed.
        self.assertNotIn('epgGuide.isEmpty() && !prefs.getString("epg_xmltv_url","").isNullOrBlank()', self.activity)
        self.assertIn('epgGuide.isEmpty() && epgUrls().isNotEmpty()', self.activity)
        self.assertIn('prefs.getBoolean("nextv_epg_enabled",true)', self.activity)
        self.assertIn('prefs.getBoolean("show_channel_epg",true)', self.activity)
        self.assertIn('banner?.parent===originalScreen', self.activity)
        self.assertLess(
            self.activity.index("setContentView(screen)", self.activity.index("private fun playIndex(index:Int)")),
            self.activity.index("screenInfoOnStart(index)", self.activity.index("private fun playIndex(index:Int)")),
        )
        self.assertIn('if(epgLoadingGeneration!=null)"Loading guide…"', self.activity)
        self.assertIn('"No current programme"', self.activity)
        self.assertIn('"No upcoming programme"', self.activity)

    def test_epg_downloads_are_deduplicated_and_display_names_matched(self):
        self.assertIn('if(epgLoadingGeneration==generation)', self.activity)
        self.assertIn('epgWaitingCallbacks.add(done)', self.activity)
        # Each XMLTV source is staged and merged only after successful parsing.
        # The previous displayNames map was superseded by priority-aware
        # per-source aliases and the reusable overlap merge policy.
        self.assertIn('val sourceNames=mutableMapOf<String,String>()', self.activity)
        self.assertIn('"display-name"->if(definitionId.isNotEmpty())', self.activity)
        self.assertIn('sourceNames.putIfAbsent(displayName,definitionId)', self.activity)
        self.assertIn('EpgMergePolicy.merge(combined[id].orEmpty(),entries,', self.activity)
        self.assertIn('namedSchedules[name]=EpgMergePolicy.merge(', self.activity)
        self.assertIn('withNames.putIfAbsent(name,entries)', self.activity)

    def test_epg_source_editor_preserves_manual_priority_and_live_schedule(self):
        # Migrates existing multi-line entries into individually editable rows.
        # Saving one URL never changes playlist-announced sources or wipes EPG.
        editor = self.activity.split("private fun savedManualEpgSources():", 1)[1].split(
            "private var epgTuneTask:", 1
        )[0]
        self.assertIn('prefs.getString("epg_xmltv_urls",null)', editor)
        self.assertIn('prefs.getString("epg_xmltv_url",null)', editor)
        self.assertIn('putString("epg_xmltv_urls",updated.joinToString', editor)
        self.assertIn("EpgSourceEditorPolicy.add(", editor)
        self.assertIn("EpgSourceEditorPolicy.edit(", editor)
        self.assertIn("EpgSourceEditorPolicy.move(", editor)
        self.assertIn("EpgSourceEditorPolicy.remove(", editor)
        self.assertIn("showEpgSourceDiagnostics()", editor)
        self.assertNotIn('setText(epgUrls().joinToString', editor)
        self.assertNotIn('epgGuide=emptyMap()', editor)
        self.assertIn('epgLoadedAt=0L', editor)

    def test_epg_off_and_source_change_cancel_the_active_download(self):
        editor = self.activity.split("private fun savedManualEpgSources():", 1)[1].split(
            "private var epgTuneTask:", 1
        )[0]
        off = self.activity.split('"nextv_epg_enabled"->{', 1)[1].split(
            '"auto_playlist_check"->{', 1
        )[0]
        loader = self.activity.split("private fun loadEpg(done:", 1)[1].split(
            "private fun showChannelEpg(", 1
        )[0]
        selection = self.activity.split("private fun saveEpgSourceMode(", 1)[1].split(
            "private fun epgUrls():", 1
        )[0]
        self.assertIn('invalidateEpgSourceSelection()', editor)
        self.assertIn('invalidateEpgSourceSelection()', selection)
        self.assertIn('epgDownloadCancellation?.cancel()', selection)
        self.assertIn('epgDownloadCancellation?.cancel()', off)
        self.assertIn('cancellation.attachConnection(connection)', loader)
        self.assertIn('cancellation.attachStream(raw)', loader)
        self.assertIn('cancellation.isCancelled()', loader)
        self.assertIn('cancellation.releaseConnection(connection)', loader)

    def test_epg_shutdown_closes_network_and_invalidates_callbacks(self):
        destroy = self.activity.split("override fun onDestroy(){", 1)[1]
        for marker in (
            "epgLoadGeneration++",
            "epgDownloadCancellation?.cancel()",
            "epgLoadingGeneration=null",
            "epgWaitingCallbacks.clear()",
        ):
            self.assertIn(marker, destroy)
        loader = self.activity.split("private fun loadEpg(done:", 1)[1].split(
            "private fun showChannelEpg(", 1
        )[0]
        ui = loader.split("runOnUiThread{", 1)[1]
        self.assertIn('!cancellation.isCancelled()', ui)
        self.assertIn('!isFinishing && !isDestroyed', ui)
        self.assertIn('epgGuide=replacement', ui)
        self.assertIn('epgLoadedAt=System.currentTimeMillis()', ui)
        self.assertNotIn('epgGuide=withNames', loader)

    def test_malformed_saved_lists_cannot_crash_startup_or_epg_actions(self):
        self.assertIn('private fun savedJsonArray(key:String):JSONArray = try {', self.activity)
        self.assertIn('catch(e:Exception){', self.activity)
        for key in ("epg_tune_schedules", "viewer_profiles"):
            self.assertIn('savedJsonArray("'+key+'")', self.activity)
        # The viewerId lookup already protects its fallback parse with
        # try/catch; only unguarded EPG reminder parsing is prohibited.
        self.assertNotIn('val saved=JSONArray(prefs.getString("epg_tune_schedules","[]"))',
                         self.activity)
        self.assertIn('savedJsonArray(viewerKey("recent_channels"))', self.activity)
        self.assertNotIn('JSONArray(prefs.getString(viewerKey("recent_channels"),"[]"))', self.activity)

    def test_approved_tv_preview_visuals_preserve_playback_controls(self):
        info = self.activity.split("private fun showChannelInfo(index:Int", 1)[1].split(
            "private fun clearChannelPreview(", 1
        )[0]
        self.assertIn("programmeColumn(\"NOW\"", info)
        self.assertIn("programmeColumn(\"NEXT\"", info)
        self.assertIn("R.drawable.tv_banner", info)
        self.assertIn("logoView(channel,64)", info)
        self.assertIn("previewInfoPanel=if(isPreview)panel else null", info)
        self.assertIn("handler.postDelayed(task,4000)", info)
        volume = self.activity.split("private fun showHomeTvVolume(){", 1)[1].split(
            "private fun safeRemoteTransport(", 1
        )[0]
        self.assertIn("orientation=LinearLayout.HORIZONTAL", volume)
        self.assertIn("progressBarStyleHorizontal", volume)
        self.assertIn("handler.postDelayed(task,2500L)", volume)
        guide = self.activity.split("private fun showEpgGrid(", 1)[1].split(
            "private fun showEpg(){", 1
        )[0]
        self.assertIn("background=infoPanelBackground()", guide)
        self.assertIn("styledMenuList()", guide)
        self.assertIn("list.setOnItemClickListener", guide)

    def test_visual_menu_theme_and_stale_channel_actions(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        theme = (ROOT / "app/src/main/res/values/myview_theme.xml").read_text()
        dialog_background = (ROOT / "app/src/main/res/drawable/myview_dialog_background.xml").read_text()
        self.assertIn('android:theme="@style/MyViewTheme"', manifest)
        self.assertIn('android:alertDialogTheme', theme)
        self.assertIn('android:dialogTheme', theme)
        self.assertIn('android:colorAccent', theme)
        self.assertIn('corners android:radius="16dp"', dialog_background)
        self.assertIn("private fun styledMenuList():ListView", self.activity)
        for name in (
            "showEpgGrid", "channelCollection", "mediaRows", "showChannels",
            "showGroupContents", "showLeftNavigation", "showLeftActions",
            "showPlaylistDrawer", "showGroupDrawerContents", "showPlaybackSettings"
        ):
            with self.subTest(name=name):
                start = self.activity.index("private fun " + name + "(")
                end = self.activity.find("\\n    private fun ", start + 12)
                content = self.activity[start:] if end < 0 else self.activity[start:end]
                self.assertIn("styledMenuList()", content)
        actions = self.activity.split("private fun showChannelInfoActions(", 1)[1].split(
            "private fun showChannelInfo(", 1
        )[0]
        self.assertIn("return@setItems", actions)
        self.assertIn("return@setPositiveButton", actions)
        self.assertNotIn("showHome()", actions)
        self.assertIn("No other visible channel in this playlist", actions)

    def test_manual_hide_is_not_reprompted_after_engine_failure(self):
        failure = self.activity.split("private fun handleEngineFailure(", 1)[1].split(
            "private fun isLocked(", 1
        )[0]
        self.assertIn('}else if(isManuallyHidden(c) || isGroupHidden(c.group)){', failure)
        self.assertIn('showBanner("No playable stream for this hidden channel",true)', failure)

        prompt = self.activity.split("private fun promptFailedChannel(", 1)[1].split(
            "private val recoveryPending=", 1
        )[0]
        self.assertIn("if(isManuallyHidden(c) || isGroupHidden(c.group)){", prompt)
        self.assertIn('failurePromptKey=null', prompt)
        self.assertIn("channels.getOrNull(index)?.let{channelKey(it)}!=key", prompt)

        skip = self.activity.split("private fun moveAfterFailedChannelHidden(", 1)[1].split(
            "private fun promptFailedChannel(", 1
        )[0]
        self.assertIn("nextSurfingIndex(index,surfingDirection,surfingIndices())", skip)
        self.assertNotIn("showHome()", skip)
        self.assertIn('showBanner("No other visible channel in this playlist",true)', skip)

    def test_hidden_channel_remotes_respect_original_order_and_direction(self):
        switching = self.activity.split("private fun nextSurfingIndex(", 1)[1].split(
            "private var rapidSurfTask:", 1
        )[0]
        self.assertIn("HiddenChannelNavigationPolicy.nextFromExcluded(", switching)
        self.assertIn("orderedChannelIndices(", switching)
        self.assertIn("val direction=if(delta>=0)1 else -1", switching)
        hiding = self.activity.split("private fun showChannelInfoActions(", 1)[1].split(
            "private fun showChannelInfo(", 1
        )[0]
        self.assertIn("nextSurfingIndex(index,surfingDirection,surfingIndices())", hiding)
        self.assertIn("playIndex(preferredChannel(next))", hiding)
        self.assertNotIn("showHome()", hiding)
        self.assertIn('showBanner("No other visible channel in this playlist",true)', hiding)

    def test_new_playlists_start_their_own_first_channel(self):
        start = self.activity.split("private fun startAddedPlaylist(", 1)[1].split(
            "private fun editPlaylist(", 1
        )[0]
        self.assertIn("channels.indexOfFirst{it.source==name", start)
        self.assertIn('putString("selected_playlist_source",name)', start)
        self.assertIn("playIndex(preferredChannel(first))", start)
        self.assertIn("if(first !in channels.indices)", start)
        self.assertIn("if(index==null)startAddedPlaylist(n)", self.activity)
        self.assertIn("refresh({startAddedPlaylist(n)},true)", self.activity)
        self.assertIn("refresh({startAddedPlaylist(title)},true)", self.activity)
        self.assertIn("refresh({startAddedPlaylist(displayName)},true)", self.activity)

    def test_channel_info_actions_are_connected(self):
        self.assertIn("private fun showChannelInfoActions(", self.activity)
        self.assertIn("openCurrentChannelEpg()", self.activity)
        self.assertIn("favorite(channel)", self.activity)
        self.assertIn("toggleManualHide(channel)", self.activity)
        self.assertIn("private fun moveChannelToPosition(", self.activity)
        self.assertIn("KEYCODE_CHANNEL_UP", self.activity)
        self.assertIn("KEYCODE_CHANNEL_DOWN", self.activity)

    def test_preload_and_multiview_lifecycle_entry_points_remain(self):
        for symbol in (
            "private fun takeReadyPrebuffer(",
            "private fun scheduleAdjacentPrebuffer(",
            "private fun releaseMultiView(",
            "private fun openMultiView(",
            "MultiViewSelectionPolicy.select(",
        ):
            with self.subTest(symbol=symbol):
                self.assertIn(symbol, self.activity)

    def test_profile_backup_and_license_paths_remain(self):
        for symbol in (
            "private fun viewerId(",
            "private fun manageViewerProfiles(",
            "private fun restoreSettingsFromText(",
            "FavoriteGroupBackupMergePolicy.merge(",
            "PortableSettingsCodec.decode(",
            "SignedLicenseCache(",
        ):
            with self.subTest(symbol=symbol):
                self.assertIn(symbol, self.activity)

    def test_source_verification_compiles_tests_lints_without_assembling(self):
        workflow = self.source_workflow
        for required in (":app:compileDebugKotlin", ":app:testDebugUnitTest",
                         ":app:lintDebug", "Verify no APK was assembled"):
            self.assertIn(required, workflow)
        self.assertNotIn(":app:assembleDebug", workflow)

    def test_optional_second_decoder_is_off_by_default(self):
        # Existing user choices remain untouched; new installations avoid
        # speculative decoder competition until deliberately enabled.
        self.assertNotIn(
            'prefs.getBoolean("myview_instant_preload_enabled",true)',
            self.activity,
        )
        self.assertIn(
            'settingsToggle("Preload adjacent Media3 channel (one only)",'
            '"myview_instant_preload_enabled",false)',
            self.activity,
        )

    def test_playback_first_defaults_preserve_advanced_features(self):
        policy = (ROOT / "app/src/main/java/com/softppj/nextv/FeatureTogglePolicy.kt").read_text(encoding="utf-8")
        self.assertIn("const val DEFAULT_MULTIVIEW = false", policy)
        self.assertIn("const val DEFAULT_SMART_LOGO_MATCHING = false", policy)
        self.assertIn('"myview_multiview_enabled" to DEFAULT_MULTIVIEW', policy)
        self.assertIn('"myview_smart_logo_matching" to DEFAULT_SMART_LOGO_MATCHING', policy)
        self.assertIn('settingsToggle("Enable MultiView","myview_multiview_enabled",FeatureTogglePolicy.DEFAULT_MULTIVIEW)', self.activity)
        self.assertIn('settingsToggle("Smart Logos — match other playlist entries","myview_smart_logo_matching",FeatureTogglePolicy.DEFAULT_SMART_LOGO_MATCHING)', self.activity)
        self.assertNotIn('prefs.getBoolean("myview_multiview_enabled",true)', self.activity)
        self.assertNotIn('prefs.getBoolean("myview_smart_logo_matching",true)', self.activity)
        self.assertIn('settingsToggle("Preload adjacent Media3 channel (one only)","myview_instant_preload_enabled",false)', self.activity)
        self.assertIn('settingsToggle("Live channel preview","live_channel_preview",false)', self.activity)
        self.assertIn('settingsToggle("Check alternative stream links in background","background_url_checks",false)', self.activity)
        # Essential functionality is still enabled and selectable.
        self.assertIn('"nextv_smartplay_enabled" to true', policy)
        self.assertIn('"nextv_epg_enabled" to true', policy)
        self.assertIn('"nextv_backup_enabled" to true', policy)

    def test_channel_skip_key_burst_is_debounced(self):
        self.assertIn("handler.postDelayed(task,350L)", self.activity)
        self.assertIn("private fun cancelRapidSurf()", self.activity)

    def test_channel_switch_detaches_media3_surface_before_release(self):
        # Prevent the previous PlayerView from retaining an outgoing codec
        # surface while the next foreground channel is being created.
        detach = self.activity.index("oldView?.player=null")
        release = self.activity.index("oldPlayer?.release()")
        self.assertLess(detach, release)

    def test_direct_vlc_native_release_precedes_new_direct_vlc_start(self):
        self.assertIn("private var pendingDirectVlcReleases=0", self.activity)
        self.assertIn("pendingDirectVlcReleases++", self.activity)
        self.assertIn("pendingDirectVlcReleases=(pendingDirectVlcReleases-1)", self.activity)
        self.assertIn("if(pendingDirectVlcReleases>0)", self.activity)
        self.assertIn("playbackGeneration!=generation", self.activity)

    def test_reentrant_switch_is_generation_bound(self):
        self.assertIn("val deferredGeneration=playbackGeneration", self.activity)
        self.assertIn("playbackGeneration==deferredGeneration", self.activity)

    def test_native_crash_exit_reasons_are_exportable(self):
        diagnostics = (ROOT / "app/src/main/java/com/softppj/nextv/HomeTvDiagnostics.kt").read_text(encoding="utf-8")
        self.assertIn("private fun recentProcessExits(includeAnrTraces:Boolean)", diagnostics)
        self.assertIn("ApplicationExitInfo.REASON_CRASH_NATIVE", diagnostics)
        self.assertIn("val report = all +", diagnostics)

    def test_isolated_vlc_event_callbacks_never_take_native_teardown_lock(self):
        service = (ROOT / "app/src/main/java/com/softppj/nextv/IsolatedVlcPlaybackService.kt").read_text(encoding="utf-8")
        callback = service.split("candidate.setEventListener { event ->", 1)[1]
        before_post = callback.split("eventHandler.post {", 1)[0]
        self.assertNotIn("synchronized(lock)", before_post)
        self.assertIn("if (player !== candidate || activeGeneration != requestGeneration)", callback)

    def test_best_available_vlc_keeps_adaptive_bitrate(self):
        self.assertIn("HomeTvPolicies.libVlcAdaptiveOptions(qualityMode(index))", self.activity)
        self.assertNotIn('else if(quality=="Best Available")', self.activity)
        policy = (ROOT / "app/src/main/java/com/softppj/nextv/HomeTvPolicies.kt").read_text(encoding="utf-8")
        self.assertIn('"Auto", "Best Available" -> emptyList()', policy)

    def test_channel_switch_startup_latency_is_measured(self):
        for marker in (
            "tuneRequestedAtMs=tuneRequestTime",
            "logFirstPlaybackProgress(index,screen,\"Media3\",\"first video frame\")",
            "logFirstPlaybackProgress(index,screen,\"LibVLC\",\"media progress\")",
            "logFirstPlaybackProgress(index,screen,\"Isolated LibVLC\",\"native progress\")",
            "Channel tune blocked during teardown",
        ):
            self.assertIn(marker, self.activity)

    def test_poster_artwork_is_size_and_concurrency_bounded(self):
        self.assertIn("LruCache<String,android.graphics.Bitmap>(12*1024*1024)", self.activity)
        self.assertIn("posterExecutor=java.util.concurrent.ThreadPoolExecutor(", self.activity)
        self.assertIn("ArrayBlockingQueue<Runnable>(64)", self.activity)
        self.assertIn("BoundedLogoDecoder.decode(it,512)", self.activity)
        self.assertIn("posterExecutor.shutdownNow()", self.activity)
        self.assertNotIn("android.graphics.BitmapFactory.decodeStream(it)", self.activity)

    def test_live_video_is_not_confirmed_by_early_audio(self):
        self.assertIn("val hasSelectedAudio=groups.any", self.activity)
        self.assertIn("val hasVideo=groups.any", self.activity)
        self.assertIn("if(hasSelectedAudio && !hasVideo)", self.activity)
        self.assertIn("exo.playbackState==Player.STATE_READY", self.activity)

    def test_libvlc_missing_native_library_is_a_recoverable_engine_failure(self):
        self.assertIn('failedEngine:String?=null', self.activity)
        self.assertIn('}catch(e:LinkageError){', self.activity)
        self.assertIn('handleEngineFailure(index,screen,failedEngine="LibVLC")', self.activity)

    def test_diagnostic_export_works_without_tv_file_picker(self):
        self.assertIn("private fun saveDiagnosticToDownloads(", self.activity)
        self.assertIn("android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI", self.activity)
        self.assertIn('thread(name="MyView-diagnostic-export")', self.activity)
        self.assertIn("getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)", self.activity)
        self.assertIn("HomeTvDiagnostics.readReport(includeAnrTraces=true)", self.activity)

    def test_anr_stack_is_bounded_in_diagnostics(self):
        diagnostics = (ROOT / "app/src/main/java/com/softppj/nextv/HomeTvDiagnostics.kt").read_text(encoding="utf-8")
        self.assertIn("exit.traceInputStream", diagnostics)
        self.assertIn("CharArray(64 * 1024)", diagnostics)
        self.assertIn("readReport(includeAnrTraces:Boolean=false)", diagnostics)

    def test_playback_diagnostics_never_write_files_on_remote_ui_thread(self):
        diagnostics = (ROOT / "app/src/main/java/com/softppj/nextv/HomeTvDiagnostics.kt").read_text(encoding="utf-8")
        self.assertIn("MyView-diagnostic-writer", diagnostics)
        self.assertIn("ArrayBlockingQueue<Runnable>(256)", diagnostics)
        self.assertIn("logWriter.execute { persist(level, tag, message, error) }", diagnostics)
        self.assertIn('@Synchronized private fun persist(', diagnostics)
        self.assertIn('persist("ERROR", "HomeTV", "Uncaught crash on thread "', diagnostics)

    def test_playlist_hash_avoids_per_byte_formatter_and_preserves_hex(self):
        self.assertNotIn('joinToString(""){"%02x".format(it)}', self.activity)
        self.assertIn('val alphabet="0123456789abcdef"', self.activity)
        self.assertIn('val value=byte.toInt() and 0xff', self.activity)
        self.assertIn('append(alphabet[value ushr 4])', self.activity)
        self.assertIn('append(alphabet[value and 0x0f])', self.activity)

    def test_anr_channel_order_is_loaded_once_per_source(self):
        # Real device ANR: dispatchKeyEvent -> surfingIndices ->
        # orderedChannelIndices -> savedChannelOrder -> SHA-256 hash.
        # Never reintroduce SHA/prefs/JSON parsing once PER CHANNEL.
        self.assertNotIn(
            "distinct.any{savedChannelOrder(channels[it].source).isNotEmpty()}",
            self.activity,
        )
        self.assertIn(
            ".distinct().associateWith(::savedChannelOrder)", self.activity
        )
        self.assertIn(
            "return applySavedChannelOrder(distinct,ordersBySource)", self.activity
        )
        self.assertIn(
            "preloadedOrders:Map<String,List<String>>?=null", self.activity
        )
        self.assertIn("now-surfingCacheAt<30000L)return cached", self.activity)

    def test_stable_media3_startup_avoids_repeated_quality_selection(self):
        # The user-selected track policy is applied before exo.prepare().
        # Reapplying on each READY transition can restart selection when
        # a live stream repeatedly buffers after its first frame.
        self.assertIn("            applyQuality()\n", self.activity)
        self.assertNotIn("                        scheduleQualityUpgrade()\n", self.activity)

    def test_default_audio_gain_uses_pcm_passthrough(self):
        processor = (ROOT / "app/src/main/java/com/softppj/nextv/ChannelGainAudioProcessor.kt").read_text(encoding="utf-8")
        self.assertIn("if(currentGain==1f)", processor)
        self.assertIn("output.put(input)", processor)
        self.assertIn("return\n        }\n        when(encoding)", processor)

    def test_apk_stays_manual_and_linted_before_packaging(self):
        workflow = self.apk_workflow
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("\n  push:", workflow)
        self.assertIn('test "${GITHUB_REF_NAME}" = "myview-full-app"', workflow)
        lint = workflow.index(":app:lintDebug")
        assemble = workflow.index(":app:assembleDebug")
        self.assertLess(lint, assemble)


if __name__ == "__main__":
    unittest.main()
