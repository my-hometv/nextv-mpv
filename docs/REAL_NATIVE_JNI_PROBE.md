# Real Android NDK runtime probe

This batch introduces an actual C++ JNI module (`nextv_vlc_probe`) built through CMake in each existing LibVLC flavor. The module uses `dlopen` and `dlsym` to find the packaged `libvlc_get_version` C API and returns its version string through JNI. The isolated playback service now fails closed if the native API cannot be loaded.

The version returned by LibVLC is **diagnostic**, not an exact comparison with the Maven artifact version: prerelease and EAP metadata can differ.

**Scope:** This is a real native compilation and JNI integration path for the currently packaged single-version flavor. It does **not** create three relocated LibVLC libraries or solve their overlapping Java/JNI namespaces. The native source and Gradle configuration are committed; no Android build, CI workflow, APK or on-device validation was run.
