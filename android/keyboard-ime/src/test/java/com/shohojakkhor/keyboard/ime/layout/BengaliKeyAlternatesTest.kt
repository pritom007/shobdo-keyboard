package com.shohojakkhor.keyboard.ime.layout

import com.shohojakkhor.keyboard.ime.state.KeyboardMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BengaliKeyAlternatesTest {
    @Test
    fun `all 26 Latin letters have Bengali alternatives`() {
        assertEquals(('a'..'z').toSet(), BengaliKeyAlternates.latinKeys)
        ('a'..'z').forEach { letter ->
            assertTrue(BengaliKeyAlternates.forLatin(letter.toString()).isNotEmpty(), letter.toString())
        }
    }

    @Test
    fun `a key exposes requested keycap hint order`() {
        assertEquals(listOf("এ", "আ"), BengaliKeyAlternates.forLatin("a").take(2))
        assertEquals(BengaliKeyAlternates.forLatin("a"), BengaliKeyAlternates.forLatin("A"))
    }

    @Test
    fun `vowel keys expose dependent signs for direct word construction`() {
        assertTrue("া" in BengaliKeyAlternates.forLatin("a"))
        assertTrue("ি" in BengaliKeyAlternates.forLatin("i"))
        assertTrue("ু" in BengaliKeyAlternates.forLatin("u"))
        assertTrue("ে" in BengaliKeyAlternates.forLatin("e"))
        assertTrue("ো" in BengaliKeyAlternates.forLatin("o"))
    }

    @Test
    fun `alternates appear only on Bengali letter layouts`() {
        val bengaliKeys = BengaliBanglish.layoutFor(KeyboardMode.BENGALI_BANGLISH)!!
            .rows.flatMap(KeyRow::keys)
            .filter { it.action is KeyAction.Character && it.label.singleOrNull()?.isLetter() == true }
        assertEquals(26, bengaliKeys.size)
        assertTrue(bengaliKeys.all { it.alternates.isNotEmpty() })

        val englishKeys = EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_LOWER)!!
            .rows.flatMap(KeyRow::keys)
        assertTrue(englishKeys.all { it.alternates.isEmpty() })
    }
}
