# Direct Bengali key alternates

Banglish mode keeps its normal tap behavior and its original clean Latin-only
keycaps. Holding a letter opens its Bengali alternatives; sliding horizontally
highlights an option and releasing inserts it directly.

Examples:

- `a` opens `এ/আ` and also offers `অ`, `া`, and `ে`;
- `k` offers `ক/খ`;
- `t` offers `ত/থ/ট/ঠ`;
- `s` offers `স/শ/ষ`;
- `n` offers `ন/ণ/ঞ/ং`.

Less-common signs remain in the expanded popup: `n` also includes chandrabindu
(`ঁ`), while `x` includes hasanta (`্`) for direct conjunct construction.

All 26 Latin letters have at least one mapping. Uppercase and lowercase expose
the same direct choices because Shift remains available for conventional
Avro-style Banglish input.

## Interaction contract

1. A short tap always types the Latin key through the existing transliterator.
2. Holding for 350 ms opens a large Bengali popup above the key.
3. Moving horizontally changes the highlighted character with haptic feedback.
4. Releasing inserts exactly the highlighted Bengali character.
5. Cancelling the gesture inserts nothing.

If Latin composition is already active, the IME commits its current best local
candidate before inserting the direct character. This prevents the next
Banglish keystroke from replacing a character explicitly chosen by the user.

Vowel mappings include both independent vowels and dependent কার signs, so a
user can construct words directly rather than producing sequences such as
`মআ` when `মা` was intended.

## Accessibility and privacy

- Key descriptions announce the available Bengali characters.
- Accessibility long-click inserts the first displayed Bengali alternative.
- The popup uses large targets, a high-contrast selection state, and haptics.
- The feature is deterministic and entirely local; it stores and transmits no
  text.
