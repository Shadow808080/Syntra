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
 * Download-once, on-disk store for remote media that plays through a plain
 * [android.media.MediaPlayer] — voice notes, and chat/profile video messages —
 * which need a real local FILE PATH to play from. (Reels use [ReelCache], an
 * ExoPlayer SimpleCache, instead; that one can't hand back a file path.)
 *
 * The backend hands out immutable media URLs — the media id is baked into the path
 * (docs/api.md §media) — so a file downloaded once stays valid forever. Callers
 * [resolve] a url to its cached file (downloading on the first miss) and play from
 * disk after; replays cost no egress.
 *
 * STORAGE: this lives in **filesDir (app data), NOT cacheDir** — on purpose, so a
 * downloaded clip survives "Clear cache"/OS eviction and is never re-downloaded.
 * Bounded by an LRU ceiling ([MAX_BYTES]); Settings → Penyimpanan shows its size
 * (together with [ReelCache]) and clears it in-app without a "Clear data".
 */
object VideoCache {

    private const val DIR = "syntra_media"
    private const val MAX_BYTES = 512L * 1024 * 1024 // 512 MB ceiling, then LRU

    private val locks = ConcurrentHashMap<String, Mutex>()

    // Cache fills run here, NOT in the caller's coroutine, so scrolling away
    // mid-download doesn't cancel the fill.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // App data (filesDir), not cacheDir — see the class doc.
    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    private fun keyFor(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * The local file for [url] if fully cached, else null. Touches the modified time
     * so the freshest files survive LRU eviction.
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
     * original [url] (streamed) while a background download caches it for next time.
     * Never blocks on the download.
     */
    suspend fun resolve(context: Context, url: String): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext url // already a local file/uri
        cachedFile(context, url)?.let { return@withContext it.absolutePath }
        prefetch(context, url)
        url
    }

    /** Warms the cache for [url] in the background. Silent & deduped. */
    fun prefetch(context: Context, url: String) {
        if (!url.startsWith("http")) return
        if (cachedFile(context, url) != null) return
        val app = context.applicationContext
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
            // Reject a truncated download: a half-written file cached as complete is
            // exactly what made media fail with no error.
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

    /** Drop a cached file for [url] — call when playback fails so it re-fetches. */
    fun evict(context: Context, url: String) {
        runCatching { File(dir(context), keyFor(url)).delete() }
    }

    /** Total bytes on disk — for the Settings storage screen. */
    fun sizeBytes(context: Context): Long =
        dir(context).listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    /** Deletes everything cached here, freeing space without touching session/settings. */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
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
