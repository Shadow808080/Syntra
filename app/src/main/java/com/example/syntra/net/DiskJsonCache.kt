package com.example.syntra.net

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * A plain "JSON text under a key" store on disk, shared by the caches that make the
 * app open instantly (messages, the Shorts feed, profiles).
 *
 * WHY NOT SHAREDPREFERENCES. Every one of these caches started life in prefs, and
 * prefs is the wrong shape for them. A SharedPreferences file is parsed **in full on
 * first touch and then held in memory for the life of the process** — so a few
 * hundred cached messages across a few conversations became megabytes of resident
 * String that never came back, on exactly the phones that can least afford it. Worse,
 * every write rewrites the entire file. One file per key means we read only what we
 * ask for and write only what changed.
 *
 * Entries are capped by [MAX_ENTRY_BYTES] so a single runaway key can't fill the
 * disk, and the whole store is bounded by [MAX_TOTAL_BYTES] with least-recently-used
 * eviction — a cache that grows forever is a bug, not a cache.
 */
object DiskJsonCache {

    private const val DIR = "syntra_json_cache"
    private const val MAX_ENTRY_BYTES = 512 * 1024 // 512 KB per key
    private const val MAX_TOTAL_BYTES = 8L * 1024 * 1024 // 8 MB across everything

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    // Keys are arbitrary (conversation ids, usernames) — hash them so they're always
    // a legal, fixed-length filename.
    private fun fileFor(context: Context, key: String): File =
        File(dir(context), MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) })

    /** The JSON stored under [key], or null when nothing is cached. */
    fun read(context: Context, key: String): String? = runCatching {
        val f = fileFor(context, key)
        if (!f.exists() || f.length() == 0L) return null
        f.setLastModified(System.currentTimeMillis()) // touch: LRU keeps the useful ones
        f.readText()
    }.getOrNull()

    /** Store [json] under [key]. Oversized payloads are dropped rather than truncated. */
    fun write(context: Context, key: String, json: String) {
        runCatching {
            if (json.toByteArray().size > MAX_ENTRY_BYTES) return
            val f = fileFor(context, key)
            // Write beside, then rename: a half-written file must never be readable as
            // a complete one.
            val part = File(f.absolutePath + ".part")
            part.writeText(json)
            if (f.exists()) f.delete()
            if (!part.renameTo(f)) part.delete()
            trim(context, keep = f)
        }
    }

    fun remove(context: Context, key: String) {
        runCatching { fileFor(context, key).delete() }
    }

    /** Total bytes on disk — for the Settings storage screen. */
    fun sizeBytes(context: Context): Long =
        dir(context).listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    /** Evict least-recently-read entries until back under [MAX_TOTAL_BYTES]. */
    private fun trim(context: Context, keep: File) {
        val files = dir(context).listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_TOTAL_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_TOTAL_BYTES) break
            if (f.absolutePath != keep.absolutePath) {
                total -= f.length()
                f.delete()
            }
        }
    }
}
