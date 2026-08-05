# Bengali handwriting mode

Shohojakkhor's handwriting mode is a third local input path beside Banglish and
voice. It uses Google ML Kit Digital Ink's Bengali (`bn`) model behind the
`BanglaHandwritingRecognizer` interface.

## Flow

1. Tap `✍️` from either letter layout. From English, this opens Bengali
   handwriting and remembers it as the selected input mode.
2. On first use, download the Bengali recognition model with a visible status.
3. Draw naturally with a finger or stylus on the tall writing surface.
4. After a 700 ms pause, recognize the complete stroke sequence on-device.
5. Show up to three large results and commit the result the user taps followed
   by a space, so the next handwritten word starts naturally.
6. Clear the ink and continue with another word or phrase.

The panel also provides undo-last-stroke, clear, host-field backspace, space,
enter/send, and return-to-keyboard actions. Enter honours the host app's
requested action (such as Send, Search, Next, or Done) and otherwise inserts a
newline. Handwriting remains the selected Bengali input
surface across field changes, screen locks, and process restarts. It is never
restored in sensitive fields; those continue to force the safe English layout.

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
