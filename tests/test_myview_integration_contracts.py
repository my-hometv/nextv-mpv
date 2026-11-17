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
        self.assertIn("private fun recentProcessExits()", diagnostics)
        self.assertIn("ApplicationExitInfo.REASON_CRASH_NATIVE", diagnostics)
        self.assertIn("val report = all +", diagnostics)

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
