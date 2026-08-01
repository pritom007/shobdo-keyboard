package com.shobdo.keyboard.ime.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure classification rules. We call the internal
 * [DefaultInputPrivacyPolicy.classifyFields] directly with plain
 * [EditorFields] so no framework object needs to be constructed.
 */
class InputPrivacyPolicyTest {

    @Test
    fun `plain text field is normal`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_TEXT,
                imeOptions = 0,
            ),
        )
        assertEquals(InputPrivacyMode.NORMAL, mode)
    }

    @Test
    fun `text password is sensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                imeOptions = 0,
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }

    @Test
    fun `visible password is sensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                imeOptions = 0,
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }

    @Test
    fun `web password is sensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                imeOptions = 0,
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }

    @Test
    fun `numeric password (PIN) is sensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                imeOptions = 0,
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }

    @Test
    fun `OTP hint is sensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_NUMBER,
                imeOptions = 0,
                hintText = "Enter OTP",
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }

    @Test
    fun `card number hint is sensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_NUMBER,
                imeOptions = 0,
                hintText = "Card number",
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }

    @Test
    fun `CVV hint is sensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_NUMBER,
                imeOptions = 0,
                hintText = "cvv",
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }

    @Test
    fun `no-personalized-learning flag alone is incognito`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_TEXT,
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            ),
        )
        assertEquals(InputPrivacyMode.INCOGNITO, mode)
    }

    @Test
    fun `sensitive beats incognito`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }

    @Test
    fun `benign hint does not flip to sensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_TEXT,
                imeOptions = 0,
                hintText = "Type a message to your friend",
            ),
        )
        assertEquals(InputPrivacyMode.NORMAL, mode)
    }

    @Test
    fun `hint match is case-insensitive`() {
        val mode = DefaultInputPrivacyPolicy.classifyFields(
            EditorFields(
                inputType = InputType.TYPE_CLASS_NUMBER,
                imeOptions = 0,
                hintText = "ONE-TIME PASSWORD",
            ),
        )
        assertEquals(InputPrivacyMode.SENSITIVE, mode)
    }
}
