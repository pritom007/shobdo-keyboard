package com.shohojakkhor.keyboard.translit

/**
 * Default [TransliterationEngine] implementation.
 *
 * Candidate sources, in order of base score:
 *
 *   1. Selection memory      — the user's previous choice for the same input.
 *   2. Dictionary exact match — a whole-word hit in the seed / personal
 *      dictionary, ranked by [DictionaryEntry.frequency].
 *   3. Rule-based primary    — deterministic transliteration using
 *      [Assembler] with the primary Bengali reading of each rule.
 *   4. Rule-based alternates — the same transliteration with one ambiguous
 *      consonant swapped for its alternate reading (e.g. `t` → ট, `sh` → ষ).
 *   5. Literal passthrough   — the raw Latin input, always included last.
 *
 * All sources contribute their candidate; [SelectionMemory.boostFor] is added
 * on top of each source's base score.  The final list is trimmed to
 * [maxCandidates], deduplicated, and returned.
 *
 * The engine is stateless apart from the caller-supplied [SelectionMemory].
 */
public class AvroLikeEngine(
    private val dictionary: BengaliDictionary = SeedBengaliDictionary(),
    private val memory: SelectionMemory = InMemorySelectionMemory(),
) : TransliterationEngine {

    override fun transliterate(latinInput: String, maxCandidates: Int): List<Candidate> {
        val trimmed = latinInput
        if (trimmed.isBlank()) return emptyList()

        val tokens = Tokenizer.tokenize(trimmed)
        val raw = mutableListOf<Candidate>()

        // -- 2. Dictionary hits ------------------------------------------------
        for (entry in dictionary.lookup(trimmed)) {
            raw += Candidate(
                bengali = entry.bengali,
                source = CandidateSource.DICTIONARY,
                score = BASE_DICTIONARY + entry.frequency,
            )
        }

        // -- 3. Rule-based primary --------------------------------------------
        val primary = Assembler.assemble(tokens)
        if (primary.isNotEmpty()) {
            raw += Candidate(
                bengali = primary,
                source = CandidateSource.RULE_PRIMARY,
                score = BASE_RULE_PRIMARY,
            )
        }

        // -- 4. Rule-based alternates -----------------------------------------
        // For each ambiguous consonant in the token stream, generate one extra
        // variant with its alternate reading. Capped so we don't explode the
        // candidate space for long inputs.
        val ambiguousIndices = tokens.mapIndexedNotNull { idx, tok ->
            if (tok is Token.Recognised
                && tok.rule.kind == RuleKind.CONSONANT
                && tok.rule.alternates.isNotEmpty()
            ) idx else null
        }
        for (idx in ambiguousIndices.take(MAX_ALTERNATE_POSITIONS)) {
            val ruleAtIdx = (tokens[idx] as Token.Recognised).rule
            for (alt in ruleAtIdx.alternates.take(MAX_ALTERNATES_PER_POSITION)) {
                val variant = Assembler.assemble(tokens) { rule ->
                    if (rule === ruleAtIdx) alt else rule.bengali
                }
                if (variant.isNotEmpty() && variant != primary) {
                    raw += Candidate(
                        bengali = variant,
                        source = CandidateSource.RULE_ALTERNATE,
                        score = BASE_RULE_ALTERNATE,
                    )
                }
            }
        }

        // -- 5. Literal passthrough ------------------------------------------
        raw += Candidate(
            bengali = trimmed,
            source = CandidateSource.LITERAL,
            score = BASE_LITERAL,
        )

        // -- Memory boost + dedupe + sort + trim -----------------------------
        val boosted = raw.map { c ->
            val boost = memory.boostFor(trimmed, c.bengali) * MEMORY_BOOST_MULTIPLIER
            val effectiveSource = if (boost > 0.0) CandidateSource.MEMORY else c.source
            c.copy(source = effectiveSource, score = c.score + boost)
        }

        return boosted
            .groupBy { it.bengali }
            .map { (_, dups) -> dups.maxBy { it.score } }
            .sortedByDescending { it.score }
            .take(maxCandidates)
    }

    override fun onUserSelection(latinInput: String, bengali: String) {
        val trimmed = latinInput.trim()
        if (trimmed.isEmpty() || bengali.isEmpty()) return
        memory.record(trimmed, bengali)
    }

    internal companion object {
        // Base scores are deliberately spaced so the *source* order is
        // preserved before memory boost is applied. Memory boost then acts
        // as a large modifier that can lift a previously-chosen candidate
        // above dictionary hits, matching the user's expectation of
        // "the keyboard remembers what I picked".
        const val BASE_DICTIONARY: Double = 10_000.0
        const val BASE_RULE_PRIMARY: Double = 5_000.0
        const val BASE_RULE_ALTERNATE: Double = 3_000.0
        const val BASE_LITERAL: Double = 1_000.0

        const val MEMORY_BOOST_MULTIPLIER: Double = 20_000.0

        const val MAX_ALTERNATE_POSITIONS: Int = 3
        const val MAX_ALTERNATES_PER_POSITION: Int = 2
    }
}
