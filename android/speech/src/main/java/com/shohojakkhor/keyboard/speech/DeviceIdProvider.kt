package com.shohojakkhor.keyboard.speech

import android.content.Context
import java.util.UUID

/**
 * Provides a stable, per-install device ID used ONLY for backend rate
 * limiting. Generated once and stored in SharedPreferences. No PII, no
 * advertising ID — a random UUID that survives until the app is reinstalled.
 *
 * The backend never sees anything else about the device.
 */
object DeviceIdProvider {
    private const val PREFS = "shohojakkhor_speech"
    private const val KEY_DEVICE_ID = "device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }
}
