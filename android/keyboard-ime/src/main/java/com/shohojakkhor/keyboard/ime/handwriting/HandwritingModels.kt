package com.shohojakkhor.keyboard.ime.handwriting

public data class InkPoint(val x: Float, val y: Float, val timestampMs: Long)

public data class InkStroke(val points: List<InkPoint>) {
    init { require(points.isNotEmpty()) }
}

public enum class HandwritingModelState {
    CHECKING,
    DOWNLOADING,
    READY,
    ERROR,
}

public interface BanglaHandwritingRecognizer : AutoCloseable {
    public fun ensureModel(onState: (HandwritingModelState) -> Unit)
    public fun recognize(strokes: List<InkStroke>, onResult: (Result<List<String>>) -> Unit)
}
