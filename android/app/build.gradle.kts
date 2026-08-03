plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val signingKeystorePath = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
val signingKeyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
val signingStorePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
val hasReleaseSigning = listOf(
    signingKeystorePath,
    signingKeyAlias,
    signingKeyPassword,
    signingStorePassword,
).all { !it.isNullOrBlank() }
val releaseBuildRequested = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}

require(!releaseBuildRequested || hasReleaseSigning) {
    "Release signing is required. Set SIGNING_KEYSTORE_PATH, SIGNING_KEY_ALIAS, " +
        "SIGNING_KEY_PASSWORD, and SIGNING_STORE_PASSWORD."
}

android {
    namespace = "com.shohojakkhor.keyboard"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.shohojakkhor.keyboard"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.1.0-M1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(signingKeystorePath))
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    splits {
        abi {
            // Split the APK per ABI so each build is ~half the universal size.
            // The sherpa-onnx native libs (ONNX Runtime + JNI) are the bulk of
            // the APK; arm64-v8a is what modern phones (incl. the test Samsung)
            // use, armeabi-v7a covers older devices. x86/x86_64 are emulators
            // we don't target. Play Store serves the right split automatically.
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // sherpa-onnx reads model files via mmap from the APK; they must be
    // stored uncompressed or load fails / is very slow.
    androidResources {
        noCompress += listOf("onnx", "txt")
    }
}

dependencies {
    implementation(project(":keyboard-ime"))
    implementation(project(":transliteration"))
    implementation(project(":voice-capture"))
    implementation(project(":speech"))
    implementation(project(":speech-ondevice"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
