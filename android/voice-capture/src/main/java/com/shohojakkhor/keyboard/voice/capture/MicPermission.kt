package com.shohojakkhor.keyboard.voice.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Helpers for the RECORD_AUDIO permission. The request flow itself is handled
 * in the Compose UI (via `rememberLauncherForActivityResult`), so this object
 * only exposes the permission name and a fast check.
 *
 * Keeping the request out of this module keeps it free of Activity/Compose
 * dependencies and makes it reusable from the IME later.
 */
object MicPermission {
    const val PERMISSION = Manifest.permission.RECORD_AUDIO

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}
