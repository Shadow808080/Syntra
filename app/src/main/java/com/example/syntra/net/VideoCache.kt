package com.example.syntra.net

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent, on-disk cache for remote videos.
 *
 * The backend hands out immutable media URLs — the media id is baked into the
 * path, and replacing a video mints a brand new id (docs/api.md §media) — so a
 * file downloaded once stays valid forever. Every video player in the app
 * resolves its source through here: the first view downloads the file to disk,
 * and every view after that plays straight from the local copy. That turns a
 * feed that re-streamed the same clip on every scroll (the thing that burned
 * through Supabase's cached-egress quota) into a single download per video, and
 * makes re-plays start instantly.
 *
 * Files are evicted least-recently-used once the directory grows past
 * [MAX_BYTES]. Anything that fails to download simply falls back to streaming
 * the original URL, so playback never breaks because of the cache.
 */
object VideoCache {

    private const val DIR = "syntra_videos"
    private const val MAX_BYTES = 1024L * 1024 * 1024 // 1 GB ceiling, then LRU

    // One lock per URL so the same clip is never downloaded twice at once (e.g.
    // the on-screen reel and a prefetch of the same id racing).
    private val locks = ConcurrentHashMap<String, Mutex>()

    // Cache fills run here, NOT in the caller's coroutine. A reel's video can be
    // 20+ MB; tying the download to the composable meant scrolling away mid-download
    // cancelled it, so nothing was cached and the next view re-downloaded from
    // scratch (exactly the "loads again like a fresh download" bug). This scope
    // outlives navigation so a started download always finishes and caches.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun dir(context: Context): File =
        File(context.cacheDir, DIR).apply { mkdirs() }

    private fun keyFor(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * The local file for [url] if it is fully cached, otherwise null. Touches the
     * modified time so the freshest files survive LRU eviction.
     */
    fun cachedFile(context: Context, url: String): File? {
        val f = File(dir(context), keyFor(url))
        return if (f.exists() && f.length() > 0) {
            f.setLastModified(System.currentTimeMillis())
            f
        } else {
            null
        }
    }

    /**
     * The source to play RIGHT NOW: the local cached file if present, otherwise the
     * original [url] so playback starts immediately by streaming — while a background
     * download (in the persistent [scope]) caches it for next time. It never blocks on
     * the download, so a big clip no longer shows a long "loading" before it starts,
     * and re-plays come from disk instantly.
     */
    suspend fun resolve(context: Context, url: String): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext url // already a local file/uri
        cachedFile(context, url)?.let { return@withContext it.absolutePath }
        prefetch(context, url) // fill the cache in the background; play by streaming now
        url
    }

    /**
     * Warms the cache for [url] in the background (the current clip, or the next reel
     * in the pager) so a replay — or a scroll to it — comes from disk. Runs in the
     * persistent scope, so it finishes even if the caller navigates away. Silent.
     */
    fun prefetch(context: Context, url: String) {
        if (!url.startsWith("http")) return
        if (cachedFile(context, url) != null) return
        val app = context.applicationContext // don't hold an Activity in the long-lived scope
        val lock = locks.getOrPut(url) { Mutex() }
        scope.launch {
            lock.withLock {
                if (cachedFile(app, url) != null) return@withLock
                runCatching { download(app, url) }
            }
        }
    }

    private fun download(context: Context, url: String): Boolean {
        val target = File(dir(context), keyFor(url))
        val part = File(target.absolutePath + ".part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                return false
            }
            val expected = conn.contentLengthLong // -1 if unknown
            conn.inputStream.use { input ->
                part.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            // Reject a truncated download: if the connection dropped mid-stream the
            // copy still returns normally, and a half-written file cached as complete
            // is exactly what made reels play BLACK with no error. Only trust the file
            // when it fully matches the advertised length.
            if (part.length() <= 0 || (expected > 0 && part.length() != expected)) {
                part.delete()
                return false
            }
            // Atomic swap so a half-written file is never seen as complete.
            if (target.exists()) target.delete()
            val moved = part.renameTo(target)
            if (!moved) {
                part.delete()
                return false
            }
            trim(context, keep = target)
            true
        } catch (_: Exception) {
            part.delete()
            false
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Drop a cached file for [url] — call when playback fails, so a corrupt/partial
     * entry (from an older build or a bad network) is re-fetched next time instead of
     * playing black forever.
     */
    fun evict(context: Context, url: String) {
        runCatching { File(dir(context), keyFor(url)).delete() }
    }

    /** Evict least-recently-used files until back under [MAX_BYTES]; never [keep]. */
    private fun trim(context: Context, keep: File) {
        val files = dir(context).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES) break
            if (f.absolutePath != keep.absolutePath) {
                total -= f.length()
                f.delete()
            }
        }
    }
}
