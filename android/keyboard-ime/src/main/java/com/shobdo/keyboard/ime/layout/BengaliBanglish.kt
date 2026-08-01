package com.shobdo.keyboard.ime.layout

import com.shobdo.keyboard.ime.state.KeyboardMode

/**
 * Banglish transliteration mode.
 *
 * Uses the same Latin QWERTY key layout as English — the user still taps
 * Latin letters — but as they type, the IME shows the composing text as
 * Bengali (via `InputConnection.setComposingText`) and offers Bengali
 * candidates in the [com.shobdo.keyboard.ime.view.CandidateStripView].
 *
 * The only visible difference from English mode is the language toggle
 * label, which reads "En" so the user knows tapping it switches back.
 */
public object BengaliBanglish {

    public fun layoutFor(mode: KeyboardMode): KeyboardLayout? {
        if (mode != KeyboardMode.BENGALI_BANGLISH) return null
        // Bengali mode always uses lowercase Latin. Shift is not meaningful
        // here — capital letters can produce retroflex consonants (T, D)
        // in Avro conventions, but for M2 we keep shift disabled to avoid
        // confusion for elderly users. Revisit at M2C.
        val base = EnglishQwerty.build(
            shifted = false,
            langToggleLabel = EnglishQwerty.LABEL_TO_ENGLISH,
        )
        return KeyboardLayout(id = "bn_banglish", rows = base.rows)
    }
}
