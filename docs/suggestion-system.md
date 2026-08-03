# Local Bangla Suggestion System

Shohojakkhor treats informal “Murad Takla” typing as **noisy Romanized
Bangla**: valid Bengali intent expressed through inconsistent Latin spelling,
missing vowels, dialect, joined words, or phonetic approximation. The app does
not label or judge the user’s writing style.

## Runtime pipeline

1. Normalize the current Latin composition conservatively.
2. Query exact seed and personal dictionaries.
3. Query curated noisy/dialect aliases.
4. Run bounded Bengali-aware weighted fuzzy matching.
5. Generate deterministic Avro-style primary and alternate readings.
6. Apply at most two preceding Latin words as ephemeral local context.
7. Add optional local phrase and emoji suggestions.
8. Add the locally learned selection boost, deduplicate, and rank.
9. Render three primary candidates and an explicit “more” control.

No suggestion provider performs network or disk I/O on a keystroke. Persistent
stores are loaded through app-private Android preferences, and the bounded
preceding-word context is never persisted or logged.

## Privacy boundaries

- Sensitive fields disable Banglish and all suggestion learning.
- Incognito fields may show local suggestions but never record selections or
  context history.
- Learned selections and personal entries stay in app-private storage.
- Android backup remains disabled.
- “শেখা শব্দগুলো মুছুন” deletes all learned selection mappings.
- Personal dictionary entries are deleted separately and intentionally.

## Ranking contract

The exact numeric scores are internal. The intended source priority is:

1. Repeated explicit user choice
2. Personal/exact dictionary
3. Curated noisy or dialect alias
4. Contextual match
5. Weighted fuzzy match
6. Deterministic rule output
7. Rule alternate
8. Emoji
9. Literal Latin escape hatch

The literal remains reachable in expanded candidates. Emoji must not displace
the most likely Bengali word.

## Correction behavior

After a word is committed with Space, alternatives remain in the strip. Tapping
one replaces only the immediately preceding committed word and its terminator.
The replacement state is discarded after another edit, cursor-destructive
action, language change, or input-session transition, preventing stale edits.

## Evaluation

`noisy-suggestions.tsv` is the initial regression corpus. Every entry declares
one expected Bengali output that must appear in the top three. Expand it only
with consented or non-personal examples and keep categories balanced across:

- traditional Banglish;
- missing-vowel and abbreviated forms;
- dialect/colloquial forms;
- religious and everyday phrases;
- names and places;
- contextual ambiguities.

Before release, record top-1/top-3 accuracy and p50/p95 suggestion latency on a
representative low-cost physical phone. The target is p95 below 10 ms for a
typical word and no regression in the traditional Banglish suite.
