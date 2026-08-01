package com.shobdo.keyboard.ime.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.shobdo.keyboard.ime.R
import com.shobdo.keyboard.ime.layout.BengaliBanglish
import com.shobdo.keyboard.ime.layout.EnglishQwerty
import com.shobdo.keyboard.ime.layout.Key
import com.shobdo.keyboard.ime.layout.KeyAction
import com.shobdo.keyboard.ime.layout.KeyRow
import com.shobdo.keyboard.ime.layout.KeyboardLayout
import com.shobdo.keyboard.ime.layout.SymbolsLayout
import com.shobdo.keyboard.ime.privacy.InputPrivacyMode
import com.shobdo.keyboard.ime.state.KeyboardMode
import com.shobdo.keyboard.ime.voice.VoiceStrings
import com.shobdo.keyboard.translit.Candidate

/**
 * Root view for the IME. See docs/architecture.md for the classic-View
 * vs. Compose decision.
 *
 * Layout stack (top to bottom):
 *   1. Sensitive-mode banner (only in SENSITIVE privacy mode).
 *   2. Candidate strip (only in BENGALI_BANGLISH mode).
 *   3. Rows of keys.
 *   4. System-navigation bar inset padding.
 */
@SuppressLint("ViewConstructor")
internal class KeyboardView(
    context: Context,
    private val onKeyAction: (KeyAction) -> Unit,
    private val onCandidateSelected: (Candidate) -> Unit,
) : LinearLayout(context) {

    private val rowsContainer: LinearLayout
    private val banner: TextView
    private val candidateStrip: CandidateStripView
    private val voicePanel: VoicePanelView

    private var currentMode: KeyboardMode = KeyboardMode.ENGLISH_LOWER
    private var privacyMode: InputPrivacyMode = InputPrivacyMode.NORMAL

    private var extraBottomGapPx: Int = 0
    private var systemBottomInsetPx: Int = 0

    /** Voice panel callbacks. Wired by the IME service. */
    var onVoiceStop: (() -> Unit)? = null
    var onVoiceCancel: (() -> Unit)? = null
    var onVoiceRetry: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(BG_COLOR)

        // 1. Sensitive banner
        banner = TextView(context).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            setBackgroundColor(BANNER_COLOR)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        addView(banner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // 2. Candidate strip (only shown in Banglish mode; managed here rather
        //    than by the service so it stays inside the keyboard window).
        candidateStrip = CandidateStripView(context, onCandidateSelected)
        addView(
            candidateStrip,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(CANDIDATE_STRIP_HEIGHT_DP)),
        )
        candidateStrip.visibility = View.GONE

        // 3. Rows
        rowsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        addView(rowsContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // 3b. Voice panel — hidden by default; shown when the user taps the mic.
        voicePanel = VoicePanelView(
            context = context,
            onStop = { onVoiceStop?.invoke() },
            onCancel = { onVoiceCancel?.invoke() },
            onRetry = { onVoiceRetry?.invoke() },
        )
        voicePanel.visibility = View.GONE
        addView(voicePanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 4. Navigation-bar / gesture inset applied as bottom padding on the
        //    root so no key is hidden under gesture-nav on Android 10+.
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            systemBottomInsetPx = maxOf(nav.bottom, gestures.bottom)
            applyBottomPadding(v)
            insets
        }

        render()
    }

    fun setMode(mode: KeyboardMode) {
        if (mode == currentMode) return
        currentMode = mode
        // Clear the candidate strip when leaving Banglish; the service is also
        // responsible for finishing any active composition.
        if (!mode.isBengaliBanglish) {
            candidateStrip.setCandidates(emptyList())
        }
        render()
    }

    fun setPrivacyMode(mode: InputPrivacyMode) {
        if (mode == privacyMode) return
        privacyMode = mode
        render()
    }

    /** Update the candidate strip contents. */
    fun setCandidates(candidates: List<Candidate>) {
        candidateStrip.setCandidates(candidates)
    }

    /** Show the voice listening panel, hiding the normal keyboard rows. */
    fun showVoicePanel() {
        banner.visibility = View.GONE
        candidateStrip.visibility = View.GONE
        rowsContainer.visibility = View.GONE
        voicePanel.visibility = View.VISIBLE
        voicePanel.showListening()
    }

    /** Update the voice panel's listening timer (ms). */
    fun setVoiceTimerMs(ms: Long) {
        voicePanel.setTimerMs(ms)
    }

    /** Switch the voice panel to the processing ("লিখছি…") state, or pass a
     *  custom [message] for the offline-fallback case. */
    fun showVoiceProcessing(message: String = VoiceStrings.PROCESSING) {
        voicePanel.showProcessing(message)
    }

    /** Switch the voice panel to an error state with a Bengali message. */
    fun showVoiceError(message: String) {
        voicePanel.showError(message)
    }

    /** Hide the voice panel and restore the normal keyboard. */
    fun hideVoicePanel() {
        voicePanel.visibility = View.GONE
        rowsContainer.visibility = View.VISIBLE
        // Restore banner / candidate strip visibility per current mode.
        banner.visibility = if (privacyMode == InputPrivacyMode.SENSITIVE) View.VISIBLE else View.GONE
        candidateStrip.visibility =
            if (currentMode.isBengaliBanglish) View.VISIBLE else View.GONE
    }

    fun setExtraBottomGapDp(gapDp: Int) {
        val clamped = gapDp.coerceIn(0, 96)
        val px = dp(clamped)
        if (px == extraBottomGapPx) return
        extraBottomGapPx = px
        applyBottomPadding(this)
    }

    private fun applyBottomPadding(v: View) {
        v.updatePadding(bottom = systemBottomInsetPx + extraBottomGapPx)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    // -- Rendering --------------------------------------------------------------

    private fun render() {
        banner.visibility = if (privacyMode == InputPrivacyMode.SENSITIVE) View.VISIBLE else View.GONE
        if (privacyMode == InputPrivacyMode.SENSITIVE) {
            banner.text = context.getString(R.string.sensitive_mode_banner)
        }

        candidateStrip.visibility =
            if (currentMode.isBengaliBanglish) View.VISIBLE else View.GONE

        rowsContainer.removeAllViews()

        val layout: KeyboardLayout = when (currentMode) {
            KeyboardMode.ENGLISH_LOWER,
            KeyboardMode.ENGLISH_UPPER,
            KeyboardMode.ENGLISH_CAPS,
            -> EnglishQwerty.layoutFor(currentMode)!!

            KeyboardMode.SYMBOLS_PAGE1,
            KeyboardMode.SYMBOLS_PAGE2,
            KeyboardMode.SYMBOLS_PAGE1_BN,
            KeyboardMode.SYMBOLS_PAGE2_BN,
            -> SymbolsLayout.layoutFor(currentMode)!!

            KeyboardMode.BENGALI_BANGLISH,
            KeyboardMode.BENGALI_BANGLISH_UPPER,
            KeyboardMode.BENGALI_BANGLISH_CAPS,
            -> BengaliBanglish.layoutFor(currentMode)!!
        }

        for (row in layout.rows) {
            rowsContainer.addView(buildRowView(row))
        }
    }

    private fun buildRowView(row: KeyRow): View {
        val rowLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(KEY_HEIGHT_DP),
            ).apply { topMargin = dp(4) }
        }
        for (key in row.keys) {
            rowLayout.addView(buildKeyButton(key))
        }
        return rowLayout
    }

    private fun buildKeyButton(key: Key): View {
        val button = Button(context).apply {
            text = when (key.action) {
                is KeyAction.Space -> ""
                else -> key.label
            }
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, keyLabelSp(key))
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LayoutParams.MATCH_PARENT,
                key.widthWeight,
            ).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                // Never log key.label — it is user content for character keys (§26 #2).
                onKeyAction(key.action)
            }
        }
        return button
    }

    private fun keyLabelSp(key: Key): Float = when (key.action) {
        is KeyAction.Character -> if (key.label.length == 1) 20f else 16f
        else -> 16f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_HEIGHT_DP: Int = 56
        const val CANDIDATE_STRIP_HEIGHT_DP: Int = 48
        val BG_COLOR: Int = Color.parseColor("#E8EAED")
        val BANNER_COLOR: Int = Color.parseColor("#B00020")
    }
}
