package com.shohojakkhor.keyboard.ime.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.shohojakkhor.keyboard.ime.voice.VoiceStrings

/**
 * Full-keyboard listening panel shown while the IME is recording audio for
 * on-device recognition. Replaces the normal rows of keys so the user can't
 * accidentally type while the mic is live.
 *
 * Elderly-first design (mirrors the standalone VoiceActivity):
 *  - One giant pulsing red circle as the focal point — impossible to miss.
 *  - A single Bengali sentence ("শুনছি…") and a live M:SS timer.
 *  - Two large buttons: "থামুন" (stop, primary) and "বাতি�ল করুন" (cancel).
 *  - In the PROCESSING state the circle becomes a spinner with "লিখছি…".
 *  - In the ERROR state the message + a single retry affordance is shown.
 *
 * The view is dumb: it only renders the state it's told to and fires
 * callbacks. The [com.shohojakkhor.keyboard.ime.voice.VoiceController] owns the
 * state machine.
 */
internal class VoicePanelView(
    context: Context,
    private val onStop: () -> Unit,
    private val onCancel: () -> Unit,
    private val onRetry: () -> Unit,
) : LinearLayout(context) {

    enum class Mode { LISTENING, PROCESSING, ERROR }

    private val indicator: View
    private val statusText: TextView
    private val timerText: TextView
    private val stopButton: Button
    private val cancelButton: Button
    private val retryButton: Button

    private var pulseAnimator: ValueAnimator? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(BG_COLOR)
        setPadding(dp(24), dp(24), dp(24), dp(24))

        // Big pulsing circle, centered.
        indicator = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#D32F2F"))
            }
            layoutParams = LayoutParams(dp(160), dp(160)).apply {
                bottomMargin = dp(24)
            }
        }
        addView(indicator)

        statusText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#111111")))
            gravity = Gravity.CENTER
            text = VoiceStrings.LISTENING
            setPadding(0, 0, 0, dp(8))
        }
        addView(statusText)

        timerText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#444444")))
            gravity = Gravity.CENTER
            text = "0:00"
            setPadding(0, 0, 0, dp(24))
        }
        addView(timerText)

        stopButton = Button(context).apply {
            text = VoiceStrings.STOP
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onStop()
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(64)).apply {
                bottomMargin = dp(12)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#D32F2F"))
            }
            setTextColor(android.content.res.ColorStateList.valueOf(Color.WHITE))
        }
        addView(stopButton)

        cancelButton = Button(context).apply {
            text = VoiceStrings.CANCEL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCancel()
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(56))
        }
        addView(cancelButton)

        retryButton = Button(context).apply {
            text = "আবার"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onRetry()
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(64))
            visibility = View.GONE
            setTextColor(android.content.res.ColorStateList.valueOf(Color.WHITE))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1976D2"))
            }
        }
        addView(retryButton)
    }

    fun showListening() {
        statusText.text = VoiceStrings.LISTENING
        statusText.setTextColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#D32F2F")))
        timerText.visibility = View.VISIBLE
        stopButton.visibility = View.VISIBLE
        cancelButton.visibility = View.VISIBLE
        retryButton.visibility = View.GONE
        (indicator.background as? GradientDrawable)?.setColor(Color.parseColor("#D32F2F"))
        startPulse()
    }

    fun showProcessing(message: String = VoiceStrings.PROCESSING) {
        stopPulse()
        statusText.text = message
        statusText.setTextColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#111111")))
        timerText.visibility = View.GONE
        stopButton.visibility = View.GONE
        cancelButton.visibility = View.VISIBLE
        retryButton.visibility = View.GONE
        (indicator.background as? GradientDrawable)?.setColor(Color.parseColor("#9E9E9E"))
    }

    fun showError(message: String) {
        stopPulse()
        statusText.text = message
        statusText.setTextColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")))
        timerText.visibility = View.GONE
        stopButton.visibility = View.GONE
        cancelButton.visibility = View.GONE
        retryButton.visibility = View.VISIBLE
        (indicator.background as? GradientDrawable)?.setColor(Color.parseColor("#C62828"))
    }

    fun setTimerMs(ms: Long) {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        timerText.text = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun startPulse() {
        stopPulse()
        pulseAnimator = ValueAnimator.ofFloat(0.7f, 1.0f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a -> indicator.alpha = a.animatedValue as Float }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        indicator.alpha = 1f
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        val BG_COLOR: Int = Color.parseColor("#E8EAED")
    }
}
