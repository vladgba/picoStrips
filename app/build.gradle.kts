plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Shared versioning (version.properties) + release signing, see gradle/android-common.gradle
apply(from = rootProject.file("gradle/android-common.gradle"))

android {
    namespace = "x.vladgba.strips"
    compileSdk = 36

    defaultConfig {
        applicationId = "x.vladgba.strips"
        minSdk = 24
        targetSdk = 36
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
