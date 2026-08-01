package com.shobdo.keyboard.voice.capture

import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration-style tests for [AudioRecorder]. Uses a real (wall) clock and a
 * fake source that sleeps ~10ms per read producing 10ms of audio — i.e.
 * real-time — so a [CountDownLatch] with a generous timeout makes the
 * threaded behaviour deterministic without flakiness.
 */
class AudioRecorderTest {

    private class RealtimeFakeSource(
        override val sampleRate: Int = 16_000,
        override val channels: Int = 1,
        private val msPerRead: Long = 10L,
    ) : AudioSource {
        var reads = 0
        var closed = false
        private val bytesPerRead: Int = (ShobdoAudio.BYTES_PER_SECOND * msPerRead / 1000L).toInt()

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            reads++
            try {
                Thread.sleep(msPerRead)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return minOf(bytesPerRead, length)
        }

        override fun close() { closed = true }
    }

    @Test
    fun `start then stop completes with a valid wav recording`() {
        val source = RealtimeFakeSource()
        val recorder = AudioRecorder(sourceProvider = { source }, maxDurationMs = 5_000)
        val latch = CountDownLatch(1)
        var result: Recording? = null

        recorder.setListener(simpleListener(onComplete = { result = it; latch.countDown() }))

        recorder.start()
        Thread.sleep(120)
        recorder.stop()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val r = result
        assertNotNull(r)
        assertTrue(r.wavBytes.size > WavWriter.HEADER_SIZE)
        assertEquals("RIFF", String(r.wavBytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(r.wavBytes, 8, 4, Charsets.US_ASCII))
        assertTrue(r.durationMs >= 0L)
        assertEquals(16_000, r.sampleRate)
        assertEquals(1, r.channels)
        assertTrue(source.closed)
        assertEquals(RecorderState.IDLE, recorder.getState())
    }

    @Test
    fun `cancel discards the in-flight recording`() {
        val source = RealtimeFakeSource()
        val recorder = AudioRecorder(sourceProvider = { source }, maxDurationMs = 5_000)
        val latch = CountDownLatch(1)
        var cancelled = false

        recorder.setListener(simpleListener(onCancelled = { cancelled = true; latch.countDown() }))

        recorder.start()
        Thread.sleep(60)
        recorder.cancel()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(cancelled)
        assertTrue(source.closed)
        assertEquals(RecorderState.IDLE, recorder.getState())
    }

    @Test
    fun `max duration auto-completes even if user never stops`() {
        val source = RealtimeFakeSource(msPerRead = 10L)
        val recorder = AudioRecorder(sourceProvider = { source }, maxDurationMs = 80L)
        val latch = CountDownLatch(1)
        var result: Recording? = null

        recorder.setListener(simpleListener(onComplete = { result = it; latch.countDown() }))

        recorder.start()
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val r = result
        assertNotNull(r)
        // ~80ms cap; allow scheduler jitter.
        assertTrue(r.durationMs in 70..500, "durationMs=${r.durationMs}")
        assertTrue(source.closed)
        assertEquals(RecorderState.IDLE, recorder.getState())
    }

    @Test
    fun `source provider throwing on start reports mic error`() {
        val recorder = AudioRecorder(sourceProvider = { throw RuntimeException("no mic") })
        var errorCode: String? = null
        recorder.setListener(simpleListener(onError = { errorCode = it }))
        recorder.start()
        assertEquals("MIC_FAILED", errorCode)
        assertEquals(RecorderState.ERROR, recorder.getState())
        // reset clears the error so another attempt is possible
        recorder.reset()
        assertEquals(RecorderState.IDLE, recorder.getState())
    }

    @Test
    fun `stop when not recording is a no-op`() {
        val source = RealtimeFakeSource()
        val recorder = AudioRecorder(sourceProvider = { source })
        // Nothing has been started; stopping must not throw or change state.
        recorder.stop()
        assertEquals(RecorderState.IDLE, recorder.getState())
        assertFalse(source.closed)
    }

    private fun simpleListener(
        onComplete: (Recording) -> Unit = {},
        onCancelled: () -> Unit = {},
        onError: (String) -> Unit = {},
    ): RecorderListener = object : RecorderListener {
        override fun onStateChanged(state: RecorderState) {}
        override fun onTick(elapsedMs: Long) {}
        override fun onComplete(recording: Recording) = onComplete(recording)
        override fun onCancelled() = onCancelled()
        override fun onError(code: String) = onError(code)
    }
}
