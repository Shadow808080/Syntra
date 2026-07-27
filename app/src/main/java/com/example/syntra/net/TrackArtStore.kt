package com.example.syntra.net

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import java.io.File

/**
 * Per-track cover art the user chose themselves, kept ONLY on this device.
 *
 * Community uploads often arrive with no artwork, and the person who published them is
 * the only one who can change what the server stores. This lets the listener put their
 * own picture on a track without touching anyone else's data: the image is copied into
 * this app's private files directory and remembered by track id.
 *
 * DELIBERATELY NOT UPLOADED. The picture is a personal preference, not a correction to
 * the catalogue — publishing it would change the track for everyone and hand one
 * listener editing rights over another person's upload. It also means no bandwidth, no
 * moderation surface, and nothing to clean up server-side.
 *
 * The copy matters: a content:// URI from the gallery is a temporary grant that dies
 * with the picker, so storing the URI alone would leave a broken image on next launch.
 */
object TrackArtStore {
    private const val PREF = "syntra_track_art"
    private const val DIR = "track_art"

    /** track id → absolute file path. Compose state so a new pick repaints at once. */
    private val paths = mutableStateMapOf<String, String>()
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                prefs(context).all.forEach { (k, v) ->
                    val p = v as? String ?: return@forEach
                    // Drop entries whose file has since been deleted, so a missing
                    // picture falls back to the real cover instead of a blank box.
                    if (File(p).exists()) paths[k] = p
                }
            }
            loaded = true
        }
    }

    /** The user's own art for [trackId], or null to use whatever the track carries. */
    fun get(context: Context, trackId: String): String? {
        ensure(context)
        return paths[trackId]
    }

    /**
     * Copies [source] into private storage and remembers it for [trackId].
     * Returns the stored path, or null if the image could not be read.
     */
    fun put(context: Context, trackId: String, source: Uri): String? {
        ensure(context)
        val app = context.applicationContext
        return runCatching {
            val dir = File(app.filesDir, DIR).apply { mkdirs() }
            // Named by track id, so re-picking replaces rather than accumulating.
            val out = File(dir, "${trackId.hashCode()}.jpg")
            app.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "tidak bisa membaca gambar" }
                out.outputStream().use { input.copyTo(it) }
            }
            paths[trackId] = out.absolutePath
            prefs(app).edit().putString(trackId, out.absolutePath).apply()
            out.absolutePath
        }.getOrNull()
    }

    /** Removes the custom art, restoring the track's own cover. */
    fun clear(context: Context, trackId: String) {
        ensure(context)
        paths.remove(trackId)?.let { runCatching { File(it).delete() } }
        runCatching { prefs(context).edit().remove(trackId).apply() }
    }

    /** Wipes everything — called on sign-out with the other per-account stores. */
    fun clearAll(context: Context) {
        paths.clear()
        runCatching { File(context.applicationContext.filesDir, DIR).deleteRecursively() }
        runCatching { prefs(context).edit().clear().apply() }
    }
}
