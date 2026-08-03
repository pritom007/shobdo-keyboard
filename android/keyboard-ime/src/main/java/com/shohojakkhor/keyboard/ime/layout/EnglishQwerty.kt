package com.shohojakkhor.keyboard.ime.layout

import com.shohojakkhor.keyboard.ime.state.KeyboardMode

/**
 * Standard QWERTY letter layout. Used both for [KeyboardMode.ENGLISH_LOWER]
 * / UPPER / CAPS and for [KeyboardMode.BENGALI_BANGLISH] (the Banglish
 * transliteration mode uses the same Latin key layout — only the language
 * toggle label differs).
 */
public object EnglishQwerty {

    /**
     * Layout for a given English mode. Returns `null` for non-English modes;
     * see [BengaliBanglish.layoutFor] for the Bengali variant.
     */
    public fun layoutFor(mode: KeyboardMode): KeyboardLayout? {
        if (!mode.isEnglish) return null
        return build(shifted = mode.isShifted, langToggleLabel = LABEL_TO_BENGALI)
    }

    /**
     * Internal helper shared with [BengaliBanglish]. Callers control the
     * caption of the language toggle key so it reads e.g. "বাং" when in
     * English mode and "En" when in Banglish mode.
     */
    internal fun build(shifted: Boolean, langToggleLabel: String): KeyboardLayout {
        val letters: List<String> = if (shifted) UPPER_LETTERS else LOWER_LETTERS
        val row1 = KeyRow(letters.subList(0, 10).map { it.asCharKey() })
        val row2Padding = 0.5f
        val row2Keys = letters.subList(10, 19).map { it.asCharKey() }
        val row2 = KeyRow(
            listOf(
                Key(row2Keys.first().label, KeyAction.Character(row2Keys.first().label), widthWeight = 1f + row2Padding),
            ) + row2Keys.drop(1).dropLast(1) + listOf(
                Key(row2Keys.last().label, KeyAction.Character(row2Keys.last().label), widthWeight = 1f + row2Padding),
            ),
        )
        val row3 = KeyRow(
            listOf(Key(SHIFT_LABEL, KeyAction.Shift, widthWeight = 1.5f))
                + letters.subList(19, 26).map { it.asCharKey() }
                + listOf(Key(BACKSPACE_LABEL, KeyAction.Backspace, widthWeight = 1.5f)),
        )
        val row4 = KeyRow(
            listOf(
                Key(SYMBOLS_LABEL, KeyAction.ToggleSymbols, widthWeight = 1.5f),
                Key(langToggleLabel, KeyAction.ToggleLanguage, widthWeight = 1.2f),
                Key(GLOBE_LABEL, KeyAction.ShowImePicker, widthWeight = 1.0f),
                Key(MIC_LABEL, KeyAction.Voice, widthWeight = 1.0f),
                Key(SPACE_LABEL, KeyAction.Space, widthWeight = 3.6f),
                Key(".", KeyAction.Character("."), widthWeight = 1.0f),
                Key(ENTER_LABEL, KeyAction.Enter, widthWeight = 1.8f),
            ),
        )

        return KeyboardLayout(
            id = "en_qwerty_${if (shifted) "upper" else "lower"}",
            rows = listOf(row1, row2, row3, row4),
        )
    }

    // -- Layout data ------------------------------------------------------------

    private val LOWER_LETTERS: List<String> = listOf(
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "z", "x", "c", "v", "b", "n", "m",
    )

    private val UPPER_LETTERS: List<String> = LOWER_LETTERS.map { it.uppercase() }

    // -- Special-key labels -----------------------------------------------------

    internal const val SHIFT_LABEL = "⇧"
    internal const val BACKSPACE_LABEL = "⌫"
    internal const val SYMBOLS_LABEL = "?123"
    internal const val LABEL_TO_BENGALI = "বাং"
    internal const val LABEL_TO_ENGLISH = "En"
    internal const val GLOBE_LABEL = "\uD83C\uDF10" // 🌐
    internal const val MIC_LABEL = "\uD83C\uDF99" // 🎙️
    internal const val SPACE_LABEL = " "
    internal const val ENTER_LABEL = "↵"

    // Backward compatibility with existing tests. Deprecated alias.
    internal const val LANG_LABEL = LABEL_TO_BENGALI

    private fun String.asCharKey(): Key = Key(this, KeyAction.Character(this))
}
