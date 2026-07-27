package com.example.syntra.net

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Messages the user deleted **for themselves only**, kept per device.
 *
 * "Hapus untuk saya" used to just drop the message out of the in-memory list, so it
 * came straight back on the next open — the cache and the server still had it, and
 * nothing recorded the user's decision. A delete that undoes itself is worse than no
 * delete at all, because the user believes it is gone.
 *
 * This is deliberately device-local: the backend has no per-user hide endpoint, and
 * hiding is a personal view preference, not a change to the conversation. The other
 * side still has their copy — that is what "for me" means, and the menu says so.
 *
 * Stored as a flat id set (small: a few hundred ids at most) and mirrored into Compose
 * state so hiding one repaints the thread immediately.
 */
object HiddenMessageStore {
    private const val PREF = "syntra_hidden_messages"
    private const val KEY = "ids"

    private val hidden = mutableStateMapOf<String, Boolean>()
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                prefs(context).getStringSet(KEY, emptySet())?.forEach { hidden[it] = true }
            }
            loaded = true
        }
    }

    fun isHidden(context: Context, messageId: String): Boolean {
        ensure(context)
        return hidden.containsKey(messageId)
    }

    fun hide(context: Context, messageId: String) {
        ensure(context)
        if (hidden.put(messageId, true) == null) persist(context)
    }

    /** Drops every id — used when clearing a conversation or signing out. */
    fun clear(context: Context) {
        hidden.clear()
        runCatching { prefs(context).edit().clear().apply() }
    }

    private fun persist(context: Context) {
        runCatching { prefs(context).edit().putStringSet(KEY, hidden.keys.toSet()).apply() }
    }
}
