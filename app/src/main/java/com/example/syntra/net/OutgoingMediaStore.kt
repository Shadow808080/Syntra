package com.example.syntra.net

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject
import java.io.File

/**
 * Keeps the sender's own copy of media they sent, keyed by message id.
 *
 * Why this exists: a sent photo is rendered from whatever the server hands back in
 * `attachments`. When that is missing or not yet resolved, the sender was left with a
 * bubble that showed nothing and did nothing when tapped — most painfully with a
 * view-once photo, where the sender could not review what they had just sent. The
 * sender uploaded the bytes, so they should never depend on the server to see them.
 *
 * Copies live in **filesDir** (app data), not the cache, so "Clear cache" or the OS
 * reclaiming space can't take away the only copy the sender has.
 */
object OutgoingMediaStore {
    private const val DIR = "outgoing_media"
    private const val PREF = "outgoing_media"
    private const val KEY = "map"

    // messageId -> absolute file path, mirrored into Compose state so a late save
    // repaints the bubble.
    private val paths = mutableStateMapOf<String, String>()
    @Volatile private var loaded = false

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
                if (!raw.isNullOrBlank()) {
                    val o = JSONObject(raw)
                    o.keys().forEach { k -> paths[k] = o.getString(k) }
                }
            }
            loaded = true
        }
    }

    /** Stores [bytes] as this message's own copy and returns its path. */
    fun save(context: Context, messageId: String, extension: String, bytes: ByteArray): String? {
        ensure(context)
        return runCatching {
            val f = File(dir(context), "$messageId.$extension")
            f.writeBytes(bytes)
            paths[messageId] = f.absolutePath
            persist(context)
            f.absolutePath
        }.getOrNull()
    }

    /** My own copy for [messageId], or null when there isn't one (or it's gone). */
    fun get(context: Context, messageId: String): String? {
        ensure(context)
        val p = paths[messageId] ?: return null
        return if (File(p).exists()) p else null
    }

    /** Re-keys a copy saved under the optimistic client id onto the server's id. */
    fun rekey(context: Context, fromId: String, toId: String) {
        ensure(context)
        val p = paths[fromId] ?: return
        paths[toId] = p
        paths.remove(fromId)
        persist(context)
    }

    fun remove(context: Context, messageId: String) {
        ensure(context)
        paths.remove(messageId)?.let { runCatching { File(it).delete() } }
        persist(context)
    }

    private fun persist(context: Context) {
        runCatching {
            val o = JSONObject()
            paths.forEach { (k, v) -> o.put(k, v) }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, o.toString()).apply()
        }
    }
}
