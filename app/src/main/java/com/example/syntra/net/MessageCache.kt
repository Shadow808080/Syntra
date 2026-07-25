package com.example.syntra.net

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device cache of chat messages, per conversation.
 *
 * Opening a chat renders whatever is cached instantly — no spinner, no re-download
 * — then the newest page is synced in the background. Scrolling up loads older
 * pages from the network, and each page that arrives is folded back into the cache,
 * so a conversation the user has already scrolled through stays offline-instant on
 * the next open. Messages are keyed by id (UUIDv7 = time-ordered), so merging is a
 * simple de-dupe + sort, and the store is bounded so it never grows without limit.
 */
object MessageCache {
    private const val PREFS = "syntra_msg_cache"
    private const val MAX_PER_CONVO = 500 // plenty of scrollback; oldest trimmed

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Cached messages for [conversationId], oldest-first. Empty if nothing cached. */
    fun load(context: Context, conversationId: String): List<NetMessage> {
        val raw = prefs(context).getString(conversationId, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
                .sortedBy { it.id } // UUIDv7 → chronological
        }.getOrDefault(emptyList())
    }

    /**
     * Merge [incoming] into the cache (de-dupe by id, keep newest edit/delete state),
     * trim to [MAX_PER_CONVO], and persist. Returns nothing — callers read via [load]
     * or keep their own in-memory list.
     */
    fun merge(context: Context, conversationId: String, incoming: List<NetMessage>) {
        if (incoming.isEmpty()) return
        val byId = LinkedHashMap<String, NetMessage>()
        for (m in load(context, conversationId)) byId[m.id] = m
        for (m in incoming) byId[m.id] = m // incoming wins (fresher delete/edit)
        val merged = byId.values
            .filter { !it.id.startsWith("local-") } // never cache optimistic locals
            .sortedBy { it.id }
            .takeLast(MAX_PER_CONVO)
        save(context, conversationId, merged)
    }

    /** Remove a single message from the cache (deleted-for-everyone). */
    fun remove(context: Context, conversationId: String, messageId: String) {
        val kept = load(context, conversationId).filterNot { it.id == messageId }
        save(context, conversationId, kept)
    }

    private fun save(context: Context, conversationId: String, messages: List<NetMessage>) {
        val arr = JSONArray()
        messages.forEach { arr.put(toJson(it)) }
        prefs(context).edit().putString(conversationId, arr.toString()).apply()
    }

    private fun toJson(m: NetMessage) = JSONObject().apply {
        put("id", m.id)
        put("conversation_id", m.conversationId)
        put("sender_id", m.senderId)
        put("type", m.type)
        put("body", m.body)
        m.replyToId?.let { put("reply_to_id", it) }
        put("created_at", m.createdAt)
        m.editedAt?.let { put("edited_at", it) }
        put("is_deleted", m.isDeleted)
        if (m.attachments.isNotEmpty()) {
            put("attachments", JSONArray().apply { m.attachments.forEach { put(it) } })
        }
    }

    private fun fromJson(o: JSONObject): NetMessage {
        val atts = o.optJSONArray("attachments")?.let { a ->
            (0 until a.length()).map { a.getString(it) }
        } ?: emptyList()
        return NetMessage(
            id = o.getString("id"),
            conversationId = o.optString("conversation_id"),
            senderId = o.optString("sender_id"),
            type = o.optString("type", "text"),
            body = o.optString("body", ""),
            replyToId = o.optString("reply_to_id", "").ifBlank { null },
            createdAt = o.optString("created_at", ""),
            editedAt = o.optString("edited_at", "").ifBlank { null },
            isDeleted = o.optBoolean("is_deleted", false),
            attachments = atts,
        )
    }
}
