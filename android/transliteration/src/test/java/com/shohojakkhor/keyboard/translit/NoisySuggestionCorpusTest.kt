package com.shohojakkhor.keyboard.translit

import org.junit.Test
import kotlin.test.assertTrue

class NoisySuggestionCorpusTest {
    @Test
    fun `curated noisy corpus expected output appears in top three`() {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("noisy-suggestions.tsv"))
        val engine = AvroLikeEngine()
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.forEachIndexed { index, line ->
                val columns = line.split('\t')
                check(columns.size == 3) { "invalid corpus line ${index + 1}" }
                val (category, input, expected) = columns
                val actual = engine.suggest(SuggestionRequest(input, maxCandidates = 3)).map(Candidate::bengali)
                assertTrue(
                    expected in actual,
                    "$category '$input' expected '$expected' in top 3, got $actual",
                )
            }
        }
    }
}
