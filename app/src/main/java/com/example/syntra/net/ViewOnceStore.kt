package com.example.syntra.net

import android.content.Context

/**
 * Tracks which "sekali lihat" (view-once) photos have already been opened, so they
 * can be shown only once and then locked as "Dibuka" — like WhatsApp.
 *
 * Enforcement is per-device (a set of opened message ids in SharedPreferences).
 * True server-side, tamper-proof view-once would need backend support; see
 * pesan-untuk-backend.md.
 */
object ViewOnceStore {
    private const val PREF = "view_once_opened"
    private const val KEY = "ids"

    fun isOpened(context: Context, messageId: String): Boolean =
        prefs(context).getStringSet(KEY, emptySet())?.contains(messageId) == true

    fun markOpened(context: Context, messageId: String) {
        val current = prefs(context).getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(messageId)
        prefs(context).edit().putStringSet(KEY, current).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
