package com.shobdo.keyboard.translit

/**
 * Position-aware Bengali assembler.
 *
 * Turns a stream of [Token]s into a Bengali string, honouring the same
 * script conventions the Avro / avro.im phonetic engines follow:
 *
 *  1. **Inherent vowel (অ).** A bare consonant in Bengali already implies
 *     ô. So `p` alone → `প`, `por` → `পর` (not `পোর`), `bar` → `বার`
 *     only because the writer explicitly types `a` for a long-a matra
 *     (or a middle-`a` that we interpret as long-ā in that position).
 *
 *     The Rules table encodes this by giving the short-a rule an *empty*
 *     matra `bengali = ""` — the assembler treats an empty VOWEL-matra
 *     emission as "no output" when it follows a consonant, but still
 *     transitions the assembler into a state where the next consonant
 *     won't get a hasant joined to it.
 *
 *  2. **Word-initial vs. matra vowels.** A vowel at the start of a word,
 *     after another vowel, after a modifier, or after whitespace/punct
 *     uses its independent glyph (আ, ই, উ, এ, ও …). A vowel immediately
 *     after a consonant uses its matra glyph (া, ি, ু, ে, ো …).
 *
 *  3. **Conjuncts (যুক্তবর্ণ).**
 *      - Two consecutive single-consonant tokens are joined by hasant
 *        (্): `km` → `ক্ম`.
 *      - A [RuleKind.CONJUNCT] token emits a pre-assembled fragment that
 *        already contains its own internal hasants (e.g. `kkh` → `ক্ষ`).
 *        If it directly follows another consonant, the assembler
 *        prepends one hasant to attach it (rare — usually the tokenizer
 *        already picks the longer joining conjunct).
 *
 *  4. **Modifiers** (anusvara ং, chandrabindu ঁ, visarga ঃ, dari ।) pass
 *     through verbatim.
 *
 *  5. **Passthrough** characters (spaces, digits, unknown chars) pass
 *     through and reset the syllable state so the next vowel is again
 *     independent.
 *
 * Alternate readings for ambiguous consonants (`t` → ত/ট, `sh` → শ/ষ,
 * etc.) are produced by [AvroLikeEngine] on top of this assembler by
 * running assemble() a second time with a different [readingChoice].
 * The assembler itself is deterministic.
 */
internal object Assembler {

    private const val HASANT: Char = '্'

    /**
     * Assemble tokens into Bengali text using [readingChoice] to select
     * which reading each recognised rule should emit. Default is the
     * rule's primary [Rule.bengali] / [Rule.independent] as appropriate.
     */
    fun assemble(
        tokens: List<Token>,
        readingChoice: (Rule) -> String = { it.bengali },
    ): String {
        if (tokens.isEmpty()) return ""

        val sb = StringBuilder(tokens.size * 2)
        var state: State = State.START

        for (tok in tokens) {
            state = when (tok) {
                is Token.Recognised -> emitRecognised(tok.rule, readingChoice, sb, state)
                is Token.Passthrough -> {
                    sb.append(tok.ch)
                    if (tok.ch.isWhitespace()) State.START else State.AFTER_PASSTHROUGH
                }
            }
        }
        return sb.toString()
    }

    private fun emitRecognised(
        rule: Rule,
        readingChoice: (Rule) -> String,
        sb: StringBuilder,
        prev: State,
    ): State = when (rule.kind) {

        RuleKind.VOWEL -> emitVowel(rule, readingChoice, sb, prev)

        RuleKind.CONSONANT -> {
            if (prev == State.AFTER_CONSONANT) {
                sb.append(HASANT)
            }
            sb.append(readingChoice(rule))
            State.AFTER_CONSONANT
        }

        RuleKind.CONJUNCT -> {
            // Pre-composed fragment (already contains its internal hasants).
            // If it follows another consonant, hasant-join the boundary.
            if (prev == State.AFTER_CONSONANT) {
                sb.append(HASANT)
            }
            sb.append(readingChoice(rule))
            State.AFTER_CONSONANT
        }

        RuleKind.MODIFIER -> {
            sb.append(readingChoice(rule))
            State.AFTER_MODIFIER
        }
    }

    /**
     * Emit a vowel token. Handles:
     *   - Inherent vowel: an empty matra after a consonant emits nothing
     *     but transitions to AFTER_MATRA so a following consonant does
     *     NOT trigger a hasant join.
     *   - Word-initial / post-vowel / post-modifier: emit independent form.
     *   - Post-consonant with non-empty matra: emit matra.
     *   - Post-vowel: emit independent form (adjacent vowels each stand
     *     alone in Bengali; the second doesn't take a matra on the first).
     */
    private fun emitVowel(
        rule: Rule,
        readingChoice: (Rule) -> String,
        sb: StringBuilder,
        prev: State,
    ): State {
        val matra = readingChoice(rule)
        val independent = rule.independent ?: rule.bengali

        return when (prev) {
            State.AFTER_CONSONANT -> {
                // Attach as matra. Empty matra means "inherent vowel" — emit
                // nothing but move into a syllable-complete state.
                if (matra.isNotEmpty()) {
                    sb.append(matra)
                }
                State.AFTER_MATRA
            }
            else -> {
                // Word-initial or post-vowel / post-modifier / post-punct.
                sb.append(independent)
                State.AFTER_VOWEL_INDEPENDENT
            }
        }
    }

    private enum class State {
        START,
        AFTER_CONSONANT,
        AFTER_MATRA,
        AFTER_VOWEL_INDEPENDENT,
        AFTER_MODIFIER,
        AFTER_PASSTHROUGH,
    }
}
