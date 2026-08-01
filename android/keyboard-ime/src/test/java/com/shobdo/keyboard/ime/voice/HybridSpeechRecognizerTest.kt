package com.shobdo.keyboard.ime.voice

import com.shobdo.keyboard.speech.SpeechError
import com.shobdo.keyboard.speech.SpeechRepository
import com.shobdo.keyboard.speech.SpeechResult
import com.shobdo.keyboard.speech.TranscriptionResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [HybridSpeechRecognizer] with manual fakes (matching the
 * project's no-mock test style). Covers: remote success, every remote-error
 * → fallback path, blank-remote → fallback, remote throw → fallback, the
 * onFallback callback contract, and argument forwarding.
 */
class HybridSpeechRecognizerTest {

    /** A fake remote that always returns [result]. */
    private fun remote(result: SpeechResult): SpeechRepository =
        object : SpeechRepository {
            override fun transcribe(wavBytes: ByteArray, language: String): SpeechResult = result
        }

    /** A fake remote that records the language it was called with. */
    private class RecordingRemote(private val result: SpeechResult) : SpeechRepository {
        var receivedLanguage: String? = null
        var receivedWav: ByteArray? = null
        var callCount = 0
        override fun transcribe(wavBytes: ByteArray, language: String): SpeechResult {
            receivedLanguage = language
            receivedWav = wavBytes
            callCount++
            return result
        }
    }

    /** A fake remote that always throws. */
    private fun throwingRemote(exc: Throwable): SpeechRepository =
        object : SpeechRepository {
            override fun transcribe(wavBytes: ByteArray, language: String): SpeechResult = throw exc
        }

    @Test
    fun `remote success returns Online and does not touch on-device`() {
        var onDeviceCalls = 0
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Success(TranscriptionResult("আজকের আবহাওয়া খুব ভালো", "bn", 1500))),
            onDeviceTranscribe = { _, _ -> onDeviceCalls++; "" },
        )

        val result = hybrid.transcribe(ByteArray(10), 16_000, "")

        assertTrue(result is HybridResult.Online)
        assertEquals("আজকের আবহাওয়া খুব ভালো", result.text)
        assertEquals(0, onDeviceCalls, "on-device must not run when remote succeeds")
    }

    @Test
    fun `remote success trims surrounding whitespace`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Success(TranscriptionResult("  hi  ", "en", 1))),
            onDeviceTranscribe = { _, _ -> "" },
        )
        val result = hybrid.transcribe(ByteArray(0), 16_000, "")
        assertTrue(result is HybridResult.Online)
        assertEquals("hi", result.text)
    }

    @Test
    fun `remote success with blank text falls back to on-device`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Success(TranscriptionResult("   ", "bn", 1))),
            onDeviceTranscribe = { _, _ -> "অফলাইন টেক্সট" },
        )

        val result = hybrid.transcribe(ByteArray(10), 16_000, "")

        assertTrue(result is HybridResult.OfflineFallback)
        assertEquals("অফলাইন টেক্সট", result.text)
    }

    @Test
    fun `remote NoNetwork falls back to on-device`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.NoNetwork)),
            onDeviceTranscribe = { _, _ -> "fallback" },
        )
        assertTrue(hybrid.transcribe(ByteArray(0), 16_000, "") is HybridResult.OfflineFallback)
    }

    @Test
    fun `remote Timeout falls back to on-device`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.Timeout)),
            onDeviceTranscribe = { _, _ -> "fallback" },
        )
        assertTrue(hybrid.transcribe(ByteArray(0), 16_000, "") is HybridResult.OfflineFallback)
    }

    @Test
    fun `remote Provider error falls back to on-device`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.Provider("PROVIDER_ERROR"))),
            onDeviceTranscribe = { _, _ -> "fallback" },
        )
        assertTrue(hybrid.transcribe(ByteArray(0), 16_000, "") is HybridResult.OfflineFallback)
    }

    @Test
    fun `remote RateLimit falls back to on-device`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.RateLimit)),
            onDeviceTranscribe = { _, _ -> "fallback" },
        )
        assertTrue(hybrid.transcribe(ByteArray(0), 16_000, "") is HybridResult.OfflineFallback)
    }

    @Test
    fun `remote Unknown error falls back to on-device`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.Unknown(null))),
            onDeviceTranscribe = { _, _ -> "fallback" },
        )
        assertTrue(hybrid.transcribe(ByteArray(0), 16_000, "") is HybridResult.OfflineFallback)
    }

    @Test
    fun `remote throws falls back to on-device`() {
        val hybrid = HybridSpeechRecognizer(
            remote = throwingRemote(RuntimeException("unexpected boom")),
            onDeviceTranscribe = { _, _ -> "fallback" },
        )
        assertTrue(hybrid.transcribe(ByteArray(0), 16_000, "") is HybridResult.OfflineFallback)
    }

    @Test
    fun `remote fails and on-device blank returns Failed`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.NoNetwork)),
            onDeviceTranscribe = { _, _ -> "   " },
        )
        assertTrue(hybrid.transcribe(ByteArray(0), 16_000, "") is HybridResult.Failed)
    }

    @Test
    fun `remote fails and on-device throws returns Failed`() {
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.NoNetwork)),
            onDeviceTranscribe = { _, _ -> throw RuntimeException("on-device exploded") },
        )
        assertTrue(hybrid.transcribe(ByteArray(0), 16_000, "") is HybridResult.Failed)
    }

    @Test
    fun `onFallback fires when remote fails`() {
        var fired = false
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.NoNetwork)),
            onDeviceTranscribe = { _, _ -> "x" },
        )
        hybrid.transcribe(ByteArray(0), 16_000, "", onFallback = { fired = true })
        assertTrue(fired, "onFallback must fire when the remote path fails")
    }

    @Test
    fun `onFallback fires when remote returns blank text`() {
        var fired = false
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Success(TranscriptionResult("", "bn", 1))),
            onDeviceTranscribe = { _, _ -> "x" },
        )
        hybrid.transcribe(ByteArray(0), 16_000, "", onFallback = { fired = true })
        assertTrue(fired, "onFallback must fire when the remote returns blank text")
    }

    @Test
    fun `onFallback does NOT fire when remote succeeds`() {
        var fired = false
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Success(TranscriptionResult("hi", "en", 1))),
            onDeviceTranscribe = { _, _ -> "x" },
        )
        hybrid.transcribe(ByteArray(0), 16_000, "", onFallback = { fired = true })
        assertEquals(false, fired, "onFallback must not fire on a successful online result")
    }

    @Test
    fun `onFallback fires before on-device runs`() {
        val order = mutableListOf<String>()
        val hybrid = HybridSpeechRecognizer(
            remote = remote(SpeechResult.Error(SpeechError.NoNetwork)),
            onDeviceTranscribe = { _, _ -> order.add("on-device"); "x" },
        )
        hybrid.transcribe(ByteArray(0), 16_000, "") { order.add("fallback") }
        assertEquals(listOf("fallback", "on-device"), order)
    }

    @Test
    fun `forwards the language to the remote path`() {
        val rec = RecordingRemote(SpeechResult.Success(TranscriptionResult("x", "bn", 1)))
        val hybrid = HybridSpeechRecognizer(rec, { _, _ -> "" })
        hybrid.transcribe(ByteArray(0), 16_000, "")
        assertEquals("", rec.receivedLanguage, "empty language (auto-detect) must be forwarded as-is")
    }

    @Test
    fun `forwards a non-empty language to the remote path`() {
        val rec = RecordingRemote(SpeechResult.Success(TranscriptionResult("x", "bn", 1)))
        val hybrid = HybridSpeechRecognizer(rec, { _, _ -> "" })
        hybrid.transcribe(ByteArray(0), 16_000, "bn")
        assertEquals("bn", rec.receivedLanguage)
    }

    @Test
    fun `forwards wavBytes and sampleRate to the on-device path`() {
        val rec = RecordingRemote(SpeechResult.Error(SpeechError.NoNetwork))
        var gotWav: ByteArray? = null
        var gotRate: Int? = null
        val hybrid = HybridSpeechRecognizer(rec) { wav, rate -> gotWav = wav; gotRate = rate; "x" }
        val sent = byteArrayOf(1, 2, 3, 4)
        hybrid.transcribe(sent, 16_000, "")
        assertEquals(sent.toList(), gotWav?.toList())
        assertEquals(16_000, gotRate)
    }

    @Test
    fun `remote is called exactly once per transcription`() {
        val rec = RecordingRemote(SpeechResult.Error(SpeechError.NoNetwork))
        val hybrid = HybridSpeechRecognizer(rec, { _, _ -> "x" })
        hybrid.transcribe(ByteArray(0), 16_000, "")
        assertEquals(1, rec.callCount, "remote must be called exactly once")
    }
}
