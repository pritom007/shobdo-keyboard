package com.shobdo.keyboard.speech.ondevice

import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmToFloatConverterTest {

    @Test
    fun `empty and single-byte input yield empty float array`() {
        assertEquals(0, PcmToFloatConverter.toFloats(ByteArray(0)).size)
        assertEquals(0, PcmToFloatConverter.toFloats(ByteArray(1)).size)
    }

    @Test
    fun `silence all zeros maps to zero floats`() {
        val pcm = ByteArray(8) // 4 samples of silence
        val out = PcmToFloatConverter.toFloats(pcm)
        assertEquals(4, out.size)
        for (v in out) assertEquals(0.0f, v)
    }

    @Test
    fun `max positive sample maps to just under one`() {
        // 0x7FFF = 32767 -> 32767 / 32768 ≈ 0.99997
        val pcm = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(32767).array()
        val out = PcmToFloatConverter.toFloats(pcm)
        assertEquals(1, out.size)
        assertTrue(out[0] > 0.9999f && out[0] < 1.0f, "got ${out[0]}")
    }

    @Test
    fun `max negative sample maps to minus one`() {
        // 0x8000 = -32768 -> -32768 / 32768 = -1.0
        val pcm = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(-32768).array()
        val out = PcmToFloatConverter.toFloats(pcm)
        assertEquals(1, out.size)
        assertEquals(-1.0f, out[0])
    }

    @Test
    fun `mid-range sample maps correctly`() {
        // 16384 -> 16384 / 32768 = 0.5
        val pcm = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(16384).array()
        val out = PcmToFloatConverter.toFloats(pcm)
        assertEquals(0.5f, out[0])
    }

    @Test
    fun `little-endian byte order is respected`() {
        // Bytes 0x01 0x00 -> 0x0001 = 1 -> 1/32768
        val pcm = byteArrayOf(0x01, 0x00)
        val out = PcmToFloatConverter.toFloats(pcm)
        assertEquals(1, out.size)
        assertEquals(1.0f / 32768.0f, out[0])
    }

    @Test
    fun `odd byte count drops the trailing half-sample`() {
        // 5 bytes -> 2 samples, last byte ignored
        val pcm = byteArrayOf(0, 0, 0, 0, 0x7F)
        val out = PcmToFloatConverter.toFloats(pcm)
        assertEquals(2, out.size)
    }

    @Test
    fun `sample count matches half the byte count`() {
        val pcm = ByteArray(2000)
        val out = PcmToFloatConverter.toFloats(pcm)
        assertEquals(1000, out.size)
    }

    @Test
    fun `all floats stay in range minus one to one`() {
        // Pseudo-random bytes including high-amplitude samples.
        val pcm = ByteArray(2048)
        for (i in pcm.indices) pcm[i] = ((i * 37) and 0xFF).toByte()
        val out = PcmToFloatConverter.toFloats(pcm)
        for (v in out) {
            assertTrue(v in -1.0f..1.0f, "out of range: $v")
        }
    }
}
