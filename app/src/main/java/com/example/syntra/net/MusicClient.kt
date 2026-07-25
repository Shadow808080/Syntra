package com.example.syntra.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Music catalogue client, backed by the free, **key-less** Deezer API
 * (https://api.deezer.com). Read-only: browse charts, search, and open a
 * playlist / album / artist. Audio is a 30-second preview MP3 whose URL each
 * track carries; the app plays it directly (see MusicPlayer).
 *
 * Deliberately separate from [SyntraClient]: this talks to a third party, not to
 * the Syntra backend, and holds no auth. If we later proxy music through our own
 * backend (to add "liked songs" or full-length streaming), only this file moves.
 */
object MusicClient {

    private const val BASE = "https://api.deezer.com"

    private val http = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .build()

    // --- public API -------------------------------------------------------

    /** The browse/home payload: top tracks, playlists, artists, albums. */
    suspend fun browse(): MusicBrowse = withContext(Dispatchers.IO) {
        val chart = getObject("/chart")
        MusicBrowse(
            trending = chart.optJSONObject("tracks").tracks(),
            playlists = chart.optJSONObject("playlists").playlists(),
            artists = chart.optJSONObject("artists").artists(),
            albums = chart.optJSONObject("albums").albums(),
        )
    }

    /** Search everything for [query]. Blank query returns empty results. */
    suspend fun search(query: String): MusicSearchResults = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext MusicSearchResults()
        val enc = java.net.URLEncoder.encode(q, "UTF-8")
        MusicSearchResults(
            tracks = getObject("/search/track?q=$enc&limit=40").optJSONArray("data").tracks(),
            artists = getObject("/search/artist?q=$enc&limit=20").optJSONArray("data").artists(),
            albums = getObject("/search/album?q=$enc&limit=20").optJSONArray("data").albums(),
        )
    }

    /** The tracks inside a playlist. */
    suspend fun playlistTracks(id: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        getObject("/playlist/$id").optJSONObject("tracks").tracks()
    }

    /** The tracks on an album (they inherit the album's cover). */
    suspend fun albumTracks(id: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val album = getObject("/album/$id")
        val cover = album.optStringOrNull("cover_big") ?: album.optStringOrNull("cover_medium")
        album.optJSONObject("tracks").tracks().map {
            if (it.artworkUrl == null && cover != null) it.copy(artworkUrl = cover) else it
        }
    }

    /** An artist's most popular tracks. */
    suspend fun artistTopTracks(id: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        getObject("/artist/$id/top?limit=40").optJSONArray("data").tracks()
    }

    // --- HTTP -------------------------------------------------------------

    private fun getObject(path: String): JSONObject {
        val req = Request.Builder().url(BASE + path).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) return JSONObject()
            return runCatching { JSONObject(body) }.getOrDefault(JSONObject())
        }
    }

    // --- parsing (Deezer shapes → app models) -----------------------------

    private fun JSONObject?.tracks(): List<MusicTrack> =
        this?.optJSONArray("data").tracks()

    private fun JSONArray?.tracks(): List<MusicTrack> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val preview = o.optString("preview")
            if (preview.isBlank()) return@mapNotNull null // unplayable → skip
            val album = o.optJSONObject("album")
            val artist = o.optJSONObject("artist")
            MusicTrack(
                id = o.optString("id"),
                title = o.optString("title_short").ifBlank { o.optString("title") },
                artist = artist?.optString("name").orEmpty(),
                artistId = artist?.optString("id").orEmpty(),
                album = album?.optString("title").orEmpty(),
                artworkUrl = album?.optStringOrNull("cover_medium")
                    ?: album?.optStringOrNull("cover_big"),
                previewUrl = preview,
                durationSec = o.optInt("duration"),
            )
        }
    }

    private fun JSONObject?.playlists(): List<MusicPlaylist> {
        val arr = this?.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MusicPlaylist(
                id = o.optString("id"),
                title = o.optString("title"),
                subtitle = o.optJSONObject("user")?.optString("name")
                    ?.takeIf { it.isNotBlank() }?.let { "Oleh $it" }.orEmpty(),
                pictureUrl = o.optStringOrNull("picture_medium") ?: o.optStringOrNull("picture_big"),
                trackCount = o.optInt("nb_tracks"),
            )
        }
    }

    private fun JSONObject?.artists(): List<MusicArtist> =
        this?.optJSONArray("data").artists()

    private fun JSONArray?.artists(): List<MusicArtist> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MusicArtist(
                id = o.optString("id"),
                name = o.optString("name"),
                pictureUrl = o.optStringOrNull("picture_medium") ?: o.optStringOrNull("picture_big"),
            )
        }
    }

    private fun JSONObject?.albums(): List<MusicAlbum> =
        this?.optJSONArray("data").albums()

    private fun JSONArray?.albums(): List<MusicAlbum> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MusicAlbum(
                id = o.optString("id"),
                title = o.optString("title"),
                artist = o.optJSONObject("artist")?.optString("name").orEmpty(),
                artworkUrl = o.optStringOrNull("cover_medium") ?: o.optStringOrNull("cover_big"),
            )
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() }
}
