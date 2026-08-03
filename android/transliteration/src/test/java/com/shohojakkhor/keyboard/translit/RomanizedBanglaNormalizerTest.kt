package com.shohojakkhor.keyboard.translit

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RomanizedBanglaNormalizerTest {
    @Test
    fun `normalization removes punctuation and limits repeated letters`() {
        assertEquals("valloo kmn", RomanizedBanglaNormalizer.normalize("  Vallooo!!!   kmn "))
    }

    @Test
    fun `vowel omissions are cheaper than unrelated edits`() {
        val omittedVowel = RomanizedBanglaNormalizer.distance("vllo", "valo")
        val unrelated = RomanizedBanglaNormalizer.distance("vllo", "doctor")
        assertTrue(omittedVowel < unrelated)
    }

    @Test
    fun `common phonetic substitutions are cheap`() {
        assertTrue(
            RomanizedBanglaNormalizer.distance("valo", "balo") <
                RomanizedBanglaNormalizer.distance("valo", "talo"),
        )
    }
}
