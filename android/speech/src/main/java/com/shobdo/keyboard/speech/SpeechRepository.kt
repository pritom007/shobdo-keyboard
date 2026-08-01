package com.shobdo.keyboard.speech

/**
 * Sends a WAV recording to the backend and returns the transcribed text.
 *
 * The implementation is intentionally blocking (synchronous) so the speech
 * module stays free of a coroutines dependency. The caller (the ViewModel)
 * is responsible for dispatching the call off the main thread, e.g.
 * `withContext(Dispatchers.IO) { repo.transcribe(wav, "bn") }`.
 *
 * Audio bytes and the returned transcript are NEVER logged anywhere.
 */
interface SpeechRepository {
    fun transcribe(wavBytes: ByteArray, language: String = "bn"): SpeechResult
}
