package com.shohojakkhor.keyboard.ime.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.shohojakkhor.keyboard.ime.R
import com.shohojakkhor.keyboard.ime.handwriting.HandwritingModelState
import com.shohojakkhor.keyboard.ime.handwriting.InkPoint
import com.shohojakkhor.keyboard.ime.handwriting.InkStroke

/** Elder-friendly handwriting surface embedded inside the IME window. */
internal class HandwritingPanelView(
    context: Context,
    private val onInkReady: (List<InkStroke>) -> Unit,
    private val onInkChanged: () -> Unit,
    private val onCandidate: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onSpace: () -> Unit,
    private val onEnter: () -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    private val status: TextView
    private val candidateRow: LinearLayout
    private val canvas: InkCanvasView

    init {
        orientation = VERTICAL
        setPadding(dp(6), dp(6), dp(6), dp(6))
        setBackgroundColor(Color.parseColor("#E8EAED"))

        candidateRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        addView(candidateRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        status = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#17383A"))
            text = context.getString(R.string.handwriting_preparing)
        }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, dp(26)))

        canvas = InkCanvasView(context) { strokes ->
            onInkChanged()
            removeCallbacks(recognizeRunnable)
            if (strokes.isNotEmpty()) postDelayed(recognizeRunnable, RECOGNITION_PAUSE_MS)
            setCandidates(emptyList())
        }.apply {
            contentDescription = context.getString(R.string.handwriting_canvas_description)
        }
        addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, dp(206)).apply {
            topMargin = dp(4)
            bottomMargin = dp(6)
        })

        val controls = LinearLayout(context).apply { orientation = HORIZONTAL }
        controls.addView(controlButton("↶", R.string.handwriting_undo) { canvas.undo() })
        controls.addView(controlButton("মুছুন", R.string.handwriting_clear) { canvas.clear() })
        controls.addView(controlButton("⌫", R.string.handwriting_backspace, action = onBackspace))
        controls.addView(
            controlButton(
                "স্পেস",
                R.string.handwriting_space,
                weight = 1.65f,
                action = onSpace,
            ),
        )
        controls.addView(controlButton("↵", R.string.handwriting_enter, action = onEnter))
        controls.addView(controlButton("⌨", R.string.handwriting_keyboard, action = onClose))
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, dp(60)))
    }

    private val recognizeRunnable = Runnable {
        val strokes = canvas.snapshot()
        if (strokes.isNotEmpty()) {
            status.text = context.getString(R.string.handwriting_recognizing)
            onInkReady(strokes)
        }
    }

    fun setModelState(state: HandwritingModelState) {
        canvas.isEnabled = state == HandwritingModelState.READY
        status.text = context.getString(
            when (state) {
                HandwritingModelState.CHECKING -> R.string.handwriting_preparing
                HandwritingModelState.DOWNLOADING -> R.string.handwriting_downloading
                HandwritingModelState.READY -> R.string.handwriting_ready
                HandwritingModelState.ERROR -> R.string.handwriting_model_error
            },
        )
    }

    fun setRecognitionError() {
        status.text = context.getString(R.string.handwriting_recognition_error)
    }

    fun setCandidates(candidates: List<String>) {
        candidateRow.removeAllViews()
        if (candidates.isEmpty()) return
        status.text = context.getString(R.string.handwriting_choose_result)
        candidates.take(3).forEach { text ->
            candidateRow.addView(
                Button(context).apply {
                    this.text = text
                    isAllCaps = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                    contentDescription = context.getString(R.string.handwriting_candidate_description, text)
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onCandidate(text)
                        clearInk()
                    }
                },
                LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                },
            )
        }
    }

    fun clearInk() {
        removeCallbacks(recognizeRunnable)
        canvas.clear()
        candidateRow.removeAllViews()
        status.text = context.getString(R.string.handwriting_ready)
    }

    private fun controlButton(
        label: String,
        descriptionRes: Int,
        weight: Float = 1f,
        action: () -> Unit,
    ): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 2) 13f else 20f)
            setPadding(0, 0, 0, 0)
            contentDescription = context.getString(descriptionRes)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                action()
            }
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val RECOGNITION_PAUSE_MS = 700L
    }
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
private class InkCanvasView(
    context: Context,
    private val onChanged: (List<InkStroke>) -> Unit,
) : View(context) {
    private val strokes = mutableListOf<InkStroke>()
    private var activePoints = mutableListOf<InkPoint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B5D61")
        strokeWidth = dp(5).toFloat()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#FFFAF1"))
            setStroke(dp(2), Color.parseColor("#0B5D61"))
            cornerRadius = dp(14).toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { drawStroke(canvas, it.points) }
        drawStroke(canvas, activePoints)
    }

    private fun drawStroke(canvas: Canvas, points: List<InkPoint>) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            canvas.drawPoint(points[0].x, points[0].y, paint)
            return
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePoints = mutableListOf(point(event))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.historySize) {
                    activePoints += InkPoint(
                        event.getHistoricalX(index),
                        event.getHistoricalY(index),
                        event.getHistoricalEventTime(index),
                    )
                }
                activePoints += point(event)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                activePoints += point(event)
                strokes += InkStroke(activePoints.toList())
                activePoints.clear()
                invalidate()
                performClick()
                onChanged(snapshot())
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activePoints.clear()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
        invalidate()
        onChanged(snapshot())
    }

    fun clear() {
        strokes.clear()
        activePoints.clear()
        invalidate()
        onChanged(emptyList())
    }

    fun snapshot(): List<InkStroke> = strokes.map { it.copy(points = it.points.toList()) }

    private fun point(event: MotionEvent) = InkPoint(event.x, event.y, event.eventTime)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
