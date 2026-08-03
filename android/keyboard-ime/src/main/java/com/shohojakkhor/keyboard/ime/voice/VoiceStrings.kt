package com.shohojakkhor.keyboard.ime.voice

/**
 * Bengali strings shown by the in-keyboard voice panel. Kept in one place so
 * the UI layer never hardcodes Bengali literals and so tests can assert on
 * stable values. No audio or transcript text is ever logged.
 */
object VoiceStrings {
    const val LISTENING = "শুনছি…"
    const val PROCESSING = "লিখছি…"
    const val STOP = "থামুন"
    const val CANCEL = "বাতিল করুন"
    const val TAP_TO_LISTEN = "শুনতে চাপ দিন"
    const val MIC_ERROR = "মাইকে সমস্যা। আবার চেষ্টা করুন।"
    const val RECOGNITION_ERROR = "বুঝতে পারিনি। আবার বলুন।"
    const val MIC_PERMISSION_NEEDED = "মাইকের অনুমতি দিন।"
    /** Shown while the on-device fallback runs after the server was
     *  unreachable. Tells the user why it's slower and that it's degraded. */
    const val OFFLINE_PROCESSING = "ইন্টারনেট নেই, অফলাইনে লিখছি…"
}
