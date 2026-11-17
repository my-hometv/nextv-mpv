#!/usr/bin/env python3
"""Read-only MyView startup, branding and release-safety contract checks.

This is a source-level regression gate, not an instrumented Android test.
It MUST NOT invoke Gradle assemble or publish any APK.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def check(root: Path = ROOT) -> list[str]:
    errors = []
    activity = (root / "app/src/main/java/com/softppj/nextv/MainActivity.kt").read_text()
    manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
    gradle = (root / "app/build.gradle.kts").read_text()
    source_ci = (root / ".github/workflows/myview-source-verification.yml").read_text()
    legacy = (root / ".github/workflows/myview-branding-build.yml").read_text()
    manual = (root / ".github/workflows/myview-full-app.yml").read_text()
    strings = (root / "app/src/main/res/values/strings.xml").read_text()

    def require(ok: bool, message: str) -> None:
        if not ok:
            errors.append(message)

    def section(start: str, end: str) -> str:
        a = activity.find(start)
        b = activity.find(end, a + len(start)) if a >= 0 else -1
        if a < 0 or b < 0:
            errors.append(f"Cannot locate source section: {start}")
            return ""
        return activity[a:b]

    startup = section("private fun openStartupDestination()", "private fun scheduleStartupHiddenChannelScan()")
    resume = section("private fun resumeLast()", "private fun liveSettings()")
    home = section("private fun showHome()", "private val importPlaylistRequest")
    checking = section("private fun showLicenseCheckingScreen()", "private fun ") if False else activity

    require("resumeLast()" in startup, "Normal licensed startup must resume television")
    require('safe_startup_channel_one_attempted' in startup,
            "Crash loop recovery must attempt the first permitted channel only once")
    require('last_working_url' in resume and 'last_working_channel_identity' in resume,
            "Resume must prioritize the last confirmed working channel")
    require("continueBanner.post{" in home and "continueBanner.requestFocus()" in home,
            "Home initial DPAD focus must target the focusable Continue TV banner")
    require("homeContent.getChildAt(2)" not in home,
            "Do not regress to focusing an unselectable Home section heading")
    require("R.drawable.tv_banner" in home and
            activity.count("setImageResource(R.drawable.tv_banner)") >= 3,
            "Startup, Home and channel information must reuse approved family artwork")
    require('android:icon="@drawable/nextv_launcher"' in manifest and
            'android:banner="@drawable/tv_banner"' in manifest,
            "TV launcher resources must reference approved branding")
    require('applicationId = "com.softppj.myview"' in gradle and
            'name="app_name">MyView' in strings,
            "MyView must install independently with its own name and ID")
    require('workflow_dispatch:' in manual and '  push:' not in manual,
            "The full-app APK build must remain manual-only")
    require("    if: ${{ false }}" in legacy and "  push:" not in legacy,
            "Retired branding build must remain a disabled, non-push workflow")
    require(":app:assemble" not in source_ci and
            "tools/verify_myview_branding.py" in source_ci and
            "tools/verify_myview_source_contract.py" in source_ci,
            "Source-only CI must never assemble an APK and must validate both contracts")
    return errors

def main() -> int:
    errors = check()
    for error in errors:
        print("FAIL:", error)
    if errors:
        print("MyView source contract FAILED; do not build an APK")
        return 1
    print("PASS: MyView startup, image, app identity and manual-only release contract")
    return 0

if __name__ == "__main__":
    sys.exit(main())
