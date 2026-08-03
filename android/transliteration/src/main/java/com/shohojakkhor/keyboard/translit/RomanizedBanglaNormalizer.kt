package com.shohojakkhor.keyboard.translit

import kotlin.math.min

/** Conservative, deterministic normalization for noisy Romanized Bangla. */
public object RomanizedBanglaNormalizer {

    public fun normalize(input: String): String = input
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9 ]+"), "")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("(.)\\1{2,}")) { match -> match.groupValues[1].repeat(2) }

    /**
     * A small candidate set; deliberately bounded to avoid combinatorial
     * expansion on the IME thread.
     */
    public fun variants(input: String): List<String> {
        val base = normalize(input)
        if (base.isEmpty()) return emptyList()
        val variants = linkedSetOf(base)
        variants += base.replace("ph", "f")
        variants += base.replace("q", "k")
        variants += base.replace("z", "j")
        variants += base.replace("bh", "v")
        variants += base.replace("sh", "s")
        variants += base.replace("aa", "a").replace("ee", "i").replace("oo", "u")
        if (' ' in base) variants += base.replace(" ", "")
        return variants.filter { it.isNotEmpty() }.take(MAX_VARIANTS)
    }

    /**
     * Weighted edit distance. Phonetically adjacent Roman spellings and vowel
     * omissions are cheaper than unrelated edits.
     */
    public fun distance(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a == b) return 0.0
        if (a.isEmpty()) return b.length.toDouble()
        if (b.isEmpty()) return a.length.toDouble()

        var previous = DoubleArray(b.length + 1) { it.toDouble() }
        for (i in 1..a.length) {
            val current = DoubleArray(b.length + 1)
            current[0] = i.toDouble()
            for (j in 1..b.length) {
                val ca = a[i - 1]
                val cb = b[j - 1]
                val deleteCost = if (ca in VOWELS) 0.55 else 1.0
                val insertCost = if (cb in VOWELS) 0.55 else 1.0
                val substitution = substitutionCost(ca, cb)
                current[j] = min(
                    min(previous[j] + deleteCost, current[j - 1] + insertCost),
                    previous[j - 1] + substitution,
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    public fun acceptableDistance(input: String): Double = when (normalize(input).length) {
        in 0..2 -> 0.0
        in 3..4 -> 0.85
        in 5..7 -> 1.45
        else -> 2.1
    }

    private fun substitutionCost(a: Char, b: Char): Double {
        if (a == b) return 0.0
        if (a in VOWELS && b in VOWELS) return 0.4
        if (PHONETIC_GROUPS.any { a in it && b in it }) return 0.35
        return 1.0
    }

    private const val MAX_VARIANTS = 8
    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
    private val PHONETIC_GROUPS = listOf(
        setOf('b', 'v'),
        setOf('s', 'z'),
        setOf('j', 'z'),
        setOf('k', 'q', 'c'),
        setOf('f', 'p'),
        setOf('t', 'd'),
    )
}
