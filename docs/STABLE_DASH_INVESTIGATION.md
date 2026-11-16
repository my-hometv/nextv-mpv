# Stable NexTV MPEG-DASH investigation (public-safe notes)

This branch is reserved for an isolated DASH investigation. It currently starts from the MPV app, **not** the private stable Media3/LibVLC app. Do not treat it as a buildable stable-app copy.

## Observed symptoms (user's TV)
- Media3: intermittent slideshow-like video, no sound, warning about audio-track availability.
- LibVLC: no playback.
- Test case: a multi-audio MPEG-DASH `.mpd` stream (URL intentionally omitted from public notes).

## Findings from private development source review (no private source copied)
- DASH MIME is already explicitly set for `.mpd` media items.
- Decoder fallback is already enabled in Media3.
- Audio preference logic filters to supported tracks and applies a `TrackSelectionOverride` when a matching language/signature is found.
- The audio selection dialog distinguishes absent audio tracks from tracks marked unsupported.
- An `onTracksChanged` callback reapplies the preferred track. Investigate whether an override can be re-applied across changing DASH adaptation groups, whether support flags change after decoder initialization, and whether fallback to automatic selection is needed.
- Do not attribute slideshow playback to audio-track selection without video decoder/timestamp evidence.

## Required diagnostics before a fix is claimed
1. DASH manifest representation list: video/audio codecs, profiles, timescales, adaptation sets, protection/DRM and segment timing.
2. Media3 `PlaybackException` error code, decoder name, track support, selected formats, dropped frames, rendered frame count, playback speed and playback state.
3. Compare audio auto-selection with explicit language choice; do not force an unsupported track.
4. Test decoder fallback and alternate video representation; preserve source resolution where compatible.
5. Investigate LibVLC DASH demux and codec errors separately.

## Public-source safety gate
Before transferring private app source to this public branch, scan every file and Git history for credentials, tokens, embedded playlists, customer endpoints, private licensing keys and sensitive URLs. Copying a private Git commit/history into a public repository would expose historical content even if removed later. Prefer a sanitized source snapshot with new history. Keep original private stable branch unchanged.

## Verification
No DASH playback fix or Android build has been performed yet.
