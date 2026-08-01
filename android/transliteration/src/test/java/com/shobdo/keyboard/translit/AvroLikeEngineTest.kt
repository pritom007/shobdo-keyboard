package com.shobdo.keyboard.translit

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AvroLikeEngineTest {

    private lateinit var memory: InMemorySelectionMemory
    private lateinit var engine: AvroLikeEngine

    @Before
    fun setUp() {
        memory = InMemorySelectionMemory()
        engine = AvroLikeEngine(memory = memory)
    }

    // -- Invariants that must always hold ------------------------------------

    @Test
    fun `blank input returns empty list`() {
        assertTrue(engine.transliterate("").isEmpty())
        assertTrue(engine.transliterate("   ").isEmpty())
    }

    @Test
    fun `every result includes a literal passthrough somewhere`() {
        val cands = engine.transliterate("qwerty12", maxCandidates = 20)
        assertTrue(
            cands.any { it.bengali == "qwerty12" },
            "raw Latin escape hatch must always be reachable",
        )
    }

    @Test
    fun `results never exceed maxCandidates`() {
        val cands = engine.transliterate("bhalobasa", maxCandidates = 3)
        assertTrue(cands.size <= 3, "got ${cands.size} > 3")
    }

    @Test
    fun `results are deduplicated by bengali text`() {
        val cands = engine.transliterate("ami", maxCandidates = 10)
        assertEquals(
            cands.map { it.bengali }.toSet().size,
            cands.size,
            "duplicate bengali in candidates: ${cands.map { it.bengali }}",
        )
    }

    @Test
    fun `results are sorted by descending score`() {
        val cands = engine.transliterate("kotha", maxCandidates = 10)
        val scores = cands.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }

    // -- Dictionary hits should beat pure rule-based ------------------------

    @Test
    fun `common dictionary word appears first`() {
        val cands = engine.transliterate("ami", maxCandidates = 5)
        assertEquals("আমি", cands.first().bengali)
        assertEquals(CandidateSource.DICTIONARY, cands.first().source)
    }

    @Test
    fun `tumi returns তুমি at the top`() {
        val cands = engine.transliterate("tumi")
        assertEquals("তুমি", cands.first().bengali)
    }

    @Test
    fun `valo returns ভালো at the top`() {
        val cands = engine.transliterate("valo")
        assertEquals("ভালো", cands.first().bengali)
    }

    @Test
    fun `dhonnobad returns ধন্যবাদ at the top`() {
        val cands = engine.transliterate("dhonnobad")
        assertEquals("ধন্যবাদ", cands.first().bengali)
    }

    // -- Rule engine still works for words not in the dictionary -----------

    @Test
    fun `bangla assembles correctly via rules even without dictionary hit`() {
        val cands = engine.transliterate("bangla", maxCandidates = 5)
        assertTrue(
            cands.any { it.bengali == "বাংলা" },
            "expected বাংলা in ${cands.map { it.bengali }}",
        )
    }

    @Test
    fun `unknown word yields at least a rule-based primary candidate`() {
        val cands = engine.transliterate("zxvpqr")
        val hasRule = cands.any {
            it.source == CandidateSource.RULE_PRIMARY || it.source == CandidateSource.RULE_ALTERNATE
        }
        assertTrue(hasRule, "no rule-based candidate for zxvpqr: $cands")
    }

    // -- Ambiguous consonants surface alternates ---------------------------

    @Test
    fun `ambiguous t generates a ট alternate`() {
        val cands = engine.transliterate("tata", maxCandidates = 10)
        // Primary should use ত (dental). At least one alternate should
        // contain ট (retroflex) somewhere.
        val alts = cands.filter { it.source == CandidateSource.RULE_ALTERNATE }
        assertTrue(alts.any { "ট" in it.bengali }, "no retroflex alt: $cands")
    }

    // -- Selection memory learns and re-ranks ------------------------------

    @Test
    fun `after user picks a candidate it is boosted on the next call`() {
        // "sh" is ambiguous — primary → শ, alternate → ষ.
        // Suppose the user prefers ষ.
        val firstPass = engine.transliterate("sh", maxCandidates = 5)
        val topBefore = firstPass.first().bengali

        engine.onUserSelection("sh", "ষ")
        engine.onUserSelection("sh", "ষ")
        engine.onUserSelection("sh", "ষ")

        val secondPass = engine.transliterate("sh", maxCandidates = 5)
        assertEquals("ষ", secondPass.first().bengali,
            "expected user-selected ষ to win, was $topBefore → ${secondPass.first().bengali}")
        assertEquals(CandidateSource.MEMORY, secondPass.first().source)
    }

    @Test
    fun `memory boost carries a MEMORY source label`() {
        engine.onUserSelection("ami", "আমি")
        val cands = engine.transliterate("ami")
        assertEquals(CandidateSource.MEMORY, cands.first().source)
    }

    @Test
    fun `clearing memory removes learned boost`() {
        engine.onUserSelection("sh", "ষ")
        engine.onUserSelection("sh", "ষ")
        engine.onUserSelection("sh", "ষ")
        memory.clear()

        val cands = engine.transliterate("sh")
        // Primary reading of "sh" is শ.
        assertEquals("শ", cands.first().bengali)
    }

    @Test
    fun `english word can still be inserted via literal passthrough`() {
        val cands = engine.transliterate("whatsapp", maxCandidates = 10)
        val literal = cands.firstOrNull { it.bengali == "whatsapp" }
        assertNotNull(literal, "literal Latin passthrough for 'whatsapp' should be present")
    }
}
