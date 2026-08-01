package com.shobdo.keyboard.voice.capture

/**
 * A source of PCM audio samples. The real implementation wraps
 * [android.media.AudioRecord]; tests supply fakes that emit canned bytes.
 *
 * Decoupling the recorder from `AudioRecord` keeps the recording loop fully
 * unit-testable (no Robolectric, no device).
 */
interface AudioSource {
    val sampleRate: Int
    val channels: Int

    /**
     * Read up to [length] bytes of 16-bit PCM into [target] starting at
     * [offset]. Returns the number of bytes read, 0 if nothing was available
     * yet, or a negative value on end-of-stream / error.
     */
    fun read(target: ByteArray, offset: Int, length: Int): Int

    /** Stop and release the underlying hardware. Safe to call once. */
    fun close()
}
