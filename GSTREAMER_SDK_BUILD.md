# Official GStreamer Android SDK compilation gate

This workflow downloads **GStreamer 1.28.8** from the official release
directory, checks the published SHA-256 sidecar, extracts the arm64 SDK,
builds the existing JNI adapter with the Android NDK, and requires Gradle
to merge both `libnextv_gst_player.so` and `libgstreamer_android.so`.

Workflow: `.github/workflows/gstreamer-native-compile.yml`

This is deliberately a **no-APK** compilation test. It does not prove
playback, codec coverage, device compatibility, or correct Android Java
GStreamer initialization. Additional ABIs, player bus handling, Java
initialization and MainActivity integration are still pending.

The ordinary MPV integration CI remains separate while migration is
unfinished. Do not treat its green result as GStreamer compilation.
