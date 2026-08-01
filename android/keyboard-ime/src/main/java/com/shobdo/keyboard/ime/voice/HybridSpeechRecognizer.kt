package com.shobdo.keyboard.ime.voice

import com.shobdo.keyboard.speech.SpeechRepository
import com.shobdo.keyboard.speech.SpeechResult

/**
 * Online-first, offline-fallback transcription orchestrator.
 *
 * - **Primary path (online):** the Shobdo backend → Groq Whisper large-v3 via
 *   [remote] (a [SpeechRepository]). Best Bengali quality and honours
 *   auto-detect — speak Bengali → Bengali script, English → English. Needs
 *   internet. Pass `language = ""` for auto-detect.
 * - **Fallback path (offline):** on-device Whisper base via [onDeviceTranscribe].
 *   Used whenever the remote path is unreachable (no network, timeout, or any
 *   server error) or returns blank text. Less accurate (and Bengali-only —
 *   the on-device model is pinned to `bn`) but always available offline.
 *
 * The fall-back decision is deliberately simple: **every** remote
 * [SpeechResult.Error] triggers the fallback, and so does a blank remote
 * transcript. We never punish the user for a server hiccup or a dead
 * network — they just want their text, and the on-device model is always
 * there as a safety net. The product rule is "friendly fallback, never an
 * error" for the offline case.
 *
 * [onFallback] is invoked on the calling thread **after** the remote fails
 * and **before** the on-device recognizer runs, so the UI can swap its
 * "লিখছি…" message for "ইন্টারনেট নেই, অফলাইনে লিখছি…" while the slower
 * on-device pass happens. The on-device model cold-loads in ~3–4 s on first
 * use, so signalling the user that offline mode is in progress matters.
 *
 * This class is blocking; callers must dispatch off the main thread. It is
 * safe for sequential use (the IME runs a single dedicated background thread
 * per transcription).
 */
class HybridSpeechRecognizer(
    private val remote: SpeechRepository,
    private val onDeviceTranscribe: (wavBytes: ByteArray, sampleRate: Int) -> String,
) {

    fun transcribe(
        wavBytes: ByteArray,
        sampleRate: Int,
        language: String = "",
        onFallback: () -> Unit = {},
    ): HybridResult {
        // --- Primary: try the server (Groq large-v3) ----------------------
        // RemoteSpeechRepository catches IOException/SocketTimeoutException
        // and maps them to SpeechResult.Error, so this normally does not
        // throw. The try/catch is defensive against anything unexpected.
        val remoteResult = try {
            remote.transcribe(wavBytes, language)
        } catch (_: Exception) {
            null
        }

        if (remoteResult is SpeechResult.Success && remoteResult.data.text.isNotBlank()) {
            return HybridResult.Online(remoteResult.data.text.trim())
        }

        // --- Fallback: on-device Whisper base (offline) --------------------
        onFallback()
        val offlineText = try {
            onDeviceTranscribe(wavBytes, sampleRate)
        } catch (_: Exception) {
            ""
        }.trim()

        return if (offlineText.isNotBlank()) {
            HybridResult.OfflineFallback(offlineText)
        } else {
            HybridResult.Failed
        }
    }
}

/** Outcome of a hybrid (online-first, offline-fallback) transcription. */
sealed class HybridResult {
    /** Server returned a transcript. Best quality; was online. */
    data class Online(val text: String) : HybridResult()

    /** Server failed; on-device recognizer produced a (degraded) transcript. */
    data class OfflineFallback(val text: String) : HybridResult()

    /** Both paths failed — show the error state. */
    object Failed : HybridResult()
}
