package com.example.syntra.net

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * The music that is actually on the phone.
 *
 * This replaces a hand-curated list. Before, "lagu dari penyimpanan" meant a file
 * picker you fed one song at a time, and the card only appeared once you had added
 * something — worse, publishing a track to the public catalogue also inserted it into
 * that list, so the feature seemed to come alive only after an upload it has nothing
 * to do with.
 *
 * A device library is a *query*, not a collection: MediaStore already knows every
 * audio file on the phone, so ask it. Nothing is stored, nothing is synced, and the
 * public catalogue is not involved at any point.
 */
object DeviceAudio {

    /** The permission that lets us read audio, which differs across versions. */
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Every music file on the device, newest first.
     *
     * Filtered by `IS_MUSIC` so ringtones, notification blips and alarm tones don't
     * pad the list out — they are audio, but nobody thinks of them as their music.
     * Very short clips are dropped for the same reason. Blocking; call it off the
     * main thread.
     */
    fun list(context: Context): List<MusicTrack> {
        if (!hasPermission(context)) return emptyList()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )

        val out = mutableListOf<MusicTrack>()
        runCatching {
            context.contentResolver.query(
                collection,
                columns,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val idAt = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleAt = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistAt = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumAt = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdAt = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durAt = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameAt = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (c.moveToNext()) {
                    val durMs = c.getLong(durAt)
                    // Under ~5s is a sound effect, not a song.
                    if (durMs in 1..5_000) continue

                    val id = c.getLong(idAt)
                    val title = c.getString(titleAt)?.takeIf { it.isNotBlank() }
                        ?: c.getString(nameAt)?.substringBeforeLast('.')
                        ?: "Tanpa judul"
                    // MediaStore writes the literal string "<unknown>" rather than null.
                    val artist = c.getString(artistAt)
                        ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        ?: "Tidak diketahui"

                    out += MusicTrack(
                        id = "device:$id",
                        title = title,
                        artist = artist,
                        album = c.getString(albumAt).orEmpty(),
                        artworkUrl = albumArtUri(c.getLong(albumIdAt)),
                        previewUrl = ContentUris.withAppendedId(collection, id).toString(),
                        durationSec = (durMs / 1000).toInt(),
                    )
                }
            }
        }
        return out
    }

    /**
     * Album art for a MediaStore album id.
     *
     * The old `content://media/external/audio/albumart` path is undocumented and has
     * been unreliable for years, but it is still the only thing that works below
     * API 29, where `loadThumbnail` does not exist. Coil handles either shape.
     */
    private fun albumArtUri(albumId: Long): String? {
        if (albumId <= 0) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        ).toString()
    }
}
