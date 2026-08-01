package com.shobdo.keyboard.voice.capture

/**
 * Pure-logic capture loop. Reads PCM frames from an [AudioSource] until told
 * to stop, until cancelled, or until the max-duration cap is reached.
 *
 * Synchronous and side-effect-free apart from reading the source and writing
 * into the in-memory buffer, so it is fully unit-testable with a fake source
 * and a controllable clock — no threads, no device.
 *
 * [AudioRecorder] wraps this in a background thread and maps the [Outcome]
 * into listener callbacks.
 */
class RecordingSession(
    private val source: AudioSource,
    private val maxDurationMs: Long = ShobdoAudio.MAX_DURATION_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed class Outcome {
        data class Completed(
            val pcm: ByteArray,
            val durationMs: Long,
            val sampleRate: Int,
            val channels: Int,
        ) : Outcome()

        data class Cancelled(val durationMs: Long) : Outcome()

        data class Error(val code: String) : Outcome()
    }

    /**
     * Run the loop. [shouldStop] is polled for a user-requested stop; returns
     * [Outcome.Completed]. [shouldCancel] is polled for a user-requested
     * discard; returns [Outcome.Cancelled]. On a source read error, returns
     * [Outcome.Error].
     */
    fun run(
        shouldStop: () -> Boolean,
        shouldCancel: () -> Boolean,
        onTick: (Long) -> Unit,
    ): Outcome {
        val startedAt = clock()
        val pcm = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1024)
        try {
            while (true) {
                if (shouldCancel()) {
                    return Outcome.Cancelled(clock() - startedAt)
                }
                if (shouldStop()) {
                    return completed(pcm, startedAt)
                }
                val elapsed = clock() - startedAt
                if (elapsed >= maxDurationMs) {
                    return completed(pcm, startedAt)
                }
                val n = source.read(buf, 0, buf.size)
                if (n < 0) {
                    // End of stream — finish with what we have.
                    return completed(pcm, startedAt)
                }
                if (n > 0) {
                    pcm.write(buf, 0, n)
                }
                onTick(clock() - startedAt)
            }
        } catch (_: Exception) {
            return Outcome.Error("MIC_FAILED")
        }
    }

    private fun completed(pcm: java.io.ByteArrayOutputStream, startedAt: Long): Outcome.Completed =
        Outcome.Completed(
            pcm = pcm.toByteArray(),
            durationMs = clock() - startedAt,
            sampleRate = source.sampleRate,
            channels = source.channels,
        )
}
