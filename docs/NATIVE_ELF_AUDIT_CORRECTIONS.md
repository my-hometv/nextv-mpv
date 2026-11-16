# Native ELF audit corrections

Corrected the ELF magic check and regex parsing for dynamic dependencies and exported JNI symbols. The previous source contained over-escaped patterns that could reject valid ELF libraries or fail to detect symbol collisions.

The audit now treats absent SONAME and absent Java_-prefixed exports as review items, not automatic proof of broken libraries: Android native libraries may use dynamic registration. Actual ELF corruption, unresolved LibVLC dependencies and duplicate JNI exports remain failures.

**Not implemented:** relocation of LibVLC source/JNI symbols, native rebuilding, or unified APK packaging. Those steps cannot be replaced by a filename or audit change. No build or APK was run.
