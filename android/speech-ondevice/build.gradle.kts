plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.shobdo.keyboard.speech.ondevice"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()

        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Keep the APK lean: only the two ABIs real Android devices use.
        // Emulators are not a target (we test on the physical Samsung device).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // The sherpa-onnx prebuilt AAR (bundled ONNX Runtime + JNI for all ABIs).
    // Hosted in android/local-repo/ as a local Maven artifact. Bump by
    // replacing the file in local-repo/ and the version below.
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx:1.13.4@aar")

    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
