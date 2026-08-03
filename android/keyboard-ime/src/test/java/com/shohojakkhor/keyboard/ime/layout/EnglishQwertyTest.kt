package com.shohojakkhor.keyboard.ime.layout

import com.shohojakkhor.keyboard.ime.state.KeyboardMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnglishQwertyTest {

    @Test
    fun `returns null for non-english modes`() {
        assertNull(EnglishQwerty.layoutFor(KeyboardMode.SYMBOLS_PAGE1))
        assertNull(EnglishQwerty.layoutFor(KeyboardMode.BENGALI_BANGLISH))
    }

    @Test
    fun `layout has 4 rows`() {
        val layout = EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_LOWER)
        assertNotNull(layout)
        assertEquals(4, layout.rows.size)
    }

    @Test
    fun `first row is q through p in order`() {
        val layout = EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_LOWER)!!
        val labels = layout.rows[0].keys.map { it.label }
        assertEquals(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), labels)
    }

    @Test
    fun `upper mode labels are uppercase`() {
        val layout = EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_UPPER)!!
        val firstRowLabels = layout.rows[0].keys.map { it.label }
        assertEquals(listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"), firstRowLabels)
    }

    @Test
    fun `third row starts with shift and ends with backspace`() {
        val layout = EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_LOWER)!!
        val row3 = layout.rows[2]
        assertTrue(row3.keys.first().action is KeyAction.Shift)
        assertTrue(row3.keys.last().action is KeyAction.Backspace)
    }

    @Test
    fun `bottom row contains space enter language and picker`() {
        val layout = EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_LOWER)!!
        val actions = layout.rows[3].keys.map { it.action::class.simpleName }
        assertTrue(actions.contains(KeyAction.Space::class.simpleName))
        assertTrue(actions.contains(KeyAction.Enter::class.simpleName))
        assertTrue(actions.contains(KeyAction.ToggleLanguage::class.simpleName))
        assertTrue(actions.contains(KeyAction.ShowImePicker::class.simpleName))
        assertTrue(actions.contains(KeyAction.ToggleSymbols::class.simpleName))
    }

    @Test
    fun `layout id reflects shift state`() {
        assertEquals(
            "en_qwerty_lower",
            EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_LOWER)!!.id,
        )
        assertEquals(
            "en_qwerty_upper",
            EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_UPPER)!!.id,
        )
        assertEquals(
            "en_qwerty_upper",
            EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_CAPS)!!.id,
        )
    }

    @Test
    fun `all character keys have positive width`() {
        val layout = EnglishQwerty.layoutFor(KeyboardMode.ENGLISH_LOWER)!!
        for (row in layout.rows) {
            for (key in row.keys) {
                assertTrue(key.widthWeight > 0f, "widthWeight for ${key.label} must be > 0")
            }
        }
    }
}
