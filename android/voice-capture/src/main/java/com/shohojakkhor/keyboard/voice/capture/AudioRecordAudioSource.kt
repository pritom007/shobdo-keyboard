package com.shohojakkhor.keyboard.voice.capture

import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * The real [AudioSource], backed by [AudioRecord] reading the device mic.
 *
 * Constructed on a background thread by [AudioRecorder]. Requests the MIC audio
 * source (noise-suppression-friendly) and 16-bit mono PCM at 16 kHz — the
 * format the backend expects.
 */
class AudioRecordAudioSource(
    override val sampleRate: Int = ShohojakkhorAudio.SAMPLE_RATE,
    override val channels: Int = ShohojakkhorAudio.CHANNELS,
) : AudioSource {

    private val channelConfig: Int =
        if (channels == 1) AndroidAudioFormat.CHANNEL_IN_MONO
        else AndroidAudioFormat.CHANNEL_IN_STEREO

    private val minBuf: Int =
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, AndroidAudioFormat.ENCODING_PCM_16BIT)

    private val record: AudioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        channelConfig,
        AndroidAudioFormat.ENCODING_PCM_16BIT,
        maxOf(minBuf, sampleRate * channels * 2 * 2),  // 2x the per-second byte rate as a floor
    )

    fun start() {
        if (record.state == AudioRecord.STATE_INITIALIZED) {
            record.startRecording()
        }
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int =
        record.read(target, offset, length)

    override fun close() {
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (_: IllegalStateException) {
            // Already stopped; ignore.
        } finally {
            record.release()
        }
    }
}
