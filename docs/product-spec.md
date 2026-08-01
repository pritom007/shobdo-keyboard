# Product Spec

A distilled reference of the master brief. If this doc disagrees with the
master brief, the master brief wins — but this file is what the day-to-day
implementer reads.

## Product name

- English: **Shobdo Keyboard**
- Bengali: **শব্দ কিবোর্ড**

## One-line promise

A patient Bengali keyboard that listens, organises, reads messages aloud,
and waits for approval.

## Who it is for

Older Bengali-speaking adults who:

- Have difficulty typing English or native Bengali layouts.
- Know Banglish only partially.
- Speak slowly, pause often, restart sentences, self-correct.
- Mix Bengali and English (WhatsApp, appointment, doctor, report, ...).
- Have regional accents.
- Struggle to read small text.
- Accidentally press nearby buttons.
- Need reassurance before a message is inserted.

## What makes it different

Not "keyboard + AI feature". The **core interaction** is:

1. **Listen patiently** — long pauses do not truncate.
2. **Understand imperfect speech** — accents, mixed language, self-corrections.
3. **Lightly organise** — remove filler, fix punctuation, keep meaning.
4. **Let the user review or hear it** — read-back, edit history, undo.
5. **Accept conversational corrections** — "শনিবার হবে", "একটু ছোট করো".
6. **Insert only after explicit approval** — never auto-Send in the host app.

## Language scope

- **Bengali** (primary): voice composition → AI organize; Banglish typing at
  M5; simplified native at M5+; handwriting at M7.
- **English** (secondary): standard QWERTY at M1; voice English is *not* MVP.

## The three writing modes

| Mode (Bengali) | Enum | Behaviour |
|---|---|---|
| হুবহু | `VERBATIM` | Preserve wording; only fix obvious recognition errors and add punctuation. |
| **একটু গুছিয়ে** *(default)* | `ORGANIZED` | Remove filler and false starts, apply explicit self-corrections, organise sentences, preserve tone. |
| সুন্দর করে | `POLISHED` | Improve fluency; still preserve facts, names, numbers, intent. |

**Never** silently switch modes. Default stays `ORGANIZED` for elderly users.

## The mixed-language preference

| Bengali label | Behaviour |
|---|---|
| বেশি বাংলা | Prefer Bengali equivalents where natural. |
| **স্বাভাবিক মিশ্র ভাষা** *(default)* | Keep familiar English words as English. |
| English শব্দ রাখুন | Aggressively keep English words as English. |

## Non-negotiable safety rules for the AI

The AI must **not**:

- Invent facts, promises, greetings, emotions.
- Change names, dates, times, amounts, addresses, phone numbers, medicine
  names — unless the user's self-correction is explicit and high-confidence.
- Change তুমি ↔ আপনি automatically.
- Change religious language.
- Turn family speech into corporate writing.
- Translate familiar mixed-language terms unless requested.

When an important entity is uncertain, the AI asks **one** focused Bengali
question. It never guesses.

## Primary user flow (§4 of master brief, condensed)

```
┌ কথা বলুন ──────────────────────────────────────────────────┐
│  আমি শুনছি…                                                 │
│  ধীরে ধীরে বলুন। মাঝে থামলেও সমস্যা নেই।                    │
│                                                             │
│  [আবার বলুন] [শেষ করুন] [বাতিল করুন]                        │
└─────────────────────────────────────────────────────────────┘
                          ↓ user says "শেষ" or taps
                     [ transcribe ]
                          ↓
                     [ rewrite (ORGANIZED) ]
                          ↓
┌ Review draft ─────────────────────────────────────────────┐
│  {generated message with entities highlighted}             │
│                                                            │
│  [ঠিক আছে] [বদলাতে চাই] [পড়ে শোনান] [আবার বলুন] [বাতিল]    │
└────────────────────────────────────────────────────────────┘
    ↓ ঠিক আছে              ↓ বদলাতে চাই
InputConnection.commitText   "কী পরিবর্তন করতে চান?" → revise → back to Review
```

## What NEVER happens automatically

- Pressing Send in the host app.
- Reading the host app's other messages.
- Uploading the personal dictionary.
- Learning from sensitive-field input.
- Persisting audio, transcripts, or drafts.

## Elderly-first UX rules

- Minimum touch target 48 dp. Primary voice controls 56–72 dp.
- Destructive vs. confirm buttons never adjacent.
- All error messages in Bengali, non-technical.
- Read-back button always accessible.
- Undo available on any draft.
- Support Android font scaling up to 200 %.
- No hidden gestures for essential actions.

## Concrete Bengali strings the product must ship with

Recording: `আমি শুনছি…`  ·  `ধীরে ধীরে বলুন। মাঝে থামলেও সমস্যা নেই।`
Long pause prompt: `আর কিছু বলবেন?`
Actions: `কথা বলুন` · `আবার বলুন` · `শেষ করুন` · `বাতিল করুন` ·
`ঠিক আছে` · `বদলাতে চাই` · `পড়ে শোনান` · `আগের অবস্থায় ফিরুন`

Errors (§21):
- `ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না।`
- `কথাটি বুঝতে একটু সমস্যা হয়েছে। আবার চেষ্টা করুন।`
- `কথাগুলো গুছিয়ে লেখা যায়নি। আপনি হুবহু লেখাটি ব্যবহার করতে পারেন।`
- `কথা রেকর্ড করতে মাইক্রোফোনের অনুমতি প্রয়োজন।`

## Success signals

Not analytics — internal beta signals:

- Elderly participant can compose a WhatsApp message end-to-end without help.
- First-draft approval rate ≥ 60 % in ORGANIZED mode.
- Zero cases of hallucinated names, dates, or amounts in the beta corpus.
- Zero cases of the keyboard sending anything without approval.
