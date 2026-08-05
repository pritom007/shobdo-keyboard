# Bengali handwriting mode

Shohojakkhor's handwriting mode is a third local input path beside Banglish and
voice. It uses Google ML Kit Digital Ink's Bengali (`bn`) model behind the
`BanglaHandwritingRecognizer` interface.

## Flow

1. Tap `✍️` from a Bengali letter layout.
2. On first use, download the Bengali recognition model with a visible status.
3. Draw naturally with a finger or stylus on the tall writing surface.
4. After a 700 ms pause, recognize the complete stroke sequence on-device.
5. Show up to three large results and commit only the result the user taps.
6. Clear the ink and continue with another word or phrase.

The panel also provides undo-last-stroke, clear, host-field backspace, space,
and return-to-keyboard actions. It closes on field changes and is unavailable
in sensitive fields.

## Architecture

- `HandwritingPanelView` owns drawing and transient stroke history.
- `BanglaHandwritingRecognizer` is the vendor-neutral boundary.
- `GoogleBanglaHandwritingRecognizer` owns model download and ML Kit lifecycle.
- `ShohojakkhorInputMethodService` owns host-field commits and rejects stale
  asynchronous recognition results.

No stroke or recognition result is persisted or logged. Model delivery may use
the network; recognition works offline once the model is present.

## Validation still required

- Test finger and stylus input on a low-cost physical Android phone.
- Tune the 700 ms pause with older Bengali writers.
- Measure first-model download and first-recognition experience.
- Build a consented handwriting corpus covering joined letters, কার signs,
  punctuation, tremor, slow strokes, and left-handed writing.
- Confirm popup/canvas height with gesture navigation and large display text.
