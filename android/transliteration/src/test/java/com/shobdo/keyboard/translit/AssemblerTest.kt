package com.shobdo.keyboard.translit

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests the position-aware Bengali assembler. Test invariants describe the
 * expected Bengali output for common Banglish inputs.
 */
class AssemblerTest {

    private fun run(input: String): String =
        Assembler.assemble(Tokenizer.tokenize(input))

    @Test
    fun `word-initial vowel uses independent form`() {
        assertEquals("আ", run("a"))
        assertEquals("ই", run("i"))
        assertEquals("উ", run("u"))
        assertEquals("এ", run("e"))
        assertEquals("ও", run("o"))
    }

    @Test
    fun `vowel after consonant uses matra form`() {
        assertEquals("কা", run("ka"))
        assertEquals("কি", run("ki"))
        assertEquals("কু", run("ku"))
        assertEquals("কে", run("ke"))
        assertEquals("কো", run("ko"))
    }

    @Test
    fun `long vowels`() {
        assertEquals("কী", run("kii"))
        assertEquals("কূ", run("kuu"))
    }

    @Test
    fun `two consonants form conjunct with hasant`() {
        // ক্ম = ক + ্ + ম
        val out = run("km")
        assertEquals("ক্ম", out)
    }

    @Test
    fun `ami produces আমি`() {
        assertEquals("আমি", run("ami"))
    }

    @Test
    fun `tumi produces তুমি`() {
        assertEquals("তুমি", run("tumi"))
    }

    @Test
    fun `kotha produces কোথা`() {
        // Rule-based only: k+o+th+a → ক + ো + থ + া = কোথা.
        // The "kotha → কথা" mapping is handled by the dictionary, not the
        // pure assembler.
        assertEquals("কোথা", run("kotha"))
    }

    @Test
    fun `valo produces ভালো`() {
        // v → ভ, a → া, l → ল, o → ো
        assertEquals("ভালো", run("valo"))
    }

    @Test
    fun `bhalo produces ভালো`() {
        assertEquals("ভালো", run("bhalo"))
    }

    @Test
    fun `khabo produces খাব`() {
        // kh, a → খ + া = খা; b, o → ব + ো = বো
        assertEquals("খাবো", run("khabo"))
    }

    @Test
    fun `bangla produces বাংলা`() {
        // b, a, ng (modifier), l, a
        assertEquals("বাংলা", run("bangla"))
    }

    @Test
    fun `space resets syllable context so next vowel is independent`() {
        val out = run("ami a")
        // "আমি" + " " + "আ"
        assertEquals("আমি আ", out)
    }

    @Test
    fun `retroflex T maps to ট`() {
        assertEquals("টা", run("Ta"))
    }

    @Test
    fun `passthrough characters preserved`() {
        assertEquals("আমি1", run("ami1"))
    }
}
