# NexTV GStreamer Edition — Migration Contract

## Scope
Convert this existing independent MPV-edition application into a **GStreamer-only** Android TV player. Keep the original NexTV stable repository untouched. Do not create another repository.

## Immutable application behavior
Preserve the existing licensing and activation flows, storage keys and settings, playlist formats and persistence, channel and favourites state, navigation and remote-control mappings, diagnostic privacy and UI behavior. Do not reset or migrate user preferences without an explicit compatibility plan.

## Playback replacement
- Replace `MpvPlaybackView` and all `MPVLib` calls with a real Android GStreamer playback integration, including surface lifecycle, stream headers, live playback, pause/resume, seek, tracks, audio/subtitle selection, volume, picture settings, aspect ratio, and error/first-frame callbacks.
- Preserve playback entry points used by `MainActivity` until equivalent GStreamer APIs exist; do not remove the active player before its replacement compiles.
- Remove MPV AAR, native binaries, engine settings and diagnostics after successful replacement.
- Remove unused Media3 and legacy LibVLC source and Gradle dependencies **only after** checking all references; the final runtime must not package any of these engines.
- Make GStreamer the only packaged playback engine. GStreamer Android SDK native binaries must be supplied from a verified, legally redistributable source and support the target Android ABIs. Do not invent a Maven artifact or substitute Media3.
- Verify MPEG-TS, HLS, H.264/H.265, interlacing, colour metadata, frame pacing and audio sync on the TV; compatibility and picture quality are not established by compilation alone.

## Release gates
1. Source audit of playback references and preservation of licensing/settings.
2. Integrate verified GStreamer SDK and compile an end-to-end playback view.
3. Remove obsolete engines and verify APK native dependency contents.
4. Run unit tests, Android compilation and native packaging audit.
5. Generate a separate TV-test APK only after the above checks pass.
6. Verify the reported Full HD channel's picture quality on-device.

**Current status:** migration contract established; GStreamer integration and engine removal are not yet complete. The existing MPV build remains functional during migration.
