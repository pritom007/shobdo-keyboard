package com.shohojakkhor.keyboard.speech.ondevice

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

/**
 * Wraps sherpa-onnx's [OfflineRecognizer] running Whisper base (multilingual)
 * on-device. Bengali is the primary language (`language = "bn"`, task =
 * transcribe). No network, no API key, no backend round-trip.
 *
 * Construction loads the model from the app's `assets/sherpa-whisper-base/`
 * folder, which takes ~2–4 s on a modern phone (base is ~2.3× the size of
 * tiny, so first-load is noticeably slower — callers must construct this on
 * a background thread). Reuse the instance across transcriptions; the model
 * stays in memory between calls.
 *
 * The recognizer is thread-safe for sequential `transcribe` calls but is not
 * designed for concurrent use; the IME calls it from a single dedicated
 * background thread.
 */
class OnDeviceSpeechRecognizer(
    context: Context,
    private val assetsFolder: String = DEFAULT_ASSETS_FOLDER,
    private val language: String = "bn",
    private val numThreads: Int = 2,
) {
    private val assetManager: AssetManager = context.applicationContext.assets
    private var recognizer: OfflineRecognizer? = null
    private var initialized = false

    /** Lazily load the model on first use. Safe to call once; subsequent
     *  calls are no-ops. */
    @Synchronized
    fun ensureInitialized() {
        if (initialized) return
        val whisper = OfflineWhisperModelConfig(
            encoder = "$assetsFolder/$ENCODER_FILE",
            decoder = "$assetsFolder/$DECODER_FILE",
            language = language,
            task = "transcribe",
            tailPaddings = 0,
            enableTokenTimestamps = false,
            enableSegmentTimestamps = false,
        )
        // Start from the no-arg default (all unused model slots empty) and
        // `copy()` in only the Whisper slot + the fields we care about. Using
        // copy() is robust against sherpa-onnx adding new model slots later.
        val model = OfflineModelConfig().copy(
            whisper = whisper,
            tokens = "$assetsFolder/$TOKENS_FILE",
            numThreads = numThreads,
            debug = false,
            provider = "cpu",
        )
        val config = OfflineRecognizerConfig().copy(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = model,
            decodingMethod = "greedy_search",
            maxActivePaths = 4,
            hotwordsScore = 1.0f,
            blankPenalty = 0.0f,
        )
        recognizer = OfflineRecognizer(assetManager, config)
        initialized = true
    }

    /** Transcribe 16-bit signed LE PCM [pcm] at [sampleRate] Hz to text.
     *  Blocks for ~0.5–3 s depending on audio length and device speed. */
    fun transcribe(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE): String {
        ensureInitialized()
        val rec = recognizer ?: return ""
        val samples = PcmToFloatConverter.toFloats(pcm)
        if (samples.isEmpty()) return ""
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate)
            rec.decode(stream)
            val result = rec.getResult(stream)
            return result.text.trim()
        } finally {
            stream.release()
        }
    }

    /** Release the native recognizer and model memory. Idempotent. */
    @Synchronized
    fun release() {
        recognizer?.release()
        recognizer = null
        initialized = false
    }

    companion object {
        const val DEFAULT_ASSETS_FOLDER = "sherpa-whisper-base"
        const val ENCODER_FILE = "base-encoder.int8.onnx"
        const val DECODER_FILE = "base-decoder.int8.onnx"
        const val TOKENS_FILE = "base-tokens.txt"
        const val SAMPLE_RATE = 16_000
    }
}
