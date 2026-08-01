package com.shobdo.keyboard.speech

/**
 * Every transcription failure mode the user can hit, with a Bengali message
 * ready to show. Backend error codes (NO_AUDIO, BAD_AUDIO, …) map here via
 * [fromCode]; network/timeout failures map here directly from caught
 * exceptions in [RemoteSpeechRepository].
 *
 * The product is Bengali-first, so messages are Bengali. Codes stay stable for
 * logging/analytics — never log the audio or transcript itself.
 */
sealed class SpeechError(val code: String, open val messageBn: String) {

    object NoAudio : SpeechError("NO_AUDIO", "কোনো অডিও পাওয়া যায়নি। আবার চেষ্টা করুন।")
    object BadAudio : SpeechError("BAD_AUDIO", "অডিও ঠিক নেই। আবার চেষ্টা করুন।")
    object TooLong : SpeechError("TOO_LONG", "খুব বেশি বড়। ছোট করে বলুন।")
    object TooLarge : SpeechError("TOO_LARGE", "খুব বেশি বড়। ছোট করে বলুন।")
    object RateLimit : SpeechError("RATE_LIMIT", "একটু পরে আবার চেষ্টা করুন।")
    object Unauthorized : SpeechError("UNAUTHORIZED", "অনুমতি নেই।")
    object NoNetwork : SpeechError("NO_NETWORK", "ইন্টারনেট নেই বা ধীর। আবার চেষ্টা করুন।")
    object Timeout : SpeechError("TIMEOUT", "সার্ভার বেশি সময় নিচ্ছে। আবার চেষ্টা করুন।")

    /** Any upstream speech-provider failure surfaced by the backend. */
    data class Provider(val providerCode: String) :
        SpeechError("PROVIDER_ERROR", "সার্ভারে সমস্যা। আবার চেষ্টা করুন।")

    /** Anything we did not expect — never shown raw to the user. */
    data class Unknown(val rawCode: String?) :
        SpeechError("UNKNOWN", "কিছু একটা সমস্যা হয়েছে। আবার চেষ্টা করুন।")

    companion object {
        /** Map a backend error `code` to a [SpeechError]. Falls back to [Unknown]. */
        fun fromCode(code: String?): SpeechError = when (code) {
            "NO_AUDIO" -> NoAudio
            "BAD_AUDIO" -> BadAudio
            "TOO_LONG" -> TooLong
            "TOO_LARGE" -> TooLarge
            "RATE_LIMIT" -> RateLimit
            "UNAUTHORIZED" -> Unauthorized
            "PROVIDER_TIMEOUT", "PROVIDER_RATE_LIMIT",
            "PROVIDER_NOT_CONFIGURED", "PROVIDER_ERROR" -> Provider(code)
            null, "" -> Unknown(null)
            else -> Unknown(code)
        }
    }
}
