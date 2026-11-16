# Native ELF/JNI inspection

A relocated AAR must contain more than renamed files. Its shared libraries must be genuine ELF objects, with compatible JNI entry points and resolvable native dependencies. The build gate now calls `tools/verify_libvlc_native_elf.py` after structural AAR checks pass.

The script uses Android NDK `llvm-readelf` (or `READELF`), examines each supplied AAR's native ELF files, and reports dynamic dependencies, JNI exports and cross-version symbol collisions. It deliberately fails closed when the tool or required AARs are unavailable.

**Limitations:** Some LibVLC builds register JNI methods dynamically through `RegisterNatives`, so symbol-only verification cannot prove compatibility. SONAME/dependency analysis and successful loading/decoding on Android TV remain mandatory. This is native binary verification code, not an implementation of relocated LibVLC binaries. No native binaries or APKs were generated.
