package com.shobdo.keyboard.ime.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.shobdo.keyboard.ime.R
import com.shobdo.keyboard.translit.Candidate

/**
 * Horizontal strip of Bengali candidates shown above the keyboard when the
 * user is composing in Banglish mode.
 *
 * Elderly-first properties:
 *  - Row height ~48 dp (comfortably above the 48 dp accessibility floor).
 *  - Candidate label 20 sp, high contrast.
 *  - Each candidate is a full-height tappable button, min width ~72 dp.
 *  - Top candidate visually distinct (slight background tint) so the user
 *    can tell which one Space / Enter will commit.
 *  - Overflow-safe via [HorizontalScrollView].
 *  - When idle (no composition) the strip shows a subtle Bengali hint.
 */
@SuppressLint("ViewConstructor")
internal class CandidateStripView(
    context: Context,
    private val onCandidateTapped: (Candidate) -> Unit,
) : HorizontalScrollView(context) {

    private val row: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val idleHint: TextView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(HINT_COLOR)
        gravity = Gravity.CENTER
        text = context.getString(R.string.banglish_empty_hint)
        setPadding(dp(16), 0, dp(16), 0)
    }

    private var currentCandidates: List<Candidate> = emptyList()

    init {
        setBackgroundColor(STRIP_BG_COLOR)
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(
            row,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )
        renderIdle()
    }

    /** Update the strip contents. Empty list → idle hint. */
    fun setCandidates(candidates: List<Candidate>) {
        currentCandidates = candidates
        row.removeAllViews()
        if (candidates.isEmpty()) {
            renderIdle()
        } else {
            for ((index, cand) in candidates.withIndex()) {
                row.addView(buildCandidateButton(cand, isTop = index == 0))
                if (index < candidates.lastIndex) {
                    row.addView(buildSeparator())
                }
            }
        }
        scrollTo(0, 0)
    }

    private fun renderIdle() {
        row.removeAllViews()
        row.addView(
            idleHint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun buildCandidateButton(cand: Candidate, isTop: Boolean): View {
        val tv = TextView(context).apply {
            text = cand.bengali
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(TEXT_COLOR)
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            minWidth = dp(72)
            isClickable = true
            isFocusable = true
            background = candidateBackground(isTop)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCandidateTapped(cand)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }
        return tv
    }

    private fun buildSeparator(): View {
        val v = View(context).apply {
            setBackgroundColor(SEPARATOR_COLOR)
            layoutParams = LinearLayout.LayoutParams(
                dp(1),
                dp(28),
            ).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        return v
    }

    private fun candidateBackground(isTop: Boolean): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(6).toFloat()
        d.setColor(if (isTop) TOP_TINT_COLOR else Color.TRANSPARENT)
        return d
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        val STRIP_BG_COLOR: Int = Color.parseColor("#F5F6F7")
        val TEXT_COLOR: Int = Color.parseColor("#111111")
        val HINT_COLOR: Int = Color.parseColor("#666666")
        val SEPARATOR_COLOR: Int = Color.parseColor("#D0D3D6")
        val TOP_TINT_COLOR: Int = Color.parseColor("#DDE7F5")
    }
}
