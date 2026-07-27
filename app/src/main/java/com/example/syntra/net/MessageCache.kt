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
    private const val LEGACY_PREFS = "syntra_msg_cache"
    private const val MAX_PER_CONVO = 200 // plenty of scrollback; oldest trimmed

    /**
     * Parsed messages for the conversation(s) in play, so re-reading during a chat
     * doesn't re-parse JSON. Deliberately tiny — a user is in one conversation at a
     * time, and holding every chat they've ever opened is what made the old
     * SharedPreferences version so heavy.
     */
    private const val MEMO_SIZE = 3
    private val memo = object : LinkedHashMap<String, List<NetMessage>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<NetMessage>>) =
            size > MEMO_SIZE
    }

    private fun key(conversationId: String) = "messages:$conversationId"

    /** Cached messages for [conversationId], oldest-first. Empty if nothing cached. */
    @Synchronized
    fun load(context: Context, conversationId: String): List<NetMessage> {
        memo[conversationId]?.let { return it }
        migrateLegacy(context)
        val raw = DiskJsonCache.read(context, key(conversationId)) ?: return emptyList()
        val parsed = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
                .sortedBy { it.id } // UUIDv7 → chronological
        }.getOrDefault(emptyList())
        memo[conversationId] = parsed
        return parsed
    }

    /**
     * Merge [incoming] into the cache (de-dupe by id, keep newest edit/delete state),
     * trim to [MAX_PER_CONVO], and persist. Returns nothing — callers read via [load]
     * or keep their own in-memory list.
     */
    @Synchronized
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

    /**
     * Drops every cached message for one conversation.
     *
     * Needed by "bersihkan obrolan" and "hapus percakapan": clearing the server side
     * alone is not enough, because this cache is what paints the thread on the next
     * open — leave it behind and the messages reappear instantly, which is exactly how
     * both features looked broken.
     */
    @Synchronized
    fun clearConversation(context: Context, conversationId: String) {
        memo.remove(conversationId)
        DiskJsonCache.remove(context, key(conversationId))
    }

    /** Remove a single message from the cache (deleted-for-everyone). */
    @Synchronized
    fun remove(context: Context, conversationId: String, messageId: String) {
        val kept = load(context, conversationId).filterNot { it.id == messageId }
        save(context, conversationId, kept)
    }

    /**
     * Drop the in-memory copies. The bytes themselves live in [DiskJsonCache], which
     * the Settings screen clears wholesale — this just makes sure nothing stale is
     * still being served from RAM afterwards.
     */
    @Synchronized
    fun clear(context: Context) {
        memo.clear()
        runCatching { context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
    }

    private fun save(context: Context, conversationId: String, messages: List<NetMessage>) {
        memo[conversationId] = messages
        val arr = JSONArray()
        messages.forEach { arr.put(toJson(it)) }
        DiskJsonCache.write(context, key(conversationId), arr.toString())
    }

    @Volatile private var migrated = false

    /**
     * One-shot move of everything off the old SharedPreferences store, the first time
     * any conversation is read in this process.
     *
     * It is done in one pass and the prefs file is then emptied, precisely because
     * touching it at all forces Android to parse the whole thing into memory. Pay that
     * once, on an upgrade, and never again — a per-conversation migration would keep
     * the old file (and its resident cost) alive for as long as unread chats remained.
     */
    private fun migrateLegacy(context: Context) {
        if (migrated) return
        migrated = true
        runCatching {
            val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            val all = prefs.all
            if (all.isEmpty()) return
            for ((convoId, value) in all) {
                val raw = value as? String ?: continue
                if (DiskJsonCache.read(context, key(convoId)) == null) {
                    DiskJsonCache.write(context, key(convoId), raw)
                }
            }
            prefs.edit().clear().apply()
        }
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
