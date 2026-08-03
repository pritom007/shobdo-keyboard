package com.shohojakkhor.keyboard.translit

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizerTest {

    @Test
    fun `empty input returns empty tokens`() {
        assertEquals(emptyList(), Tokenizer.tokenize(""))
    }

    @Test
    fun `single vowel yields one recognised token`() {
        val tokens = Tokenizer.tokenize("a")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.Recognised)
        assertEquals("a", tokens[0].latin)
    }

    @Test
    fun `longest match beats shorter prefix (kh not k)`() {
        val tokens = Tokenizer.tokenize("kh")
        assertEquals(1, tokens.size, "'kh' must tokenize as one rule, not k+h")
        assertEquals("kh", tokens[0].latin)
    }

    @Test
    fun `longest match beats shorter prefix (chh not ch or c)`() {
        val tokens = Tokenizer.tokenize("chh")
        assertEquals(1, tokens.size)
        assertEquals("chh", tokens[0].latin)
    }

    @Test
    fun `ng tokenizes as modifier when standalone`() {
        val tokens = Tokenizer.tokenize("ng")
        assertEquals(1, tokens.size)
        val rule = (tokens[0] as Token.Recognised).rule
        assertEquals(RuleKind.MODIFIER, rule.kind)
    }

    @Test
    fun `ami tokenizes into a plus m plus i`() {
        val tokens = Tokenizer.tokenize("ami")
        assertEquals(listOf("a", "m", "i"), tokens.map { it.latin })
    }

    @Test
    fun `case is preserved so T and t are different tokens`() {
        val lower = Tokenizer.tokenize("t")
        val upper = Tokenizer.tokenize("T")
        val ruleLower = (lower[0] as Token.Recognised).rule.bengali
        val ruleUpper = (upper[0] as Token.Recognised).rule.bengali
        assertEquals("ত", ruleLower)
        assertEquals("ট", ruleUpper)
    }

    @Test
    fun `unrecognised characters pass through`() {
        val tokens = Tokenizer.tokenize("a1b")
        assertEquals(3, tokens.size)
        assertTrue(tokens[0] is Token.Recognised)
        assertTrue(tokens[1] is Token.Passthrough)
        assertTrue(tokens[2] is Token.Recognised)
        assertEquals("1", (tokens[1] as Token.Passthrough).ch.toString())
    }

    @Test
    fun `bangla tokenizes with ng as anusvara modifier`() {
        val tokens = Tokenizer.tokenize("bangla")
        // b, a, ng, l, a
        assertEquals(listOf("b", "a", "ng", "l", "a"), tokens.map { it.latin })
    }
}
