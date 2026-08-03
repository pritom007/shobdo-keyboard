package com.shohojakkhor.keyboard.ime.voice

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceControllerTest {

    @Test
    fun `starts in IDLE`() {
        val c = VoiceController()
        assertEquals(VoiceController.State.IDLE, c.state)
    }

    @Test
    fun `start transitions IDLE to LISTENING and fires callback`() {
        val c = VoiceController()
        var seen: VoiceController.State? = null
        c.onStateChanged = { seen = it }
        c.start()
        assertEquals(VoiceController.State.LISTENING, c.state)
        assertEquals(VoiceController.State.LISTENING, seen)
    }

    @Test
    fun `start from ERROR also reaches LISTENING`() {
        val c = VoiceController()
        c.start(); c.stop(); c.onError()
        assertEquals(VoiceController.State.ERROR, c.state)
        c.start()
        assertEquals(VoiceController.State.LISTENING, c.state)
    }

    @Test
    fun `start while LISTENING is a no-op`() {
        val c = VoiceController()
        c.start()
        var calls = 0
        c.onStateChanged = { calls++ }
        c.start()
        assertEquals(0, calls, "second start must not fire a transition")
        assertEquals(VoiceController.State.LISTENING, c.state)
    }

    @Test
    fun `stop transitions LISTENING to PROCESSING`() {
        val c = VoiceController()
        c.start()
        c.stop()
        assertEquals(VoiceController.State.PROCESSING, c.state)
    }

    @Test
    fun `stop while not LISTENING is a no-op`() {
        val c = VoiceController()
        c.stop()
        assertEquals(VoiceController.State.IDLE, c.state)
    }

    @Test
    fun `cancel from LISTENING returns to IDLE`() {
        val c = VoiceController()
        c.start()
        c.cancel()
        assertEquals(VoiceController.State.IDLE, c.state)
    }

    @Test
    fun `cancel from PROCESSING returns to IDLE`() {
        val c = VoiceController()
        c.start(); c.stop()
        c.cancel()
        assertEquals(VoiceController.State.IDLE, c.state)
    }

    @Test
    fun `onRecognitionResult commits text and returns to IDLE`() {
        val c = VoiceController()
        c.start(); c.stop()
        var committed: String? = null
        c.onTranscript = { committed = it }
        c.onRecognitionResult("আমি ভালো আছি")
        assertEquals(VoiceController.State.IDLE, c.state)
        assertEquals("আমি ভালো আছি", committed)
    }

    @Test
    fun `onRecognitionResult with blank text does not commit`() {
        val c = VoiceController()
        c.start(); c.stop()
        var committed: String? = null
        c.onTranscript = { committed = it }
        c.onRecognitionResult("   ")
        assertEquals(VoiceController.State.IDLE, c.state)
        assertNull(committed)
    }

    @Test
    fun `onRecognitionResult while not PROCESSING is ignored`() {
        val c = VoiceController()
        var committed: String? = null
        c.onTranscript = { committed = it }
        c.onRecognitionResult("আমি")
        assertNull(committed)
        assertEquals(VoiceController.State.IDLE, c.state)
    }

    @Test
    fun `onError transitions to ERROR and does not commit`() {
        val c = VoiceController()
        c.start(); c.stop()
        var committed: String? = null
        c.onTranscript = { committed = it }
        c.onError()
        assertEquals(VoiceController.State.ERROR, c.state)
        assertNull(committed)
    }

    @Test
    fun `reset clears ERROR to IDLE`() {
        val c = VoiceController()
        c.start(); c.stop(); c.onError()
        c.reset()
        assertEquals(VoiceController.State.IDLE, c.state)
    }

    @Test
    fun `reset while not ERROR is a no-op`() {
        val c = VoiceController()
        c.start()
        var calls = 0
        c.onStateChanged = { calls++ }
        c.reset()
        assertEquals(0, calls)
        assertEquals(VoiceController.State.LISTENING, c.state)
    }

    @Test
    fun `every transition fires the callback exactly once`() {
        val c = VoiceController()
        val seen = mutableListOf<VoiceController.State>()
        c.onStateChanged = { seen.add(it) }
        c.start()       // LISTENING
        c.stop()        // PROCESSING
        c.onRecognitionResult("x")  // IDLE
        assertEquals(
            listOf(VoiceController.State.LISTENING, VoiceController.State.PROCESSING, VoiceController.State.IDLE),
            seen,
        )
    }
}
