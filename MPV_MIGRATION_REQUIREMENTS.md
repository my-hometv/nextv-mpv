# NexTV MPV migration: non-negotiable requirements

## Feature parity
The MPV edition must retain **all existing NexTV user-facing features** unless the owner explicitly approves their removal. Replacing Media3, LibVLC or experimental GStreamer is an engine migration, **not permission to delete features**.

Retain licensing and entitlement checks, parental controls, playlists, favorites, settings, channel navigation, last-working-channel restoration, EPG, picture controls, URL checking, hidden-channel scans, manual alternatives, background checks, automatic reconnect and alternative URL recovery.

## Single playback engine
Use libmpv for foreground playback and for decoder-backed availability confirmation. Remove legacy Media3/LibVLC dependencies only **after** their dependent features have equivalent MPV implementations. Keep HTTP reachability checks independent of the playback engine where possible. Avoid multiple concurrent native decoders on Android TV.

## Recovery and checks
A brief signal loss must not trigger a misleading alternative-URL prompt for a previously confirmed working stream. Keep background checks non-disruptive and do not replace the active channel without the established user-selection policy. Preserve channel check settings and their existing default values.

## Video quality
Support MPEG-TS (.ts), HLS (.m3u8), and other formats supported by the bundled FFmpeg/libmpv build. Preserve source color metadata; do not globally force SDR/HDR, BT.709 or full-range. Allow user-selected HDR/SDR output tests without claiming unsupported hardware can output HDR.

## Engineering sequence
1. Inventory every use of legacy playback, probing, menus and settings.
2. Replace each legacy capability with MPV-compatible functionality and regression tests.
3. Remove obsolete code, dependencies and native packaging only when no call sites remain.
4. Audit compile/test readiness and validate on actual Android TV hardware.
5. **Do not run workflows, build APKs or publish releases until explicitly requested.**

## Verification gate
Do not claim 100% migration or release readiness while MainActivity still references ExoPlayer, LibVLC, isolated VLC sessions, GStreamer playback or legacy decoder probes. Commit source changes independently from CI execution.
