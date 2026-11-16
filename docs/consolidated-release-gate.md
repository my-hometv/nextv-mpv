# NexTV consolidated release gate

A successful flavor compilation is **not** evidence that three LibVLC native runtimes can coexist. Do not publish an all-engine release until source-built relocated AARs for all three versions pass `tools/verify_libvlc_isolation.py`, and on-device loading, playback, recovery and remote-control tests pass. MPV must be separately verified before advertising it.

The existing manual beta workflow builds only the vlc351 flavor and is for internal diagnostics, not the complete multi-engine release. No debug build should be described as a production release. Signed release publishing requires explicit authorization and signing credentials.

Required before enabling version-specific engine IDs:
- Three audited source-built, relocated AARs with recorded commit provenance and checksums
- Noncolliding Java/JNI and ELF SONAME/DT_NEEDED dependencies, plus supported ABIs
- Runtime tests confirming three independently loadable versions
- Engine selection and preferences migrated without falsely exposing absent runtimes
- URL-first recovery, stale callback and background probe regression tests
- Android TV device playback, remote focus, process restart, and crash recovery
- Distribution license review and properly signed APK
