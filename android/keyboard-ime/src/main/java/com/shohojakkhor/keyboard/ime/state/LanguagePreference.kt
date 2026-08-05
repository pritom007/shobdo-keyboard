package com.shohojakkhor.keyboard.ime.state

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the user's last chosen keyboard language across process death,
 * screen lock, and focus changes.
 *
 * We remember whether the user's last **language** choice was Bengali
 * (Banglish) or English, plus whether Bengali handwriting was the selected
 * input surface. Symbols pages and transient shift/caps state still start
 * fresh.
 *
 * Storage is a single [SharedPreferences] file, `keyboard_language`, with
 * one key `last_language_mode`. Values are the [KeyboardMode] enum name.
 * Reads and writes are synchronous — the file is tiny (a few bytes) and
 * we consult it once per input session on the IME thread.
 *
 * Sensitive fields do *not* touch this preference: they force English
 * temporarily via [ShohojakkhorInputMethodService] but the persisted mode is
 * only updated by explicit user action (the language toggle key), so
 * after the sensitive field goes away the previously-persisted mode is
 * restored on the next non-sensitive field.
 */
public class LanguagePreference(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Return the last **language** the user was in. Defaults to
     * [KeyboardMode.ENGLISH_LOWER] on first launch. If the stored value
     * is corrupted or from a future version, we also fall back to
     * English so the user is never stuck in an unknown state.
     */
    public fun getSavedLanguage(): KeyboardMode {
        val name = prefs.getString(KEY_LAST_LANGUAGE, null) ?: return KeyboardMode.ENGLISH_LOWER
        return runCatching { KeyboardMode.valueOf(name) }
            .getOrDefault(KeyboardMode.ENGLISH_LOWER)
            .normalizeToLanguage()
    }

    /**
     * Save the current mode as the user's language preference, but only if
     * it is a *language* mode (English or Bengali). Symbols pages and
     * transient shift states are ignored — we don't want the user to unlock
     * their phone and find themselves stuck on a symbols page just because
     * that's what the screen last showed.
     */
    public fun saveLanguage(mode: KeyboardMode) {
        val normalized = mode.normalizeToLanguage()
        prefs.edit()
            .putString(KEY_LAST_LANGUAGE, normalized.name)
            .apply()
    }

    /** Whether the user explicitly left Bengali handwriting selected. */
    public fun isHandwritingPreferred(): Boolean =
        prefs.getBoolean(KEY_HANDWRITING_PREFERRED, false)

    /** Persist an explicit switch between Bengali keys and handwriting. */
    public fun saveHandwritingPreferred(preferred: Boolean) {
        prefs.edit()
            .putBoolean(KEY_HANDWRITING_PREFERRED, preferred)
            .apply()
    }

    /**
     * Reduce any mode to a language mode: English variants collapse to
     * [KeyboardMode.ENGLISH_LOWER]; Bengali variants (Banglish lower/upper/
     * caps and the Bengali symbols pages) collapse to
     * [KeyboardMode.BENGALI_BANGLISH]; English symbols collapse to English.
     */
    private fun KeyboardMode.normalizeToLanguage(): KeyboardMode = when {
        this.isBengaliBanglish -> KeyboardMode.BENGALI_BANGLISH
        this.isBengaliSymbols -> KeyboardMode.BENGALI_BANGLISH
        this.isEnglish -> KeyboardMode.ENGLISH_LOWER
        this.isSymbols -> KeyboardMode.ENGLISH_LOWER
        else -> KeyboardMode.ENGLISH_LOWER
    }

    private companion object {
        const val FILE_NAME: String = "keyboard_language"
        const val KEY_LAST_LANGUAGE: String = "last_language_mode"
        const val KEY_HANDWRITING_PREFERRED: String = "handwriting_preferred"
    }
}
