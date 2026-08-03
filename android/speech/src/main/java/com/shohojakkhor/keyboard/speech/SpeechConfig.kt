package com.shohojakkhor.keyboard.speech

/**
 * Where and how to reach the Shohojakkhor backend.
 *
 * Default base URL is `http://127.0.0.1:8000`, which works on a **physical
 * device** once you run `adb reverse tcp:8000 tcp:8000` (the device's
 * localhost then maps to the dev machine's localhost). For the Android
 * emulator, switch to `http://10.0.2.2:8000`. In production, set the deployed
 * backend URL.
 */
data class SpeechConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val connectTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 30_000L,
    val writeTimeoutMs: Long = 10_000L,
    val language: String = "bn",
) {
    companion object {
        /** Physical device with `adb reverse tcp:8000 tcp:8000`. */
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8000"

        /** Android emulator — the host machine is reachable as 10.0.2.2. */
        const val EMULATOR_BASE_URL = "http://10.0.2.2:8000"
    }
}
