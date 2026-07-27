package com.example.syntra.net

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks the user has hearted, kept on the device.
 *
 * The like button in the now-playing screen used to be `remember { mutableStateOf(false) }`
 * — pure local state that was thrown away the moment the track changed. Tapping it did
 * nothing at all, which is why there was no "liked music" to browse. Everything lands
 * here now, newest first, and the Music home reads it back.
 *
 * Whole [MusicTrack]s are stored rather than ids, because the catalogue is a third
 * party ([MusicClient]) — keeping the title, artist and artwork means the liked list
 * renders and plays without a round-trip, and still works with no connection.
 */
object LikedMusicStore {

    private const val PREF = "syntra_liked_music"
    private const val KEY = "tracks"
    private const val MAX = 300

    /** Newest-first, mirrored into Compose state so the UI updates as it changes. */
    val tracks = mutableStateListOf<MusicTrack>()
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val raw = prefs(context).getString(KEY, null)
                if (raw != null) {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) tracks.add(fromJson(arr.getJSONObject(i)))
                }
            }
            loaded = true
        }
    }

    fun isLiked(context: Context, id: String): Boolean {
        ensure(context)
        return tracks.any { it.id == id }
    }

    /** Adds or removes [track]. Returns the state it ended up in. */
    fun toggle(context: Context, track: MusicTrack): Boolean {
        ensure(context)
        val existing = tracks.indexOfFirst { it.id == track.id }
        return if (existing >= 0) {
            tracks.removeAt(existing)
            persist(context)
            false
        } else {
            tracks.add(0, track) // newest first
            while (tracks.size > MAX) tracks.removeAt(tracks.lastIndex)
            persist(context)
            true
        }
    }

    /**
     * The artists the user likes most, most-liked first — the material for the
     * quick-access chips on the Music home.
     */
    fun topArtists(context: Context, limit: Int = 6): List<String> {
        ensure(context)
        return tracks.asSequence()
            .map { it.artist }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    fun clear(context: Context) {
        tracks.clear()
        runCatching { prefs(context).edit().clear().apply() }
    }

    private fun persist(context: Context) {
        runCatching {
            val arr = JSONArray()
            tracks.forEach { arr.put(toJson(it)) }
            prefs(context).edit().putString(KEY, arr.toString()).apply()
        }
    }

    private fun toJson(t: MusicTrack) = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("artist", t.artist)
        put("artist_id", t.artistId)
        put("album", t.album)
        t.artworkUrl?.let { put("artwork", it) }
        put("preview", t.previewUrl)
        put("duration", t.durationSec)
    }

    private fun fromJson(o: JSONObject) = MusicTrack(
        id = o.getString("id"),
        title = o.optString("title"),
        artist = o.optString("artist"),
        artistId = o.optString("artist_id"),
        album = o.optString("album"),
        artworkUrl = o.optString("artwork", "").ifBlank { null },
        previewUrl = o.optString("preview"),
        durationSec = o.optInt("duration"),
    )
}
