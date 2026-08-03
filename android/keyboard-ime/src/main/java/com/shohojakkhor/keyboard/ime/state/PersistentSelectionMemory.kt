package com.shohojakkhor.keyboard.ime.state

import android.content.Context
import android.util.Base64
import com.shohojakkhor.keyboard.translit.SelectionMemory

/**
 * Small app-private learned-choice store. It never runs in sensitive or
 * incognito fields (enforced by the IME caller), never logs content, and is
 * fully removable through [clear].
 */
public class PersistentSelectionMemory(context: Context) : SelectionMemory {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private var sequence: Long = prefs.getLong(KEY_SEQUENCE, 0L)

    override fun record(latin: String, bengali: String) {
        val normalized = latin.trim().lowercase()
        if (normalized.isEmpty() || bengali.isEmpty()) return
        val key = pairKey(normalized, bengali)
        val old = decodeEntry(prefs.getString(key, null))
        val nextSequence = ++sequence
        prefs.edit()
            .putString(key, "${old.first + 1},$nextSequence")
            .putLong(KEY_SEQUENCE, nextSequence)
            .apply()
        trimIfNeeded()
    }

    override fun boostFor(latin: String, bengali: String): Double {
        val normalized = latin.trim().lowercase()
        val target = decodeEntry(prefs.getString(pairKey(normalized, bengali), null))
        if (target.first == 0) return 0.0
        val mostRecent = entriesFor(normalized).maxByOrNull { it.second.second }?.first
        val recencyBonus = if (mostRecent == bengali) 0.5 else 0.0
        return target.first * (1.0 + recencyBonus)
    }

    override fun clear() {
        prefs.edit().clear().apply()
        sequence = 0L
    }

    private fun entriesFor(latin: String): List<Pair<String, Pair<Int, Long>>> {
        val prefix = "${encode(latin)}:"
        return prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix) || value !is String) return@mapNotNull null
            val bengali = decode(key.substringAfter(':')) ?: return@mapNotNull null
            bengali to decodeEntry(value)
        }
    }

    private fun trimIfNeeded() {
        val learned = prefs.all.filterKeys { it != KEY_SEQUENCE }
        if (learned.size <= MAX_ENTRIES) return
        val oldestKeys = learned.entries
            .mapNotNull { (key, value) ->
                if (value !is String) null else key to decodeEntry(value).second
            }
            .sortedBy { it.second }
            .take(learned.size - MAX_ENTRIES)
            .map { it.first }
        prefs.edit().also { editor -> oldestKeys.forEach(editor::remove) }.apply()
    }

    private fun pairKey(latin: String, bengali: String): String = "${encode(latin)}:${encode(bengali)}"

    private fun encode(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun decode(value: String): String? = runCatching {
        String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
    }.getOrNull()

    private fun decodeEntry(raw: String?): Pair<Int, Long> {
        if (raw == null) return 0 to 0L
        val parts = raw.split(',', limit = 2)
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to
            (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    public companion object {
        private const val FILE_NAME = "learned_suggestions"
        private const val KEY_SEQUENCE = "__sequence"
        private const val MAX_ENTRIES = 1_000

        public fun clearAll(context: Context) {
            context.applicationContext
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }
}
