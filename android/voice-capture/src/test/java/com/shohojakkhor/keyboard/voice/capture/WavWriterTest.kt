package com.shohojakkhor.keyboard.voice.capture

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class WavWriterTest {

    @Test
    fun `wav header has correct RIFF and WAVE markers`() {
        val wav = WavWriter.toWav(ByteArray(0))
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
    }

    @Test
    fun `wav header declares fmt chunk and PCM format`() {
        val wav = WavWriter.toWav(ByteArray(0))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals(1, readLeShort(wav, 20))      // PCM
    }

    @Test
    fun `wav header encodes default 16 kHz mono 16-bit`() {
        val wav = WavWriter.toWav(ByteArray(0))
        assertEquals(16_000, readLeInt(wav, 24))   // sample rate
        assertEquals(1, readLeShort(wav, 22))      // channels
        assertEquals(16, readLeShort(wav, 34))     // bits per sample
        assertEquals(32_000, readLeInt(wav, 28))   // byte rate = 16000 * 1 * 2
        assertEquals(2, readLeShort(wav, 32))      // block align = 1 * 16/8
    }

    @Test
    fun `riff chunk size is 36 plus data size`() {
        val pcm = ByteArray(1_000) // arbitrary
        val wav = WavWriter.toWav(pcm)
        assertEquals(36 + 1_000, readLeInt(wav, 4))
    }

    @Test
    fun `data chunk marker and size precede the pcm bytes`() {
        val pcm = ByteArray(124)
        val wav = WavWriter.toWav(pcm)
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(124, readLeInt(wav, 40))
        // Body follows immediately after the 44-byte header.
        assertEquals(44 + 124, wav.size)
        for (i in pcm.indices) {
            assertEquals(pcm[i], wav[44 + i])
        }
    }

    @Test
    fun `stereo 44 kHz header is encoded when requested`() {
        val wav = WavWriter.toWav(ByteArray(0), sampleRate = 44_100, channels = 2)
        assertEquals(44_100, readLeInt(wav, 24))
        assertEquals(2, readLeShort(wav, 22))
        assertEquals(176_400, readLeInt(wav, 28))  // 44100 * 2 * 16/8
        assertEquals(4, readLeShort(wav, 32))      // block align = 2 * 16/8
    }

    @Test
    fun `rejects pcm length that is not a whole frame`() {
        var threw = false
        try {
            WavWriter.toWav(ByteArray(3)) // 3 bytes, frame = 2 bytes
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `durationMs computes correctly for known byte counts`() {
        // 1 second of 16kHz mono 16-bit = 32000 bytes
        assertEquals(1000L, WavWriter.durationMs(32_000))
        assertEquals(500L, WavWriter.durationMs(16_000))
        assertEquals(0L, WavWriter.durationMs(0))
    }

    private fun readLeShort(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

    private fun readLeInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)
}
