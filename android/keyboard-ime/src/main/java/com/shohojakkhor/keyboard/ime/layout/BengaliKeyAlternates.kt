package com.shohojakkhor.keyboard.ime.layout

/**
 * Direct Bengali characters available from each Latin key in Banglish mode.
 *
 * The first two entries become the small keycap hint. The remaining entries
 * stay reachable in the hold-and-slide popup. Similar Bengali sounds are kept
 * together so the mapping remains useful even when a user does not know a
 * formal Banglish spelling.
 */
public object BengaliKeyAlternates {
    private val mapping: Map<Char, List<String>> = mapOf(
        'a' to listOf("এ", "আ", "অ", "া", "ে"),
        'b' to listOf("ব", "ভ"),
        'c' to listOf("চ", "ছ"),
        'd' to listOf("দ", "ধ", "ড", "ঢ"),
        'e' to listOf("এ", "ঐ", "ে", "ৈ"),
        'f' to listOf("ফ"),
        'g' to listOf("গ", "ঘ"),
        'h' to listOf("হ", "ঃ"),
        'i' to listOf("ই", "ঈ", "ি", "ী"),
        'j' to listOf("জ", "ঝ", "য"),
        'k' to listOf("ক", "খ"),
        'l' to listOf("ল"),
        'm' to listOf("ম"),
        'n' to listOf("ন", "ণ", "ঞ", "ং"),
        'o' to listOf("ও", "ঔ", "ো", "ৌ"),
        'p' to listOf("প", "ফ"),
        'q' to listOf("ক", "খ"),
        'r' to listOf("র", "ড়", "ঢ়", "ঋ"),
        's' to listOf("স", "শ", "ষ"),
        't' to listOf("ত", "থ", "ট", "ঠ"),
        'u' to listOf("উ", "ঊ", "ু", "ূ"),
        'v' to listOf("ভ", "ব"),
        'w' to listOf("ও", "য়", "ওয়"),
        'x' to listOf("ক্ষ", "ক্স"),
        'y' to listOf("য", "য়"),
        'z' to listOf("জ", "ঝ"),
    )

    public fun forLatin(label: String): List<String> =
        label.singleOrNull()?.lowercaseChar()?.let(mapping::get).orEmpty()

    public val latinKeys: Set<Char>
        get() = mapping.keys
}
