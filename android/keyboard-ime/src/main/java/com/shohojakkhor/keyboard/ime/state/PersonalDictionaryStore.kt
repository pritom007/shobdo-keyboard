package com.shohojakkhor.keyboard.ime.state

import android.content.Context
import android.util.Base64
import com.shohojakkhor.keyboard.translit.BengaliDictionary
import com.shohojakkhor.keyboard.translit.DictionaryEntry

/** App-private personal names/places dictionary shared by setup UI and IME. */
public class PersonalDictionaryStore(context: Context) : BengaliDictionary {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    public fun add(latin: String, bengali: String) {
        val normalized = latin.trim().lowercase()
        val output = bengali.trim()
        if (normalized.isEmpty() || output.isEmpty()) return
        val key = encode(normalized)
        val values = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        values += output
        prefs.edit().putStringSet(key, values).apply()
    }

    public fun remove(latin: String, bengali: String) {
        val key = encode(latin.trim().lowercase())
        val values = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        values -= bengali
        if (values.isEmpty()) prefs.edit().remove(key).apply()
        else prefs.edit().putStringSet(key, values).apply()
    }

    public fun clear() {
        prefs.edit().clear().apply()
    }

    override fun lookup(latin: String): List<DictionaryEntry> =
        prefs.getStringSet(encode(latin.trim().lowercase()), emptySet())
            .orEmpty()
            .map { DictionaryEntry(latin.trim().lowercase(), it, PERSONAL_FREQUENCY) }

    override fun allEntries(): List<DictionaryEntry> = prefs.all.flatMap { (key, value) ->
        val latin = decode(key) ?: return@flatMap emptyList()
        @Suppress("UNCHECKED_CAST")
        val values = value as? Set<String> ?: return@flatMap emptyList()
        values.map { DictionaryEntry(latin, it, PERSONAL_FREQUENCY) }
    }

    public fun entries(): List<DictionaryEntry> = allEntries().sortedWith(
        compareBy<DictionaryEntry> { it.latin }.thenBy { it.bengali },
    )

    private fun encode(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun decode(value: String): String? = runCatching {
        String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val FILE_NAME = "personal_dictionary"
        const val PERSONAL_FREQUENCY = 1_000
    }
}
