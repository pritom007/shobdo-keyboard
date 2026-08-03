package com.shohojakkhor.keyboard.speech

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class SpeechConfigTest {
    @Test
    fun `production backend uses Render over HTTPS`() {
        assertEquals(
            "https://shobdo-keyboard-backend.onrender.com",
            SpeechConfig.PRODUCTION_BASE_URL,
        )
        assertTrue(SpeechConfig.PRODUCTION_BASE_URL.startsWith("https://"))
    }
}
