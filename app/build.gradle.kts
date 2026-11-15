plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Resolve the optional SDK once and pass the same path to Gradle and CMake.
// Previously -PnextvGstreamerSdk staged libraries but never built the JNI target.
val nextvGstreamerSdkPath = providers.gradleProperty("nextvGstreamerSdk")
    .orElse(providers.environmentVariable("NEXTV_GSTREAMER_SDK"))
    .orNull?.takeIf { it.isNotBlank() }?.let { file(it).absolutePath }
// NDK builds may already have compiled and staged an official GStreamer runtime.
// Only preserve that staging directory on an explicit, validated opt-in.
val nextvGstreamerPrebuilt = providers.gradleProperty("nextvGstreamerPrebuilt")
    .orElse(providers.environmentVariable("NEXTV_GSTREAMER_PREBUILT"))
    .map { it.equals("true", ignoreCase = true) || it == "1" }
    .getOrElse(false)

android {
    namespace = "com.softppj.nextv"
    compileSdk = 37
    buildFeatures { buildConfig = true; aidl = true }
    // Native SDK verification builds deposit GStreamer libraries here before Gradle merge.
    sourceSets.getByName("main").jniLibs.srcDir(file("$buildDir/gstreamer-jniLibs"))
    // Small real NDK/JNI runtime probe. It is not a relocated LibVLC.
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    defaultConfig {
        // The supplied optional GStreamer sysroot currently supports arm64 only.
        // Without this filter Gradle may configure x86/armeabi native targets
        // and fail even though the arm64 SDK is valid.
        if (nextvGstreamerSdkPath != null) {
            ndk { abiFilters += "arm64-v8a" }
        }
        if (nextvGstreamerSdkPath != null) {
            externalNativeBuild {
                cmake { arguments("-DNEXTV_GSTREAMER_SDK=$nextvGstreamerSdkPath") }
            }
        }
        // MPV edition application ID: installs independently beside stable NexTV.
        applicationId = "com.softppj.nextv.mpv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-mpv-experiment"
        // Legacy BuildConfig field retained until unused VLC source is fully removed;
        // no LibVLC runtime is packaged in the MPV APK.
        buildConfigField("String", "LIBVLC_VERSION", "\"3.5.1\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // CI may run without the optional pre-fetched Robolectric SDK cache.
        // Use that cache only when complete; otherwise let Robolectric resolve
        // instrumented Android SDK jars from Maven in its normal online mode.
        unitTests.all {
            val robolectricSdkDir =
                layout.buildDirectory.dir("robolectric-deps").get().asFile.absoluteFile
            val expected = listOf(
                "android-all-instrumented-9-robolectric-4913185-2-i7.jar",
                "android-all-instrumented-12.1-robolectric-8229987-i7.jar",
                "android-all-instrumented-15-robolectric-12650502-i7.jar"
            )
            if (expected.all { filename -> File(robolectricSdkDir, filename).let { it.isFile && it.canRead() } }) {
                it.systemProperty("robolectric.dependency.dir", robolectricSdkDir.absolutePath)
                println("Using pre-fetched Robolectric SDK jars from $robolectricSdkDir")
            } else {
                println("Robolectric SDK cache incomplete or absent; using Maven SDK resolution")
            }
            it.systemProperty("robolectric.logging.enabled", "true")
            it.testLogging.showStandardStreams = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Candidate libmpv Android AAR. Its native FFmpeg/mpv binaries are GPL-3.0-or-later.
    // This experiment must not be distributed without GPL source/licensing compliance.
    implementation("dev.jdtech.mpv:libmpv:1.0.0")
    // Legacy isolated service is not registered in the MPV manifest.
    // Compile-only VLC types remain until that unused source is removed.
    compileOnly("org.videolan.android:libvlc-all:3.5.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}


// MPV Edition must never package the old LibVLC runtime.
val verifyMpvRuntime by tasks.registering {
    group = "verification"
    description = "Checks that the MPV runtime has no LibVLC or nextlib native dependencies."
    doLast {
        val modules = configurations.getByName("debugRuntimeClasspath")
            .incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
        check(modules.any { it.group == "dev.jdtech.mpv" && it.name == "libmpv" }) {
            "MPV native runtime dependency missing"
        }
        check(modules.none {
            (it.group == "org.videolan.android" && it.name.startsWith("libvlc")) ||
                (it.group == "io.github.anilbeesetti" && it.name.startsWith("nextlib"))
        }) { "Legacy native playback dependency leaked into MPV runtime" }
        check(modules.none {
            it.group == "androidx.media3" &&
                it.name in setOf("media3-ui", "media3-effect", "media3-exoplayer-hls",
                    "media3-exoplayer-dash", "media3-exoplayer-rtsp",
                    "media3-exoplayer-smoothstreaming")
        }) { "Unused legacy Media3 playback/UI module leaked into MPV runtime" }
        println("Verified MPV native runtime without LibVLC or nextlib")
    }
}
tasks.named("check") { dependsOn(verifyMpvRuntime) }

/**
 * Prevent obsolete native engine entry points from being reintroduced into
 * the independently installed MPV edition while migration proceeds.
 */
val verifyMpvOnlyEntryPoints by tasks.registering {
    group = "verification"
    description = "Reject legacy player imports and VLC service registration."
    doLast {
        val activity = file("src/main/java/com/softppj/nextv/MainActivity.kt").readText()
        val manifest = file("src/main/AndroidManifest.xml").readText()
        listOf("import org.videolan.libvlc.", "import androidx.media3.").forEach { prefix ->
            check(!activity.contains(prefix)) { "Legacy native player imported into MPV activity: $prefix" }
        }
        check(!manifest.contains("IsolatedVlcPlaybackService")) {
            "Legacy isolated VLC service must not be registered in the MPV APK"
        }
        check(activity.contains("private fun startMpv(c:Channel,index:Int,screen:FrameLayout)")) {
            "MPV foreground playback route missing"
        }
        println("Verified MPV activity entry points and manifest isolation")
    }
}
tasks.named("check") { dependsOn(verifyMpvOnlyEntryPoints) }


/**
 * Migration safety gate: GStreamer must not silently change the app identity,
 * persisted licensing keys, licence server contract, playlist parser or TV launcher.
 * This intentionally runs without any GStreamer native SDK being installed.
 */
val verifyGstreamerPreservation by tasks.registering {
    group = "verification"
    description = "Protect NexTV licensing, settings and TV launcher during engine migration."
    doLast {
        val activity = file("src/main/java/com/softppj/nextv/MainActivity.kt").readText()
        val parser = file("src/main/java/com/softppj/nextv/M3uParser.kt").readText()
        val license = file("src/main/java/com/softppj/nextv/SignedLicenseCache.kt").readText()
        val manifest = file("src/main/AndroidManifest.xml").readText()
        val build = file("build.gradle.kts").readText()
        val requiredActivity = listOf(
            "getSharedPreferences(\"nextv\", MODE_PRIVATE)",
            "https://hometv-license-next.prasanthpjoy.workers.dev",
            "hometv://licensed-playlist",
            "license_device", "license_token", "license_expiry",
            "SignedLicenseCache(", "M3uParser"
        )
        requiredActivity.forEach { invariant ->
            check(activity.contains(invariant)) { "NexTV persistence/licensing contract changed: $invariant" }
        }
        listOf("signed_license_lease", "signed_license_server_time",
            "signed_license_wall_time", "signed_license_elapsed_time",
            "signed_license_last_seen_wall", "SHA256withRSA").forEach { invariant ->
            check(license.contains(invariant)) { "Signed license contract changed: $invariant" }
        }
        listOf("group-title", "tvg-logo", "tvg-id", "User-Agent", "Referer",
            "Origin", "media-type").forEach { invariant ->
            check(parser.contains(invariant)) { "Playlist parser contract changed: $invariant" }
        }
        check(manifest.contains("android.intent.category.LEANBACK_LAUNCHER")) {
            "Android TV launcher entry missing"
        }
        check(manifest.contains("android:allowBackup=\"false\"")) {
            "Unexpected backup security policy change"
        }
        check(build.contains("applicationId = \"com.softppj.nextv.mpv\"")) {
            "Independent app ID changed; would affect existing installations"
        }
        println("Verified NexTV licensing, persistence, playlist and TV launcher invariants")
    }
}
tasks.named("check") { dependsOn(verifyGstreamerPreservation) }


/**
 * MPV-only activity integration contract. The experimental GStreamer JNI source
 * remains independently checked by verifyGstreamerNativeBridge, but no removed
 * GStreamer playback route is required in the MPV edition.
 */
val verifyGstreamerMainActivityIntegration by tasks.registering {
    group = "verification"
    description = "Verify MPV-only activity routing, decoder controls and playback lifecycle."
    doLast {
        val activity = file("src/main/java/com/softppj/nextv/MainActivity.kt").readText()
        val player = file("src/main/java/com/softppj/nextv/MpvPlaybackView.kt").readText()
        listOf(
            "private var mpvPlayer:MpvPlaybackView?=null",
            "private fun startMpv(c:Channel,index:Int,screen:FrameLayout)",
            "view.initialize(selectedMpvDecoder(c),selectedMpvOutput(c))",
            "private fun selectedMpvDecoder(c:Channel)",
            "private fun selectedMpvOutput(c:Channel)",
            "private fun showMpvPlaybackDiagnostics()",
            "generation==playbackGeneration"
        ).forEach { invariant ->
            check(activity.contains(invariant)) { "MPV MainActivity integration missing: $invariant" }
        }
        listOf("engine.attachSurface(", "engine.detachSurface()",
            "fun diagnosticSnapshot()", "fun initialize(", "fun play(").forEach { invariant ->
            check(player.contains(invariant)) { "MPV playback lifecycle missing: $invariant" }
        }
        check(!activity.contains("private var gstreamerPlayer:GstreamerPlaybackView?=null")) {
            "Removed GStreamer player must not be reintroduced into the MPV edition"
        }
        println("Verified MPV-only activity routing, decoder/output controls and surface lifecycle")
    }
}
tasks.named("check") { dependsOn(verifyGstreamerMainActivityIntegration) }

/**
 * Fail closed: the experimental GStreamer route must not be considered verified
 * until the actual JNI playback bridge is declared and implemented.
 */
val verifyGstreamerNativeBridge by tasks.registering {
    group = "verification"
    description = "Verify JNI player source, CMake integration and official SDK for native builds."
    doLast {
        val cmake = file("src/main/cpp/CMakeLists.txt").readText()
        val adapter = file("src/main/java/com/softppj/nextv/GstreamerPlaybackView.kt").readText()
        val bridge = file("src/main/cpp/gstreamer_player_jni.cpp")
        check(bridge.isFile) { "GStreamer JNI player source missing" }
        val native = bridge.readText()
        listOf("nativeCreate", "nativePlay", "nativePause", "nativeStop",
            "nativeSeek", "nativeVolume", "nativePollStatus", "nativeLastError",
            "nativeSetSurface", "nativeRelease").forEach { symbol ->
            check(native.contains("GstreamerPlaybackView_$symbol")) {
                "GStreamer JNI method missing: $symbol"
            }
        }
        check(adapter.contains("System.loadLibrary(\"nextv_gst_player\")")) {
            "GStreamer adapter no longer loads its JNI library"
        }
        check(Regex("""add_library\s*\(\s*nextv_gst_player\s+SHARED""").containsMatchIn(cmake)) {
            "GStreamer CMake JNI target missing"
        }
        val sdk = providers.gradleProperty("nextvGstreamerSdk")
            .orElse(providers.environmentVariable("NEXTV_GSTREAMER_SDK")).orNull
        check(!sdk.isNullOrBlank() && file(sdk).isDirectory) {
            "Set NEXTV_GSTREAMER_SDK or -PnextvGstreamerSdk to an installed SDK sysroot"
        }
        check(file("$sdk/include/gstreamer-1.0/gst/gst.h").isFile) {
            "Missing GStreamer SDK headers"
        }
        val abi = "arm64-v8a"
        check(file("$sdk/include/gstreamer-1.0/gst/video/videooverlay.h").isFile) {
            "Missing GStreamer video overlay headers"
        }
        check(file("$sdk/lib/$abi/glib-2.0/include/glibconfig.h").isFile) {
            "Missing ABI-specific GLib configuration headers"
        }
        check(file("$sdk/lib/$abi/libgstreamer_android.so").isFile) {
            "Missing libgstreamer_android.so required by the Kotlin loader"
        }
        listOf("gstreamer-1.0", "gstvideo-1.0", "gobject-2.0", "glib-2.0").forEach { lib ->
            check(file("$sdk/lib/$abi/lib$lib.so").isFile) {
                "GStreamer SDK missing lib$lib.so for $abi"
            }
        }
        println("GStreamer native source and SDK sysroot verified for $abi")
    }
}

// Regular checks must work without the optional native SDK; release approval
// must invoke verifyGstreamerNativeBridge explicitly, after provisioning it.
val verifyGstreamerNativeSource by tasks.registering {
    group = "verification"
    description = "Check all JNI symbols and CMake target without an SDK installation."
    doLast {
        val native = file("src/main/cpp/gstreamer_player_jni.cpp").readText()
        listOf("nativeCreate", "nativePlay", "nativePause", "nativeStop",
            "nativeSeek", "nativeVolume", "nativePollStatus", "nativeLastError",
            "nativeSetSurface", "nativeRelease").forEach { symbol ->
            check(native.contains("GstreamerPlaybackView_$symbol")) {
                "Missing JNI entry point: $symbol"
            }
        }
    }
}
tasks.named("check") { dependsOn(verifyGstreamerNativeSource) }


/**
 * Optional native runtime staging. The official GStreamer SDK is not downloaded
 * or redistributed by this project. When supplied, stage its arm64 shared
 * libraries for Android packaging, preserving the ABI directory structure.
 * This does not claim the SDK plugin registry is complete or playback tested.
 */
val stageGstreamerRuntime by tasks.registering(Sync::class) {
    group = "build"
    description = "Stage supplied GStreamer arm64 native libraries for the optional player."
    val sdk = providers.gradleProperty("nextvGstreamerSdk")
        .orElse(providers.environmentVariable("NEXTV_GSTREAMER_SDK"))
    val source = sdk.map { file("$it/lib/arm64-v8a") }
    onlyIf {
        if (!sdk.isPresent) {
            if (nextvGstreamerPrebuilt) {
                // Native SDK verification compiled these via ndk-build.
                // Do not erase them just before :app:mergeDebugNativeLibs.
                val staged = layout.buildDirectory.dir("gstreamer-jniLibs/arm64-v8a")
                    .get().asFile
                val required = listOf("libgstreamer_android.so", "libnextv_gst_player.so")
                val missing = required.filterNot { staged.resolve(it).isFile }
                check(missing.isEmpty()) {
                    "Prebuilt GStreamer runtime missing: ${missing.joinToString()}"
                }
            } else {
                // A normal MPV build must never reuse a previous SDK build's
                // native libraries accidentally.
                delete(layout.buildDirectory.dir("gstreamer-jniLibs"))
            }
            false
        } else {
            check(source.get().isDirectory) {
                "NEXTV_GSTREAMER_SDK is set but its arm64-v8a library directory is missing"
            }
            true
        }
    }
    from(source) {
        include("*.so")
        into("arm64-v8a")
    }
    into(layout.buildDirectory.dir("gstreamer-jniLibs"))
    doFirst {
        val nativeDir = source.get()
        val required = listOf(
            "libgstreamer-1.0.so", "libgstvideo-1.0.so",
            "libgobject-2.0.so", "libglib-2.0.so", "libgstreamer_android.so"
        )
        val missing = required.filterNot { nativeDir.resolve(it).isFile }
        check(missing.isEmpty()) {
            "GStreamer Android arm64 runtime is incomplete: ${missing.joinToString()}"
        }
        check(nativeDir.listFiles()?.any { it.name.startsWith("libgst") && it.extension == "so" } == true) {
            "No GStreamer plugins or runtime libraries found in the SDK"
        }
    }
}
tasks.named("preBuild") { dependsOn(stageGstreamerRuntime) }
