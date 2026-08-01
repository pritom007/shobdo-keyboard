package com.shobdo.keyboard.translit

/**
 * A single token produced by [Tokenizer]. Each token corresponds either to
 * a rule from [Rules] or to an unrecognised character (e.g. a digit or a
 * space) that is emitted verbatim.
 */
internal sealed interface Token {
    val latin: String

    /** A recognised phonetic rule. */
    data class Recognised(val rule: Rule) : Token {
        override val latin: String get() = rule.latin
    }

    /** A single character that matched no rule. Passed through unchanged. */
    data class Passthrough(val ch: Char) : Token {
        override val latin: String get() = ch.toString()
    }
}

/**
 * Splits a Latin composing string into an ordered list of [Token]s using
 * longest-match against [Rules.prefixesLongestFirst].
 *
 * At each position the tokenizer:
 *  1. Enumerates candidate rule prefixes longest-first.
 *  2. For each prefix that matches the input at the current position,
 *     checks the rule's `prevMatch` and `nextMatch` context predicates
 *     against the character immediately before the prefix and the
 *     character immediately after the prefix's end.
 *  3. Emits the first satisfying rule as a [Token.Recognised] and advances
 *     by the prefix's length.
 *  4. If no rule qualifies, emits a [Token.Passthrough] for the single
 *     current character.
 *
 * Pure function; no state, no I/O.
 */
internal object Tokenizer {

    fun tokenize(input: String): List<Token> {
        if (input.isEmpty()) return emptyList()

        val out = ArrayList<Token>(input.length)
        var i = 0
        while (i < input.length) {
            val match = longestMatchAt(input, i)
            if (match != null) {
                out += Token.Recognised(match)
                i += match.latin.length
            } else {
                out += Token.Passthrough(input[i])
                i += 1
            }
        }
        return out
    }

    /**
     * Find the longest rule whose Latin form matches [input] at [index]
     * and whose context predicates are satisfied.
     */
    private fun longestMatchAt(input: String, index: Int): Rule? {
        for (prefix in Rules.prefixesLongestFirst) {
            if (index + prefix.length > input.length) continue
            if (!input.regionMatches(index, prefix, 0, prefix.length, ignoreCase = false)) continue

            // Multiple rules may share the same Latin key with different
            // context predicates; try them in declaration order.
            for (candidate in Rules.rulesFor(prefix)) {
                val prevOk = Rules.matches(input, index - 1, candidate.prevMatch)
                val nextOk = Rules.matches(input, index + prefix.length, candidate.nextMatch)
                if (prevOk && nextOk) return candidate
            }
        }
        return null
    }
}
