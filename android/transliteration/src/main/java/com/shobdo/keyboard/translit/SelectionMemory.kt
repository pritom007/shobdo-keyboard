package com.shobdo.keyboard.translit

/**
 * Records which Bengali candidate the user picked for a given Latin input,
 * and returns a boost score used to re-rank future candidates for the same
 * input.
 *
 * The M2 implementation lives in memory and does not persist across process
 * restarts. A DataStore-backed version lands at end of M2; a Room-backed
 * personal-dictionary version lands at M6.
 *
 * The store MUST never persist content into logs (§26). Implementations
 * should treat every recorded pair as sensitive.
 */
public interface SelectionMemory {

    /**
     * Record that the user picked [bengali] as the transliteration of [latin].
     * Repeated recording increases the boost.
     */
    public fun record(latin: String, bengali: String)

    /**
     * Returns a non-negative boost score for the pair (latin → bengali).
     * Larger means the user has picked this pair more times / more recently.
     * Zero means "never selected".
     */
    public fun boostFor(latin: String, bengali: String): Double

    /** Wipe all learned selections. */
    public fun clear()
}

/**
 * In-memory selection memory with mild recency weighting.
 *
 * The exact boost formula is intentionally simple:
 *
 *     boost = count * (1.0 + recencyBonus)
 *
 * where `recencyBonus` is 0.5 for the most-recently-used bengali for this
 * latin key, tapering to 0 for others. This lets the top choice win ties
 * even when both candidates have equal absolute counts.
 */
public class InMemorySelectionMemory : SelectionMemory {

    private data class Entry(var count: Int, var lastSeq: Long)

    private val map: MutableMap<String, MutableMap<String, Entry>> = mutableMapOf()
    private var sequence: Long = 0

    override fun record(latin: String, bengali: String) {
        val key = latin.lowercase()
        val bucket = map.getOrPut(key) { mutableMapOf() }
        val entry = bucket.getOrPut(bengali) { Entry(count = 0, lastSeq = 0) }
        entry.count += 1
        entry.lastSeq = ++sequence
    }

    override fun boostFor(latin: String, bengali: String): Double {
        val bucket = map[latin.lowercase()] ?: return 0.0
        val entry = bucket[bengali] ?: return 0.0

        val mostRecentBengali = bucket.maxByOrNull { it.value.lastSeq }?.key
        val recencyBonus = if (bengali == mostRecentBengali) 0.5 else 0.0
        return entry.count * (1.0 + recencyBonus)
    }

    override fun clear() {
        map.clear()
        sequence = 0
    }
}
