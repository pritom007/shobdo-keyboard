package com.shohojakkhor.keyboard.translit

internal data class AliasEntry(
    val latin: String,
    val bengali: String,
    val source: CandidateSource,
    val frequency: Int,
    val colloquialBengali: String? = null,
)

/** High-value noisy and colloquial forms observed in Bangladeshi messaging. */
internal object LocalSuggestionLexicon {
    val aliases = listOf(
        AliasEntry("vlo", "ভালো", CandidateSource.FUZZY_PHONETIC, 100),
        AliasEntry("vllo", "ভালো", CandidateSource.FUZZY_PHONETIC, 98),
        AliasEntry("valo", "ভালো", CandidateSource.NORMALIZED_DICTIONARY, 100),
        AliasEntry("korsi", "করেছি", CandidateSource.DIALECT, 96, "করছি"),
        AliasEntry("kortasi", "করছি", CandidateSource.DIALECT, 92, "করতেছি"),
        AliasEntry("kortesi", "করছি", CandidateSource.DIALECT, 94, "করতেছি"),
        AliasEntry("aitasi", "আসছি", CandidateSource.DIALECT, 92, "আসতেছি"),
        AliasEntry("aitesi", "আসছি", CandidateSource.DIALECT, 92, "আসতেছি"),
        AliasEntry("asi", "আছি", CandidateSource.FUZZY_PHONETIC, 96),
        AliasEntry("aso", "আছ", CandidateSource.FUZZY_PHONETIC, 94),
        AliasEntry("kmn", "কেমন", CandidateSource.FUZZY_PHONETIC, 98),
        AliasEntry("amr", "আমার", CandidateSource.FUZZY_PHONETIC, 98),
        AliasEntry("tmr", "তোমার", CandidateSource.FUZZY_PHONETIC, 96),
        AliasEntry("apnr", "আপনার", CandidateSource.FUZZY_PHONETIC, 94),
        AliasEntry("sorir", "শরীর", CandidateSource.FUZZY_PHONETIC, 95),
        AliasEntry("basai", "বাসায়", CandidateSource.FUZZY_PHONETIC, 94),
        AliasEntry("aslmualikum", "আসসালামু আলাইকুম", CandidateSource.PHRASE, 100),
        AliasEntry("assalamu alaikum", "আসসালামু আলাইকুম", CandidateSource.PHRASE, 100),
        AliasEntry("salam alaikum", "আসসালামু আলাইকুম", CandidateSource.PHRASE, 94),
        AliasEntry("allah hafez", "আল্লাহ হাফেজ", CandidateSource.PHRASE, 96),
        AliasEntry("thik ase", "ঠিক আছে", CandidateSource.PHRASE, 96),
        AliasEntry("thikache", "ঠিক আছে", CandidateSource.PHRASE, 94),
    )

    val contextCompletions: Map<List<String>, List<Pair<String, String>>> = mapOf(
        listOf("ami") to listOf("valo" to "ভালো", "asi" to "আছি"),
        listOf("ami", "valo") to listOf("asi" to "আছি"),
        listOf("phone") to listOf("dio" to "দিও"),
        listOf("sorir") to listOf("kharap" to "খারাপ"),
        listOf("assalamu") to listOf("alaikum" to "আলাইকুম"),
    )

    val emojiKeywords: Map<String, List<String>> = mapOf(
        "hasi" to listOf("😊", "😂"),
        "haha" to listOf("😂", "😊"),
        "valo" to listOf("❤️", "👍"),
        "bhalobasha" to listOf("❤️"),
        "dukho" to listOf("😔"),
        "dua" to listOf("🤲"),
        "thik" to listOf("👍"),
        "congratulations" to listOf("🎉"),
    )
}
