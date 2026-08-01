package com.shobdo.keyboard.voice.capture

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a standard RIFF/WAVE byte array from raw little-endian PCM samples,
 * entirely in memory. The result is never written to disk — this honours the
 * project's hard privacy rule ("never persist audio anywhere").
 *
 * Pure logic with no Android dependencies, so it is fully unit-testable
 * without Robolectric.
 */
object WavWriter {

    /** Build a WAV container around the given 16-bit signed PCM samples. */
    fun toWav(
        pcm: ByteArray,
        sampleRate: Int = ShobdoAudio.SAMPLE_RATE,
        channels: Int = ShobdoAudio.CHANNELS,
        bitsPerSample: Int = ShobdoAudio.BITS_PER_SAMPLE,
    ): ByteArray {
        require(pcm.size % (channels * bitsPerSample / 8) == 0) {
            "pcm length ${pcm.size} is not a whole number of ${bitsPerSample}-bit frames"
        }
        val audioFormatPcm = 1
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val out = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        out.put(ASCII("RIFF"))
        out.putInt(36 + dataSize)          // chunkSize = 36 + data
        out.put(ASCII("WAVE"))
        out.put(ASCII("fmt "))
        out.putInt(16)                    // subchunk1 size for PCM
        out.putShort(audioFormatPcm.toShort())
        out.putShort(channels.toShort())
        out.putInt(sampleRate)
        out.putInt(byteRate)
        out.putShort(blockAlign.toShort())
        out.putShort(bitsPerSample.toShort())
        out.put(ASCII("data"))
        out.putInt(dataSize)
        out.put(pcm)
        return out.array()
    }

    /** Duration in milliseconds of the given PCM byte buffer. */
    fun durationMs(
        pcmBytes: Int,
        sampleRate: Int = ShobdoAudio.SAMPLE_RATE,
        channels: Int = ShobdoAudio.CHANNELS,
        bitsPerSample: Int = ShobdoAudio.BITS_PER_SAMPLE,
    ): Long {
        val bytesPerFrame = channels * bitsPerSample / 8
        if (bytesPerFrame == 0) return 0L
        return pcmBytes.toLong() * 1000L / (sampleRate * bytesPerFrame)
    }

    const val HEADER_SIZE = 44

    private fun ASCII(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)
}
