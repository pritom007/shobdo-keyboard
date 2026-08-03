package com.shohojakkhor.keyboard.translit

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoisySuggestionEngineTest {
    private val engine = AvroLikeEngine()

    @Test
    fun `traditional valo remains correct`() {
        assertEquals("ভালো", engine.suggest(SuggestionRequest("valo")).first().bengali)
    }

    @Test
    fun `missing-vowel vlo suggests ভালো`() {
        assertTrue(engine.suggest(SuggestionRequest("vlo")).any { it.bengali == "ভালো" })
    }

    @Test
    fun `compressed kmn suggests কেমন`() {
        assertEquals("কেমন", engine.suggest(SuggestionRequest("kmn")).first().bengali)
    }

    @Test
    fun `colloquial korsi offers standard and conversational readings`() {
        val suggestions = engine.suggest(SuggestionRequest("korsi", maxCandidates = 5))
        assertTrue(suggestions.any { it.bengali == "করেছি" })
        assertTrue(suggestions.any { it.bengali == "করছি" })
    }

    @Test
    fun `dialect-preserving mode ranks colloquial output first`() {
        val suggestions = engine.suggest(
            SuggestionRequest("kortasi", preferStandardBangla = false, maxCandidates = 5),
        )
        assertEquals("করতেছি", suggestions.first().bengali)
    }

    @Test
    fun `noisy greeting becomes a Bengali phrase`() {
        val suggestion = engine.suggest(SuggestionRequest("aslmualikum", maxCandidates = 3)).first()
        assertEquals("আসসালামু আলাইকুম", suggestion.bengali)
        assertEquals(CandidateKind.PHRASE, suggestion.kind)
    }

    @Test
    fun `emoji stays below likely Bengali words`() {
        val suggestions = engine.suggest(SuggestionRequest("valo", maxCandidates = 5))
        assertEquals("ভালো", suggestions.first().bengali)
        assertTrue(suggestions.any { it.kind == CandidateKind.EMOJI })
    }

    @Test
    fun `literal escape hatch remains available in expanded results`() {
        val suggestions = engine.suggest(SuggestionRequest("kmn", maxCandidates = 20))
        assertTrue(suggestions.any { it.bengali == "kmn" && it.kind == CandidateKind.LITERAL })
    }

    @Test
    fun `noisy provider can be disabled`() {
        val suggestions = engine.suggest(
            SuggestionRequest("kmn", maxCandidates = 10, enableNoisyMatching = false),
        )
        assertTrue(suggestions.none { it.bengali == "কেমন" && it.source == CandidateSource.FUZZY_PHONETIC })
    }

    @Test
    fun `idle context after ami valo suggests আছি`() {
        val suggestions = engine.suggest(
            SuggestionRequest("", previousWords = listOf("ami", "valo"), maxCandidates = 3),
        )
        assertTrue(suggestions.any { it.bengali == "আছি" && it.replacement == CandidateReplacement.INSERT })
    }
}
