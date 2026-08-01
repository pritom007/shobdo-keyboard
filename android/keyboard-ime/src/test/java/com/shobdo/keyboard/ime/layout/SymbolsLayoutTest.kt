package com.shobdo.keyboard.ime.layout

import com.shobdo.keyboard.ime.state.KeyboardMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SymbolsLayoutTest {

    @Test
    fun `returns null for letter modes`() {
        assertNull(SymbolsLayout.layoutFor(KeyboardMode.ENGLISH_LOWER))
        assertNull(SymbolsLayout.layoutFor(KeyboardMode.BENGALI_BANGLISH))
        assertNull(SymbolsLayout.layoutFor(KeyboardMode.BENGALI_BANGLISH_UPPER))
    }

    @Test
    fun `english page 1 starts with ascii digits 0-9`() {
        val layout = SymbolsLayout.layoutFor(KeyboardMode.SYMBOLS_PAGE1)!!
        assertEquals("symbols_page1", layout.id)
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            layout.rows[0].keys.map { it.label },
        )
    }

    // -- Bengali symbols pages ------------------------------------------------

    @Test
    fun `bengali page 1 starts with bengali digits 0-9`() {
        val layout = SymbolsLayout.layoutFor(KeyboardMode.SYMBOLS_PAGE1_BN)!!
        assertEquals("symbols_page1_bn", layout.id)
        assertEquals(
            listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"),
            layout.rows[0].keys.map { it.label },
            "BN page 1 must lead with Bengali digits, not ASCII",
        )
    }

    @Test
    fun `bengali page 1 includes dari double-dari and khanda-ta`() {
        val layout = SymbolsLayout.layoutFor(KeyboardMode.SYMBOLS_PAGE1_BN)!!
        val allLabels = layout.rows.flatMap { it.keys.map { k -> k.label } }
        assertTrue("।" in allLabels, "dari (।) must be on BN page 1")
        assertTrue("॥" in allLabels, "double dari (॥) must be on BN page 1")
        assertTrue("ৎ" in allLabels, "khanda ta (ৎ) must be on BN page 1")
    }

    @Test
    fun `bengali page 2 exists and is reachable`() {
        val layout = SymbolsLayout.layoutFor(KeyboardMode.SYMBOLS_PAGE2_BN)!!
        assertEquals("symbols_page2_bn", layout.id)
        assertEquals(4, layout.rows.size)
    }

    @Test
    fun `bengali symbols bottom row has back-to-bangla and En lang toggle`() {
        val layout = SymbolsLayout.layoutFor(KeyboardMode.SYMBOLS_PAGE1_BN)!!
        val bottomRow = layout.rows[3].keys
        val backKey = bottomRow.first { it.action is KeyAction.ToggleSymbols }
        assertEquals("অ", backKey.label, "BN symbols back-to-letters key should read অ")
        val langToggle = bottomRow.first { it.action is KeyAction.ToggleLanguage }
        assertEquals("En", langToggle.label, "BN symbols lang toggle should read En")
    }

    @Test
    fun `english symbols bottom row still has ABC and bangla lang toggle`() {
        val layout = SymbolsLayout.layoutFor(KeyboardMode.SYMBOLS_PAGE1)!!
        val bottomRow = layout.rows[3].keys
        val backKey = bottomRow.first { it.action is KeyAction.ToggleSymbols }
        assertEquals("ABC", backKey.label, "EN symbols back-to-letters key should read ABC")
        val langToggle = bottomRow.first { it.action is KeyAction.ToggleLanguage }
        assertEquals("বাং", langToggle.label, "EN symbols lang toggle should read বাং")
    }

    @Test
    fun `all four symbol pages have 4 rows`() {
        for (mode in listOf(
            KeyboardMode.SYMBOLS_PAGE1,
            KeyboardMode.SYMBOLS_PAGE2,
            KeyboardMode.SYMBOLS_PAGE1_BN,
            KeyboardMode.SYMBOLS_PAGE2_BN,
        )) {
            assertNotNull(SymbolsLayout.layoutFor(mode))
            assertEquals(4, SymbolsLayout.layoutFor(mode)!!.rows.size, "rows in $mode")
        }
    }

    @Test
    fun `every key on every symbol page has positive width`() {
        for (mode in listOf(
            KeyboardMode.SYMBOLS_PAGE1,
            KeyboardMode.SYMBOLS_PAGE2,
            KeyboardMode.SYMBOLS_PAGE1_BN,
            KeyboardMode.SYMBOLS_PAGE2_BN,
        )) {
            val layout = SymbolsLayout.layoutFor(mode)!!
            for (row in layout.rows) {
                for (key in row.keys) {
                    assertTrue(key.widthWeight > 0f, "widthWeight for ${key.label} in $mode must be > 0")
                }
            }
        }
    }
}
