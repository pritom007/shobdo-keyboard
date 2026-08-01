package com.shobdo.keyboard.voice.capture

/**
 * The public recorder. Handles mic source lifecycle and the background
 * capture thread, mapping [RecordingSession] outcomes to [RecorderListener]
 * callbacks.
 *
 * Tap-to-toggle semantics (chosen for elderly users — no press-and-hold):
 *   - [start] begins a recording from [RecorderState.IDLE].
 *   - [stop] finishes the recording and fires [RecorderListener.onComplete].
 *   - [cancel] discards the in-flight recording and fires
 *     [RecorderListener.onCancelled].
 *   - A recording auto-finishes at [ShobdoAudio.MAX_DURATION_MS] even if the
 *     user never taps stop, so a confused user never gets stuck recording.
 *
 * Audio stays in memory for the whole recording and is never persisted.
 */
class AudioRecorder(
    private val sourceProvider: () -> AudioSource,
    private val maxDurationMs: Long = ShobdoAudio.MAX_DURATION_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val lock = Any()
    private var state: RecorderState = RecorderState.IDLE
    private var listener: RecorderListener? = null

    private var source: AudioSource? = null
    private var thread: Thread? = null
    @Volatile private var stopRequested = false
    @Volatile private var cancelRequested = false

    fun setListener(l: RecorderListener?) {
        synchronized(lock) { listener = l }
    }

    fun getState(): RecorderState = synchronized(lock) { state }

    fun isRecording(): Boolean = getState() == RecorderState.RECORDING

    /** Begin a new recording. No-op unless the recorder is [RecorderState.IDLE]. */
    fun start() {
        synchronized(lock) {
            if (state != RecorderState.IDLE) return
            stopRequested = false
            cancelRequested = false
            val src = try {
                sourceProvider()
            } catch (_: Exception) {
                state = RecorderState.ERROR
                listener?.onError("MIC_FAILED")
                listener?.onStateChanged(RecorderState.ERROR)
                return
            }
            source = src
            // AudioRecordAudioSource.start() must be called on the capture thread
            // to avoid blocking the UI; do it inside the thread run.
            state = RecorderState.RECORDING
        }
        listener?.onStateChanged(RecorderState.RECORDING)
        thread = Thread({ runCapture() }, "shobdo-audio-recorder").apply {
            isDaemon = true
            start()
        }
    }

    /** Finish the current recording. Fires [RecorderListener.onComplete]. */
    fun stop() {
        synchronized(lock) {
            if (state != RecorderState.RECORDING) return
            stopRequested = true
            state = RecorderState.COMPLETING
        }
        listener?.onStateChanged(RecorderState.COMPLETING)
    }

    /** Discard the current recording. Fires [RecorderListener.onCancelled]. */
    fun cancel() {
        synchronized(lock) {
            if (state != RecorderState.RECORDING && state != RecorderState.COMPLETING) return
            cancelRequested = true
            stopRequested = true
        }
    }

    /** Clear an [RecorderState.ERROR] so a new recording can be attempted. */
    fun reset() {
        synchronized(lock) {
            if (state != RecorderState.ERROR) return
            state = RecorderState.IDLE
        }
        listener?.onStateChanged(RecorderState.IDLE)
    }

    private fun runCapture() {
        val src = source ?: return
        if (src is AudioRecordAudioSource) {
            try {
                src.start()
            } catch (_: Exception) {
                fail("MIC_FAILED")
                closeQuietly(src)
                return
            }
        }
        val session = RecordingSession(src, maxDurationMs, clock)
        val outcome = session.run(
            shouldStop = { stopRequested },
            shouldCancel = { cancelRequested },
            onTick = { elapsed -> listener?.onTick(elapsed) },
        )
        closeQuietly(src)
        when (outcome) {
            is RecordingSession.Outcome.Completed -> {
                val wav = WavWriter.toWav(
                    pcm = outcome.pcm,
                    sampleRate = outcome.sampleRate,
                    channels = outcome.channels,
                )
                synchronized(lock) { state = RecorderState.IDLE }
                listener?.onComplete(
                    Recording(
                        wavBytes = wav,
                        durationMs = outcome.durationMs,
                        sampleRate = outcome.sampleRate,
                        channels = outcome.channels,
                    ),
                )
                listener?.onStateChanged(RecorderState.IDLE)
            }
            is RecordingSession.Outcome.Cancelled -> {
                synchronized(lock) { state = RecorderState.IDLE }
                listener?.onCancelled()
                listener?.onStateChanged(RecorderState.IDLE)
            }
            is RecordingSession.Outcome.Error -> fail(outcome.code)
        }
        synchronized(lock) { source = null; thread = null }
    }

    private fun fail(code: String) {
        synchronized(lock) { state = RecorderState.ERROR }
        listener?.onError(code)
        listener?.onStateChanged(RecorderState.ERROR)
    }

    private fun closeQuietly(src: AudioSource) {
        try {
            src.close()
        } catch (_: Exception) {
            // Best-effort release.
        }
    }
}
