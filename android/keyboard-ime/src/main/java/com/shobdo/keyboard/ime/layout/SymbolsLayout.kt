package com.shobdo.keyboard.ime.layout

import com.shobdo.keyboard.ime.state.KeyboardMode

/**
 * Two-page symbols / number layout. Page 1 is numbers + common punctuation;
 * page 2 is less-common symbols. Layouts are intentionally sparse — elderly
 * users get lost in dense symbol grids.
 *
 * There are two parallel sets of pages:
 *  - **English** ([KeyboardMode.SYMBOLS_PAGE1] / [KeyboardMode.SYMBOLS_PAGE2]):
 *    ASCII digits 0-9 and Latin punctuation. Reached from English letter
 *    modes; the bottom-row back-to-letters key reads "ABC" and the language
 *    toggle reads "বাং".
 *  - **Bengali** ([KeyboardMode.SYMBOLS_PAGE1_BN] / [KeyboardMode.SYMBOLS_PAGE2_BN]):
 *    Bengali digits ০-৯ and Bengali punctuation । ॥ ৎ plus the common
 *    ASCII punctuation that's still useful in Bengali text. Reached from
 *    Bangla letter modes; the back-to-letters key reads "অ" and the
 *    language toggle reads "En".
 */
public object SymbolsLayout {

    public fun layoutFor(mode: KeyboardMode): KeyboardLayout? = when (mode) {
        KeyboardMode.SYMBOLS_PAGE1 -> page1()
        KeyboardMode.SYMBOLS_PAGE2 -> page2()
        KeyboardMode.SYMBOLS_PAGE1_BN -> page1Bn()
        KeyboardMode.SYMBOLS_PAGE2_BN -> page2Bn()
        else -> null
    }

    // -- English symbols -------------------------------------------------------

    private fun page1(): KeyboardLayout {
        val row1 = "1234567890".map { it.toString().asCharKey() }
        val row2 = listOf("-", "/", ":", ";", "(", ")", "৳", "&", "@", "\"").map { it.asCharKey() }
        val row3 = listOf(
            Key("#+=", KeyAction.SymbolsPageFlip, widthWeight = 1.5f),
        ) + listOf(".", ",", "?", "!", "'", "\"").map { it.asCharKey() } + listOf(
            Key(EnglishQwerty.BACKSPACE_LABEL, KeyAction.Backspace, widthWeight = 1.5f),
        )
        val row4 = commonBottomRow(
            backToLettersLabel = BACK_TO_ENGLISH_LABEL,
            langToggleLabel = EnglishQwerty.LABEL_TO_BENGALI,
        )
        return KeyboardLayout(
            id = "symbols_page1",
            rows = listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), row4),
        )
    }

    private fun page2(): KeyboardLayout {
        val row1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { it.asCharKey() }
        val row2 = listOf("£", "€", "¥", "$", "^", "°", "=", "{", "}", "\\").map { it.asCharKey() }
        val row3 = listOf(
            Key("123", KeyAction.SymbolsPageFlip, widthWeight = 1.5f),
        ) + listOf("%", "©", "®", "™", "✓", "[", "]").map { it.asCharKey() } + listOf(
            Key(EnglishQwerty.BACKSPACE_LABEL, KeyAction.Backspace, widthWeight = 1.5f),
        )
        val row4 = commonBottomRow(
            backToLettersLabel = BACK_TO_ENGLISH_LABEL,
            langToggleLabel = EnglishQwerty.LABEL_TO_BENGALI,
        )
        return KeyboardLayout(
            id = "symbols_page2",
            rows = listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), row4),
        )
    }

    // -- Bengali symbols -------------------------------------------------------
    //
    // Page 1 leads with Bengali digits ০-৯, then the punctuation an elderly
    // Bengali writer reaches for most: । (dari), ॥ (double dari), ৎ (khanda
    // ta), plus the ASCII punctuation that's still normal in Bengali text
    // (, . ? ! - / : ;). ৳ is the Bengali taka sign.
    //
    // Page 2 mirrors the English page 2 set (rare symbols are universal) so
    // the user has one consistent "more symbols" page regardless of language.

    private fun page1Bn(): KeyboardLayout {
        val row1 = BENGALI_DIGITS.map { it.asCharKey() }
        val row2 = listOf("।", "॥", "ৎ", ",", ".", "?", "!", "-", "/", ":").map { it.asCharKey() }
        val row3 = listOf(
            Key("#+=", KeyAction.SymbolsPageFlip, widthWeight = 1.5f),
        ) + listOf(";", "(", ")", "৳", "&", "@", "'", "\"").map { it.asCharKey() } + listOf(
            Key(EnglishQwerty.BACKSPACE_LABEL, KeyAction.Backspace, widthWeight = 1.5f),
        )
        val row4 = commonBottomRow(
            backToLettersLabel = BACK_TO_BANGLA_LABEL,
            langToggleLabel = EnglishQwerty.LABEL_TO_ENGLISH,
        )
        return KeyboardLayout(
            id = "symbols_page1_bn",
            rows = listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), row4),
        )
    }

    private fun page2Bn(): KeyboardLayout {
        val row1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { it.asCharKey() }
        val row2 = listOf("£", "€", "¥", "$", "^", "°", "=", "{", "}", "\\").map { it.asCharKey() }
        val row3 = listOf(
            Key("123", KeyAction.SymbolsPageFlip, widthWeight = 1.5f),
        ) + listOf("%", "©", "®", "™", "✓", "[", "]").map { it.asCharKey() } + listOf(
            Key(EnglishQwerty.BACKSPACE_LABEL, KeyAction.Backspace, widthWeight = 1.5f),
        )
        val row4 = commonBottomRow(
            backToLettersLabel = BACK_TO_BANGLA_LABEL,
            langToggleLabel = EnglishQwerty.LABEL_TO_ENGLISH,
        )
        return KeyboardLayout(
            id = "symbols_page2_bn",
            rows = listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), row4),
        )
    }

    // -- Shared bottom row ----------------------------------------------------

    /**
     * Bottom row shared by all four symbols pages. [backToLettersLabel] is
     * "ABC" for the English pages and "অ" for the Bengali pages;
     * [langToggleLabel] is "বাং" (switch to Bengali) for the English pages
     * and "En" (switch to English) for the Bengali pages.
     */
    private fun commonBottomRow(backToLettersLabel: String, langToggleLabel: String): KeyRow =
        KeyRow(
            listOf(
                Key(backToLettersLabel, KeyAction.ToggleSymbols, widthWeight = 1.5f),
                Key(langToggleLabel, KeyAction.ToggleLanguage, widthWeight = 1.2f),
                Key(EnglishQwerty.GLOBE_LABEL, KeyAction.ShowImePicker, widthWeight = 1.0f),
                Key(EnglishQwerty.MIC_LABEL, KeyAction.Voice, widthWeight = 1.0f),
                Key(EnglishQwerty.SPACE_LABEL, KeyAction.Space, widthWeight = 3.6f),
                Key(".", KeyAction.Character("."), widthWeight = 1.0f),
                Key(EnglishQwerty.ENTER_LABEL, KeyAction.Enter, widthWeight = 1.8f),
            ),
        )

    private fun String.asCharKey(): Key = Key(this, KeyAction.Character(this))

    /** "ABC" — back to English letters from the English symbols pages. */
    private const val BACK_TO_ENGLISH_LABEL = "ABC"

    /** "অ" — back to Bengali letters from the Bengali symbols pages.
     *  Matches Gboard's Bengali symbols-back convention. */
    private const val BACK_TO_BANGLA_LABEL = "অ"

    /** Bengali digits ০ ১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯. */
    private val BENGALI_DIGITS: List<String> =
        listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯")
}
