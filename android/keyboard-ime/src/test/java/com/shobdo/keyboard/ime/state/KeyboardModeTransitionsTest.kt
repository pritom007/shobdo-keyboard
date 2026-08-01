package com.shobdo.keyboard.ime.state

import org.junit.Test
import kotlin.test.assertEquals

class KeyboardModeTransitionsTest {

    @Test
    fun `shift tap cycles lower to upper to caps to lower`() {
        val a = KeyboardModeTransitions.onShiftTap(KeyboardMode.ENGLISH_LOWER)
        val b = KeyboardModeTransitions.onShiftTap(a)
        val c = KeyboardModeTransitions.onShiftTap(b)
        assertEquals(KeyboardMode.ENGLISH_UPPER, a)
        assertEquals(KeyboardMode.ENGLISH_CAPS, b)
        assertEquals(KeyboardMode.ENGLISH_LOWER, c)
    }

    @Test
    fun `shift tap is no-op on non-english modes`() {
        assertEquals(
            KeyboardMode.SYMBOLS_PAGE1,
            KeyboardModeTransitions.onShiftTap(KeyboardMode.SYMBOLS_PAGE1),
        )
        assertEquals(
            KeyboardMode.BENGALI_BANGLISH,
            KeyboardModeTransitions.onShiftTap(KeyboardMode.BENGALI_BANGLISH),
        )
    }

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
    fun `symbols toggle flips between letters and symbols`() {
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
            KeyboardMode.ENGLISH_LOWER,
            KeyboardModeTransitions.onSymbolsPageFlip(KeyboardMode.ENGLISH_LOWER),
        )
    }

    @Test
    fun `isShifted and isEnglish helpers agree with data`() {
        assertEquals(true, KeyboardMode.ENGLISH_LOWER.isEnglish)
        assertEquals(true, KeyboardMode.ENGLISH_UPPER.isShifted)
        assertEquals(true, KeyboardMode.ENGLISH_CAPS.isShifted)
        assertEquals(false, KeyboardMode.ENGLISH_LOWER.isShifted)
        assertEquals(false, KeyboardMode.SYMBOLS_PAGE1.isEnglish)
        assertEquals(true, KeyboardMode.SYMBOLS_PAGE1.isSymbols)
    }
}
