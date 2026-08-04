package com.shohojakkhor.keyboard.ime.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.shohojakkhor.keyboard.ime.R
import com.shohojakkhor.keyboard.ime.layout.BengaliBanglish
import com.shohojakkhor.keyboard.ime.layout.EnglishQwerty
import com.shohojakkhor.keyboard.ime.layout.Key
import com.shohojakkhor.keyboard.ime.layout.KeyAction
import com.shohojakkhor.keyboard.ime.layout.KeyRow
import com.shohojakkhor.keyboard.ime.layout.KeyboardLayout
import com.shohojakkhor.keyboard.ime.layout.SymbolsLayout
import com.shohojakkhor.keyboard.ime.privacy.InputPrivacyMode
import com.shohojakkhor.keyboard.ime.state.KeyboardMode
import com.shohojakkhor.keyboard.ime.voice.VoiceStrings
import com.shohojakkhor.keyboard.translit.Candidate

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
    private var activeAlternatePopup: PopupWindow? = null

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
        dismissActiveAlternatePopup()
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

    override fun onDetachedFromWindow() {
        dismissActiveAlternatePopup()
        super.onDetachedFromWindow()
    }

    // -- Rendering --------------------------------------------------------------

    private fun render() {
        dismissActiveAlternatePopup()
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
            text = keycapText(key)
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, keyLabelSp(key))
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LayoutParams.MATCH_PARENT,
                key.widthWeight,
            ).apply popupWindow@{
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        }

        if (key.action is KeyAction.Backspace) {
            configureRepeatingBackspace(button)
        } else if (key.alternates.isNotEmpty()) {
            configureAlternateGesture(button, key)
        } else {
            button.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                // Never log key.label — it is user content for character keys (§26 #2).
                onKeyAction(key.action)
            }
        }
        return button
    }

    private fun keycapText(key: Key): CharSequence = when {
        key.action is KeyAction.Space -> ""
        key.alternateHint == null -> key.label
        else -> {
            val hint = key.alternateHint.orEmpty()
            SpannableString("$hint\n${key.label}").apply {
                setSpan(RelativeSizeSpan(0.55f), 0, hint.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(HINT_COLOR), 0, hint.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureAlternateGesture(button: Button, key: Key) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var popup: PopupWindow? = null
        var popupContent: LinearLayout? = null
        var selectedIndex = 0
        var touchActive = false

        fun updateSelection(rawX: Float) {
            val content = popupContent ?: return
            val location = IntArray(2)
            content.getLocationOnScreen(location)
            val index = ((rawX - location[0]) / ALTERNATE_ITEM_WIDTH_PX)
                .toInt()
                .coerceIn(0, key.alternates.lastIndex)
            if (index != selectedIndex) {
                selectedIndex = index
                button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            for (i in 0 until content.childCount) {
                content.getChildAt(i).background = roundedBackground(
                    if (i == selectedIndex) POPUP_SELECTED_COLOR else POPUP_COLOR,
                    POPUP_CORNER_DP,
                )
            }
        }

        fun dismissPopup() {
            popup?.dismiss()
            if (activeAlternatePopup === popup) activeAlternatePopup = null
            popup = null
            popupContent = null
            button.isPressed = false
        }

        val showPopup = Runnable {
            if (!touchActive || popup != null) return@Runnable
            dismissActiveAlternatePopup()
            selectedIndex = 0
            val content = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = roundedBackground(POPUP_COLOR, POPUP_CORNER_DP)
            }
            key.alternates.forEachIndexed { index, character ->
                content.addView(
                    TextView(context).apply {
                        text = character
                        gravity = Gravity.CENTER
                        setTextColor(Color.BLACK)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                        contentDescription = context.getString(
                            R.string.direct_bengali_character_description,
                            character,
                        )
                        background = roundedBackground(
                            if (index == selectedIndex) POPUP_SELECTED_COLOR else POPUP_COLOR,
                            POPUP_CORNER_DP,
                        )
                    },
                    LinearLayout.LayoutParams(ALTERNATE_ITEM_WIDTH_PX, dp(ALTERNATE_POPUP_HEIGHT_DP)),
                )
            }
            content.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            popupContent = content
            popup = PopupWindow(
                content,
                content.measuredWidth,
                content.measuredHeight,
                false,
            ).apply {
                isClippingEnabled = true
                elevation = dp(8).toFloat()
                setOnDismissListener {
                    if (activeAlternatePopup === this@popupWindow) activeAlternatePopup = null
                    popup = null
                    popupContent = null
                    button.isPressed = false
                }
                showAsDropDown(button, 0, -(button.height + content.measuredHeight + dp(6)))
            }
            activeAlternatePopup = popup
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            button.announceForAccessibility(
                context.getString(R.string.bengali_alternates_opened, key.alternates.joinToString(" ")),
            )
        }

        button.contentDescription = context.getString(
            R.string.banglish_key_with_alternates_description,
            key.label,
            key.alternates.joinToString(", "),
        )
        button.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onKeyAction(key.action)
        }
        // Provides a deterministic alternative for TalkBack and switch access;
        // touch users get the complete slide selector below.
        button.setOnLongClickListener {
            onKeyAction(KeyAction.DirectBengali(key.alternates.first()))
            it.announceForAccessibility(key.alternates.first())
            true
        }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchActive = true
                    downX = event.x
                    downY = event.y
                    view.isPressed = true
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.postDelayed(showPopup, ALTERNATE_HOLD_DELAY_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (popup != null) {
                        updateSelection(event.rawX)
                    } else if (
                        kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop
                    ) {
                        view.removeCallbacks(showPopup)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    touchActive = false
                    view.removeCallbacks(showPopup)
                    if (popup != null) {
                        updateSelection(event.rawX)
                        val selected = key.alternates[selectedIndex]
                        dismissPopup()
                        onKeyAction(KeyAction.DirectBengali(selected))
                        view.announceForAccessibility(selected)
                    } else {
                        view.isPressed = false
                        view.performClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchActive = false
                    view.removeCallbacks(showPopup)
                    dismissPopup()
                    true
                }

                else -> true
            }
        }
    }

    private fun roundedBackground(color: Int, cornerDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(cornerDp).toFloat()
        }

    private fun dismissActiveAlternatePopup() {
        activeAlternatePopup?.dismiss()
        activeAlternatePopup = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureRepeatingBackspace(button: Button) {
        var touchActive = false
        var suppressNextClick = false
        lateinit var repeatDelete: Runnable

        repeatDelete = Runnable {
            if (!touchActive) return@Runnable
            onKeyAction(KeyAction.Backspace)
            button.postDelayed(repeatDelete, BACKSPACE_REPEAT_INTERVAL_MS)
        }

        // Keeps keyboard and switch-access activation working. Touch releases
        // call performClick for accessibility but suppress its duplicate delete.
        button.setOnClickListener {
            if (suppressNextClick) {
                suppressNextClick = false
            } else {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onKeyAction(KeyAction.Backspace)
            }
        }

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchActive = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onKeyAction(KeyAction.Backspace)
                    button.postDelayed(repeatDelete, BACKSPACE_REPEAT_DELAY_MS)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    touchActive = false
                    button.removeCallbacks(repeatDelete)
                    suppressNextClick = true
                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchActive = false
                    button.removeCallbacks(repeatDelete)
                    suppressNextClick = false
                    true
                }

                else -> true
            }
        }
    }

    private fun keyLabelSp(key: Key): Float = when (key.action) {
        is KeyAction.Character -> if (key.label.length == 1) 20f else 16f
        else -> 16f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_HEIGHT_DP: Int = 56
        const val CANDIDATE_STRIP_HEIGHT_DP: Int = 56
        const val BACKSPACE_REPEAT_DELAY_MS: Long = 350L
        const val BACKSPACE_REPEAT_INTERVAL_MS: Long = 55L
        const val ALTERNATE_HOLD_DELAY_MS: Long = 350L
        const val ALTERNATE_POPUP_HEIGHT_DP: Int = 58
        const val ALTERNATE_ITEM_WIDTH_DP: Int = 58
        const val POPUP_CORNER_DP: Int = 12
        val BG_COLOR: Int = Color.parseColor("#E8EAED")
        val BANNER_COLOR: Int = Color.parseColor("#B00020")
        val HINT_COLOR: Int = Color.parseColor("#0B5D61")
        val POPUP_COLOR: Int = Color.parseColor("#FFFDF7")
        val POPUP_SELECTED_COLOR: Int = Color.parseColor("#BFE6E3")
    }

    private val ALTERNATE_ITEM_WIDTH_PX: Int
        get() = dp(ALTERNATE_ITEM_WIDTH_DP)
}
