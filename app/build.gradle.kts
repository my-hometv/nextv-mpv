plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.softppj.nextv"
    compileSdk = 37
    buildFeatures { buildConfig = true; aidl = true }
    // Small real NDK/JNI runtime probe. It is not a relocated LibVLC.
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    defaultConfig {
        // Beta 4 application ID: installs independently beside the stable NexTV app.
        applicationId = "com.softppj.nextv.mpv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-mpv-experiment"
        // Exactly one experimental LibVLC version is packaged in this APK.
        // Other native versions require separate JNI isolation.
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
    // Use the updated LibVLC engine in the independent Beta application.
    // GPLv3 decoder; retain only with appropriate distribution/licensing compliance.
    // Compile legacy unreachable code without packaging LibVLC native binaries.
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
