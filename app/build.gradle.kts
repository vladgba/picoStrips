plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.vladgba.picostrips"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.vladgba.picostrips"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed so the release APK is installable out of the box; replace with your own key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    packaging { resources.excludes += listOf("META-INF/**", "kotlin/**", "DebugProbesKt.bin") }
    lint { abortOnError = false }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // Scanning: native zxing-cpp. It declares CameraX core only for its ImageProxy overload,
    // which we never call, so it is excluded to keep the APK small.
    implementation("io.github.zxing-cpp:android:3.1.1") {
        exclude(group = "androidx.camera")
    }
    // Generating: the zxing-cpp Android wrapper has no writer, so ZXing's Java core draws codes.
    // R8 strips its (unused) reader classes.
    implementation("com.google.zxing:core:3.5.4")
}
