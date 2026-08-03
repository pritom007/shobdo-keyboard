package com.shohojakkhor.keyboard.speech

/**
 * Where and how to reach the Shohojakkhor backend.
 *
 * The default is build-specific: debug builds use localhost for `adb reverse`,
 * while release builds use the HTTPS Render service. Tests and emulator tools
 * can still pass an explicit URL.
 */
data class SpeechConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val connectTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 30_000L,
    val writeTimeoutMs: Long = 10_000L,
    val language: String = "bn",
) {
    companion object {
        val DEFAULT_BASE_URL: String = BuildConfig.BACKEND_BASE_URL

        const val PRODUCTION_BASE_URL = "https://shobdo-keyboard-backend.onrender.com"

        /** Android emulator — the host machine is reachable as 10.0.2.2. */
        const val EMULATOR_BASE_URL = "http://10.0.2.2:8000"
    }
}
