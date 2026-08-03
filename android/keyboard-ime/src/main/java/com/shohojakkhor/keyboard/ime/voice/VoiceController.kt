package com.shohojakkhor.keyboard.ime.voice

/**
 * The in-keyboard voice state machine. Pure logic — no Android, no native,
 * no threads — so it is fully unit-testable.
 *
 * States:
 *   IDLE        — keyboard is normal; mic key visible (if allowed).
 *   LISTENING   — recording audio; the panel shows the pulsing indicator.
 *   PROCESSING  — recording finished; running on-device recognition.
 *   ERROR       — recognition or mic failed; the panel shows a Bengali
 *                 message briefly, then returns to IDLE.
 *
 * The controller does NOT own the recorder or recognizer; the host (the IME
 * service) wires state transitions to those collaborators. This keeps the
 * state machine trivial to test and side-effect-free.
 */
class VoiceController {

    enum class State { IDLE, LISTENING, PROCESSING, ERROR }

    var state: State = State.IDLE
        private set

    /** A listener the host wires to update the panel UI. Called on every
     *  transition, on the same thread the transition was made on. */
    var onStateChanged: ((State) -> Unit)? = null

    /** A listener the host wires to commit the final transcript to the
     *  InputConnection. Called only on a successful recognition. */
    var onTranscript: ((String) -> Unit)? = null

    /** Begin listening. Only valid from IDLE (or ERROR, which clears to IDLE). */
    fun start() {
        if (state == State.LISTENING || state == State.PROCESSING) return
        transition(State.LISTENING)
    }

    /** User tapped stop: finish listening and move to PROCESSING. The host
     *  stops the recorder, takes the PCM, and calls [onRecognitionResult]
     *  when the recognizer returns. */
    fun stop() {
        if (state != State.LISTENING) return
        transition(State.PROCESSING)
    }

    /** User tapped cancel: discard everything and return to IDLE. */
    fun cancel() {
        if (state == State.IDLE) return
        transition(State.IDLE)
    }

    /** Recognizer returned [text]. Commit it and return to IDLE. */
    fun onRecognitionResult(text: String) {
        if (state != State.PROCESSING) return
        if (text.isNotBlank()) onTranscript?.invoke(text)
        transition(State.IDLE)
    }

    /** Recognizer or mic failed. Show an error briefly, then return to IDLE. */
    fun onError() {
        if (state == State.IDLE) return
        transition(State.ERROR)
        // The host decides how long to dwell on ERROR before resetting; tests
        // can call reset() directly.
    }

    /** Clear an ERROR state so the mic key becomes usable again. */
    fun reset() {
        if (state != State.ERROR) return
        transition(State.IDLE)
    }

    private fun transition(to: State) {
        state = to
        onStateChanged?.invoke(to)
    }
}
