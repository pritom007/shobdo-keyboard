package com.shobdo.keyboard.speech

/** A successful transcription returned by the backend. */
data class TranscriptionResult(
    val text: String,
    val language: String,
    val durationMs: Long,
)
