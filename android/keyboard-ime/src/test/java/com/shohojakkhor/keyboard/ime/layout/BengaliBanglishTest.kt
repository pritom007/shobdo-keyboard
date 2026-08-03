package com.shohojakkhor.keyboard.ime.layout

import com.shohojakkhor.keyboard.ime.state.KeyboardMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BengaliBanglishTest {

    @Test
    fun `returns null for non-bangla modes`() {
        assertNull(BengaliBanglish.layoutFor(KeyboardMode.ENGLISH_LOWER))
        assertNull(BengaliBanglish.layoutFor(KeyboardMode.SYMBOLS_PAGE1))
        assertNull(BengaliBanglish.layoutFor(KeyboardMode.SYMBOLS_PAGE1_BN))
    }

    @Test
    fun `lower mode shows lowercase latin keycaps`() {
        val layout = BengaliBanglish.layoutFor(KeyboardMode.BENGALI_BANGLISH)!!
        val firstRowLabels = layout.rows[0].keys.map { it.label }
        assertEquals(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            firstRowLabels,
        )
        assertEquals("bn_banglish_lower", layout.id)
    }

    @Test
    fun `upper mode shows uppercase latin keycaps for avro-style retroflex`() {
        val layout = BengaliBanglish.layoutFor(KeyboardMode.BENGALI_BANGLISH_UPPER)!!
        val firstRowLabels = layout.rows[0].keys.map { it.label }
        assertEquals(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            firstRowLabels,
            "shift must surface uppercase Latin so T→ট, D→ড, etc. via the engine",
        )
        assertEquals("bn_banglish_upper", layout.id)
    }

    @Test
    fun `caps mode also shows uppercase latin keycaps`() {
        val layout = BengaliBanglish.layoutFor(KeyboardMode.BENGALI_BANGLISH_CAPS)!!
        val firstRowLabels = layout.rows[0].keys.map { it.label }
        assertEquals(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            firstRowLabels,
        )
        assertEquals("bn_banglish_upper", layout.id)
    }

    @Test
    fun `language toggle key reads En in all bangla layers`() {
        for (mode in listOf(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardMode.BENGALI_BANGLISH_UPPER,
            KeyboardMode.BENGALI_BANGLISH_CAPS,
        )) {
            val layout = BengaliBanglish.layoutFor(mode)!!
            val toggle = layout.rows[3].keys.first { it.action is KeyAction.ToggleLanguage }
            assertEquals("En", toggle.label, "lang toggle in $mode should read En")
        }
    }

    @Test
    fun `layout has 4 rows in every bangla layer`() {
        for (mode in listOf(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardMode.BENGALI_BANGLISH_UPPER,
            KeyboardMode.BENGALI_BANGLISH_CAPS,
        )) {
            assertNotNull(BengaliBanglish.layoutFor(mode))
            assertEquals(4, BengaliBanglish.layoutFor(mode)!!.rows.size, "rows in $mode")
        }
    }

    @Test
    fun `all character keys have positive width in upper mode`() {
        val layout = BengaliBanglish.layoutFor(KeyboardMode.BENGALI_BANGLISH_UPPER)!!
        for (row in layout.rows) {
            for (key in row.keys) {
                assertTrue(key.widthWeight > 0f, "widthWeight for ${key.label} must be > 0")
            }
        }
    }
}
