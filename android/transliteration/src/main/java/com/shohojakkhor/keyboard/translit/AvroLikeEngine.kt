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
        return suggest(
            SuggestionRequest(
                latinInput = latinInput,
                maxCandidates = maxCandidates,
                includeEmoji = false,
            ),
        )
    }

    override fun suggest(request: SuggestionRequest): List<Candidate> {
        val trimmed = request.latinInput
        if (trimmed.isBlank()) return idleSuggestions(request)

        val tokens = Tokenizer.tokenize(trimmed)
        val raw = mutableListOf<Candidate>()
        val normalized = RomanizedBanglaNormalizer.normalize(trimmed)
        val variants = RomanizedBanglaNormalizer.variants(trimmed)

        // -- 2. Dictionary hits ------------------------------------------------
        for (variant in variants) {
            for (entry in dictionary.lookup(variant)) {
                val exact = variant == normalized
                raw += Candidate(
                    bengali = entry.bengali,
                    source = if (entry.source == CandidateSource.PERSONAL_DICTIONARY) {
                        CandidateSource.PERSONAL_DICTIONARY
                    } else if (exact) {
                        CandidateSource.DICTIONARY
                    } else {
                        CandidateSource.NORMALIZED_DICTIONARY
                    },
                    score = (if (exact) BASE_DICTIONARY else BASE_NORMALIZED) + entry.frequency,
                )
            }
        }

        // -- Noisy aliases and colloquial forms -------------------------------
        for (entry in if (request.enableNoisyMatching) LocalSuggestionLexicon.aliases else emptyList()) {
            if (entry.latin in variants) {
                val output = if (!request.preferStandardBangla && entry.colloquialBengali != null) {
                    entry.colloquialBengali
                } else {
                    entry.bengali
                }
                raw += Candidate(
                    bengali = output,
                    source = entry.source,
                    score = BASE_ALIAS + entry.frequency,
                    kind = if (' ' in output) CandidateKind.PHRASE else CandidateKind.WORD,
                )
                if (entry.colloquialBengali != null && entry.colloquialBengali != output) {
                    raw += Candidate(
                        bengali = entry.colloquialBengali,
                        source = CandidateSource.DIALECT,
                        score = BASE_ALIAS + entry.frequency - 40,
                    )
                }
            }
        }

        // -- Bounded Bengali-aware fuzzy match --------------------------------
        val threshold = RomanizedBanglaNormalizer.acceptableDistance(normalized)
        if (request.enableNoisyMatching && threshold > 0.0) {
            val fuzzyPool = buildList {
                addAll(dictionary.allEntries().map { AliasEntry(it.latin, it.bengali, CandidateSource.FUZZY_PHONETIC, it.frequency) })
                addAll(LocalSuggestionLexicon.aliases)
            }
            fuzzyPool.asSequence()
                .map { it to RomanizedBanglaNormalizer.distance(normalized, it.latin) }
                .filter { (_, distance) -> distance > 0.0 && distance <= threshold }
                .sortedWith(compareBy<Pair<AliasEntry, Double>> { it.second }.thenByDescending { it.first.frequency })
                .take(MAX_FUZZY_RESULTS)
                .forEach { (entry, distance) ->
                    raw += Candidate(
                        bengali = entry.bengali,
                        source = CandidateSource.FUZZY_PHONETIC,
                        score = BASE_FUZZY + entry.frequency - distance * FUZZY_DISTANCE_PENALTY,
                        kind = if (' ' in entry.bengali) CandidateKind.PHRASE else CandidateKind.WORD,
                    )
                }
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
            kind = CandidateKind.LITERAL,
        )

        // -- Small local context and emoji providers ---------------------------
        val context = request.previousWords.takeLast(2).map(RomanizedBanglaNormalizer::normalize)
        val contextual = (LocalSuggestionLexicon.contextCompletions[context] ?: emptyList())
            .firstOrNull { (latin, _) -> latin == normalized }
        if (contextual != null) {
            raw += Candidate(
                bengali = contextual.second,
                source = CandidateSource.CONTEXT,
                score = BASE_CONTEXT,
            )
        }

        if (request.includeEmoji) {
            LocalSuggestionLexicon.emojiKeywords[normalized]
                .orEmpty()
                .take(MAX_EMOJI_RESULTS)
                .forEachIndexed { index, emoji ->
                    raw += Candidate(
                        bengali = emoji,
                        source = CandidateSource.EMOJI,
                        score = BASE_EMOJI - index,
                        kind = CandidateKind.EMOJI,
                    )
                }
        }

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
            .take(request.maxCandidates)
    }

    override fun onUserSelection(latinInput: String, bengali: String) {
        val trimmed = latinInput.trim()
        if (trimmed.isEmpty() || bengali.isEmpty()) return
        memory.record(trimmed, bengali)
    }

    private fun idleSuggestions(request: SuggestionRequest): List<Candidate> {
        val context = request.previousWords.takeLast(2).map(RomanizedBanglaNormalizer::normalize)
        val raw = mutableListOf<Candidate>()
        val completions = LocalSuggestionLexicon.contextCompletions[context]
            ?: LocalSuggestionLexicon.contextCompletions[context.takeLast(1)]
            ?: emptyList()
        completions.forEachIndexed { index, (_, bengali) ->
            raw += Candidate(
                bengali = bengali,
                source = CandidateSource.CONTEXT,
                score = BASE_CONTEXT - index,
                kind = CandidateKind.PHRASE,
                replacement = CandidateReplacement.INSERT,
            )
        }
        if (request.includeEmoji) {
            val last = context.lastOrNull()
            LocalSuggestionLexicon.emojiKeywords[last]
                .orEmpty()
                .take(MAX_EMOJI_RESULTS)
                .forEachIndexed { index, emoji ->
                    raw += Candidate(
                        bengali = emoji,
                        source = CandidateSource.EMOJI,
                        score = BASE_EMOJI - index,
                        kind = CandidateKind.EMOJI,
                        replacement = CandidateReplacement.INSERT,
                    )
                }
        }
        return raw.sortedByDescending(Candidate::score).take(request.maxCandidates)
    }

    internal companion object {
        // Base scores are deliberately spaced so the *source* order is
        // preserved before memory boost is applied. Memory boost then acts
        // as a large modifier that can lift a previously-chosen candidate
        // above dictionary hits, matching the user's expectation of
        // "the keyboard remembers what I picked".
        const val BASE_DICTIONARY: Double = 10_000.0
        const val BASE_ALIAS: Double = 9_500.0
        const val BASE_NORMALIZED: Double = 9_000.0
        const val BASE_CONTEXT: Double = 8_800.0
        const val BASE_FUZZY: Double = 8_000.0
        const val BASE_RULE_PRIMARY: Double = 5_000.0
        const val BASE_RULE_ALTERNATE: Double = 3_000.0
        const val BASE_EMOJI: Double = 2_000.0
        const val BASE_LITERAL: Double = 1_000.0

        const val MEMORY_BOOST_MULTIPLIER: Double = 20_000.0

        const val MAX_ALTERNATE_POSITIONS: Int = 3
        const val MAX_ALTERNATES_PER_POSITION: Int = 2
        const val MAX_FUZZY_RESULTS: Int = 8
        const val MAX_EMOJI_RESULTS: Int = 2
        const val FUZZY_DISTANCE_PENALTY: Double = 500.0
    }
}
