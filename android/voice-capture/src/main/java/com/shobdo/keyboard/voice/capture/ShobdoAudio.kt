package com.shobdo.keyboard.voice.capture

/**
 * Audio constants for Shobdo voice capture.
 *
 * 16 kHz / mono / 16-bit PCM is the sweet spot for Whisper-class STT: small
 * enough to upload over a slow connection (~32 KB/s), high enough quality for
 * Bengali recognition. All constants in one place so the recorder, WAV writer,
 * and the backend stay in sync.
 */
object ShobdoAudio {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    /** Hard cap for a single recording. Elderly users get plenty of time to
     *  finish a sentence; long silences don't bleed into the upload. */
    const val MAX_DURATION_MS = 30_000L

    /** Bytes per second of 16-bit mono PCM at [SAMPLE_RATE]. */
    const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)
}
