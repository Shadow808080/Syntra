plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.syntra"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.syntra"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The UI is Indonesian with an English fallback. Every AndroidX/Compose
        // artifact ships ~80 translations we never show; keeping two drops the rest
        // of them out of resources.arsc.
        androidResources {
            localeFilters += listOf("in", "en")
        }
    }

    buildTypes {
        release {
            // R8: shrink + optimize + obfuscate the release build. The biggest
            // single runtime win — smaller & faster code, less GC pressure — and
            // resource shrinking trims the APK. Keep-rules live in proguard-rules.pro
            // (the app parses JSON by hand, so only native/JNI libs need keeping).
            // MUST be smoke-tested on a device release build before shipping.
            optimization {
                enable = true
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    // A device only ever needs ONE ABI, but the old build shipped all four in one
    // "universal" APK — so an arm64 phone downloaded the x86, x86_64 AND armeabi-v7a
    // copies of LiveKit's WebRTC blob and opened none of them. That alone was 30 MB
    // of the 46. One APK per ABI instead; Play does the same automatically for a
    // .aab, this is the equivalent for APKs handed out directly.
    //
    // 32-bit x86 is dropped outright (nothing but very old emulators); x86_64 stays
    // only so the development emulator keeps working — no phone ever downloads it.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            // Build metadata that ships inside dependency jars and is dead weight in
            // an APK — protobuf schemas pulled in by WebRTC, licence texts, Kotlin
            // module descriptors, and the coroutines debug hook.
            //
            // NOT excluded: `kotlin/**`. It holds kotlin_builtins, which anything
            // using kotlin-reflect reads at runtime — worth ~10 KB and a release-only
            // crash if some dependency turns out to need it.
            excludes += listOf(
                "**/*.proto",
                "META-INF/*.version",
                "META-INF/**/LICENSE.txt",
                "META-INF/**/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
        // The .so files are already compressed inside the APK; leaving them
        // uncompressed costs download size but saves disk after install. Compressed
        // is the right trade for a 40 MB native blob on a cheap phone.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Off by default in AGP 9, but spelled out: no BuildConfig class, no unused
        // resource classes for libraries we don't read them from.
        buildConfig = false
        resValues = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.gif)
    implementation(libs.okhttp)
    implementation(libs.livekit.android)
    // zxing.core still used to GENERATE the user's own QR image (settings). The ML Kit
    // barcode SCANNER was removed with the scan feature.
    implementation(libs.zxing.core)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    // Installs the bundled baseline profile (src/main/baseline-prof.txt) at first
    // run on devices/installers that don't do it automatically, so the hot app
    // paths are AOT-compiled from the start — smoother scrolling & faster launch.
    implementation(libs.androidx.profileinstaller)
    // Media3 ExoPlayer for the reels player: plays a video WHILE writing it to a
    // shared on-disk cache (CacheDataSource), so a clip is downloaded exactly once
    // — no separate stream+prefetch double-fetch — and replays come from disk.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}