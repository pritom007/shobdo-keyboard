package com.shohojakkhor.keyboard.speech.ondevice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts 16-bit signed little-endian PCM samples to the normalized
 * [-1.0, 1.0] float array that sherpa-onnx's `OfflineStream.acceptWaveform`
 * expects.
 *
 * Pure logic with no Android or native dependencies, so it is fully
 * unit-testable without the sherpa-onnx native library.
 */
object PcmToFloatConverter {

    /** Convert [pcm] (16-bit signed LE) to a float array of the same sample
     *  count, each sample normalized to roughly [-1.0, 1.0]. */
    fun toFloats(pcm: ByteArray): FloatArray {
        if (pcm.size < 2) return FloatArray(0)
        val sampleCount = pcm.size / 2
        val out = FloatArray(sampleCount)
        val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            // short range is [-32768, 32767]; divide by 32768 to normalize.
            out[i] = buf.short / 32768.0f
        }
        return out
    }
}
