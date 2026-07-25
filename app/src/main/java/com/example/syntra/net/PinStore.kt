package com.example.syntra.net

import android.content.Context

/**
 * Per-conversation "pinned message" (sematkan pesan), stored on-device.
 *
 * The backend has no cross-device pin endpoint yet (only starred messages), so a
 * pin lives locally: one pinned message id per conversation, shown as a banner at
 * the top of the chat. See pesan-untuk-backend.md for the request to make this
 * server-side so pins sync across devices/participants.
 */
object PinStore {
    private const val PREF = "pinned_messages"

    fun get(context: Context, conversationId: String): String? =
        prefs(context).getString(conversationId, null)

    fun set(context: Context, conversationId: String, messageId: String) {
        prefs(context).edit().putString(conversationId, messageId).apply()
    }

    fun clear(context: Context, conversationId: String) {
        prefs(context).edit().remove(conversationId).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
