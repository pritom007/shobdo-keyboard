package com.shohojakkhor.keyboard.ime

import android.content.Context
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import com.shohojakkhor.keyboard.ime.layout.KeyAction
import com.shohojakkhor.keyboard.ime.handwriting.GoogleBanglaHandwritingRecognizer
import com.shohojakkhor.keyboard.ime.handwriting.HandwritingModelState
import com.shohojakkhor.keyboard.ime.handwriting.InkStroke
import com.shohojakkhor.keyboard.ime.privacy.DefaultInputPrivacyPolicy
import com.shohojakkhor.keyboard.ime.privacy.InputPrivacyMode
import com.shohojakkhor.keyboard.ime.privacy.InputPrivacyPolicy
import com.shohojakkhor.keyboard.ime.state.KeyboardMode
import com.shohojakkhor.keyboard.ime.state.KeyboardModeTransitions
import com.shohojakkhor.keyboard.ime.state.LanguagePreference
import com.shohojakkhor.keyboard.ime.state.PersistentSelectionMemory
import com.shohojakkhor.keyboard.ime.state.PersonalDictionaryStore
import com.shohojakkhor.keyboard.ime.state.SuggestionPreferences
import com.shohojakkhor.keyboard.ime.voice.HybridResult
import com.shohojakkhor.keyboard.ime.voice.HybridSpeechRecognizer
import com.shohojakkhor.keyboard.ime.voice.VoiceController
import com.shohojakkhor.keyboard.ime.voice.VoiceStrings
import com.shohojakkhor.keyboard.ime.view.KeyboardView
import com.shohojakkhor.keyboard.speech.DeviceIdProvider
import com.shohojakkhor.keyboard.speech.RemoteSpeechRepository
import com.shohojakkhor.keyboard.speech.SpeechConfig
import com.shohojakkhor.keyboard.speech.ondevice.OnDeviceSpeechRecognizer
import com.shohojakkhor.keyboard.translit.AvroLikeEngine
import com.shohojakkhor.keyboard.translit.Candidate
import com.shohojakkhor.keyboard.translit.CandidateKind
import com.shohojakkhor.keyboard.translit.CandidateReplacement
import com.shohojakkhor.keyboard.translit.CompositeBengaliDictionary
import com.shohojakkhor.keyboard.translit.SeedBengaliDictionary
import com.shohojakkhor.keyboard.translit.TransliterationEngine
import com.shohojakkhor.keyboard.translit.SuggestionRequest
import com.shohojakkhor.keyboard.voice.capture.AudioRecordAudioSource
import com.shohojakkhor.keyboard.voice.capture.AudioRecorder
import com.shohojakkhor.keyboard.voice.capture.MicPermission
import com.shohojakkhor.keyboard.voice.capture.RecorderListener
import com.shohojakkhor.keyboard.voice.capture.Recording

/**
 * The Shohojakkhor Keyboard IME service.
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
public class ShohojakkhorInputMethodService : InputMethodService() {

    private val privacyPolicy: InputPrivacyPolicy = DefaultInputPrivacyPolicy()
    private val engine: TransliterationEngine by lazy {
        AvroLikeEngine(
            dictionary = CompositeBengaliDictionary(
                PersonalDictionaryStore(this),
                SeedBengaliDictionary(),
            ),
            memory = PersistentSelectionMemory(this),
        )
    }

    /**
     * Persistent store of the user's last chosen language. Lazily created
     * because we need a Context, which is only usable after the service
     * has been attached to Android.
     */
    private val languagePref: LanguagePreference by lazy { LanguagePreference(this) }
    private val suggestionPrefs: SuggestionPreferences by lazy { SuggestionPreferences(this) }

    private var keyboardView: KeyboardView? = null
    private var mode: KeyboardMode = KeyboardMode.ENGLISH_LOWER
    private var privacyMode: InputPrivacyMode = InputPrivacyMode.NORMAL
    private var currentEditorInfo: EditorInfo? = null

    /** Latin composing buffer, non-empty only when [mode] is BENGALI_BANGLISH. */
    private var composing: String = ""
    private val recentLatinWords: ArrayDeque<String> = ArrayDeque()
    private var lastCommittedWord: LastCommittedWord? = null

    // -- Voice (M3B + session 3 hybrid) ----------------------------------------
    //
    // Hybrid speech recognition: the server (Groq Whisper large-v3) is the
    // primary path when online — best Bengali quality and auto-detects
    // Bengali/English. The on-device Whisper base is the offline fallback
    // (less accurate, Bengali-only) used when the server is unreachable.
    // The mic key is hidden in sensitive fields.
    private val voiceController = VoiceController()
    private val voiceRecorder = AudioRecorder(sourceProvider = { AudioRecordAudioSource() })
    private var voiceRecognizer: OnDeviceSpeechRecognizer? = null
    private var hybridRecognizer: HybridSpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var voiceTickSecond = -1L
    private var handwritingRecognizer: GoogleBanglaHandwritingRecognizer? = null
    private var handwritingRequestId: Long = 0

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
        // Wire the voice panel's stop/cancel/retry buttons to the controller.
        view.onVoiceStop = { voiceController.stop() }
        view.onVoiceCancel = { voiceController.cancel() }
        view.onVoiceRetry = { voiceController.reset(); voiceController.start() }
        view.onHandwritingInk = ::recognizeHandwriting
        view.onHandwritingChanged = { handwritingRequestId++ }
        view.onHandwritingCandidate = ::commitHandwritingCandidate
        view.onHandwritingBackspace = {
            currentInputConnection?.let(::handleBackspace)
        }
        view.onHandwritingSpace = {
            currentInputConnection?.commitText(" ", 1)
        }
        view.onHandwritingClose = { view.hideHandwritingPanel() }
        keyboardView = view
        wireVoiceController()
        return view
    }

    // -- Voice (M3B) ------------------------------------------------------------

    /**
     * Connect the [VoiceController] state machine to the panel UI, the
     * recorder, and the recognizer. Called once per input view creation.
     */
    private fun wireVoiceController() {
        voiceController.onStateChanged = { state ->
            mainHandler.post { renderVoiceState(state) }
        }
        voiceController.onTranscript = { text ->
            mainHandler.post { commitTranscript(text) }
        }
        voiceRecorder.setListener(RecorderListenerImpl())
    }

    private fun renderVoiceState(state: VoiceController.State) {
        val view = keyboardView ?: return
        when (state) {
            VoiceController.State.IDLE -> {
                view.hideVoicePanel()
                if (voiceRecorder.isRecording()) voiceRecorder.cancel()
            }
            VoiceController.State.LISTENING -> {
                view.showVoicePanel()
                voiceTickSecond = -1L
                voiceRecorder.start()
            }
            VoiceController.State.PROCESSING -> {
                view.showVoiceProcessing()
                if (voiceRecorder.isRecording()) voiceRecorder.stop()
            }
            VoiceController.State.ERROR -> view.showVoiceError(VoiceStrings.RECOGNITION_ERROR)
        }
    }

    private fun commitTranscript(text: String) {
        val ic = currentInputConnection ?: return
        // Commit any pending Banglish composition first so voice text
        // appends cleanly after it rather than replacing it.
        commitComposingLatinAsIs()
        ic.commitText(text, 1)
    }

    private fun handleVoice() {
        // Never allow voice in sensitive fields (password / PIN / OTP / card).
        if (privacyMode == InputPrivacyMode.SENSITIVE) return

        // Check mic permission. If denied, show the error panel prompting the
        // user to grant it; the panel's retry button lets them try again after
        // granting via system settings.
        if (!MicPermission.isGranted(this)) {
            keyboardView?.showVoiceError(VoiceStrings.MIC_PERMISSION_NEEDED)
            return
        }

        // Lazily construct the recognizers on first use so the on-device
        // model loads (~3-4s for base) only when voice is actually invoked,
        // not at IME startup.
        ensureVoiceRecognizer()

        voiceController.start()
    }

    /**
     * Build the on-device recognizer (offline fallback) and the hybrid
     * recognizer (online-first, offline-fallback) on first voice use. Safe
     * to call multiple times; a no-op after the first successful call.
     */
    private fun ensureVoiceRecognizer() {
        if (hybridRecognizer != null) return
        if (voiceRecognizer == null) {
            voiceRecognizer = OnDeviceSpeechRecognizer(this)
        }
        val remote = RemoteSpeechRepository(
            config = SpeechConfig(),
            deviceId = DeviceIdProvider.get(this),
        )
        hybridRecognizer = HybridSpeechRecognizer(
            remote = remote,
            onDeviceTranscribe = { wav, sr -> voiceRecognizer?.transcribe(wav, sr) ?: "" },
        )
    }

    /**
     * Recorder callback. Runs on the recorder's background thread. Drives
     * the panel timer (throttled to 1s) and, on completion, runs the
     * recognizer on a background thread.
     */
    private inner class RecorderListenerImpl : RecorderListener {
        override fun onStateChanged(state: com.shohojakkhor.keyboard.voice.capture.RecorderState) {}

        override fun onTick(elapsedMs: Long) {
            val second = elapsedMs / 1000L
            if (second != voiceTickSecond) {
                voiceTickSecond = second
                mainHandler.post { keyboardView?.setVoiceTimerMs(elapsedMs) }
            }
        }

        override fun onComplete(recording: Recording) {
            voiceTickSecond = -1L
            // Run the hybrid recognizer on a background thread. It tries the
            // server (Groq large-v3) first; on any remote failure it calls
            // onFallback so we can show the offline message, then runs the
            // on-device Whisper base. The result is committed on the main
            // thread via the controller.
            Thread {
                try {
                    val result = hybridRecognizer?.transcribe(
                        wavBytes = recording.wavBytes,
                        sampleRate = recording.sampleRate,
                        language = "",
                        onFallback = {
                            mainHandler.post {
                                keyboardView?.showVoiceProcessing(VoiceStrings.OFFLINE_PROCESSING)
                            }
                        },
                    )
                    mainHandler.post {
                        when (result) {
                            is HybridResult.Online ->
                                voiceController.onRecognitionResult(result.text)
                            is HybridResult.OfflineFallback ->
                                voiceController.onRecognitionResult(result.text)
                            HybridResult.Failed -> voiceController.onError()
                            null -> voiceController.onError()
                        }
                    }
                } catch (_: Exception) {
                    mainHandler.post { voiceController.onError() }
                }
            }.start()
        }

        override fun onCancelled() {
            voiceTickSecond = -1L
        }

        override fun onError(code: String) {
            voiceTickSecond = -1L
            mainHandler.post { voiceController.onError() }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentEditorInfo = attribute
        privacyMode = privacyPolicy.classify(attribute)
        recentLatinWords.clear()
        lastCommittedWord = null

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
        if (privacyMode == InputPrivacyMode.SENSITIVE && mode.isBengaliBanglish) {
            mode = KeyboardMode.ENGLISH_LOWER
            composing = ""
        }
        keyboardView?.setMode(mode)
        keyboardView?.setPrivacyMode(privacyMode)
        keyboardView?.hideHandwritingPanel()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // Cancel any in-flight voice recording when the field goes away so the
        // mic is never left on after the user moves focus.
        if (voiceController.state != VoiceController.State.IDLE) {
            voiceController.cancel()
        }
        commitComposingLatinAsIs()
        currentEditorInfo = null
        recentLatinWords.clear()
        lastCommittedWord = null
        privacyMode = InputPrivacyMode.NORMAL
        keyboardView?.setPrivacyMode(privacyMode)
        keyboardView?.hideHandwritingPanel()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceRecognizer?.release()
        voiceRecognizer = null
        handwritingRecognizer?.close()
        handwritingRecognizer = null
    }

    // -- Key dispatch -----------------------------------------------------------

    private fun handleAction(action: KeyAction) {
        val ic = currentInputConnection ?: return

        when (action) {
            is KeyAction.Character -> handleCharacter(ic, action.text)
            is KeyAction.DirectBengali -> handleDirectBengali(ic, action.text)
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

            KeyAction.Voice -> handleVoice()
            KeyAction.Handwriting -> handleHandwriting()
        }
    }

    private fun handleHandwriting() {
        if (privacyMode == InputPrivacyMode.SENSITIVE || !mode.isBengaliBanglish) return
        commitComposingLatinAsIs()
        val view = keyboardView ?: return
        view.showHandwritingPanel()
        val recognizer = handwritingRecognizer ?: GoogleBanglaHandwritingRecognizer().also {
            handwritingRecognizer = it
        }
        recognizer.ensureModel { state ->
            mainHandler.post { keyboardView?.setHandwritingModelState(state) }
        }
    }

    private fun recognizeHandwriting(strokes: List<InkStroke>) {
        val recognizer = handwritingRecognizer ?: return
        val requestId = ++handwritingRequestId
        recognizer.recognize(strokes) { result ->
            mainHandler.post {
                if (requestId != handwritingRequestId) return@post
                result.onSuccess {
                    if (it.isEmpty()) keyboardView?.setHandwritingError()
                    else keyboardView?.setHandwritingCandidates(it)
                }
                    .onFailure { keyboardView?.setHandwritingError() }
            }
        }
    }

    private fun commitHandwritingCandidate(text: String) {
        if (text.isBlank()) return
        currentInputConnection?.commitText(text, 1)
        lastCommittedWord = null
        keyboardView?.setCandidates(emptyList())
    }

    // -- Character handling -----------------------------------------------------

    private fun handleCharacter(ic: InputConnection, text: String) {
        if (mode.isBengaliBanglish && privacyMode != InputPrivacyMode.SENSITIVE) {
            lastCommittedWord = null
            composing += text
            refreshComposition(ic)
            // One-shot shift decays after each char (caps stays). Matches
            // Avro: shift-tap T → ট, then shift auto-releases so the next
            // letter is lowercase again.
            val next = KeyboardModeTransitions.afterCharCommit(mode)
            if (next != mode) {
                mode = next
                keyboardView?.setMode(mode)
            }
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

    private fun handleDirectBengali(ic: InputConnection, text: String) {
        // Direct Bengali characters and an active Latin composition must never
        // overlap: finish the existing word first, then insert exactly what the
        // user selected from the popup.
        if (composing.isNotEmpty()) {
            val candidates = suggestionsFor(composing)
            val committed = candidates.firstOrNull()?.bengali ?: composing
            rememberSelection(composing, committed)
            rememberLatinWord(composing)
            ic.commitText(committed, 1)
            composing = ""
        }
        lastCommittedWord = null
        keyboardView?.setCandidates(emptyList())
        ic.commitText(text, 1)

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
        val cands = suggestionsFor(composing)
        val top = cands.firstOrNull()?.bengali ?: composing
        ic.setComposingText(top, 1)
        keyboardView?.setCandidates(cands)
    }

    // -- Backspace --------------------------------------------------------------

    private fun handleBackspace(ic: InputConnection) {
        // Selection takes priority over an in-memory Banglish composition. The
        // user explicitly selected the host field's text, so delete that range
        // and discard any stale composition state first.
        BackspaceHandler.deleteSelection(ic)?.let { deleted ->
            composing = ""
            lastCommittedWord = null
            keyboardView?.setCandidates(emptyList())
            if (!deleted) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }

        if (mode.isBengaliBanglish && composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            refreshComposition(ic)
            return
        }
        lastCommittedWord = null
        keyboardView?.setCandidates(emptyList())
        if (!BackspaceHandler.deleteBeforeCursor(ic)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    // -- Space / Enter (commit top candidate) -----------------------------------

    private fun handleSpaceOrEnter(ic: InputConnection, terminator: String, isEnter: Boolean) {
        if (mode.isBengaliBanglish && composing.isNotEmpty()) {
            val cands = suggestionsFor(composing)
            val topBengali = cands.firstOrNull()?.bengali ?: composing
            val committedLatin = composing
            rememberSelection(committedLatin, topBengali)
            rememberLatinWord(committedLatin)
            ic.commitText(topBengali + terminator, 1)
            composing = ""
            lastCommittedWord = LastCommittedWord(
                latin = committedLatin,
                bengali = topBengali,
                terminator = terminator,
                alternatives = cands.filter { it.bengali != topBengali },
            )
            showPostCommitSuggestions()
            return
        }

        if (isEnter) {
            lastCommittedWord = null
            keyboardView?.setCandidates(emptyList())
            handleEditorEnter(ic)
        } else {
            lastCommittedWord = null
            keyboardView?.setCandidates(emptyList())
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
        when (candidate.replacement) {
            CandidateReplacement.PREVIOUS_WORD -> {
                val previous = lastCommittedWord ?: return
                ic.deleteSurroundingText(previous.bengali.length + previous.terminator.length, 0)
                ic.commitText(candidate.bengali + previous.terminator, 1)
                lastCommittedWord = previous.copy(bengali = candidate.bengali)
                rememberSelection(previous.latin, candidate.bengali)
                showPostCommitSuggestions()
                return
            }
            CandidateReplacement.INSERT -> {
                ic.commitText(candidate.bengali + " ", 1)
                lastCommittedWord = null
                keyboardView?.setCandidates(emptyList())
                return
            }
            CandidateReplacement.COMPOSING -> Unit
        }
        // Remember the Latin-to-Bengali choice for future ranking BEFORE we
        // clear the composing buffer.
        rememberSelection(composing, candidate.bengali)
        rememberLatinWord(composing)
        ic.commitText(candidate.bengali, 1)
        composing = ""
        keyboardView?.setCandidates(emptyList())
    }

    private fun showPostCommitSuggestions() {
        val previous = lastCommittedWord ?: return
        val corrections = previous.alternatives
            .filter { it.kind == CandidateKind.WORD || it.kind == CandidateKind.PHRASE }
            .take(2)
            .map { it.copy(replacement = CandidateReplacement.PREVIOUS_WORD) }
        val completions = engine.suggest(
            SuggestionRequest(
                latinInput = "",
                previousWords = recentLatinWords.toList(),
                maxCandidates = 3,
                includeEmoji = suggestionPrefs.emojiSuggestionsEnabled,
                preferStandardBangla = suggestionPrefs.preferStandardBangla,
                enableNoisyMatching = suggestionPrefs.noisySuggestionsEnabled,
            ),
        )
        keyboardView?.setCandidates((corrections + completions).distinctBy { it.bengali }.take(6))
    }

    private fun suggestionsFor(latin: String): List<Candidate> = engine.suggest(
        SuggestionRequest(
            latinInput = latin,
            previousWords = recentLatinWords.toList(),
            maxCandidates = 6,
            includeEmoji = suggestionPrefs.emojiSuggestionsEnabled,
            preferStandardBangla = suggestionPrefs.preferStandardBangla,
            enableNoisyMatching = suggestionPrefs.noisySuggestionsEnabled,
        ),
    )

    private fun rememberSelection(latin: String, bengali: String) {
        if (privacyMode == InputPrivacyMode.NORMAL) {
            engine.onUserSelection(latin, bengali)
        }
    }

    private fun rememberLatinWord(latin: String) {
        if (privacyMode != InputPrivacyMode.NORMAL) return
        recentLatinWords.addLast(latin.trim().lowercase())
        while (recentLatinWords.size > 2) recentLatinWords.removeFirst()
    }

    // -- Language toggle --------------------------------------------------------

    private fun handleLanguageToggle() {
        // Preserve any in-flight Banglish typing when switching away by
        // committing the raw Latin — safer than dropping the user's input.
        commitComposingLatinAsIs()

        // Refuse to enter Banglish mode in sensitive fields.
        val proposedNext = KeyboardModeTransitions.onLanguageToggle(mode)
        val safeNext =
            if (proposedNext.isBengaliBanglish &&
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

    private data class LastCommittedWord(
        val latin: String,
        val bengali: String,
        val terminator: String,
        val alternatives: List<Candidate>,
    )
}
