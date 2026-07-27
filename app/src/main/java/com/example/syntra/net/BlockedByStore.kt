package com.example.syntra.net

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Who has blocked ME — the mirror image of [BlockStore].
 *
 * WHY THIS IS SEPARATE. [BlockStore] answers "did I block them?". Nothing answered
 * "did they block me?", so from the blocked person's side the app looked completely
 * normal: the blocker's real name, their photo, their online dot, working call buttons,
 * a composer that accepted text. The server refused the requests, but the UI never
 * said why — a block that the blocked person cannot perceive is not a block.
 *
 * Kept apart from BlockStore on purpose. The two mean opposite things and are cleared
 * at different times: unblocking someone is my action, being unblocked is theirs.
 *
 * Filled from three places: the launch sync ([sync]), and the realtime `user.blocked` /
 * `user.unblocked` events the server publishes to my own user topic — which is what
 * makes the change land without reopening the app.
 */
object BlockedByStore {
    private const val PREF = "syntra_blocked_by"
    private const val KEY_PAIRS = "pairs"

    /** user id → username (may be blank when the event didn't carry one). */
    private val ids = mutableStateMapOf<String, String>()
    private val names = mutableStateMapOf<String, String>()
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                prefs(context).getStringSet(KEY_PAIRS, emptySet())?.forEach { row ->
                    val id = row.substringBefore('\t')
                    val name = row.substringAfter('\t', "")
                    if (id.isNotBlank()) put(id, name)
                }
            }
            loaded = true
        }
    }

    private fun put(userId: String, username: String) {
        ids[userId] = username
        if (username.isNotBlank()) names[username.lowercase()] = userId
    }

    /** True when this person has blocked me. Either identifier may be given. */
    fun isBlockedBy(context: Context, username: String? = null, userId: String? = null): Boolean {
        ensure(context)
        if (!userId.isNullOrBlank() && ids.containsKey(userId)) return true
        if (!username.isNullOrBlank() && names.containsKey(username.lowercase())) return true
        return false
    }

    fun add(context: Context, userId: String?, username: String?) {
        ensure(context)
        if (userId.isNullOrBlank()) return
        put(userId, username.orEmpty())
        persist(context)
    }

    fun remove(context: Context, userId: String?, username: String?) {
        ensure(context)
        val id = userId?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }?.let { names[it.lowercase()] }
        val name = username?.lowercase()?.takeIf { it.isNotBlank() }
            ?: id?.let { ids[it] }?.takeIf { it.isNotBlank() }?.lowercase()
        if (id != null) ids.remove(id)
        if (name != null) names.remove(name)
        persist(context)
    }

    /** Replaces the mirror with the server's answer. */
    fun sync(context: Context, blockers: List<NetUser>) {
        ensure(context)
        ids.clear()
        names.clear()
        blockers.forEach { if (it.id.isNotBlank()) put(it.id, it.username) }
        persist(context)
    }

    fun clear(context: Context) {
        ids.clear()
        names.clear()
        runCatching { prefs(context).edit().clear().apply() }
    }

    private fun persist(context: Context) {
        runCatching {
            prefs(context).edit()
                .putStringSet(KEY_PAIRS, ids.map { (i, n) -> "$i\t$n" }.toSet())
                .apply()
        }
    }
}

/**
 * What a screen should show about someone, once blocks in EITHER direction are applied.
 *
 * Centralised so every surface hides the same things. Scattering these rules is how the
 * chat header ended up showing a live "typing…" for someone who had blocked the user.
 */
object BlockMask {
    /** The name to display: real name, or a neutral placeholder when hidden. */
    fun name(context: Context, real: String, username: String?, userId: String?): String =
        if (hidden(context, username, userId)) "Pengguna" else real

    /** The avatar to display — null (letter tile) when hidden. */
    fun avatar(context: Context, real: String?, username: String?, userId: String?): String? =
        if (hidden(context, username, userId)) null else real

    /**
     * True when this person's details must not be shown: either I blocked them, or
     * they blocked me. Both directions hide the same things — the difference is only
     * in what the UI offers to do about it (unblock vs. nothing).
     */
    fun hidden(context: Context, username: String?, userId: String?): Boolean =
        BlockStore.isBlocked(context, username, userId) ||
            BlockedByStore.isBlockedBy(context, username, userId)
}
