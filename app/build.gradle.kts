plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Resolve the optional SDK once and pass the same path to Gradle and CMake.
// Previously -PnextvGstreamerSdk staged libraries but never built the JNI target.
val nextvGstreamerSdkPath = providers.gradleProperty("nextvGstreamerSdk")
    .orElse(providers.environmentVariable("NEXTV_GSTREAMER_SDK"))
    .orNull?.takeIf { it.isNotBlank() }?.let { file(it).absolutePath }

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
    implementation("androidx.media3:media3-exoplayer:1.12.0-beta01")
    implementation("androidx.media3:media3-exoplayer-hls:1.12.0-beta01")
    implementation("androidx.media3:media3-exoplayer-dash:1.12.0-beta01")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.12.0-beta01")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.12.0-beta01")
    implementation("androidx.media3:media3-ui:1.12.0-beta01")
    // OpenGL video effects used by the optional Media3 picture presets.
    implementation("androidx.media3:media3-effect:1.12.0-beta01")
    // Compile-only legacy VLC API bridge: never included in runtime APK.
    // Remove after extracting legacy player classes from MainActivity.
    // Remove this compile-only bridge after deleting legacy player classes.
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
        println("Verified MPV native runtime without LibVLC or nextlib")
    }
}
tasks.named("check") { dependsOn(verifyMpvRuntime) }

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
 * Static integration contract checked before any release candidate is built.
 * Native GStreamer SDK packaging and actual playback still require CI/device checks.
 */
val verifyGstreamerMainActivityIntegration by tasks.registering {
    group = "verification"
    description = "Check opt-in routing, MPV fallback and teardown wiring."
    doLast {
        val activity = file("src/main/java/com/softppj/nextv/MainActivity.kt").readText()
        val adapter = file("src/main/java/com/softppj/nextv/GstreamerPlaybackView.kt").readText()
        listOf(
            "private var gstreamerPlayer:GstreamerPlaybackView?=null",
            "gstreamer_experimental",
            "startGstreamer(c,index,screen)",
            "startMpv(c,index,screen)",
            "gstreamerPlayer?.release()",
            "generation==playbackGeneration"
        ).forEach { invariant ->
            check(activity.contains(invariant)) { "GStreamer MainActivity integration missing: $invariant" }
        }
        listOf("nativeStop(handle)", "nativeRelease(handle)", "playbackGeneration++").forEach { invariant ->
            check(adapter.contains(invariant)) { "GStreamer adapter lifecycle missing: $invariant" }
        }
        println("Verified experimental routing, MPV fallback and native teardown wiring")
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
            // Never silently package native libraries left by a previous SDK build.
            delete(layout.buildDirectory.dir("gstreamer-jniLibs"))
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
        check(source.get().listFiles()?.any { it.name == "libgstreamer-1.0.so" } == true) {
            "Supplied GStreamer SDK does not contain arm64 libgstreamer-1.0.so"
        }
    }
}
tasks.named("preBuild") { dependsOn(stageGstreamerRuntime) }
