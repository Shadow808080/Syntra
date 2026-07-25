package com.example.syntra.net

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The user's own audio files, picked from device storage, so they appear in the
 * Music tab next to the online catalogue and survive app restarts.
 *
 * A file is referenced by its `content://` Uri with a *persisted* read grant, so
 * playback still works after the process dies. Title/artist/duration and any
 * embedded cover art are read once via [MediaMetadataRetriever]; the cover is
 * cached to a file so Coil can display it. Stored as a small JSON array in
 * SharedPreferences — no backend involved; these tracks are private to the device.
 */
object LocalMusicStore {
    private const val PREF = "local_music"
    private const val KEY = "tracks"

    fun list(context: Context): List<MusicTrack> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val uri = o.getString("uri")
                MusicTrack(
                    id = uri,
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    artworkUrl = o.optString("art").ifBlank { null },
                    previewUrl = uri,
                    durationSec = o.optInt("dur"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Import a freshly picked audio [uri]; returns the updated list. */
    fun add(context: Context, uri: Uri): List<MusicTrack> {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val existing = list(context)
        if (existing.any { it.id == uri.toString() }) return existing
        val updated = existing + readMetadata(context, uri)
        save(context, updated)
        return updated
    }

    fun remove(context: Context, id: String): List<MusicTrack> {
        val updated = list(context).filterNot { it.id == id }
        save(context, updated)
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(id), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return updated
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun save(context: Context, tracks: List<MusicTrack>) {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("uri", t.previewUrl)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("art", t.artworkUrl ?: "")
                    put("dur", t.durationSec)
                },
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun readMetadata(context: Context, uri: Uri): MusicTrack {
        val r = MediaMetadataRetriever()
        var title = ""
        var artist = ""
        var durSec = 0
        var art: String? = null
        runCatching {
            r.setDataSource(context, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            durSec = ((r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L).toInt()
            r.embeddedPicture?.let { pic ->
                val f = File(context.cacheDir, "localart_${uri.toString().hashCode()}.jpg")
                f.writeBytes(pic)
                art = Uri.fromFile(f).toString()
            }
        }
        runCatching { r.release() }
        if (title.isBlank()) title = displayName(context, uri) ?: "Lagu lokal"
        if (artist.isBlank()) artist = "Dari perangkat"
        return MusicTrack(
            id = uri.toString(),
            title = title,
            artist = artist,
            artworkUrl = art,
            previewUrl = uri.toString(),
            durationSec = durSec,
        )
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.substringBeforeLast('.') else null
        }
    }.getOrNull()
}
