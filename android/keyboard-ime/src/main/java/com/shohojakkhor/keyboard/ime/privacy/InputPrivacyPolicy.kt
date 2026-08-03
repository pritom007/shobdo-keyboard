package com.shohojakkhor.keyboard.ime.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Classifies the currently active edit target into a privacy mode.
 *
 * Downstream code MUST consult this before enabling voice, cloud requests,
 * personalization, or learning. See docs/privacy-model.md and §13 of the
 * master brief.
 *
 * The public API accepts a framework [EditorInfo]. All decision logic lives in
 * [DefaultInputPrivacyPolicy.classifyFields], which takes a plain data holder,
 * so the classification rules are unit-testable on the JVM without needing
 * Robolectric or the Android mockable-jar quirks.
 */
public interface InputPrivacyPolicy {
    public fun classify(editorInfo: EditorInfo?): InputPrivacyMode
}

public enum class InputPrivacyMode {
    /** Ordinary text field. All features available. */
    NORMAL,

    /**
     * Field marked as no-personalized-learning (e.g. incognito browser tab).
     * We show suggestions and voice, but do not persist any learning.
     */
    INCOGNITO,

    /**
     * Password, PIN, OTP, card, security code, etc. Cloud features disabled,
     * personal dictionary not consulted, no learning, no logging.
     */
    SENSITIVE,
}

/**
 * Framework-independent snapshot of the fields the privacy policy inspects.
 * Exists so the classification rules can be exhaustively unit-tested without
 * constructing an [EditorInfo].
 */
public data class EditorFields(
    val inputType: Int,
    val imeOptions: Int,
    val hintText: String? = null,
)

public class DefaultInputPrivacyPolicy : InputPrivacyPolicy {

    override fun classify(editorInfo: EditorInfo?): InputPrivacyMode {
        if (editorInfo == null) return InputPrivacyMode.NORMAL
        return classifyFields(
            EditorFields(
                inputType = editorInfo.inputType,
                imeOptions = editorInfo.imeOptions,
                hintText = editorInfo.hintText?.toString(),
            ),
        )
    }

    internal companion object {

        /**
         * Localised keywords are intentionally NOT included: we do not want to
         * enable / disable features based on host-app-supplied Bengali strings,
         * which are easier to spoof. English hint keywords catch the common
         * cases (banking apps, WhatsApp OTP screens, etc.).
         */
        val SENSITIVE_HINT_KEYWORDS: List<String> = listOf(
            "otp",
            "one-time",
            "one time",
            "verification code",
            "pin",
            "cvv",
            "cvc",
            "card number",
            "security code",
            "passcode",
        )

        fun classifyFields(fields: EditorFields): InputPrivacyMode {
            val cls = fields.inputType and InputType.TYPE_MASK_CLASS
            val variation = fields.inputType and InputType.TYPE_MASK_VARIATION

            if (cls == InputType.TYPE_CLASS_TEXT) {
                when (variation) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return InputPrivacyMode.SENSITIVE
                }
            }
            if (cls == InputType.TYPE_CLASS_NUMBER) {
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                    return InputPrivacyMode.SENSITIVE
                }
            }

            val hint = fields.hintText?.lowercase().orEmpty()
            if (hint.isNotEmpty() && SENSITIVE_HINT_KEYWORDS.any { hint.contains(it) }) {
                return InputPrivacyMode.SENSITIVE
            }

            val noLearning =
                (fields.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
            if (noLearning) return InputPrivacyMode.INCOGNITO

            return InputPrivacyMode.NORMAL
        }
    }
}
