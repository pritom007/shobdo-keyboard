package com.shohojakkhor.keyboard.voice.capture

/** A completed recording, ready to send to the backend. */
data class Recording(
    val wavBytes: ByteArray,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Recording) return false
        return durationMs == other.durationMs &&
            sampleRate == other.sampleRate &&
            channels == other.channels &&
            wavBytes.contentEquals(other.wavBytes)
    }

    override fun hashCode(): Int {
        var r = wavBytes.contentHashCode()
        r = 31 * r + durationMs.hashCode()
        r = 31 * r + sampleRate
        r = 31 * r + channels
        return r
    }
}
