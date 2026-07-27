package com.example.syntra.net

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Who this user has blocked — the client's mirror of the server's block list.
 *
 * The block button used to be cosmetic: `ProfileScreen` held a local `blocked` flag
 * that started `false`, was never loaded from the server, and was forgotten the moment
 * you left the screen. So blocking someone changed a button's colour and nothing else —
 * they still appeared in search, their reels still played, and their chat still opened.
 *
 * This keeps the list in one place, persisted and Compose-observable, so every screen
 * can ask the same question and repaint the instant it changes. It is a CACHE of the
 * server's answer, refreshed by [sync]; the server remains the authority.
 *
 * Blocks are recorded under BOTH username and user id, because screens hold different
 * identifiers — a reel knows an author id, a conversation knows a username, and a block
 * that only matches one of them leaks through the other.
 */
object BlockStore {
    private const val PREF = "syntra_blocked"
    private const val KEY_NAMES = "usernames"
    private const val KEY_IDS = "user_ids"

    private val names = mutableStateMapOf<String, Boolean>()
    private val ids = mutableStateMapOf<String, Boolean>()
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                prefs(context).getStringSet(KEY_NAMES, emptySet())?.forEach { names[it.lowercase()] = true }
                prefs(context).getStringSet(KEY_IDS, emptySet())?.forEach { ids[it] = true }
            }
            loaded = true
        }
    }

    /** True when either identifier is on the block list. Blank inputs are ignored. */
    fun isBlocked(context: Context, username: String? = null, userId: String? = null): Boolean {
        ensure(context)
        if (!username.isNullOrBlank() && names.containsKey(username.lowercase())) return true
        if (!userId.isNullOrBlank() && ids.containsKey(userId)) return true
        return false
    }

    /** How many people are blocked — for the Settings row subtitle. */
    fun count(context: Context): Int {
        ensure(context)
        return names.size
    }

    fun add(context: Context, username: String?, userId: String?) {
        ensure(context)
        if (!username.isNullOrBlank()) names[username.lowercase()] = true
        if (!userId.isNullOrBlank()) ids[userId] = true
        persist(context)
    }

    fun remove(context: Context, username: String?, userId: String?) {
        ensure(context)
        if (!username.isNullOrBlank()) names.remove(username.lowercase())
        if (!userId.isNullOrBlank()) ids.remove(userId)
        persist(context)
    }

    /**
     * Replaces the mirror with the server's list. Called on launch and whenever the
     * blocked-contacts screen loads, so a block made on another device applies here.
     */
    fun sync(context: Context, blocked: List<NetUser>) {
        ensure(context)
        names.clear()
        ids.clear()
        blocked.forEach { u ->
            if (u.username.isNotBlank()) names[u.username.lowercase()] = true
            if (u.id.isNotBlank()) ids[u.id] = true
        }
        persist(context)
    }

    /** Every blocked username — for the Settings list. */
    fun all(context: Context): Set<String> {
        ensure(context)
        return names.keys.toSet()
    }

    fun clear(context: Context) {
        names.clear()
        ids.clear()
        runCatching { prefs(context).edit().clear().apply() }
    }

    private fun persist(context: Context) {
        runCatching {
            prefs(context).edit()
                .putStringSet(KEY_NAMES, names.keys.toSet())
                .putStringSet(KEY_IDS, ids.keys.toSet())
                .apply()
        }
    }
}
