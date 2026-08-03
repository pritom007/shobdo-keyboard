package com.shohojakkhor.keyboard.ime.state

import android.content.Context

public class SuggestionPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    public var emojiSuggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_EMOJI, true)
        set(value) { prefs.edit().putBoolean(KEY_EMOJI, value).apply() }

    public var preferStandardBangla: Boolean
        get() = prefs.getBoolean(KEY_STANDARD_BANGLA, true)
        set(value) { prefs.edit().putBoolean(KEY_STANDARD_BANGLA, value).apply() }

    public var noisySuggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOISY, true)
        set(value) { prefs.edit().putBoolean(KEY_NOISY, value).apply() }

    public companion object {
        private const val FILE_NAME = "suggestion_preferences"
        private const val KEY_EMOJI = "emoji_suggestions"
        private const val KEY_STANDARD_BANGLA = "prefer_standard_bangla"
        private const val KEY_NOISY = "noisy_suggestions"
    }
}
