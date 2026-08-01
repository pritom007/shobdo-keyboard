package com.shobdo.keyboard.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import com.shobdo.keyboard.ime.layout.KeyAction
import com.shobdo.keyboard.ime.privacy.DefaultInputPrivacyPolicy
import com.shobdo.keyboard.ime.privacy.InputPrivacyMode
import com.shobdo.keyboard.ime.privacy.InputPrivacyPolicy
import com.shobdo.keyboard.ime.state.KeyboardMode
import com.shobdo.keyboard.ime.state.KeyboardModeTransitions
import com.shobdo.keyboard.ime.state.LanguagePreference
import com.shobdo.keyboard.ime.view.KeyboardView
import com.shobdo.keyboard.translit.AvroLikeEngine
import com.shobdo.keyboard.translit.Candidate
import com.shobdo.keyboard.translit.TransliterationEngine

/**
 * The Shobdo Keyboard IME service.
 *
 * ### Responsibilities (M2)
 *  - Render the keyboard view (English QWERTY / symbols / Bengali Banglish).
 *  - Route key taps to the active `InputConnection`.
 *  - Honour the Enter action declared by the host's `EditorInfo`.
 *  - Classify the current field with [InputPrivacyPolicy] and show a banner
 *    when SENSITIVE.  In SENSITIVE mode Banglish composition is forcibly
 *    disabled — the field always shows plain English QWERTY.
 *  - Run the Banglish transliteration composition loop:
 *      1. In BENGALI_BANGLISH mode, each Character keystroke appends to an
 *         in-memory Latin composing buffer.
 *      2. The buffer is fed to [TransliterationEngine] every keystroke.
 *      3. The top Bengali candidate is shown to the host app via
 *         `InputConnection.setComposingText`, so the user sees Bengali as
 *         they type.
 *      4. The candidate strip is populated with up to 5 candidates.
 *      5. Space / Enter commit the top candidate (space appends " ").
 *      6. Tapping a specific candidate commits that one.
 *      7. Backspace inside a non-empty buffer trims a single Latin char.
 *      8. Switching to English commits the raw Latin as a safe default —
 *         never silently discard the user's typing.
 *
 * ### Not in scope for M2
 *  - Persistent selection memory (in-process only for now; DataStore at M2C).
 *  - Voice (M3+), AI rewrite (M4+).
 */
public class ShobdoInputMethodService : InputMethodService() {

    private val privacyPolicy: InputPrivacyPolicy = DefaultInputPrivacyPolicy()
    private val engine: TransliterationEngine = AvroLikeEngine()

    /**
     * Persistent store of the user's last chosen language. Lazily created
     * because we need a Context, which is only usable after the service
     * has been attached to Android.
     */
    private val languagePref: LanguagePreference by lazy { LanguagePreference(this) }

    private var keyboardView: KeyboardView? = null
    private var mode: KeyboardMode = KeyboardMode.ENGLISH_LOWER
    private var privacyMode: InputPrivacyMode = InputPrivacyMode.NORMAL
    private var currentEditorInfo: EditorInfo? = null

    /** Latin composing buffer, non-empty only when [mode] is BENGALI_BANGLISH. */
    private var composing: String = ""

    override fun onCreate() {
        super.onCreate()
        // Restore the user's last language choice as soon as the service
        // starts, so the very first keyboard view we build shows Bengali
        // if that's what the user was using before the screen locked or
        // the process was killed.
        mode = languagePref.getSavedLanguage()
    }

    override fun onCreateInputView(): View {
        window?.window?.let { w -> WindowCompat.setDecorFitsSystemWindows(w, false) }

        val view = KeyboardView(
            context = this,
            onKeyAction = ::handleAction,
            onCandidateSelected = ::commitSpecificCandidate,
        )
        view.setMode(mode)
        view.setPrivacyMode(privacyMode)
        keyboardView = view
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentEditorInfo = attribute
        privacyMode = privacyPolicy.classify(attribute)

        // Restore the user's persisted language for every new input session
        // (screen unlock, field focus change, etc.). This is the fix for
        // "screen lock changes the keyboard back to English": we now
        // remember the last-chosen language across the entire process
        // lifetime, not just within a single input session.
        val savedLanguage = languagePref.getSavedLanguage()
        mode = savedLanguage

        // Sensitive fields force English keyboard — never Banglish, never AI.
        // We do NOT overwrite the persisted preference here — once the user
        // leaves the sensitive field, we want to return to their chosen
        // language automatically.
        if (privacyMode == InputPrivacyMode.SENSITIVE && mode == KeyboardMode.BENGALI_BANGLISH) {
            mode = KeyboardMode.ENGLISH_LOWER
            composing = ""
        }
        keyboardView?.setMode(mode)
        keyboardView?.setPrivacyMode(privacyMode)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // Field is going away — commit whatever we have as a safe default so
        // no user typing is lost. Then reset transient state.
        //
        // IMPORTANT: we deliberately do NOT reset `mode` here. The user's
        // language choice is persisted via [LanguagePreference] and will
        // be restored on the next onStartInput. Resetting `mode` here was
        // the bug that caused "screen lock changes the keyboard back to
        // English".
        commitComposingLatinAsIs()
        currentEditorInfo = null
        privacyMode = InputPrivacyMode.NORMAL
        keyboardView?.setPrivacyMode(privacyMode)
    }

    // -- Key dispatch -----------------------------------------------------------

    private fun handleAction(action: KeyAction) {
        val ic = currentInputConnection ?: return

        when (action) {
            is KeyAction.Character -> handleCharacter(ic, action.text)
            KeyAction.Space -> handleSpaceOrEnter(ic, terminator = " ", isEnter = false)
            KeyAction.Backspace -> handleBackspace(ic)
            KeyAction.Enter -> handleSpaceOrEnter(ic, terminator = "\n", isEnter = true)

            KeyAction.Shift -> {
                mode = KeyboardModeTransitions.onShiftTap(mode)
                keyboardView?.setMode(mode)
            }

            KeyAction.ToggleLanguage -> handleLanguageToggle()

            KeyAction.ToggleSymbols -> {
                commitComposingLatinAsIs() // never carry composition into symbol layer
                mode = KeyboardModeTransitions.onSymbolsToggle(mode)
                keyboardView?.setMode(mode)
            }

            KeyAction.SymbolsPageFlip -> {
                mode = KeyboardModeTransitions.onSymbolsPageFlip(mode)
                keyboardView?.setMode(mode)
            }

            KeyAction.ShowImePicker -> {
                commitComposingLatinAsIs()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        }
    }

    // -- Character handling -----------------------------------------------------

    private fun handleCharacter(ic: InputConnection, text: String) {
        if (mode == KeyboardMode.BENGALI_BANGLISH && privacyMode != InputPrivacyMode.SENSITIVE) {
            composing += text
            refreshComposition(ic)
            return
        }
        val out = if (mode.isShifted) text.uppercase() else text
        ic.commitText(out, 1)
        val next = KeyboardModeTransitions.afterCharCommit(mode)
        if (next != mode) {
            mode = next
            keyboardView?.setMode(mode)
        }
    }

    private fun refreshComposition(ic: InputConnection) {
        if (composing.isEmpty()) {
            ic.finishComposingText()
            keyboardView?.setCandidates(emptyList())
            return
        }
        val cands = engine.transliterate(composing, maxCandidates = 5)
        val top = cands.firstOrNull()?.bengali ?: composing
        ic.setComposingText(top, 1)
        keyboardView?.setCandidates(cands)
    }

    // -- Backspace --------------------------------------------------------------

    private fun handleBackspace(ic: InputConnection) {
        if (mode == KeyboardMode.BENGALI_BANGLISH && composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            refreshComposition(ic)
            return
        }
        if (!ic.deleteSurroundingText(1, 0)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    // -- Space / Enter (commit top candidate) -----------------------------------

    private fun handleSpaceOrEnter(ic: InputConnection, terminator: String, isEnter: Boolean) {
        if (mode == KeyboardMode.BENGALI_BANGLISH && composing.isNotEmpty()) {
            val cands = engine.transliterate(composing, maxCandidates = 5)
            val topBengali = cands.firstOrNull()?.bengali ?: composing
            engine.onUserSelection(composing, topBengali)
            ic.commitText(topBengali + terminator, 1)
            composing = ""
            keyboardView?.setCandidates(emptyList())
            return
        }

        if (isEnter) {
            handleEditorEnter(ic)
        } else {
            ic.commitText(" ", 1)
        }
    }

    /**
     * Enter with no composing buffer: honour the host's requested action or
     * insert a newline. Never presses host-app "Send" implicitly.
     */
    private fun handleEditorEnter(ic: InputConnection) {
        val info = currentEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val hasFlagNone = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val actionable = !hasFlagNone && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            action != EditorInfo.IME_ACTION_NONE
        if (actionable) ic.performEditorAction(action) else ic.commitText("\n", 1)
    }

    // -- Candidate strip callback ----------------------------------------------

    private fun commitSpecificCandidate(candidate: Candidate) {
        val ic = currentInputConnection ?: return
        // Remember the Latin-to-Bengali choice for future ranking BEFORE we
        // clear the composing buffer.
        engine.onUserSelection(composing, candidate.bengali)
        ic.commitText(candidate.bengali, 1)
        composing = ""
        keyboardView?.setCandidates(emptyList())
    }

    // -- Language toggle --------------------------------------------------------

    private fun handleLanguageToggle() {
        // Preserve any in-flight Banglish typing when switching away by
        // committing the raw Latin — safer than dropping the user's input.
        commitComposingLatinAsIs()

        // Refuse to enter Banglish mode in sensitive fields.
        val proposedNext = KeyboardModeTransitions.onLanguageToggle(mode)
        val safeNext =
            if (proposedNext == KeyboardMode.BENGALI_BANGLISH &&
                privacyMode == InputPrivacyMode.SENSITIVE
            ) {
                KeyboardMode.ENGLISH_LOWER
            } else {
                proposedNext
            }

        mode = safeNext
        keyboardView?.setMode(mode)

        // Persist the user's explicit language choice so it survives
        // screen lock, process death, and focus changes. Only persist
        // when we actually landed on a language mode (not a symbols
        // layer or transient state) — LanguagePreference filters this.
        languagePref.saveLanguage(mode)
    }

    // -- Composition safety helpers --------------------------------------------

    /**
     * Commit whatever raw Latin is in [composing] to the field without any
     * transliteration and reset state. Used whenever the user is leaving the
     * Banglish context in a way that isn't a deliberate candidate choice
     * (e.g. switching to symbols, opening the IME picker, field loses focus).
     */
    private fun commitComposingLatinAsIs() {
        if (composing.isEmpty()) return
        val ic = currentInputConnection
        if (ic != null) {
            // finishComposingText() accepts whatever is currently in the
            // composing region. Since we've been showing the *Bengali* top
            // candidate as composing text, we replace it with the raw Latin
            // first so the accepted text matches what the user typed.
            ic.commitText(composing, 1)
        }
        composing = ""
        keyboardView?.setCandidates(emptyList())
    }

    private fun switchToEnglishClearingComposition() {
        commitComposingLatinAsIs()
        mode = KeyboardMode.ENGLISH_LOWER
        keyboardView?.setMode(mode)
    }
}
