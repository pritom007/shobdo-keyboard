package com.shohojakkhor.keyboard.translit

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionMemoryTest {

    @Test
    fun `unknown key has zero boost`() {
        val m = InMemorySelectionMemory()
        assertEquals(0.0, m.boostFor("ami", "আমি"))
    }

    @Test
    fun `recorded pair has positive boost`() {
        val m = InMemorySelectionMemory()
        m.record("ami", "আমি")
        assertTrue(m.boostFor("ami", "আমি") > 0.0)
    }

    @Test
    fun `boost grows with repeated recording`() {
        val m = InMemorySelectionMemory()
        m.record("ami", "আমি")
        val once = m.boostFor("ami", "আমি")
        m.record("ami", "আমি")
        m.record("ami", "আমি")
        val thrice = m.boostFor("ami", "আমি")
        assertTrue(thrice > once, "boost should grow: $once → $thrice")
    }

    @Test
    fun `most-recent bengali gets extra recency bonus over equal-count peer`() {
        val m = InMemorySelectionMemory()
        // Two candidates, same count, but B was recorded more recently.
        m.record("sh", "শ")
        m.record("sh", "ষ")
        m.record("sh", "শ")
        m.record("sh", "ষ")   // last write

        assertTrue(
            m.boostFor("sh", "ষ") > m.boostFor("sh", "শ"),
            "recency should tie-break in favour of ষ",
        )
    }

    @Test
    fun `lookup is case-insensitive on the latin key`() {
        val m = InMemorySelectionMemory()
        m.record("Ami", "আমি")
        assertTrue(m.boostFor("ami", "আমি") > 0.0)
        assertTrue(m.boostFor("AMI", "আমি") > 0.0)
    }

    @Test
    fun `clear resets state`() {
        val m = InMemorySelectionMemory()
        m.record("ami", "আমি")
        m.clear()
        assertEquals(0.0, m.boostFor("ami", "আমি"))
    }
}
