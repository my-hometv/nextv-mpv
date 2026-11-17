# MyView physical-TV verification — executable acceptance gate

This branch prepares **on-device evidence collection**, not a claim of hardware PASS.
The baseline source-only CI passed on commit `d23501b3c27595aae25eedbf92829e4a030c6118`.
No APK build or publication is authorized by this change.

## Prerequisites
- Python 3.9 or later and Android SDK Platform Tools (`adb`) installed on your computer
- Android TV with developer options / USB or wireless debugging enabled
- Approve the debugging prompt on the TV
- A separately authorized MyView test installation with package `com.softppj.myview`
- Valid licensed access and streams you are authorized to test

## Run
```sh
adb devices
python tools/myview_tv_acceptance.py --interactive --output myview-tv-evidence
```
For multiple connected devices, pass `--serial DEVICE_SERIAL`. The script records model,
Android release, device codec/display capabilities, application presence and up to 100
filtered crash indicators from the most recent 3000 logcat entries. It never drives playback,
downloads streams, captures private playlists, creates APKs or uploads reports.

The interactive checklist asks for human-observed PASS, FAIL, BLOCKED or NOT_TESTED and
optional notes for each of the 30 TV scenarios in `MYVIEW_ANDROID_TV_RELEASE_GATE.md`.
Without `--interactive`, all scenarios remain NOT_TESTED.

## Test order and evidence
1. Record model/Android version, baseline stream and app commit SHA.
2. Compare the **same** SuryaTV HDR10 stream with the known-good Media3 setup using
   the TV's unchanged picture settings. Record HDR icon/mode, transfer characteristics,
   resolution, colors, framerate and audio sync. Do not assume HDR is supported by all TVs.
3. Test SDR 1080p and MPEG-TS streams with permitted sources. Watch for false fallback prompts.
4. Test network drop/reconnect and rapid remote switching; record freezes, repeated
   stream starts, abandoned known-good URLs and orphaned audio.
5. Test 2/4-grid MultiView (if the TV has capacity), then 50+ channel switches,
   return HOME and restart. Track memory/decoder resources if diagnostics allow.
6. Walk through numeric keys, UP/DOWN preview, LEFT three times, OK, BACK, CH+/CH-,
   favorites, hidden/unhidden, EPG, saved rearrangement, profiles, and backup/restore.
7. Repeat with automatic preloading disabled; confirm no speculative decoding.
8. Run the collector again immediately after failures to collect recent crash evidence.

## Gate interpretation
- The tool **never** marks a scenario PASS automatically. Unobserved items remain NOT_TESTED.
- `CANDIDATE_FOR_REVIEW` requires all 30 manually marked PASS, package detected, and
  no matching recent crash indicators. It is **not** release certification and does not
  exclude missed ANRs, codec faults, or native issues.
- Any FAIL, BLOCKED or NOT_TESTED is a HOLD. A human reviewer must compare source SHA
  against the tested build and inspect evidence before approval.
- The report is local JSON. Inspect it before sharing; avoid placing secrets or private
  stream endpoints in notes. The filtered log lines are redacted but may still carry
  incidental device metadata.
- This acceptance collector does not replace instrumentation, sustained memory profiling,
  native tombstones, or testing on the specific target TV model.

## Current limitations
No interactive TV, HDMI display, physical remote, or installed binary is connected to
GitHub Actions, so these tests cannot be truthfully marked PASS remotely. The current
source-only workflow cannot establish pixel-accurate HDR/SDR fidelity or hardware
MPEG-TS support. Maintain production APK hold until the user explicitly approves a
separate test-APK build and the observations justify it.
