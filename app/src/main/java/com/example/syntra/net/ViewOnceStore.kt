package com.example.syntra.net

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Tracks which "sekali lihat" (view-once) photos have been opened.
 *
 * Two separate sets, on purpose:
 *  - [markOpened] / [isOpened] — photos **I received and opened**. This is what locks
 *    a received photo after its single view.
 *  - [markPeerOpened] / [isPeerOpened] — photos **I sent that the recipient opened**,
 *    learned from a realtime signal (see ChatDetailScreen). Kept apart so the sender
 *    re-viewing their own photo can never mark it spent — a sender may look at what
 *    they sent as often as they like.
 *
 * Backed by SharedPreferences but mirrored into Compose snapshot state, so a change
 * repaints the bubble and the home-list preview immediately instead of waiting for
 * the next list rebuild.
 */
object ViewOnceStore {
    private const val PREF = "view_once_opened"
    private const val KEY_SELF = "ids"
    private const val KEY_PEER = "peer_ids"

    // Observable mirrors (value is always true; the map is just a reactive set).
    private val self = mutableStateMapOf<String, Boolean>()
    private val peer = mutableStateMapOf<String, Boolean>()
    @Volatile private var loaded = false

    private fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = prefs(context)
            p.getStringSet(KEY_SELF, emptySet())?.forEach { self[it] = true }
            p.getStringSet(KEY_PEER, emptySet())?.forEach { peer[it] = true }
            loaded = true
        }
    }

    /** A photo I received and already opened — its single view is spent. */
    fun isOpened(context: Context, messageId: String): Boolean {
        ensure(context)
        return self.containsKey(messageId)
    }

    fun markOpened(context: Context, messageId: String) {
        ensure(context)
        if (self.put(messageId, true) == null) persist(context, KEY_SELF, self.keys)
    }

    /** A photo I sent that the other side has opened. */
    fun isPeerOpened(context: Context, messageId: String): Boolean {
        ensure(context)
        return peer.containsKey(messageId)
    }

    fun markPeerOpened(context: Context, messageId: String) {
        ensure(context)
        if (peer.put(messageId, true) == null) persist(context, KEY_PEER, peer.keys)
    }

    /** True when the photo is spent from [fromMe]'s point of view. */
    fun isSpent(context: Context, messageId: String, fromMe: Boolean): Boolean =
        if (fromMe) isPeerOpened(context, messageId) else isOpened(context, messageId)

    private fun persist(context: Context, key: String, ids: Set<String>) {
        prefs(context).edit().putStringSet(key, ids.toSet()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
