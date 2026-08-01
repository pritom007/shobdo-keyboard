package com.shobdo.keyboard.ime.state

/**
 * Which keyboard surface is currently shown, and what layer of it.
 *
 * Kept intentionally small at M1. Voice-composition state (Listening,
 * ReviewingDraft, etc.) will land in a separate `VoiceCompositionState`
 * sealed hierarchy at M4 per docs/architecture.md.
 */
public enum class KeyboardMode {
    /** English QWERTY, lowercase. */
    ENGLISH_LOWER,

    /** English QWERTY, shifted (uppercase). */
    ENGLISH_UPPER,

    /** English QWERTY, shift latched (caps lock). */
    ENGLISH_CAPS,

    /** Number and symbol layer, page 1. */
    SYMBOLS_PAGE1,

    /** Number and symbol layer, page 2. */
    SYMBOLS_PAGE2,

    /** Bengali via Banglish phonetic transliteration. Latin key layout with a
     *  candidate strip that offers Bengali readings for the current composing
     *  buffer. See :transliteration module. */
    BENGALI_BANGLISH,
    ;

    public val isEnglish: Boolean
        get() = this == ENGLISH_LOWER || this == ENGLISH_UPPER || this == ENGLISH_CAPS

    public val isSymbols: Boolean
        get() = this == SYMBOLS_PAGE1 || this == SYMBOLS_PAGE2

    public val isShifted: Boolean
        get() = this == ENGLISH_UPPER || this == ENGLISH_CAPS
}

/**
 * Pure transitions on [KeyboardMode]. Isolated here so they are trivially unit-testable
 * without touching Android framework classes.
 */
public object KeyboardModeTransitions {

    /**
     * User tapped the shift key. Cycles: LOWER → UPPER → CAPS → LOWER.
     *
     * Non-English modes are returned unchanged (shift has no meaning there yet).
     */
    public fun onShiftTap(current: KeyboardMode): KeyboardMode = when (current) {
        KeyboardMode.ENGLISH_LOWER -> KeyboardMode.ENGLISH_UPPER
        KeyboardMode.ENGLISH_UPPER -> KeyboardMode.ENGLISH_CAPS
        KeyboardMode.ENGLISH_CAPS -> KeyboardMode.ENGLISH_LOWER
        else -> current
    }

    /**
     * After a shifted letter is committed, one-shot shift decays back to lowercase.
     * Caps lock stays on. Symbols and Bengali are unaffected.
     */
    public fun afterCharCommit(current: KeyboardMode): KeyboardMode = when (current) {
        KeyboardMode.ENGLISH_UPPER -> KeyboardMode.ENGLISH_LOWER
        else -> current
    }

    /** User tapped the language toggle (English ↔ Bengali). */
    public fun onLanguageToggle(current: KeyboardMode): KeyboardMode = when {
        current.isEnglish || current.isSymbols -> KeyboardMode.BENGALI_BANGLISH
        current == KeyboardMode.BENGALI_BANGLISH -> KeyboardMode.ENGLISH_LOWER
        else -> KeyboardMode.ENGLISH_LOWER
    }

    /** User tapped the "?123" / "ABC" key toggling between letters and symbols. */
    public fun onSymbolsToggle(current: KeyboardMode): KeyboardMode = when {
        current.isEnglish -> KeyboardMode.SYMBOLS_PAGE1
        current.isSymbols -> KeyboardMode.ENGLISH_LOWER
        else -> current
    }

    /** Within the symbols layer, flip pages. No-op elsewhere. */
    public fun onSymbolsPageFlip(current: KeyboardMode): KeyboardMode = when (current) {
        KeyboardMode.SYMBOLS_PAGE1 -> KeyboardMode.SYMBOLS_PAGE2
        KeyboardMode.SYMBOLS_PAGE2 -> KeyboardMode.SYMBOLS_PAGE1
        else -> current
    }
}
