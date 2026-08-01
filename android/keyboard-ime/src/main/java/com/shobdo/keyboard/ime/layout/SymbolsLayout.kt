package com.shobdo.keyboard.ime.layout

import com.shobdo.keyboard.ime.state.KeyboardMode

/**
 * Two-page symbols / number layout. Page 1 is numbers + common punctuation;
 * page 2 is less-common symbols. Layouts are intentionally sparse — elderly
 * users get lost in dense symbol grids.
 */
public object SymbolsLayout {

    public fun layoutFor(mode: KeyboardMode): KeyboardLayout? = when (mode) {
        KeyboardMode.SYMBOLS_PAGE1 -> page1()
        KeyboardMode.SYMBOLS_PAGE2 -> page2()
        else -> null
    }

    private fun page1(): KeyboardLayout {
        val row1 = "1234567890".map { it.toString().asCharKey() }
        val row2 = listOf("-", "/", ":", ";", "(", ")", "৳", "&", "@", "\"").map { it.asCharKey() }
        val row3 = listOf(
            Key("#+=", KeyAction.SymbolsPageFlip, widthWeight = 1.5f),
        ) + listOf(".", ",", "?", "!", "'", "\"").map { it.asCharKey() } + listOf(
            Key(EnglishQwerty.BACKSPACE_LABEL, KeyAction.Backspace, widthWeight = 1.5f),
        )
        val row4 = commonBottomRow()
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
        val row4 = commonBottomRow()
        return KeyboardLayout(
            id = "symbols_page2",
            rows = listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), row4),
        )
    }

    private fun commonBottomRow(): KeyRow = KeyRow(
        listOf(
            Key("ABC", KeyAction.ToggleSymbols, widthWeight = 1.5f),
            Key(EnglishQwerty.LANG_LABEL, KeyAction.ToggleLanguage, widthWeight = 1.2f),
            Key(EnglishQwerty.GLOBE_LABEL, KeyAction.ShowImePicker, widthWeight = 1.0f),
            Key(EnglishQwerty.SPACE_LABEL, KeyAction.Space, widthWeight = 4.0f),
            Key(".", KeyAction.Character("."), widthWeight = 1.0f),
            Key(EnglishQwerty.ENTER_LABEL, KeyAction.Enter, widthWeight = 1.8f),
        ),
    )

    private fun String.asCharKey(): Key = Key(this, KeyAction.Character(this))
}
