package com.shohojakkhor.keyboard.voice

/** All states the Voice screen can be in. One state at a time, so the UI
 *  can render each unambiguously — important for elderly users who should
 *  never wonder "what is it doing right now?". */
sealed interface VoiceUiState {

    /** Nothing happening. The big mic button invites a tap. */
    object Idle : VoiceUiState

    /** Capturing audio. [elapsedMs] drives the on-screen timer. */
    data class Recording(val elapsedMs: Long) : VoiceUiState

    /** Recording finished; waiting for the backend to return text. */
    object Processing : VoiceUiState

    /** Transcription succeeded. The text is shown for the user to review. */
    data class Done(val text: String) : VoiceUiState

    /** Something went wrong (network / server / bad audio). [messageBn] is a
     *  Bengali sentence ready to display, with no jargon and no codes. */
    data class Error(val messageBn: String) : VoiceUiState

    /** Mic permission was denied. The UI offers a button to open settings. */
    data class PermissionDenied(val messageBn: String) : VoiceUiState
}
