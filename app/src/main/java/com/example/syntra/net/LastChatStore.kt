package com.example.syntra.net

import android.content.Context

/** The minimal snapshot needed to reopen a conversation instantly, before the
 *  conversation list has loaded. */
data class LastChat(
    val id: String,
    val name: String,
    val isGroup: Boolean,
    val counterpartId: String?,
    val counterpartUsername: String?,
    val avatarUrl: String?,
)

/**
 * Remembers the last conversation the user had open, so the app can reopen it the
 * next time the chat screen appears from a fresh start (a cold launch, or right
 * after unlocking with the app-lock PIN).
 *
 * Enough of the conversation is stored — not just the id — so the detail screen can
 * open on the very first frame without waiting for the list to load, the way
 * WhatsApp lands you straight back in the chat. Device-local; cleared on sign-out.
 */
object LastChatStore {
    private const val PREFS = "syntra_last_chat"
    private const val KEY_ID = "conversation_id"
    private const val KEY_NAME = "name"
    private const val KEY_GROUP = "is_group"
    private const val KEY_CP_ID = "counterpart_id"
    private const val KEY_CP_USER = "counterpart_username"
    private const val KEY_AVATAR = "avatar_url"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun set(
        context: Context,
        id: String,
        name: String,
        isGroup: Boolean,
        counterpartId: String?,
        counterpartUsername: String?,
        avatarUrl: String?,
    ) {
        if (id.isBlank()) return
        prefs(context).edit()
            .putString(KEY_ID, id)
            .putString(KEY_NAME, name)
            .putBoolean(KEY_GROUP, isGroup)
            .putString(KEY_CP_ID, counterpartId)
            .putString(KEY_CP_USER, counterpartUsername)
            .putString(KEY_AVATAR, avatarUrl)
            .apply()
    }

    fun get(context: Context): LastChat? {
        val p = prefs(context)
        val id = p.getString(KEY_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return LastChat(
            id = id,
            name = p.getString(KEY_NAME, "").orEmpty(),
            isGroup = p.getBoolean(KEY_GROUP, false),
            counterpartId = p.getString(KEY_CP_ID, null),
            counterpartUsername = p.getString(KEY_CP_USER, null),
            avatarUrl = p.getString(KEY_AVATAR, null),
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
