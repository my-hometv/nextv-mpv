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
