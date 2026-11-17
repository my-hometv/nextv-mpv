plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.softppj.nextv"
    compileSdk = 36
    buildFeatures { buildConfig = true; aidl = true }
    // Small real NDK/JNI runtime probe. It is not a relocated LibVLC.
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    defaultConfig {
        // MyView is a private full-featured fork, installed independently from NexTV.
        applicationId = "com.softppj.myview"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2-myview-live-tv-test"
        // Exactly one LibVLC baseline is packaged in this APK.
        // Other native versions require separate JNI isolation.
        buildConfigField("String", "LIBVLC_VERSION", "\"3.5.1\"")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.11.1")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    // OpenGL video effects used by the optional Media3 picture presets.
    implementation("androidx.media3:media3-effect:1.11.1")
    // Restore the V6 LibVLC baseline for controlled Android TV playback regression testing.
    // GPLv3 decoder; retain only with appropriate distribution/licensing compliance.
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.18.0")
    implementation("org.videolan.android:libvlc-all:3.5.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}


// Guard against accidentally bundling multiple LibVLC versions while testing
// the single-engine baseline. Run: ./gradlew :app:verifySingleLibVlc
val verifySingleLibVlc by tasks.registering {
    group = "verification"
    description = "Checks that only the intended LibVLC baseline is declared."
    doLast {
        val declared = configurations.getByName("implementation").dependencies
            .filter { it.group == "org.videolan.android" && it.name.startsWith("libvlc") }
        check(declared.size == 1 && declared.single().name == "libvlc-all" &&
            declared.single().version == "3.5.1") {
            "Expected exactly one org.videolan.android:libvlc-all:3.5.1 dependency; found: $declared"
        }
        // Both APK variants must resolve the same native LibVLC baseline.
        // Checking the resolved graph catches transitive version substitutions.
        listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").forEach { variant ->
            val resolved = configurations.getByName(variant)
                .incoming.resolutionResult.allComponents
                .mapNotNull { it.moduleVersion }
                .filter { it.group == "org.videolan.android" && it.name.startsWith("libvlc") }
                .distinctBy { Triple(it.group, it.name, it.version) }
            check(resolved.size == 1 && resolved.single().name == "libvlc-all" &&
                resolved.single().version == "3.5.1") {
                "Unexpected LibVLC modules in $variant: $resolved"
            }
            println("$variant: verified LibVLC 3.5.1")
        }

    }
}

// Run the native-dependency guard before the standard verification lifecycle.
tasks.named("check") {
    dependsOn(verifySingleLibVlc)
}
