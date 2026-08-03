package com.shohojakkhor.keyboard.ime.state

/**
 * Which keyboard surface is currently shown, and what layer of it.
 *
 * Kept intentionally small. Voice-composition state (Listening,
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

    /** Number and symbol layer, page 1 (reached from English). */
    SYMBOLS_PAGE1,

    /** Number and symbol layer, page 2 (reached from English). */
    SYMBOLS_PAGE2,

    /** Bengali via Banglish phonetic transliteration, lowercase Latin keys.
     *  The composing buffer is transliterated to Bengali as the user types. */
    BENGALI_BANGLISH,

    /** Bengali Banglish, shifted (uppercase Latin keys). Avro-style: uppercase
     *  retroflex / long-vowel forms — `T`→ট, `D`→ড, `N`→ণ, `R`→ড়, `Sh`→ষ,
     *  `A`→আ, `I`→ঈ, `U`→ঊ, `E`→এ, `O`→ও. One-shot (decays to lower after a
     *  char), matching Avro's typical use. */
    BENGALI_BANGLISH_UPPER,

    /** Bengali Banglish, shift latched (caps). Lets the user type several
     *  retroflex letters in a row without re-shifting. */
    BENGALI_BANGLISH_CAPS,

    /** Bengali number and symbol layer, page 1 (reached from Bangla). Has
     *  Bengali digits ০-৯ and Bengali punctuation । ॥ ৎ. */
    SYMBOLS_PAGE1_BN,

    /** Bengali number and symbol layer, page 2 (reached from Bangla). */
    SYMBOLS_PAGE2_BN,
    ;

    public val isEnglish: Boolean
        get() = this == ENGLISH_LOWER || this == ENGLISH_UPPER || this == ENGLISH_CAPS

    /** Any of the three Bengali Banglish letter modes (lower / upper / caps). */
    public val isBengaliBanglish: Boolean
        get() = this == BENGALI_BANGLISH ||
            this == BENGALI_BANGLISH_UPPER ||
            this == BENGALI_BANGLISH_CAPS

    /** English symbols pages (SYMBOLS_PAGE1 / PAGE2). */
    public val isEnglishSymbols: Boolean
        get() = this == SYMBOLS_PAGE1 || this == SYMBOLS_PAGE2

    /** Bengali symbols pages (SYMBOLS_PAGE1_BN / PAGE2_BN). */
    public val isBengaliSymbols: Boolean
        get() = this == SYMBOLS_PAGE1_BN || this == SYMBOLS_PAGE2_BN

    public val isSymbols: Boolean
        get() = isEnglishSymbols || isBengaliSymbols

    /** Shifted letters (English upper/caps OR Bangla upper/caps). Used by the
     *  layout builders to render uppercase Latin keycaps. */
    public val isShifted: Boolean
        get() = this == ENGLISH_UPPER || this == ENGLISH_CAPS ||
            this == BENGALI_BANGLISH_UPPER || this == BENGALI_BANGLISH_CAPS
}

/**
 * Pure transitions on [KeyboardMode]. Isolated here so they are trivially unit-testable
 * without touching Android framework classes.
 */
public object KeyboardModeTransitions {

    /**
     * User tapped the shift key. Cycles: LOWER → UPPER → CAPS → LOWER, for
     * both English and Bengali Banglish. Symbols pages are returned
     * unchanged (shift has no meaning in the symbols layer).
     */
    public fun onShiftTap(current: KeyboardMode): KeyboardMode = when (current) {
        KeyboardMode.ENGLISH_LOWER -> KeyboardMode.ENGLISH_UPPER
        KeyboardMode.ENGLISH_UPPER -> KeyboardMode.ENGLISH_CAPS
        KeyboardMode.ENGLISH_CAPS -> KeyboardMode.ENGLISH_LOWER
        KeyboardMode.BENGALI_BANGLISH -> KeyboardMode.BENGALI_BANGLISH_UPPER
        KeyboardMode.BENGALI_BANGLISH_UPPER -> KeyboardMode.BENGALI_BANGLISH_CAPS
        KeyboardMode.BENGALI_BANGLISH_CAPS -> KeyboardMode.BENGALI_BANGLISH
        else -> current
    }

    /**
     * After a shifted letter is committed, one-shot shift decays back to
     * lowercase. Caps lock stays on. Symbols are unaffected.
     */
    public fun afterCharCommit(current: KeyboardMode): KeyboardMode = when (current) {
        KeyboardMode.ENGLISH_UPPER -> KeyboardMode.ENGLISH_LOWER
        KeyboardMode.BENGALI_BANGLISH_UPPER -> KeyboardMode.BENGALI_BANGLISH
        else -> current
    }

    /**
     * User tapped the language toggle (English ↔ Bengali). Always returns to
     * the other language's lowercase letter mode, regardless of which layer
     * (shift, symbols, letters) the user was in — language toggle is a
     * "switch language, start fresh" action, not a "preserve shift" one.
     */
    public fun onLanguageToggle(current: KeyboardMode): KeyboardMode = when {
        current.isBengaliBanglish || current.isBengaliSymbols -> KeyboardMode.ENGLISH_LOWER
        else -> KeyboardMode.BENGALI_BANGLISH
    }

    /**
     * User tapped the "?123" / back-to-letters key toggling between letters
     * and the matching-language symbols layer:
     *  - English letters ↔ English symbols (SYMBOLS_PAGE1 / PAGE2).
     *  - Bangla letters  ↔ Bengali symbols (SYMBOLS_PAGE1_BN / PAGE2_BN).
     * Returning from symbols always lands on the lowercase letter mode of
     * the same language (shift does not persist across a symbols detour).
     */
    public fun onSymbolsToggle(current: KeyboardMode): KeyboardMode = when {
        current.isEnglish || current.isEnglishSymbols ->
            if (current.isSymbols) KeyboardMode.ENGLISH_LOWER else KeyboardMode.SYMBOLS_PAGE1
        current.isBengaliBanglish || current.isBengaliSymbols ->
            if (current.isSymbols) KeyboardMode.BENGALI_BANGLISH else KeyboardMode.SYMBOLS_PAGE1_BN
        else -> current
    }

    /** Within a symbols layer, flip pages. No-op elsewhere. */
    public fun onSymbolsPageFlip(current: KeyboardMode): KeyboardMode = when (current) {
        KeyboardMode.SYMBOLS_PAGE1 -> KeyboardMode.SYMBOLS_PAGE2
        KeyboardMode.SYMBOLS_PAGE2 -> KeyboardMode.SYMBOLS_PAGE1
        KeyboardMode.SYMBOLS_PAGE1_BN -> KeyboardMode.SYMBOLS_PAGE2_BN
        KeyboardMode.SYMBOLS_PAGE2_BN -> KeyboardMode.SYMBOLS_PAGE1_BN
        else -> current
    }
}
