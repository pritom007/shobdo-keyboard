package com.shohojakkhor.keyboard.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shohojakkhor.keyboard.speech.RemoteSpeechRepository
import com.shohojakkhor.keyboard.speech.SpeechConfig
import com.shohojakkhor.keyboard.speech.SpeechError
import com.shohojakkhor.keyboard.speech.SpeechResult
import com.shohojakkhor.keyboard.speech.DeviceIdProvider
import com.shohojakkhor.keyboard.voice.capture.AudioRecordAudioSource
import com.shohojakkhor.keyboard.voice.capture.AudioRecorder
import com.shohojakkhor.keyboard.voice.capture.RecorderListener
import com.shohojakkhor.keyboard.voice.capture.RecorderState
import com.shohojakkhor.keyboard.voice.capture.Recording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the voice screen's state and orchestrates the recorder (capture) and
 * the speech repository (network).
 *
 * Designed for tap-to-toggle (no press-and-hold): [startRecording] begins a
 * capture, [stopRecording] finishes it and sends it for transcription,
 * [cancelRecording] discards it. The recorder auto-finishes at 30s even if the
 * user never taps stop, so a confused user never gets stuck recording.
 *
 * Audio never leaves the device until the single upload; the WAV bytes are
 * held in memory and dropped as soon as the request returns.
 */
class VoiceViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val deviceId: String = DeviceIdProvider.get(app)
    private val repo = RemoteSpeechRepository(SpeechConfig(), deviceId)
    private val recorder = AudioRecorder(sourceProvider = { AudioRecordAudioSource() })

    /** Only the recorder thread writes this; no synchronization needed. */
    private var lastShownSecond = -1L

    init {
        recorder.setListener(RecorderCallback())
    }

    /** Begin a recording. Caller must have already confirmed mic permission. */
    fun startRecording() {
        lastShownSecond = -1L
        _state.value = VoiceUiState.Recording(elapsedMs = 0L)
        recorder.start()
    }

    /** Finish the recording and send it for transcription. */
    fun stopRecording() {
        recorder.stop()
    }

    /** Discard the recording and return to idle. */
    fun cancelRecording() {
        recorder.cancel()
    }

    /** Return to the idle state so the user can try again. */
    fun reset() {
        _state.value = VoiceUiState.Idle
    }

    /** Called by the Activity when the permission request resolves. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startRecording()
        } else {
            _state.value = VoiceUiState.PermissionDenied("মাইকের অনুমতি দিন।")
        }
    }

    private inner class RecorderCallback : RecorderListener {
        override fun onStateChanged(state: RecorderState) {
            // The specific callbacks below carry the meaningful transitions.
        }

        override fun onTick(elapsedMs: Long) {
            // Throttle to one update per second so the timer reads cleanly and
            // Compose doesn't recompose ~100x/sec.
            val second = elapsedMs / 1000L
            if (second != lastShownSecond) {
                lastShownSecond = second
                _state.value = VoiceUiState.Recording(elapsedMs)
            }
        }

        override fun onComplete(recording: Recording) {
            _state.value = VoiceUiState.Processing
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    repo.transcribe(recording.wavBytes, language = "bn")
                }
                _state.value = when (result) {
                    is SpeechResult.Success -> VoiceUiState.Done(text = result.data.text)
                    is SpeechResult.Error -> VoiceUiState.Error(messageBn = result.error.messageBn)
                }
            }
        }

        override fun onCancelled() {
            _state.value = VoiceUiState.Idle
        }

        override fun onError(code: String) {
            _state.value = VoiceUiState.Error(messageBn = MIC_ERROR_BN)
        }
    }

    companion object {
        private const val MIC_ERROR_BN = "মাইকে সমস্যা। আবার চেষ্টা করুন।"
    }
}
