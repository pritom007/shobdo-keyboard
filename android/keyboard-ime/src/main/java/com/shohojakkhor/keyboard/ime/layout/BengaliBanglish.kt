package com.shohojakkhor.keyboard.ime.layout

import com.shohojakkhor.keyboard.ime.state.KeyboardMode

/**
 * Banglish transliteration mode.
 *
 * Uses the same Latin QWERTY key layout as English — the user still taps
 * Latin letters — but as they type, the IME shows the composing text as
 * Bengali (via `InputConnection.setComposingText`) and offers Bengali
 * candidates in the [com.shohojakkhor.keyboard.ime.view.CandidateStripView].
 *
 * The only visible difference from English mode is the language toggle
 * label, which reads "En" so the user knows tapping it switches back.
 *
 * ### Avro-style shift (session 3)
 * The layout supports three shift layers, mirroring English:
 *  - [KeyboardMode.BENGALI_BANGLISH]        — lowercase Latin keys.
 *  - [KeyboardMode.BENGALI_BANGLISH_UPPER]  — uppercase Latin keys (one-shot).
 *  - [KeyboardMode.BENGALI_BANGLISH_CAPS]   — uppercase Latin keys (latched).
 *
 * Uppercase keys feed the transliteration engine uppercase input, which the
 * rule table already maps to Avro's retroflex / long-vowel forms
 * (`T`→ট, `D`→ড, `N`→ণ, `R`→ড়, `Sh`→ষ, `A`→আ, `I`→ঈ, `U`→ঊ, `E`→এ, `O`→ও).
 * The IME's one-shot decay (afterCharCommit) returns to lowercase after a
 * single char, matching Avro's typical shift-tap usage.
 */
public object BengaliBanglish {

    public fun layoutFor(mode: KeyboardMode): KeyboardLayout? {
        if (!mode.isBengaliBanglish) return null
        val base = EnglishQwerty.build(
            shifted = mode.isShifted,
            langToggleLabel = EnglishQwerty.LABEL_TO_ENGLISH,
            includeHandwriting = true,
        )
        val rows = base.rows.map { row ->
            KeyRow(
                row.keys.map { key ->
                    if (key.action is KeyAction.Character) {
                        key.copy(alternates = BengaliKeyAlternates.forLatin(key.label))
                    } else {
                        key
                    }
                },
            )
        }
        return KeyboardLayout(id = "bn_banglish_${if (mode.isShifted) "upper" else "lower"}", rows = rows)
    }
}
