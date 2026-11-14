# GStreamer native CI coverage

The GStreamer native-compile workflow now triggers on changes to GstreamerPlaybackView.kt as well as JNI and Gradle files. Previously Kotlin-only GStreamer lifecycle changes ran the MPV verification workflow but did not run the official GStreamer SDK native build gate. The native gate also checks for exported JNI create and poll-status entry points after compilation.

The workflow still compiles only arm64, merges native libraries into Gradle intermediates, and intentionally does not build an APK. This does not establish runtime playback or guarantee all Android ABIs work.
