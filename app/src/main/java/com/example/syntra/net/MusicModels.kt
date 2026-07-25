package com.example.syntra.net

/**
 * Data models for the Music tab.
 *
 * Kept provider-agnostic on purpose: today the source is the free, key-less
 * Deezer API ([MusicClient]), but nothing in the UI depends on Deezer's shapes —
 * only on these. Swapping to another catalogue (Audius for full-length tracks,
 * a Syntra backend proxy, …) means rewriting [MusicClient], not the screens.
 */

/** One playable track. [previewUrl] is a 30-second MP3 on the provider's CDN. */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val previewUrl: String = "",
    /** Full track length in seconds (the preview itself is ~30s). */
    val durationSec: Int = 0,
)

/** An album cover + title, for browse rails and search. */
data class MusicAlbum(
    val id: String,
    val title: String,
    val artist: String = "",
    val artworkUrl: String? = null,
)

/** An artist with a round picture. */
data class MusicArtist(
    val id: String,
    val name: String,
    val pictureUrl: String? = null,
)

/** A playlist card (editorial or chart). */
data class MusicPlaylist(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val pictureUrl: String? = null,
    val trackCount: Int = 0,
)

/** The browse/home payload: the sections a Spotify-like landing shows. */
data class MusicBrowse(
    val trending: List<MusicTrack> = emptyList(),
    val playlists: List<MusicPlaylist> = emptyList(),
    val artists: List<MusicArtist> = emptyList(),
    val albums: List<MusicAlbum> = emptyList(),
) {
    val isEmpty: Boolean
        get() = trending.isEmpty() && playlists.isEmpty() && artists.isEmpty() && albums.isEmpty()
}

/** Search results, split by kind so the UI can tab/section them. */
data class MusicSearchResults(
    val tracks: List<MusicTrack> = emptyList(),
    val artists: List<MusicArtist> = emptyList(),
    val albums: List<MusicAlbum> = emptyList(),
)
