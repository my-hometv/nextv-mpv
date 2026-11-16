# DASH live playback: source-grounded investigation

User-supplied manifest (16 Nov 2026): dynamic DASH, refresh 2s, 60s time-shift window, 10s minimum buffer, 1s video segments, H.264 360p/480p/720p/1080p at 25 fps; seven audio adaptation sets (AAC except Hindi AC-3). Both audio and video adaptation sets advertise Common Encryption and Widevine. Do not publish full manifest, keys, license traffic, session tokens, or stream URL.

Observed: PC browser plays clearly; NexTV Media3 displays low-quality video that freezes for seconds and jumps ahead, without sound; LibVLC fails. Browser success does not establish that standalone Android TV has authorized DRM access.

Private stable development source inspection:
- DASH MIME mapping and decoder fallback already exist.
- Track preference is reapplied from onTracksChanged.
- Media3 STATE_BUFFERING calls autoBufferStalled and can invoke handleEngineFailure after 20 seconds; distinguish transient buffering from fatal errors.
- Current onPlayerError logs throwable and HTTP status but does not explicitly classify DRM errors.
- No frame-drop callback was found in MainActivity.

Next integration requirements (not yet performed):
1. Record Media3 selected video format (width, height, codecs), selected audio format, decoder identity, video dropped frames, buffered duration, playback position and live offset, without logging sensitive URL/query.
2. Classify Media3 DRM errors separately from HTTP, codec and buffering failures. Do not attempt to bypass license checks.
3. Check if authorized Widevine license configuration exists; the manifest's PSSH is not a license endpoint.
4. Use the pure DashStallDecision policy only after validating with actual runtime events and foreground recovery semantics.
5. Test Android TV hardware against authorized DRM content and independent unencrypted DASH control stream.
6. Keep MPV main branch and private stable source unchanged; these diagnostic files alone do not constitute a working Media3 fix.
