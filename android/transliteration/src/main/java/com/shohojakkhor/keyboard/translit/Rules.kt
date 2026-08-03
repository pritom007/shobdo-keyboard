package com.shohojakkhor.keyboard.translit

/**
 * Category of a single phonetic rule. The assembler treats each category
 * differently:
 *
 *  - **VOWEL** tokens toggle between their word-initial "independent"
 *    form (আ, ই, উ, এ, ও …) and their attached "matra" form
 *    (া, ি, ু, ে, ো …) based on whether the previous emission was a
 *    consonant.
 *  - **CONSONANT** tokens output a bare consonant (with implicit inherent
 *    vowel অ). Two consonants in a row are joined with a hasant (্).
 *  - **CONJUNCT** tokens emit a pre-assembled Bengali fragment (already
 *    containing consonants + hasants). They behave like a consonant for
 *    the purposes of matra handling but do not receive an extra hasant
 *    at the boundary.
 *  - **MODIFIER** tokens (anusvara ং, chandrabindu ঁ, visarga ঃ, dari ।)
 *    pass through as-is.
 */
internal enum class RuleKind { VOWEL, CONSONANT, CONJUNCT, MODIFIER }

/**
 * A context match condition. A rule may declare an optional predicate on
 * the character *before* its match position and/or *after* its match end.
 * This lets us express Avro-style rules such as *"the sequence `y` between
 * two consonants means the ্য (ya-phala) suffix, not the initial য়"*.
 *
 * The predicate uses simple character classes for readability:
 *  - [CONSONANT] — matches a Latin char that starts a consonant rule
 *    (k, g, t, d, p, b, m, n, r, l, s, h, y, w, z, x, plus their
 *    uppercase retroflex forms and digraph starters like c, j, f, v).
 *  - [VOWEL] — matches a Latin char that starts a vowel rule
 *    (a, e, i, o, u and their long / uppercase variants).
 *  - [ANY] — always matches.
 *  - [START] — matches the beginning of the composing buffer.
 *  - [END] — matches the end of the composing buffer.
 *  - [PUNCT] — matches whitespace / digit / any other non-letter.
 */
internal enum class MatchClass { CONSONANT, VOWEL, ANY, START, END, PUNCT, LETTER, NOT_VOWEL }

/**
 * A single Latin-to-Bengali phonetic rule.
 *
 * @property latin the exact Latin substring that triggers the rule.
 * @property bengali the Bengali fragment to emit. For [RuleKind.VOWEL] this
 *   is the *matra* form. For everything else it is the literal fragment.
 * @property kind category (see [RuleKind]).
 * @property independent for vowels only — the word-initial independent form
 *   (আ, ই, …). Ignored for other kinds.
 * @property prevMatch optional constraint on the character just before this
 *   rule's match position. Defaults to [MatchClass.ANY].
 * @property nextMatch optional constraint on the character just after this
 *   rule's match end. Defaults to [MatchClass.ANY].
 * @property alternates other plausible Bengali readings, used to seed
 *   RULE_ALTERNATE candidates. Only [RuleKind.CONSONANT] and
 *   [RuleKind.CONJUNCT] rules produce alternates.
 */
internal data class Rule(
    val latin: String,
    val bengali: String,
    val kind: RuleKind,
    val independent: String? = null,
    val prevMatch: MatchClass = MatchClass.ANY,
    val nextMatch: MatchClass = MatchClass.ANY,
    val alternates: List<String> = emptyList(),
)

/**
 * Avro-compatible Latin → Bengali phonetic rule table.
 *
 * Design principles (mirroring avro.im / AvroPhonetic behaviour):
 *
 *  1. **Inherent vowel** — a bare consonant already carries অ.
 *     `k` alone → `ক` (implying "kô"). The `a` vowel is only *emitted*
 *     when the writer types `aa`, `A`, or an explicit `a` after a
 *     consonant followed by another vowel-worthy position.
 *  2. **Long vs. short vowels** — `i` = হ্রস্ব-ই (ি/ই), `ii`/`I` = দীর্ঘ-ঈ
 *     (ী/ঈ). Same for u/uu, o/O.
 *  3. **Retroflex uppercase** — `T`,`D`,`N`,`R`,`Sh` → ট,ড,ণ,ড়,ষ.
 *  4. **Common conjunct shortcuts** — high-frequency yuktakkhor patterns
 *     like `kkh` / `kSh` / `kkhya` → ক্ষ, `gg` / `jNG` → জ্ঞ, `hm` → হ্ম,
 *     `NGkh` / `nkh` → ঙ্ক্ষ, are emitted directly by the tokenizer
 *     without needing to go through the two-consonant-hasant machinery.
 *  5. **Ya-phala (্য) and ra-phala (্র)** — a `y` sandwiched between
 *     consonants produces the ্য (ya-phala) suffix; likewise `r` before
 *     a consonant (in Avro's `rr` / `rry` convention) gives ্র (ra-phala).
 *  6. **Refa (র্)** — a leading `r` before a consonant at the start of a
 *     syllable becomes র্ (reph). Handled in [Assembler] contextually.
 *
 * Rule order in this file is arbitrary — the tokenizer always picks the
 * longest matching prefix at each position, then filters by context.
 */
internal object Rules {

    /** Word-boundary characters used by context match predicates. */
    private val consonantStarters: Set<Char> = setOf(
        'b', 'B',
        'c', 'C',
        'd', 'D',
        'f', 'F',
        'g', 'G',
        'h', 'H',
        'j', 'J',
        'k', 'K',
        'l', 'L',
        'm', 'M',
        'n', 'N',
        'p', 'P',
        'q', 'Q',
        'r', 'R',
        's', 'S',
        't', 'T',
        'v', 'V',
        'w', 'W',
        'x', 'X',
        'y', 'Y',
        'z', 'Z',
    )

    private val vowelStarters: Set<Char> = setOf(
        'a', 'A',
        'e', 'E',
        'i', 'I',
        'o', 'O',
        'u', 'U',
    )

    /**
     * Test whether the character at [index] in [input] (or absence thereof)
     * satisfies the given [MatchClass].
     *
     * @param index may be -1 (before start) or ≥ input.length (past end).
     */
    fun matches(input: String, index: Int, cls: MatchClass): Boolean {
        if (index < 0) {
            return cls == MatchClass.START || cls == MatchClass.ANY ||
                cls == MatchClass.NOT_VOWEL || cls == MatchClass.PUNCT
        }
        if (index >= input.length) {
            return cls == MatchClass.END || cls == MatchClass.ANY ||
                cls == MatchClass.NOT_VOWEL || cls == MatchClass.PUNCT
        }
        val ch = input[index]
        return when (cls) {
            MatchClass.ANY -> true
            MatchClass.START -> false
            MatchClass.END -> false
            MatchClass.CONSONANT -> ch in consonantStarters
            MatchClass.VOWEL -> ch in vowelStarters
            MatchClass.NOT_VOWEL -> ch !in vowelStarters
            MatchClass.LETTER -> ch.isLetter()
            MatchClass.PUNCT -> !ch.isLetterOrDigit()
        }
    }

    /** All rules. Order in this list is not significant. */
    val all: List<Rule> = buildList {

        // ------------------------------------------------------------------
        // 1. High-priority conjunct shortcuts.
        //
        // These are declared first (as long Latin sequences) so the tokenizer
        // greedily picks the whole conjunct rather than splitting into
        // consonant + consonant. Every entry here emits a *fully composed*
        // Bengali fragment including its internal hasants.
        // ------------------------------------------------------------------

        // ক্ষ family (ksh / kkh / kkhy)
        add(Rule("kkhy", "ক্ষ্য", RuleKind.CONJUNCT))
        add(Rule("kkhh", "ক্ষ", RuleKind.CONJUNCT))
        add(Rule("kkh", "ক্ষ", RuleKind.CONJUNCT))
        add(Rule("kSh", "ক্ষ", RuleKind.CONJUNCT))
        add(Rule("ksh", "ক্ষ", RuleKind.CONJUNCT, alternates = listOf("ক্শ")))
        add(Rule("kh", "খ", RuleKind.CONSONANT))

        // জ্ঞ (gya sound)
        add(Rule("gyan", "জ্ঞান", RuleKind.CONJUNCT))
        add(Rule("ggy", "জ্ঞ", RuleKind.CONJUNCT))
        add(Rule("gg", "জ্ঞ", RuleKind.CONJUNCT, alternates = listOf("গ্গ")))
        add(Rule("jn", "জ্ঞ", RuleKind.CONJUNCT))
        add(Rule("gyo", "জ্ঞ", RuleKind.CONJUNCT))
        add(Rule("gy", "জ্ঞ", RuleKind.CONJUNCT, alternates = listOf("গ্য")))

        // Common consonant + য (ya-phala) suffixes. Emit as pre-composed
        // fragments so we skip the hasant machinery for these very common
        // patterns.
        add(Rule("mrittu", "মৃত্যু", RuleKind.CONJUNCT))
        add(Rule("hm", "হ্ম", RuleKind.CONJUNCT))
        add(Rule("hn", "হ্ন", RuleKind.CONJUNCT))
        add(Rule("hb", "হ্ব", RuleKind.CONJUNCT))
        add(Rule("hl", "হ্ল", RuleKind.CONJUNCT))
        add(Rule("hr", "হ্র", RuleKind.CONJUNCT))
        add(Rule("hy", "হ্য", RuleKind.CONJUNCT))
        add(Rule("shch", "শ্চ", RuleKind.CONJUNCT))
        add(Rule("shT", "ষ্ট", RuleKind.CONJUNCT))
        add(Rule("sht", "ষ্ট", RuleKind.CONJUNCT, alternates = listOf("শ্ত")))
        add(Rule("shN", "ষ্ণ", RuleKind.CONJUNCT))
        add(Rule("shn", "ষ্ণ", RuleKind.CONJUNCT))
        add(Rule("shp", "ষ্প", RuleKind.CONJUNCT))
        add(Rule("shk", "শ্ক", RuleKind.CONJUNCT))
        add(Rule("shl", "শ্ল", RuleKind.CONJUNCT))
        add(Rule("shm", "শ্ম", RuleKind.CONJUNCT))
        add(Rule("shb", "শ্ব", RuleKind.CONJUNCT))
        add(Rule("shr", "শ্র", RuleKind.CONJUNCT))
        add(Rule("sthy", "স্থ্য", RuleKind.CONJUNCT))
        add(Rule("sth", "স্থ", RuleKind.CONJUNCT))
        add(Rule("stw", "স্ত্ব", RuleKind.CONJUNCT))
        add(Rule("str", "স্ত্র", RuleKind.CONJUNCT))
        add(Rule("st", "স্ত", RuleKind.CONJUNCT))
        add(Rule("sp", "স্প", RuleKind.CONJUNCT))
        add(Rule("sm", "স্ম", RuleKind.CONJUNCT))
        add(Rule("sn", "স্ন", RuleKind.CONJUNCT))
        add(Rule("sk", "স্ক", RuleKind.CONJUNCT))
        add(Rule("sl", "স্ল", RuleKind.CONJUNCT))
        add(Rule("sw", "স্ব", RuleKind.CONJUNCT))
        add(Rule("sr", "স্র", RuleKind.CONJUNCT))
        add(Rule("ntt", "ন্ত্ত", RuleKind.CONJUNCT))
        add(Rule("ntw", "ন্ত্ব", RuleKind.CONJUNCT))
        add(Rule("ntr", "ন্ত্র", RuleKind.CONJUNCT))
        add(Rule("nt", "ন্ত", RuleKind.CONJUNCT))
        add(Rule("nd", "ন্দ", RuleKind.CONJUNCT))
        add(Rule("ndr", "ন্দ্র", RuleKind.CONJUNCT))
        add(Rule("ndh", "ন্ধ", RuleKind.CONJUNCT))
        add(Rule("nn", "ন্ন", RuleKind.CONJUNCT))
        add(Rule("nm", "ন্ম", RuleKind.CONJUNCT))
        add(Rule("mp", "ম্প", RuleKind.CONJUNCT))
        add(Rule("mb", "ম্ব", RuleKind.CONJUNCT))
        add(Rule("mbh", "ম্ভ", RuleKind.CONJUNCT))
        add(Rule("mm", "ম্ম", RuleKind.CONJUNCT))
        add(Rule("ml", "ম্ল", RuleKind.CONJUNCT))
        add(Rule("mr", "ম্র", RuleKind.CONJUNCT))
        add(Rule("kt", "ক্ত", RuleKind.CONJUNCT))
        add(Rule("kr", "ক্র", RuleKind.CONJUNCT))
        add(Rule("kl", "ক্ল", RuleKind.CONJUNCT))
        add(Rule("kw", "ক্ব", RuleKind.CONJUNCT))
        add(Rule("ky", "ক্য", RuleKind.CONJUNCT))
        add(Rule("kk", "ক্ক", RuleKind.CONJUNCT))
        add(Rule("gr", "গ্র", RuleKind.CONJUNCT))
        add(Rule("gl", "গ্ল", RuleKind.CONJUNCT))
        add(Rule("gn", "গ্ন", RuleKind.CONJUNCT))
        add(Rule("gd", "গ্দ", RuleKind.CONJUNCT))
        add(Rule("ghr", "ঘ্র", RuleKind.CONJUNCT))
        add(Rule("chch", "চ্চ", RuleKind.CONJUNCT))
        add(Rule("cch", "চ্ছ", RuleKind.CONJUNCT))
        add(Rule("cchh", "চ্ছ", RuleKind.CONJUNCT))
        add(Rule("jj", "জ্জ", RuleKind.CONJUNCT))
        add(Rule("jjh", "জ্ঝ", RuleKind.CONJUNCT))
        add(Rule("jw", "জ্ব", RuleKind.CONJUNCT))
        add(Rule("jr", "জ্র", RuleKind.CONJUNCT))
        add(Rule("Tt", "ট্ট", RuleKind.CONJUNCT))
        add(Rule("TT", "ট্ট", RuleKind.CONJUNCT))
        add(Rule("Tm", "ট্ম", RuleKind.CONJUNCT))
        add(Rule("Dd", "ড্ড", RuleKind.CONJUNCT))
        add(Rule("DD", "ড্ড", RuleKind.CONJUNCT))
        add(Rule("tt", "ত্ত", RuleKind.CONJUNCT))
        add(Rule("ttw", "ত্ত্ব", RuleKind.CONJUNCT))
        add(Rule("tth", "ত্থ", RuleKind.CONJUNCT))
        add(Rule("tn", "ত্ন", RuleKind.CONJUNCT))
        add(Rule("tm", "ত্ম", RuleKind.CONJUNCT))
        add(Rule("tr", "ত্র", RuleKind.CONJUNCT))
        add(Rule("tw", "ত্ব", RuleKind.CONJUNCT))
        add(Rule("dd", "দ্দ", RuleKind.CONJUNCT))
        add(Rule("ddh", "দ্ধ", RuleKind.CONJUNCT))
        add(Rule("dh", "ধ", RuleKind.CONSONANT, alternates = listOf("ঢ")))
        add(Rule("dbh", "দ্ভ", RuleKind.CONJUNCT))
        add(Rule("dm", "দ্ম", RuleKind.CONJUNCT))
        add(Rule("dr", "দ্র", RuleKind.CONJUNCT))
        add(Rule("dw", "দ্ব", RuleKind.CONJUNCT))
        add(Rule("pp", "প্প", RuleKind.CONJUNCT))
        add(Rule("pt", "প্ত", RuleKind.CONJUNCT))
        add(Rule("pn", "প্ন", RuleKind.CONJUNCT))
        add(Rule("pl", "প্ল", RuleKind.CONJUNCT))
        add(Rule("pr", "প্র", RuleKind.CONJUNCT))
        add(Rule("ps", "প্স", RuleKind.CONJUNCT))
        add(Rule("bd", "ব্দ", RuleKind.CONJUNCT))
        add(Rule("bdh", "ব্ধ", RuleKind.CONJUNCT))
        add(Rule("bj", "ব্জ", RuleKind.CONJUNCT))
        add(Rule("bl", "ব্ল", RuleKind.CONJUNCT))
        add(Rule("br", "ব্র", RuleKind.CONJUNCT))
        add(Rule("bb", "ব্ব", RuleKind.CONJUNCT))
        add(Rule("lk", "ল্ক", RuleKind.CONJUNCT))
        add(Rule("lg", "ল্গ", RuleKind.CONJUNCT))
        add(Rule("lp", "ল্প", RuleKind.CONJUNCT))
        add(Rule("lb", "ল্ব", RuleKind.CONJUNCT))
        add(Rule("lm", "ল্ম", RuleKind.CONJUNCT))
        add(Rule("ll", "ল্ল", RuleKind.CONJUNCT))
        add(Rule("lt", "ল্ত", RuleKind.CONJUNCT))
        add(Rule("ld", "ল্দ", RuleKind.CONJUNCT))
        add(Rule("lph", "ল্ফ", RuleKind.CONJUNCT))
        add(Rule("nc", "ঞ্চ", RuleKind.CONJUNCT))
        add(Rule("nch", "ঞ্চ", RuleKind.CONJUNCT))
        add(Rule("nchh", "ঞ্ছ", RuleKind.CONJUNCT))
        add(Rule("nj", "ঞ্জ", RuleKind.CONJUNCT))
        add(Rule("nk", "ঙ্ক", RuleKind.CONJUNCT))
        add(Rule("nkh", "ঙ্খ", RuleKind.CONJUNCT))
        add(Rule("ng", "ং", RuleKind.MODIFIER))
        add(Rule("Ng", "ঙ্গ", RuleKind.CONJUNCT))

        // ------------------------------------------------------------------
        // 2. Vowels.
        //
        // The `a` character is deliberately NOT in the rule table as an
        // emit-a-matra rule after a consonant — Bengali's inherent vowel
        // rule means বাংলা's schwa is implicit. Instead:
        //
        //  - At syllable start (word-initial, after space, after another
        //    vowel/modifier), `a` yields the independent form আ. This is
        //    handled by the Assembler treating VOWEL rules contextually.
        //  - Between consonants (`bar`, `kal`, `por`) `a` produces the
        //    inherent vowel and is *suppressed* (no matra emitted).
        //  - `aa` and `A` always emit an explicit long ā matra / আ.
        //
        // The Assembler consults [Rule.independent] for word-initial vs.
        // matra, and consults the special "inherent" flag baked into the
        // shortA rule (see below) to decide whether to emit anything.
        // ------------------------------------------------------------------
        addAll(
            listOf(
                // Short-a: matches Avro's behavior — always emits ā matra
                // after a consonant, independent আ at word start. The
                // subtlety of Bengali's inherent vowel (bare consonant =
                // consonant + ô) is resolved via dictionary lookup for
                // known words (porikkha → পরীক্ষা, kobita → কবিতা), not
                // via rules. Rules are one-to-one: every typed `a` gets
                // a Bengali reading.
                Rule("a", bengali = "া", kind = RuleKind.VOWEL, independent = "আ"),

                // Long-a
                Rule("aa", "া", RuleKind.VOWEL, independent = "আ"),
                Rule("A", "া", RuleKind.VOWEL, independent = "আ"),

                // Short-i (হ্রস্ব-ই)
                Rule("i", "ি", RuleKind.VOWEL, independent = "ই"),

                // Long-i (দীর্ঘ-ঈ)
                Rule("ii", "ী", RuleKind.VOWEL, independent = "ঈ"),
                Rule("I", "ী", RuleKind.VOWEL, independent = "ঈ"),
                Rule("ee", "ী", RuleKind.VOWEL, independent = "ঈ"),

                // Short-u
                Rule("u", "ু", RuleKind.VOWEL, independent = "উ"),

                // Long-u
                Rule("uu", "ূ", RuleKind.VOWEL, independent = "ঊ"),
                Rule("U", "ূ", RuleKind.VOWEL, independent = "ঊ"),
                Rule("oo", "ু", RuleKind.VOWEL, independent = "উ"),

                // Ri (ঋ)
                Rule("rri", "ৃ", RuleKind.VOWEL, independent = "ঋ"),
                Rule("Ri", "ৃ", RuleKind.VOWEL, independent = "ঋ"),

                // e-kar
                Rule("e", "ে", RuleKind.VOWEL, independent = "এ"),
                Rule("E", "ে", RuleKind.VOWEL, independent = "এ"),

                // oi
                Rule("oi", "ৈ", RuleKind.VOWEL, independent = "ঐ"),
                Rule("OI", "ৈ", RuleKind.VOWEL, independent = "ঐ"),

                // o-kar
                Rule("o", "ো", RuleKind.VOWEL, independent = "ও"),
                Rule("O", "ো", RuleKind.VOWEL, independent = "ও"),

                // ou
                Rule("ou", "ৌ", RuleKind.VOWEL, independent = "ঔ"),
                Rule("OU", "ৌ", RuleKind.VOWEL, independent = "ঔ"),
            ),
        )

        // ------------------------------------------------------------------
        // 3. Single (non-conjunct) consonants.
        //
        // Order note: any digraphs (kh, gh, ch, chh, jh, Th, Dh, dh, bh,
        // ph, sh, Sh, th, NG, NGY) must also live here at their exact
        // Latin length so the tokenizer's longest-first search picks them
        // over their prefixes.
        // ------------------------------------------------------------------
        addAll(
            listOf(
                Rule("k", "ক", RuleKind.CONSONANT),
                Rule("g", "গ", RuleKind.CONSONANT),
                Rule("gh", "ঘ", RuleKind.CONSONANT),
                Rule("NG", "ঙ", RuleKind.CONSONANT),

                // Affricates
                Rule("c", "চ", RuleKind.CONSONANT),
                Rule("ch", "চ", RuleKind.CONSONANT),
                Rule("chh", "ছ", RuleKind.CONSONANT),
                Rule("Ch", "ছ", RuleKind.CONSONANT),

                Rule("j", "জ", RuleKind.CONSONANT, alternates = listOf("য")),
                Rule("jh", "ঝ", RuleKind.CONSONANT),
                Rule("NGY", "ঞ", RuleKind.CONSONANT),

                // Retroflex (uppercase) vs. dental (lowercase).
                Rule("T", "ট", RuleKind.CONSONANT),
                Rule("Th", "ঠ", RuleKind.CONSONANT),
                Rule("D", "ড", RuleKind.CONSONANT),
                Rule("Dh", "ঢ", RuleKind.CONSONANT),
                Rule("N", "ণ", RuleKind.CONSONANT),

                Rule("t", "ত", RuleKind.CONSONANT, alternates = listOf("ট")),
                Rule("th", "থ", RuleKind.CONSONANT, alternates = listOf("ঠ")),
                Rule("d", "দ", RuleKind.CONSONANT, alternates = listOf("ড")),
                // "dh" is already declared above as ধ (with ঢ alternate).

                Rule("n", "ন", RuleKind.CONSONANT, alternates = listOf("ণ")),

                Rule("p", "প", RuleKind.CONSONANT),
                Rule("ph", "ফ", RuleKind.CONSONANT),
                Rule("f", "ফ", RuleKind.CONSONANT),
                Rule("b", "ব", RuleKind.CONSONANT),
                Rule("bh", "ভ", RuleKind.CONSONANT),
                Rule("v", "ভ", RuleKind.CONSONANT, alternates = listOf("ব")),
                Rule("m", "ম", RuleKind.CONSONANT),

                // য / য় — Avro treats `y` between vowels or word-final as য়
                // and between consonants as ্য (ya-phala). We hand the
                // between-consonant case to the CONJUNCT rules above (ky,
                // ny, hy, etc.). Anything left uses the plain "y → য়" rule
                // for smoothness in words like "meye", "koye".
                Rule("y", "য়", RuleKind.CONSONANT, alternates = listOf("য")),
                Rule("Y", "য়", RuleKind.CONSONANT, alternates = listOf("য")),
                Rule("z", "জ", RuleKind.CONSONANT, alternates = listOf("য")),

                Rule("r", "র", RuleKind.CONSONANT, alternates = listOf("ড়", "ঢ়")),
                Rule("R", "ড়", RuleKind.CONSONANT, alternates = listOf("র")),
                Rule("l", "ল", RuleKind.CONSONANT),

                Rule("s", "স", RuleKind.CONSONANT, alternates = listOf("শ", "ষ")),
                Rule("sh", "শ", RuleKind.CONSONANT, alternates = listOf("ষ")),
                Rule("Sh", "ষ", RuleKind.CONSONANT, alternates = listOf("শ")),

                Rule("h", "হ", RuleKind.CONSONANT),
                Rule("w", "ও", RuleKind.CONSONANT, alternates = listOf("ব")),
                Rule("q", "ক", RuleKind.CONSONANT),
                Rule("x", "ক্স", RuleKind.CONJUNCT),
            ),
        )

        // ------------------------------------------------------------------
        // 4. Modifiers & punctuation.
        // ------------------------------------------------------------------
        addAll(
            listOf(
                // `ng` is already declared above as MODIFIER ং (higher
                // priority than N+g); duplicate here is intentional and
                // harmless since the byExactLatin map will keep the first.
                Rule("NN", "ঁ", RuleKind.MODIFIER),           // chandrabindu
                Rule("H", "ঃ", RuleKind.MODIFIER),           // visarga
                Rule(".", "।", RuleKind.MODIFIER),           // Bengali dari
                Rule("^", "ঁ", RuleKind.MODIFIER),           // alt chandrabindu
                Rule(":", "ঃ", RuleKind.MODIFIER),           // alt visarga
            ),
        )
    }

    /**
     * All rule Latin prefixes, sorted longest-first. The tokenizer walks
     * this list at each position and returns the first rule whose Latin
     * form matches and whose prev/next context predicates are satisfied.
     */
    val prefixesLongestFirst: List<String> =
        all.map { it.latin }.distinct().sortedByDescending { it.length }

    private val byExactLatin: Map<String, List<Rule>> = all.groupBy { it.latin }

    /**
     * Return all rules registered under the given exact Latin form.
     * Multiple rules may share a Latin key (e.g. distinct context matches),
     * in which case the tokenizer picks the first whose context is
     * satisfied.
     */
    fun rulesFor(latin: String): List<Rule> = byExactLatin[latin] ?: emptyList()

    /** Convenience: return the first (default-context) rule for a Latin key. */
    fun ruleFor(latin: String): Rule? = byExactLatin[latin]?.firstOrNull()
}
