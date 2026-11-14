# NexTV MPV consolidated playback audit

- Runtime AAR: dev.jdtech.mpv:libmpv:1.0.0. Its version does not prove embedded native MPV/FFmpeg versions. Do not upgrade blindly.
- MPV only for selected playback; legacy Media3/LibVLC source still exists and requires call-graph refactoring before removal.
- Default decoder uses MediaCodec copy-back; Software disables hardware decoding.
- Preserve source colour metadata; do not globally force BT.709 or full-range.
- Diagnostics expose decoder, pixel format, colour metadata, resolution, FPS and dropped frames.
- Avoid video-output resets on surface transitions.
- CI compilation and tests do not prove real-device colour accuracy, MPEG-TS timing, 50/60fps smoothness or native crash safety.
- Before release: inspect native binary versions, Android ABIs, licensing and run real TV playback tests.
