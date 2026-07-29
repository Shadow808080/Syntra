package com.example.syntra.net

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Reads the tags out of ONE audio file the user just picked.
 *
 * This used to be a curated library: files added one at a time through a picker and
 * persisted as JSON in SharedPreferences. That whole idea is gone — the phone's music
 * is [DeviceAudio]'s MediaStore query now, which needs no bookkeeping and cannot
 * drift out of sync with the filesystem. What remains is the one job that query
 * cannot do: reading title/artist/duration/cover from an arbitrary picked file to
 * prefill the publish form and size the trim slider.
 */
object LocalMusicStore {

    fun probe(context: Context, uri: Uri): MusicTrack = readMetadata(context, uri)

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
