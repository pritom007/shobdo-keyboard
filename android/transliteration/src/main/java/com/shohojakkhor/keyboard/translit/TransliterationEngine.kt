package com.shohojakkhor.keyboard.translit

/**
 * Public entry point for Banglish → Bengali transliteration.
 *
 * Implementations MUST:
 *   - Be safe to call from the IME's main thread; each `transliterate`
 *     call must return in well under one frame (target < 5 ms for typical
 *     inputs up to ~30 characters).
 *   - Be free of side effects other than reading local state; in
 *     particular, no network I/O, no disk I/O, no logging.
 *
 * @see AvroLikeEngine for the default implementation.
 */
public interface TransliterationEngine {

    /**
     * Produces an ordered list of Bengali candidates for [latinInput]. The
     * list is:
     *   - Sorted by descending relevance to the user (best guess first).
     *   - Trimmed to at most [maxCandidates] entries.
     *   - Deduplicated (no two candidates share [Candidate.bengali]).
     *   - Never empty for non-blank input — the raw Latin input is always
     *     included as a last-resort [CandidateSource.LITERAL] entry.
     *
     * The returned list is safe to display directly in the candidate strip.
     */
    public fun transliterate(latinInput: String, maxCandidates: Int = 5): List<Candidate>

    /**
     * Rich suggestion entry point. Implementations may use at most the small,
     * caller-provided context window. No provider may perform I/O or retain
     * [SuggestionRequest.previousWords].
     */
    public fun suggest(request: SuggestionRequest): List<Candidate> =
        transliterate(request.latinInput, request.maxCandidates)

    /**
     * Notify the engine that the user just committed [bengali] for
     * [latinInput]. Implementations MAY use this to re-rank future
     * candidates for the same input.
     */
    public fun onUserSelection(latinInput: String, bengali: String)
}

public data class SuggestionRequest(
    val latinInput: String,
    val previousWords: List<String> = emptyList(),
    val maxCandidates: Int = 3,
    val includeEmoji: Boolean = true,
    val preferStandardBangla: Boolean = true,
    val enableNoisyMatching: Boolean = true,
)
