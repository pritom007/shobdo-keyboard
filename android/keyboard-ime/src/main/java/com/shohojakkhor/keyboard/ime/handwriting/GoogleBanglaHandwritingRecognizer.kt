package com.shohojakkhor.keyboard.ime.handwriting

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink

/** Google ML Kit `bn` recognizer. Stroke content never leaves the device. */
public class GoogleBanglaHandwritingRecognizer : BanglaHandwritingRecognizer {
    private val modelIdentifier = requireNotNull(
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(BANGLA_LANGUAGE_TAG),
    ) { "ML Kit Bangla handwriting model is unavailable" }
    private val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
    private val modelManager = RemoteModelManager.getInstance()
    private val recognizer: DigitalInkRecognizer = DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).build(),
    )
    private var state: HandwritingModelState = HandwritingModelState.CHECKING

    override fun ensureModel(onState: (HandwritingModelState) -> Unit) {
        if (state == HandwritingModelState.READY) {
            onState(state)
            return
        }
        state = HandwritingModelState.CHECKING
        onState(state)
        modelManager.isModelDownloaded(model)
            .addOnSuccessListener { downloaded ->
                if (downloaded) {
                    state = HandwritingModelState.READY
                    onState(state)
                } else {
                    state = HandwritingModelState.DOWNLOADING
                    onState(state)
                    modelManager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            state = HandwritingModelState.READY
                            onState(state)
                        }
                        .addOnFailureListener {
                            state = HandwritingModelState.ERROR
                            onState(state)
                        }
                }
            }
            .addOnFailureListener {
                state = HandwritingModelState.ERROR
                onState(state)
            }
    }

    override fun recognize(strokes: List<InkStroke>, onResult: (Result<List<String>>) -> Unit) {
        if (state != HandwritingModelState.READY || strokes.isEmpty()) {
            onResult(Result.failure(IllegalStateException("Bangla handwriting model is not ready")))
            return
        }
        val ink = Ink.builder().apply {
            strokes.forEach { stroke ->
                addStroke(
                    Ink.Stroke.builder().apply {
                        stroke.points.forEach { point ->
                            addPoint(Ink.Point.create(point.x, point.y, point.timestampMs))
                        }
                    }.build(),
                )
            }
        }.build()
        recognizer.recognize(ink)
            .addOnSuccessListener { recognition ->
                onResult(Result.success(recognition.candidates.map { it.text }.distinct().take(3)))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun close() {
        recognizer.close()
    }

    private companion object {
        const val BANGLA_LANGUAGE_TAG = "bn"
    }
}
