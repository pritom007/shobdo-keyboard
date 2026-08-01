package com.shobdo.keyboard.speech

/** Outcome of a transcription call. */
sealed class SpeechResult {
    data class Success(val data: TranscriptionResult) : SpeechResult()
    data class Error(val error: SpeechError) : SpeechResult()
}
