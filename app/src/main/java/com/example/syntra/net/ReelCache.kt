package com.example.syntra.net

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared on-disk store for reel videos, backed by ExoPlayer's [SimpleCache].
 *
 * The whole point of routing reel playback through [dataSourceFactory] is
 * **download-once**: ExoPlayer plays a clip by reading through a [CacheDataSource]
 * that writes every byte it streams into this cache as it goes. So the FIRST view
 * both plays instantly (streaming) AND fills the cache in the same pass — there is
 * no separate "stream to play + download to cache" double fetch. Every view after
 * that reads straight from disk. Backend media URLs are immutable, so a cached
 * clip stays valid forever.
 *
 * STORAGE: **filesDir (app data), NOT cacheDir** — so a watched video survives
 * "Clear cache"/OS eviction and is never re-downloaded. Bounded by an LRU evictor
 * ([MAX_BYTES]); Settings → Penyimpanan shows its size and offers an in-app
 * "kosongkan" that reclaims it WITHOUT a "Clear data" that would sign the user out.
 */
@OptIn(UnstableApi::class)
object ReelCache {

    private const val DIR = "reel_media"
    private const val MAX_BYTES = 1024L * 1024 * 1024 // 1 GB ceiling, then LRU

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefetching = ConcurrentHashMap.newKeySet<String>()

    // SimpleCache MUST be a process-wide singleton per directory — a second instance
    // over the same folder throws. Guarded so all callers share one.
    @Volatile private var cacheRef: SimpleCache? = null

    @Synchronized
    private fun cache(context: Context): SimpleCache {
        cacheRef?.let { return it }
        val app = context.applicationContext
        // One-time cleanup of the old raw-file reel store from before the ExoPlayer
        // cache existed, so its bytes don't sit orphaned after the format change.
        runCatching { File(app.cacheDir, "syntra_videos").deleteRecursively() }
        val created = SimpleCache(
            File(app.filesDir, DIR),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(app),
        )
        cacheRef = created
        return created
    }

    private fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(upstream)
            // A cache write hiccup must never break playback — fall through to network.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * DataSource factory ExoPlayer plays through: serves from cache, filling it from
     * the network in the same read. Give this to the player's media-source factory —
     * that's what makes playback download-once.
     */
    fun dataSourceFactory(context: Context): DataSource.Factory = cacheDataSourceFactory(context)

    /**
     * Warms the cache for [url] in the background (the next reel in the pager) so it
     * plays from disk the moment it scrolls into view. Silent & deduped.
     */
    fun prefetch(context: Context, url: String) {
        if (!url.startsWith("http")) return
        if (!prefetching.add(url)) return
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val source = cacheDataSourceFactory(app).createDataSource()
                CacheWriter(source, DataSpec(Uri.parse(url)), null, null).cache()
            }
            prefetching.remove(url)
        }
    }

    /** Total bytes of downloaded reel videos on disk — for the Settings storage screen. */
    fun sizeBytes(context: Context): Long =
        runCatching { cache(context).cacheSpace }.getOrDefault(0L)

    /**
     * Deletes every downloaded reel, freeing the space WITHOUT touching session/
     * settings — the in-app "clear downloads". A reel watched again re-downloads once.
     */
    fun clear(context: Context) {
        runCatching {
            val c = cache(context)
            c.keys.toList().forEach { key -> c.removeResource(key) }
        }
    }
}
