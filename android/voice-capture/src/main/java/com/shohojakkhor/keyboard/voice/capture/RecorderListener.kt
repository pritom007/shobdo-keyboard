package com.shohojakkhor.keyboard.voice.capture

/** Recorder state observed by the UI. */
enum class RecorderState {
    /** Idle, ready to start a new recording. */
    IDLE,

    /** Actively capturing audio. */
    RECORDING,

    /** Stop requested; the capture loop is finishing. */
    COMPLETING,

    /** Mic failed; [AudioRecorder.reset] is required before another attempt. */
    ERROR,
}

/** Callbacks the UI/ViewModel subscribes to. All invoked from the recorder's
 *  background thread except [onStateChanged] which is also posted on transitions. */
interface RecorderListener {
    fun onStateChanged(state: RecorderState)
    fun onTick(elapsedMs: Long)
    fun onComplete(recording: Recording)
    fun onCancelled()
    fun onError(code: String)
}
