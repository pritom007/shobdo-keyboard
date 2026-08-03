package com.shohojakkhor.keyboard.ime.state

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyboardModeTransitionsTest {

    // -- Shift cycle ----------------------------------------------------------

    @Test
    fun `shift tap cycles english lower to upper to caps to lower`() {
        val a = KeyboardModeTransitions.onShiftTap(KeyboardMode.ENGLISH_LOWER)
        val b = KeyboardModeTransitions.onShiftTap(a)
        val c = KeyboardModeTransitions.onShiftTap(b)
        assertEquals(KeyboardMode.ENGLISH_UPPER, a)
        assertEquals(KeyboardMode.ENGLISH_CAPS, b)
        assertEquals(KeyboardMode.ENGLISH_LOWER, c)
    }

    @Test
    fun `shift tap cycles bengali lower to upper to caps to lower`() {
        val a = KeyboardModeTransitions.onShiftTap(KeyboardMode.BENGALI_BANGLISH)
        val b = KeyboardModeTransitions.onShiftTap(a)
        val c = KeyboardModeTransitions.onShiftTap(b)
        assertEquals(KeyboardMode.BENGALI_BANGLISH_UPPER, a)
        assertEquals(KeyboardMode.BENGALI_BANGLISH_CAPS, b)
        assertEquals(KeyboardMode.BENGALI_BANGLISH, c)
    }

    @Test
    fun `shift tap is no-op on symbols pages`() {
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1,
            KeyboardModeTransitions.onShiftTap(KeyboardMode.SYMBOLS_PAGE1),
        )
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1_BN,
            KeyboardModeTransitions.onShiftTap(KeyboardMode.SYMBOLS_PAGE1_BN),
        )
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE2_BN,
            KeyboardModeTransitions.onShiftTap(KeyboardMode.SYMBOLS_PAGE2_BN),
        )
    }

    // -- One-shot decay -------------------------------------------------------

    @Test
    fun `one-shot upper decays to lower after commit but caps stays`() {
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.afterCharCommit(KeyboardMode.ENGLISH_UPPER),
        )
        assertEquals(
            KeyboardMode.ENGLISH_CAPS,
            KeyboardModeTransitions.afterCharCommit(KeyboardMode.ENGLISH_CAPS),
        )
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.afterCharCommit(KeyboardMode.ENGLISH_LOWER),
        )
    }

    @Test
    fun `bengali one-shot upper decays to lower after commit but caps stays`() {
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardModeTransitions.afterCharCommit(KeyboardMode.BENGALI_BANGLISH_UPPER),
        )
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH_CAPS,
            KeyboardModeTransitions.afterCharCommit(KeyboardMode.BENGALI_BANGLISH_CAPS),
        )
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardModeTransitions.afterCharCommit(KeyboardMode.BENGALI_BANGLISH),
        )
    }

    // -- Language toggle ------------------------------------------------------

    @Test
    fun `language toggle switches english and bengali both ways`() {
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardModeTransitions.onLanguageToggle(KeyboardMode.ENGLISH_LOWER),
        )
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardModeTransitions.onLanguageToggle(KeyboardMode.ENGLISH_CAPS),
        )
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardModeTransitions.onLanguageToggle(KeyboardMode.SYMBOLS_PAGE1),
        )
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onLanguageToggle(KeyboardMode.BENGALI_BANGLISH),
        )
    }

    @Test
    fun `language toggle from bengali upper or caps goes to english lower`() {
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onLanguageToggle(KeyboardMode.BENGALI_BANGLISH_UPPER),
        )
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onLanguageToggle(KeyboardMode.BENGALI_BANGLISH_CAPS),
        )
    }

    @Test
    fun `language toggle from bengali symbols goes to english lower`() {
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onLanguageToggle(KeyboardMode.SYMBOLS_PAGE1_BN),
        )
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onLanguageToggle(KeyboardMode.SYMBOLS_PAGE2_BN),
        )
    }

    // -- Symbols toggle -------------------------------------------------------

    @Test
    fun `symbols toggle flips between english letters and english symbols`() {
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1,
            KeyboardModeTransitions.onSymbolsToggle(KeyboardMode.ENGLISH_LOWER),
        )
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onSymbolsToggle(KeyboardMode.SYMBOLS_PAGE1),
        )
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onSymbolsToggle(KeyboardMode.SYMBOLS_PAGE2),
        )
    }

    @Test
    fun `symbols toggle flips between bangla letters and bengali symbols`() {
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1_BN,
            KeyboardModeTransitions.onSymbolsToggle(KeyboardMode.BENGALI_BANGLISH),
        )
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1_BN,
            KeyboardModeTransitions.onSymbolsToggle(KeyboardMode.BENGALI_BANGLISH_UPPER),
        )
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1_BN,
            KeyboardModeTransitions.onSymbolsToggle(KeyboardMode.BENGALI_BANGLISH_CAPS),
        )
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardModeTransitions.onSymbolsToggle(KeyboardMode.SYMBOLS_PAGE1_BN),
        )
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardModeTransitions.onSymbolsToggle(KeyboardMode.SYMBOLS_PAGE2_BN),
        )
    }

    // -- Symbols page flip ----------------------------------------------------

    @Test
    fun `symbols page flip cycles pages`() {
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE2,
            KeyboardModeTransitions.onSymbolsPageFlip(KeyboardMode.SYMBOLS_PAGE1),
        )
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1,
            KeyboardModeTransitions.onSymbolsPageFlip(KeyboardMode.SYMBOLS_PAGE2),
        )
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE2_BN,
            KeyboardModeTransitions.onSymbolsPageFlip(KeyboardMode.SYMBOLS_PAGE1_BN),
        )
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1_BN,
            KeyboardModeTransitions.onSymbolsPageFlip(KeyboardMode.SYMBOLS_PAGE2_BN),
        )
        assertEquals(
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onSymbolsPageFlip(KeyboardMode.ENGLISH_LOWER),
        )
    }

    // -- Helpers --------------------------------------------------------------

    @Test
    fun `isShifted isEnglish isSymbols helpers agree with data`() {
        assertEquals(true, KeyboardMode.ENGLISH_LOWER.isEnglish)
        assertEquals(true, KeyboardMode.ENGLISH_UPPER.isShifted)
        assertEquals(true, KeyboardMode.ENGLISH_CAPS.isShifted)
        assertEquals(false, KeyboardMode.ENGLISH_LOWER.isShifted)
        assertEquals(false, KeyboardMode.SYMBOLS_PAGE1.isEnglish)
        assertEquals(true, KeyboardMode.SYMBOLS_PAGE1.isSymbols)
    }

    @Test
    fun `isBengaliBanglish covers lower upper and caps`() {
        assertTrue(KeyboardMode.BENGALI_BANGLISH.isBengaliBanglish)
        assertTrue(KeyboardMode.BENGALI_BANGLISH_UPPER.isBengaliBanglish)
        assertTrue(KeyboardMode.BENGALI_BANGLISH_CAPS.isBengaliBanglish)
        assertFalse(KeyboardMode.ENGLISH_LOWER.isBengaliBanglish)
        assertFalse(KeyboardMode.SYMBOLS_PAGE1_BN.isBengaliBanglish,
            "Bengali symbols pages are not the letter mode")
    }

    @Test
    fun `isBengaliSymbols and isEnglishSymbols partition the symbol pages`() {
        assertTrue(KeyboardMode.SYMBOLS_PAGE1_BN.isBengaliSymbols)
        assertTrue(KeyboardMode.SYMBOLS_PAGE2_BN.isBengaliSymbols)
        assertTrue(KeyboardMode.SYMBOLS_PAGE1.isEnglishSymbols)
        assertTrue(KeyboardMode.SYMBOLS_PAGE2.isEnglishSymbols)
        assertFalse(KeyboardMode.SYMBOLS_PAGE1.isBengaliSymbols)
        assertFalse(KeyboardMode.SYMBOLS_PAGE1_BN.isEnglishSymbols)
        // isSymbols is the union of both.
        assertTrue(KeyboardMode.SYMBOLS_PAGE1_BN.isSymbols)
        assertTrue(KeyboardMode.SYMBOLS_PAGE2.isSymbols)
        assertFalse(KeyboardMode.BENGALI_BANGLISH.isSymbols)
    }

    @Test
    fun `isShifted is true for bengali upper and caps`() {
        assertTrue(KeyboardMode.BENGALI_BANGLISH_UPPER.isShifted)
        assertTrue(KeyboardMode.BENGALI_BANGLISH_CAPS.isShifted)
        assertFalse(KeyboardMode.BENGALI_BANGLISH.isShifted)
    }
}
