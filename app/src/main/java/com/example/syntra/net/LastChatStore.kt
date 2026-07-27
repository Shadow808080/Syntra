package com.example.syntra.net

import android.content.Context

/**
 * Remembers the last conversation the user had open, so the app can reopen it the
 * next time the chat screen appears from a fresh start (a cold launch, or right
 * after unlocking with the app-lock PIN). Device-local; cleared on sign-out.
 */
object LastChatStore {
    private const val PREFS = "syntra_last_chat"
    private const val KEY_ID = "conversation_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun set(context: Context, conversationId: String) {
        if (conversationId.isBlank()) return
        prefs(context).edit().putString(KEY_ID, conversationId).apply()
    }

    fun get(context: Context): String? =
        prefs(context).getString(KEY_ID, null)?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ID).apply()
    }
}
