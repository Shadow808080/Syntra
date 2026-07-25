package com.example.syntra

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

/**
 * Application entry point, used to tune the shared Coil image loader.
 *
 * Every media URL the backend hands out is immutable — the media id is part of
 * the path, and a replaced photo gets a brand new id (docs/api.md §media). The
 * server also gives us no thumbnails or resized variants, so each photo is
 * fetched at full size exactly once and then must come from cache; without this
 * the same avatars were re-downloaded and re-decoded on every screen.
 */
class SyntraApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Defining our own loader replaces the default one, so the video
            // decoder has to be registered here or video posters stop rendering.
            .components { add(VideoFrameDecoder.Factory()) }
            // A quarter of the heap for decoded bitmaps: avatars and chat photos
            // are shown over and over, so keeping them decoded is the big win.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Survives process death, so scrolling back never re-downloads.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("syntra_images"))
                    // Generous: the more avatars/photos/thumbnails stay on disk, the
                    // fewer repeat downloads — the same cached-egress win the video
                    // cache gives, applied to images.
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            // Storage objects never change under a given URL, so short/absent
            // cache headers from the bucket must not force a revalidation.
            .respectCacheHeaders(false)
            .networkCachePolicy(CachePolicy.ENABLED)
            // Fading every avatar in costs a frame for no real benefit here.
            .crossfade(false)
            // RGB_565 halves the memory per decoded bitmap (2 bytes/pixel instead
            // of 4). For avatars, story photos and reel thumbnails the quality
            // loss is invisible, but the GC pressure — the main cause of the
            // "heavy when loading images" jank — drops sharply.
            .allowRgb565(true)
            .build()
}
