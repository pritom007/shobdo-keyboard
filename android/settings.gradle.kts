pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local Maven repo hosting the sherpa-onnx AAR (prebuilt, bundled ONNX
        // Runtime). Lives in android/local-repo/ so it ships with the source.
        maven { url = uri("./local-repo") }
    }
}

rootProject.name = "shobdo-keyboard"

include(":app")
include(":keyboard-ime")
include(":transliteration")
include(":voice-capture")
include(":speech")
include(":speech-ondevice")
