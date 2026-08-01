# Elderly Usability Guidelines

Concrete rules the implementer follows at every screen and every keyboard
surface. These apply *in addition* to standard Android accessibility rules,
not instead of them.

## Touch targets

- **Minimum**: 48 dp × 48 dp for any tappable element.
- **Primary voice controls** (কথা বলুন, শেষ করুন, ঠিক আছে): 56–72 dp,
  ideally more when there is space.
- **Spacing between adjacent keys**: ≥ 4 dp visual gap, ≥ 6 dp hit-test
  padding so slight fat-finger errors do not spill over.
- **Destructive vs. confirm buttons must never be adjacent.**
  E.g. `বাতিল করুন` and `ঠিক আছে` are separated by an inert element or a
  large gap.

## Typography

- Default keycap label size: **20 sp** English, **22 sp** Bengali. Bengali
  glyphs are visually denser, so their labels get an extra 2 sp.
- Body text in setup screens: **20 sp** baseline.
- Bengali is the *primary* label language for controls; English appears as
  a secondary line where a technical term is unavoidable.
- Respect Android font scaling up to at least 200 %; primary controls must
  remain fully visible at 150 %.

## Contrast & colour

- Text contrast ratio ≥ 4.5:1 against its background.
- Never rely on colour alone for meaning. Recording state uses colour +
  animation + icon + text.
- Avoid pure red for the primary "cancel" — some users read it as danger and
  freeze. Use neutral outline for `বাতিল করুন`, filled tone for `ঠিক আছে`.

## Feedback

- Every tap has visible feedback (ripple or state change).
- Haptic feedback is **on by default**, adjustable in settings.
- Optional key sound is **off by default** — many elderly users share
  living space and mute keyboards.
- Recording state animates clearly and shows a live "listening" indicator
  so users are not left wondering.
- Processing states are always visible: "কথা বুঝছি…" / "গুছিয়ে লিখছি…".
  Never leave the user staring at an unresponsive UI.

## Language and tone

- All error messages in plain Bengali (§21). No error codes, no stack traces,
  no English fallback for essential text.
- Actions phrased as verbs: `কথা বলুন`, not `রেকর্ড`.
- Instructions use ছোট বাক্য (short sentences). One idea per sentence.
- Reassurance beats precision when they conflict: "মাঝে থামলেও সমস্যা নেই"
  is more useful than any technical warning about VAD thresholds.

## Cancellation is always available

Every screen and every state exposes a way out. The user must never feel
trapped by the AI flow. Cancelling the flow discards the raw transcript.

## Read-back

- Available on the review screen, always.
- Speaks the Bengali message using Android TTS (`bn_IN` / `bn_BD`).
- If Bengali TTS is unavailable, explain that in Bengali and offer a
  shortcut to system TTS settings. Do not fail silently.

## Confirmation before commit

- The final `commitText` to the host app happens **only** after the user
  taps `ঠিক আছে` on the review screen.
- We never press the host app's Send. The keyboard only inserts.

## Undo

- Small in-memory edit history for the current draft, cleared on approval
  or cancel.
- `আগের অবস্থায় ফিরুন` accessible from the review screen with a large tap
  target.

## Onboarding

- Two clear steps, one per screen:
  1. Enable the keyboard.
  2. Select the keyboard.
- Each step has: a single sentence, one large button, and a static image
  showing where the system dialog will appear.
- Skip animations that could confuse. Fade in / out is fine; slide-across
  transitions are not.

## What we do not do

- No tutorial that requires reading more than three sentences before use.
- No coach marks that block the screen.
- No forced sign-in.
- No pop-up asking for a rating or feedback in the first month.
- No dark-pattern buttons ("Not now" in a much smaller font than "Enable").

## Usability testing checklist (used from M6 onward)

- [ ] Test with participants ≥ 60 years old, both women and men.
- [ ] Test with reading glasses on and off.
- [ ] Test with hand tremor / reduced dexterity simulation.
- [ ] Test in bright outdoor light and low indoor light.
- [ ] Test with the phone at 200 % font scale.
- [ ] Test after a phone call interrupts recording.
- [ ] Test with slow network (throttled to 2G).
- [ ] Ask the participant to insert a message in **WhatsApp** without
      assistance. Observe silently.
