# NexTV GStreamer — Progress Chart

## Operating mode
**Manual, user-directed.** No automatic engineering schedule. Work only when the user requests continuation. After each substantial, verified work package, report the commit, tests, completion percentage, and next package.

## Measurement
Implementation completion starts at **0%**. Planning and migration documentation are committed but are not counted as GStreamer implementation. Progress is earned only for accepted, tested outcomes. Do not claim 100% before final APK readiness; TV picture quality is a separate device-test gate.

| Stage | Weight | Completed | Acceptance gate |
| --- | ---: | ---: | --- |
| 01 Source audit and licensing/settings preservation tests | 10% | 10% | Audit and regression safeguards committed |
| 02 GStreamer Android SDK and JNI/native integration | 15% | 0% | Verified SDK/ABI and CI compilation |
| 03 GStreamer playback core | 20% | 0% | Surface, stream, lifecycle and playback APIs compiled/tested |
| 04 TV UI and settings integration | 15% | 0% | Remote, switching, audio/subtitle, picture controls verified |
| 05 Format support and picture fidelity | 15% | 0% | Compatibility tests and evidence, device caveats documented |
| 06 MPV, Media3 and VLC removal | 10% | 0% | No legacy runtime/compile dependencies or dead playback code |
| 07 Full audit and regression verification | 10% | 0% | Gradle CI green and licensing/settings preserved |
| 08 APK readiness | 5% | 0% | APK artifact workflow passes, installable test APK |
| **TOTAL** | **100%** | **10%** | |

## Commit policy
- Prefer **substantial coherent commits**: a fully integrated feature, major cleanup with replacements, or a meaningful set of regression tests.
- Avoid progress inflation or cosmetic-only commits.
- Each completed package must include code, relevant tests, CI verification and a progress chart update.
- Do not delete legacy engine paths until the replacement compiles.
- Never change licensing, activation, playlist or existing user settings behavior.
- If blocked, report the blocker and retain the last working commit.

## Existing planning commits
- `c539cb0`: migration contract
- `553f984`: eight-stage development flow
- Stage 01 source inventory and automated preservation gate committed; **implementation 10%** (CI verification pending).

## Next work
Stage 02: integrate a verified GStreamer Android SDK and JNI bridge. Do not remove MPV/Media3/VLC until equivalent native playback is compiled.
