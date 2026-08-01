package com.shobdo.keyboard.ime.layout

/**
 * Semantic action a key performs when tapped.
 *
 * Kept as a sealed hierarchy so unit tests can build layouts and assert
 * structure without instantiating any Android framework classes.
 */
public sealed interface KeyAction {

    /** Insert a literal string (typically a single character). */
    public data class Character(val text: String) : KeyAction

    public data object Backspace : KeyAction
    public data object Space : KeyAction
    public data object Enter : KeyAction

    /** Cycle shift LOWER → UPPER → CAPS → LOWER. */
    public data object Shift : KeyAction

    /** Toggle English ↔ Bengali placeholder. */
    public data object ToggleLanguage : KeyAction

    /** Toggle letters ↔ symbols. */
    public data object ToggleSymbols : KeyAction

    /** Flip symbols page 1 ↔ 2. */
    public data object SymbolsPageFlip : KeyAction

    /** Show the Android IME picker (globe key). */
    public data object ShowImePicker : KeyAction

    /** Open the voice listening panel (mic key). Hidden in sensitive fields. */
    public data object Voice : KeyAction
}

/**
 * A single key on a layout. Width is expressed as a proportional weight so
 * layouts are resolution-independent.
 */
public data class Key(
    /** Label shown on the keycap. */
    val label: String,
    val action: KeyAction,
    /** Relative width. 1.0 = a normal letter key. */
    val widthWeight: Float = 1f,
) {
    init {
        require(widthWeight > 0f) { "widthWeight must be positive" }
        require(label.isNotEmpty()) { "label must not be empty" }
    }
}

/** A horizontal row of keys. */
public data class KeyRow(val keys: List<Key>) {
    init {
        require(keys.isNotEmpty()) { "row must have at least one key" }
    }
}

/** A named layout (English lowercase, symbols page 1, etc.). */
public data class KeyboardLayout(
    val id: String,
    val rows: List<KeyRow>,
) {
    init {
        require(rows.isNotEmpty()) { "layout must have at least one row" }
    }
}
