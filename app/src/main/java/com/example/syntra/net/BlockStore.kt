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
 *
 * THE PAIRING IS THE POINT. An earlier version kept two independent sets, so unblocking
 * from a screen that only knew the username (Settings → Kontak diblokir) dropped the
 * name and ORPHANED THE ID — and since [isBlocked] matches on either, the person stayed
 * blocked everywhere that works in ids (reels, profiles opened from a feed), with no UI
 * left anywhere to clear it. Storing username → id keeps the two ends together so
 * removing one always removes the other.
 */
object BlockStore {
    private const val PREF = "syntra_blocked"
    private const val KEY_PAIRS = "pairs"

    /** username (lowercase) → user id, or "" when this screen never learned the id. */
    private val entries = mutableStateMapOf<String, String>()
    /** Reverse index, so an id-only check stays O(1). Always derived from [entries]. */
    private val ids = mutableStateMapOf<String, String>()
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                // Stored as "username\tid" so one key holds the pairing. Older installs
                // wrote two separate sets; those are read once and folded in, with an
                // unknown id, rather than dropping people's existing blocks on upgrade.
                prefs(context).getStringSet(KEY_PAIRS, emptySet())?.forEach { row ->
                    val name = row.substringBefore('\t').lowercase()
                    val id = row.substringAfter('\t', "")
                    if (name.isNotBlank()) put(name, id)
                }
                if (entries.isEmpty()) {
                    prefs(context).getStringSet("usernames", emptySet())
                        ?.forEach { put(it.lowercase(), "") }
                }
            }
            loaded = true
        }
    }

    private fun put(username: String, userId: String) {
        entries[username] = userId
        if (userId.isNotBlank()) ids[userId] = username
    }

    /** True when either identifier is on the block list. Blank inputs are ignored. */
    fun isBlocked(context: Context, username: String? = null, userId: String? = null): Boolean {
        ensure(context)
        if (!username.isNullOrBlank() && entries.containsKey(username.lowercase())) return true
        if (!userId.isNullOrBlank() && ids.containsKey(userId)) return true
        return false
    }

    /** How many people are blocked — for the Settings row subtitle. */
    fun count(context: Context): Int {
        ensure(context)
        return entries.size
    }

    fun add(context: Context, username: String?, userId: String?) {
        ensure(context)
        val name = username?.lowercase().orEmpty()
        val id = userId.orEmpty()
        when {
            name.isNotBlank() -> put(name, id.ifBlank { entries[name].orEmpty() })
            // Blocked from a screen that only ever had an id (a reel author, say).
            id.isNotBlank() -> ids[id] = ""
        }
        persist(context)
    }

    /**
     * Removes a block by EITHER identifier, clearing both ends of the pair.
     *
     * Callers legitimately know only one — Settings lists usernames, a feed knows ids —
     * so each is resolved to the other through the stored pairing rather than trusting
     * the caller to supply both.
     */
    fun remove(context: Context, username: String?, userId: String?) {
        ensure(context)
        val name = username?.lowercase()?.takeIf { it.isNotBlank() }
            ?: userId?.takeIf { it.isNotBlank() }?.let { ids[it] }
        val id = userId?.takeIf { it.isNotBlank() }
            ?: name?.let { entries[it] }?.takeIf { it.isNotBlank() }
        if (name != null) entries.remove(name)
        if (id != null) ids.remove(id)
        persist(context)
    }

    /**
     * Replaces the mirror with the server's list. Called on launch and whenever the
     * blocked-contacts screen loads, so a block made on another device applies here.
     */
    fun sync(context: Context, blocked: List<NetUser>) {
        ensure(context)
        entries.clear()
        ids.clear()
        blocked.forEach { u ->
            if (u.username.isNotBlank()) put(u.username.lowercase(), u.id)
            else if (u.id.isNotBlank()) ids[u.id] = ""
        }
        persist(context)
    }

    /** Every blocked username — for the Settings list. */
    fun all(context: Context): Set<String> {
        ensure(context)
        return entries.keys.toSet()
    }

    /** The user id paired with [username], or null when it was never learned. */
    fun idFor(context: Context, username: String): String? {
        ensure(context)
        return entries[username.lowercase()]?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        entries.clear()
        ids.clear()
        runCatching { prefs(context).edit().clear().apply() }
    }

    private fun persist(context: Context) {
        runCatching {
            prefs(context).edit()
                .putStringSet(KEY_PAIRS, entries.map { (n, i) -> "$n\t$i" }.toSet())
                // The old keys are cleared so an upgrade can't resurrect stale blocks.
                .remove("usernames")
                .remove("user_ids")
                .apply()
        }
    }
}
