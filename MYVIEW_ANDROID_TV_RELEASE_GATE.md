# MyView — physical Android TV release gate

**Scope:** playback, picture fidelity, prebuffer/decoder limits, instant switching, and remote navigation. **Licensing is out of scope and must not be modified.**

**Source baseline:** `myview-full-app` branch of `my-hometv/nextv-mpv`. This checklist records required on-device evidence; source inspection and a green JVM test suite are not substitutes for TV acceptance.

## Device/test conditions
- Record TV make/model, Android version, CPU/ABI, display mode, HDR support and app build SHA.
- Record which playback engine was selected, channel format (HLS / MPEG-TS / DASH), codec, resolution, frame rate and whether the stream is HDR or SDR.
- Test using permitted streams; redact credentials, tokens and private playlist URLs in reports.
- Compare the same stream and TV picture preset against a known good Media3 baseline.
- For each failing case record observed/result, reproducibility, diagnostics and a short video where useful.

## Release acceptance matrix

| ID | Scenario | Acceptance |
| --- | --- | --- |
| TV-01 | Fresh launch, resume last working channel | Video and audio stabilize; remote remains responsive; no crash or black screen |
| TV-02 | MPEG-TS channel | Sound and picture play; no repeated inappropriate alternative prompts |
| TV-03 | HLS 1080p SDR | Correct colour, scaling, aspect ratio and stable motion |
| TV-04 | HDR10 stream on HDR-capable TV | TV indicates expected HDR behavior, no washed-out/negative picture |
| TV-05 | HDR10 stream on SDR-only TV | Correct tone mapping/colour or graceful documented fallback |
| TV-06 | Video decoder exception/stall | Bounded recovery does not crash, loop, or incorrectly abandon a known working URL |
| TV-07 | Sudden short network interruption | Same confirmed URL recovers where possible; no immediate skip prompt |
| TV-08 | Alternative URL / engine fallback | Sequence is bounded; stale callbacks cannot replace a newer selection |
| TV-09 | Repeated rapid channel changes | No native crash, orphaned audio, leaked player or long UI freeze |
| TV-10 | Default aspect ratio | First-run/default screen mode is 16:9; user override persists |
| TV-11 | Default stream quality | Best Available is selected; verify source quality actually used |
| TV-12 | UP/DOWN remote | Changes only highlighted preview; old channel continues until OK |
| TV-13 | Preview timeout | Highlight disappears after ~5 seconds by default without tuning |
| TV-14 | Preview settings | Chosen timeout survives restart and is applied |
| TV-15 | OK / BACK | OK tunes highlighted channel, BACK dismisses preview/drawer before exit |
| TV-16 | Numeric keys | Multi-digit channel selection matches current playlist, does not cross playlists |
| TV-17 | LEFT → LEFT → LEFT | Navigation drawers move predictably and retain correct DPAD focus |
| TV-18 | Clock display | Time advances every second; default 10-second time / 5-second day-date cycle |
| TV-19 | Clock settings | Adjusted durations and show/hide settings persist and apply live |
| TV-20 | Automatic adjacent preload enabled | At most one speculative Media3 player is allocated; active playback remains stable |
| TV-21 | Prepared-channel promotion | Ready, unlocked, matching channel reuses prepared player; no second stream initialization |
| TV-22 | Preload not ready / source rejected | Safe ordinary switch without black-screen loop or crash |
| TV-23 | Preload disabled | Speculative player releases immediately; no additional playback requests |
| TV-24 | LibVLC / locked content / MultiView active | Speculative preloading does not compete or bypass any access constraints |
| TV-25 | 2-screen and 4-screen MultiView | Respect device decoder limits; degrade gracefully without crashing active TV |
| TV-26 | HOME / app switch / return | Background video and audio stop; resumed channel does not duplicate audio |
| TV-27 | Repeat switching 50+ times | No steadily increasing decoder/session count, memory exhaustion or leaked surfaces |
| TV-28 | TV reboot / cold startup | Last eligible channel resumes with safe-startup protection when applicable |
| TV-29 | Display mode matching for VOD | Off by default, only explicit VOD; never unexpectedly blanks Live TV |
| TV-30 | Physical remote variants | DPAD, OK, BACK, CH+/CH-, number buttons, menu and mapped transport keys |

## Release conditions
- Unit tests, Kotlin compilation and native packaging checks must pass on the **same commit** proposed for testing.
- Identify and review APK-native dependencies and avoid publishing production APKs until all acceptance checks are complete.
- Block release for crashes, inaccessible UI, persistent video corruption, accidental playlist crossover, unbounded retries or duplicate decoder sessions.
- Record actual-device evidence for the matrix; any untested case remains **PENDING**, never **PASS** by assumption.
- Cloudflare Worker, activation keys, signed lease formats and all licensing code are explicitly outside this exercise.
