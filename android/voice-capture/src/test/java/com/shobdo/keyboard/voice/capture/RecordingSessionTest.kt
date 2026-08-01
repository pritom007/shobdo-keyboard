package com.shobdo.keyboard.voice.capture

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.shobdo.keyboard.voice.capture.RecordingSession.Outcome

/**
 * Deterministic, thread-free tests for the capture loop. A fake source emits
 * a fixed number of bytes per read and advances a mutable clock by a fixed
 * amount, so the session's elapsed-time and byte-count math is fully
 * predictable.
 */
class RecordingSessionTest {

    private class MutableClock { var now = 0L }

    private class FakeAudioSource(
        private val clock: MutableClock,
        private val advanceMsPerRead: Long = 100L,
        override val sampleRate: Int = 16_000,
        override val channels: Int = 1,
        private val bytesPerRead: Int = 320,
        private val eofAfter: Int = Int.MAX_VALUE,
    ) : AudioSource {
        var reads = 0
        var closed = false
        var throwOn: Exception? = null

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            if (throwOn != null) throw throwOn as Exception
            reads++
            if (reads > eofAfter) return -1
            clock.now += advanceMsPerRead
            return minOf(bytesPerRead, length)
        }

        override fun close() { closed = true }
    }

    @Test
    fun `stop requested after several reads completes with captured bytes`() {
        val clock = MutableClock()
        val source = FakeAudioSource(clock, advanceMsPerRead = 100L, bytesPerRead = 320)
        val session = RecordingSession(source, maxDurationMs = Long.MAX_VALUE, clock = { clock.now })

        var lastTick = -1L
        val outcome = session.run(
            shouldStop = { source.reads >= 5 },
            shouldCancel = { false },
            onTick = { lastTick = it },
        )

        assertTrue(outcome is Outcome.Completed)
        val c = outcome as Outcome.Completed
        assertEquals(5 * 320, c.pcm.size)
        assertEquals(500L, c.durationMs)
        assertEquals(16_000, c.sampleRate)
        assertEquals(1, c.channels)
        assertEquals(500L, lastTick)
        // The session does not own the source lifecycle; the recorder closes it.
        assertFalse(source.closed)
    }

    @Test
    fun `cancel requested discards audio and reports elapsed time`() {
        val clock = MutableClock()
        val source = FakeAudioSource(clock, advanceMsPerRead = 100L, bytesPerRead = 320)
        val session = RecordingSession(source, maxDurationMs = Long.MAX_VALUE, clock = { clock.now })

        val outcome = session.run(
            shouldStop = { false },
            shouldCancel = { source.reads >= 3 },
            onTick = {},
        )

        assertTrue(outcome is Outcome.Cancelled)
        assertEquals(300L, (outcome as Outcome.Cancelled).durationMs)
    }

    @Test
    fun `max duration cap auto-completes the recording`() {
        val clock = MutableClock()
        val source = FakeAudioSource(clock, advanceMsPerRead = 100L, bytesPerRead = 320)
        val session = RecordingSession(source, maxDurationMs = 500L, clock = { clock.now })

        val outcome = session.run(shouldStop = { false }, shouldCancel = { false }, onTick = {})

        assertTrue(outcome is Outcome.Completed)
        val c = outcome as Outcome.Completed
        // Five 100ms reads bring elapsed to 500ms == cap, then the loop completes.
        assertEquals(5, source.reads)
        assertEquals(500L, c.durationMs)
        assertEquals(5 * 320, c.pcm.size)
    }

    @Test
    fun `source end of stream completes with whatever was captured`() {
        val clock = MutableClock()
        val source = FakeAudioSource(clock, advanceMsPerRead = 100L, bytesPerRead = 320, eofAfter = 4)
        val session = RecordingSession(source, maxDurationMs = Long.MAX_VALUE, clock = { clock.now })

        val outcome = session.run(shouldStop = { false }, shouldCancel = { false }, onTick = {})

        assertTrue(outcome is Outcome.Completed)
        val c = outcome as Outcome.Completed
        assertEquals(4 * 320, c.pcm.size)
        assertEquals(400L, c.durationMs)
    }

    @Test
    fun `source read error yields a Mic error outcome`() {
        val clock = MutableClock()
        val source = FakeAudioSource(clock)
        source.throwOn = RuntimeException("mic died")
        val session = RecordingSession(source, maxDurationMs = Long.MAX_VALUE, clock = { clock.now })

        val outcome = session.run(shouldStop = { false }, shouldCancel = { false }, onTick = {})

        assertTrue(outcome is Outcome.Error)
        assertEquals("MIC_FAILED", (outcome as Outcome.Error).code)
    }

    @Test
    fun `stop checked before first read yields empty completed recording`() {
        val clock = MutableClock()
        val source = FakeAudioSource(clock)
        val session = RecordingSession(source, maxDurationMs = Long.MAX_VALUE, clock = { clock.now })

        val outcome = session.run(shouldStop = { true }, shouldCancel = { false }, onTick = {})

        assertTrue(outcome is Outcome.Completed)
        val c = outcome as Outcome.Completed
        assertEquals(0, c.pcm.size)
        assertEquals(0L, c.durationMs)
        assertEquals(0, source.reads)
    }

    @Test
    fun `cancel takes priority over stop`() {
        val clock = MutableClock()
        val source = FakeAudioSource(clock)
        val session = RecordingSession(source, maxDurationMs = Long.MAX_VALUE, clock = { clock.now })

        val outcome = session.run(
            shouldStop = { true },
            shouldCancel = { true },
            onTick = {},
        )

        assertTrue(outcome is Outcome.Cancelled)
    }
}
