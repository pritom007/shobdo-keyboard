package com.shohojakkhor.keyboard.translit

/**
 * A single Bengali candidate for a Latin input string.
 *
 * @property bengali the Bengali text to insert if the user picks this candidate.
 * @property source how the candidate was produced. Different sources rank
 *   differently; see [AvroLikeEngine].
 * @property score final ranking score, larger is better. Callers should not
 *   rely on the absolute magnitude, only relative order.
 */
public data class Candidate(
    val bengali: String,
    val source: CandidateSource,
    val score: Double,
    val kind: CandidateKind = CandidateKind.WORD,
    val replacement: CandidateReplacement = CandidateReplacement.COMPOSING,
)

public enum class CandidateKind {
    WORD,
    PHRASE,
    EMOJI,
    LITERAL,
}

public enum class CandidateReplacement {
    COMPOSING,
    PREVIOUS_WORD,
    INSERT,
}

public enum class CandidateSource {
    /** Exact whole-word match in the seed / personal Bengali dictionary. */
    DICTIONARY,

    /** Rule-based deterministic transliteration (primary reading). */
    RULE_PRIMARY,

    /** Rule-based alternative (e.g. `t` → ট instead of ত). */
    RULE_ALTERNATE,

    /** The raw Latin input, passed through unchanged. Always available as
     *  an escape hatch so the user can insert English words. */
    LITERAL,

    /** The user has previously selected this candidate for the same input. */
    MEMORY,

    /** Exact match after conservative noisy Romanized-Bangla normalization. */
    NORMALIZED_DICTIONARY,

    /** Approximate match using Bengali-aware weighted edit distance. */
    FUZZY_PHONETIC,

    /** Common colloquial or regional Romanized form. */
    DIALECT,

    /** Completion from bounded local preceding-word context. */
    CONTEXT,

    /** Locally bundled or user-defined multi-word completion. */
    PHRASE,

    /** Local keyword-to-emoji suggestion. */
    EMOJI,

    /** User-created name, place, medicine, or other personal entry. */
    PERSONAL_DICTIONARY,
}
