package com.example.syntra

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import com.example.syntra.net.AudioTrimmer
import com.example.syntra.net.MusicAlbum
import com.example.syntra.net.MusicArtist
import com.example.syntra.net.MusicBrowse
import com.example.syntra.net.MusicClient
import com.example.syntra.net.MusicPlayer
import com.example.syntra.net.MusicPlaylist
import com.example.syntra.net.MusicTrack
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Music tab — a Spotify-like browse/search over the free Deezer catalogue.
//
// Data: net/MusicClient (Deezer). Playback: net/MusicPlayer (a 30-second preview
// per track). The mini-player + now-playing screen live at the app root
// (MainActivity) so music keeps playing across tabs.
// ---------------------------------------------------------------------------

/** What the detail overlay is showing, if anything. */
private sealed interface MusicDetail {
    data class Playlist(val item: MusicPlaylist) : MusicDetail
    data class Album(val item: MusicAlbum) : MusicDetail
    data class Artist(val item: MusicArtist) : MusicDetail
}

/**
 * App-root music UI state. Lives outside the Music tab so the now-playing screen
 * (mounted in MainActivity) can be opened from anywhere — tapping a song, or the
 * mini-player — and collapsed back to the mini-player.
 */
object MusicUi {
    var showNowPlaying by mutableStateOf(false)
}

@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onOverlayChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var browse by remember { mutableStateOf(MusicBrowse()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<MusicDetail?>(null) }

    // The user's own audio, picked from device storage. Loaded once, then kept in
    // sync as files are added/removed. These play through the same MusicPlayer.
    val localTracks = remember { mutableStateListOf<MusicTrack>() }
    LaunchedEffect(Unit) {
        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.syntra.net.LocalMusicStore.list(context)
        }
        localTracks.clear(); localTracks.addAll(saved)
    }
    // Public, community-uploaded tracks (searchable across users). Best-effort: the
    // backend endpoint may not exist yet, in which case this stays empty.
    val communityTracks = remember { mutableStateListOf<MusicTrack>() }

    // The file the user just picked and is about to trim/preview/publish. Non-null
    // shows the upload screen; picking no longer publishes straight away.
    var uploadUri by remember { mutableStateOf<Uri?>(null) }

    // OpenDocument (not GetContent) so the read grant can be persisted across restarts.
    val pickAudio = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            uploadUri = uri
        }
    }

    // Selecting any song plays it AND opens the dedicated now-playing page.
    val play: (MusicTrack, List<MusicTrack>) -> Unit = { track, queue ->
        MusicPlayer.play(context, track, queue)
        MusicUi.showNowPlaying = true
    }

    // Load the charts once — but ONLY when the tab is actually shown, not while it's
    // just a warm neighbour of the home. Fetching the catalogue (network + a wall of
    // cover images) the instant the app opens is a big part of why entering felt heavy;
    // now the Music tab pays that cost only when you open it.
    var browseLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible || browseLoaded) return@LaunchedEffect
        browseLoaded = true
        runCatching { MusicClient.browse() }
            .onSuccess { browse = it; failed = it.isEmpty }
            .onFailure { failed = true }
        // Community catalogue is a bonus rail — its absence must not fail the tab.
        runCatching { com.example.syntra.net.SyntraClient.getMusicFeed() }
            .onSuccess { communityTracks.clear(); communityTracks.addAll(it) }
        loading = false
    }

    // A detail page or the upload screen counts as a full-screen overlay (hide the bottom bar).
    LaunchedEffect(detail, uploadUri) { onOverlayChange(detail != null || uploadUri != null) }

    // Refresh the community rail after a successful publish.
    val reloadCommunity: () -> Unit = {
        scope.launch {
            runCatching { com.example.syntra.net.SyntraClient.getMusicFeed() }
                .onSuccess { communityTracks.clear(); communityTracks.addAll(it) }
        }
    }

    // Background publish (reels-style, non-blocking): the upload screen closes the
    // moment you confirm, and the trim+upload runs here behind a small status banner
    // while you keep using the tab. Runs on the Music tab's scope, which the pager
    // keeps warm; the phased status makes the (unavoidable) re-encode wait legible.
    var publishing by remember { mutableStateOf(false) }
    var publishStatus by remember { mutableStateOf("") }
    var publishOk by remember { mutableStateOf(true) }
    val publishMusic: (MusicPublishSpec) -> Unit = { spec ->
        scope.launch {
            publishing = true
            publishOk = true
            val io = kotlinx.coroutines.Dispatchers.IO
            val result = runCatching {
                val lenMs = spec.endMs - spec.startMs
                publishStatus = "Memotong audio…"
                val clip = kotlinx.coroutines.withContext(io) {
                    AudioTrimmer.trim(context, spec.uri, spec.startMs, spec.endMs)
                } ?: error("File tidak memiliki trek audio")

                publishStatus = "Mengunggah…"
                val bytes = kotlinx.coroutines.withContext(io) { clip.readBytes() }
                val (audioMediaId, audioUrl) = com.example.syntra.net.SyntraClient.uploadMediaFull(
                    kind = "audio", extension = "m4a", mimeType = "audio/mp4",
                    bytes = bytes, durationMs = lenMs,
                )

                // Cover art, if the picked file carried an embedded picture.
                var coverMediaId: String? = null
                var coverUrl: String? = spec.artPath
                if (!spec.artPath.isNullOrBlank() && spec.artPath.startsWith("file")) {
                    runCatching {
                        val cb = kotlinx.coroutines.withContext(io) {
                            java.io.File(Uri.parse(spec.artPath).path!!).readBytes()
                        }
                        val (cid, curl) = com.example.syntra.net.SyntraClient.uploadMediaFull(
                            kind = "image", extension = "jpg", mimeType = "image/jpeg", bytes = cb,
                        )
                        coverMediaId = cid; coverUrl = curl
                    }
                }

                // Register as public (best-effort — works once the backend adds it).
                publishStatus = "Menerbitkan…"
                val published = runCatching {
                    com.example.syntra.net.SyntraClient.postMusic(
                        audioMediaId, spec.title, spec.artist, lenMs, coverMediaId,
                    )
                }.getOrNull()
                kotlinx.coroutines.withContext(io) { runCatching { clip.delete() } }

                published ?: MusicTrack(
                    id = audioUrl,
                    title = spec.title,
                    artist = spec.artist,
                    artworkUrl = coverUrl,
                    previewUrl = audioUrl,
                    durationSec = (lenMs / 1000).toInt(),
                )
            }
            result
                .onSuccess { track ->
                    val updated = kotlinx.coroutines.withContext(io) {
                        com.example.syntra.net.LocalMusicStore.addTrack(context, track)
                    }
                    localTracks.clear(); localTracks.addAll(updated)
                    reloadCommunity()
                    publishOk = true
                    publishStatus = "Lagu diterbitkan"
                }
                .onFailure {
                    publishOk = false
                    publishStatus = "Gagal mengunggah: ${it.message}"
                }
            delay(if (result.isSuccess) 1600 else 3500)
            publishing = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(NexusBackground)) {
        Column(Modifier.fillMaxSize()) {
            MusicTopBar(
                searching = searching,
                query = query,
                onQueryChange = { query = it },
                onOpenSearch = { searching = true },
                onCloseSearch = { searching = false; query = "" },
            )

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp)
                }
                searching -> MusicSearchBody(
                    query = query,
                    localTracks = localTracks,
                    onPlay = play,
                    onOpenArtist = { detail = MusicDetail.Artist(it) },
                    onOpenAlbum = { detail = MusicDetail.Album(it) },
                )
                failed -> MusicError { scope.launch {
                    loading = true; failed = false
                    runCatching { MusicClient.browse() }.onSuccess { browse = it; failed = it.isEmpty }.onFailure { failed = true }
                    loading = false
                } }
                else -> MusicBrowseBody(
                    browse = browse,
                    localTracks = localTracks,
                    communityTracks = communityTracks,
                    onPlay = play,
                    onAddLocal = { pickAudio.launch(arrayOf("audio/*")) },
                    onRemoveLocal = { t ->
                        scope.launch {
                            val updated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.example.syntra.net.LocalMusicStore.remove(context, t.id)
                            }
                            localTracks.clear(); localTracks.addAll(updated)
                        }
                    },
                    onOpenPlaylist = { detail = MusicDetail.Playlist(it) },
                    onOpenAlbum = { detail = MusicDetail.Album(it) },
                    onOpenArtist = { detail = MusicDetail.Artist(it) },
                )
            }
        }

        // Detail overlay (playlist / album / artist track list). MUST be inside this
        // Box so it STACKS on top of the browse Column — as a top-level sibling of
        // MusicScreen it wasn't reliably drawn over the browse (it looked "blocked").
        detail?.let { d ->
            MusicDetailScreen(
                detail = d,
                onBack = { detail = null },
                onPlay = play,
            )
        }

        // Upload/trim/preview overlay — the entry point for publishing a device song.
        // Confirming closes it instantly and publishes in the background (below).
        uploadUri?.let { u ->
            MusicUploadScreen(
                uri = u,
                onClose = { uploadUri = null },
                    onConfirm = { spec ->
                    uploadUri = null
                    publishMusic(spec)
                },
            )
        }

        // Non-blocking publish banner — a small card at the top, reels-style. The
        // user keeps browsing/playing while the song trims & uploads behind it.
        if (publishing) {
            MusicPublishBanner(status = publishStatus, ok = publishOk)
        }
    }
}

/** Small top banner shown while a song publishes in the background. */
@Composable
private fun BoxScope.MusicPublishBanner(status: String, ok: Boolean) {
    val done = status.startsWith("Lagu diterbitkan")
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 64.dp)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NexusSurface)
            .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!ok || done) {
            Icon(
                if (done) Icons.Filled.Check else Icons.Filled.Close,
                null,
                tint = if (done) NexusAccentSoft else Color(0xFFFF6B6B),
                modifier = Modifier.size(22.dp),
            )
        } else {
            CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (done) "Selesai" else if (!ok) "Gagal" else "Menerbitkan lagu",
                color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(status, color = NexusTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun MusicTopBar(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            Box(
                modifier = Modifier.size(40.dp).clickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() },
                    onClick = onCloseSearch,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tutup", tint = NexusTextPrimary, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Cari lagu, artis, album…", color = NexusTextSecondary, fontSize = 16.sp)
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = NexusTextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(NexusAccentSoft),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(36.dp).clickable(
                        indication = null, interactionSource = remember { MutableInteractionSource() },
                    ) { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Bersihkan", tint = NexusTextSecondary, modifier = Modifier.size(20.dp)) }
            }
        } else {
            Text("Musik", color = NexusTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.size(42.dp).clickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() },
                    onClick = onOpenSearch,
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Search, "Cari", tint = NexusTextPrimary, modifier = Modifier.size(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Browse body
// ---------------------------------------------------------------------------

@Composable
private fun MusicBrowseBody(
    browse: MusicBrowse,
    localTracks: List<MusicTrack>,
    communityTracks: List<MusicTrack>,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    onAddLocal: () -> Unit,
    onRemoveLocal: (MusicTrack) -> Unit,
    onOpenPlaylist: (MusicPlaylist) -> Unit,
    onOpenAlbum: (MusicAlbum) -> Unit,
    onOpenArtist: (MusicArtist) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 150.dp),
    ) {
        // Device music — the user's own files, always first. No section header/title
        // anymore: the add-from-storage banner IS the entry point (its "+" sits at the
        // start), and it stays put whether or not the user has added tracks yet.
        item { LocalMusicEmpty(onAddLocal) }
        items(localTracks, key = { "local_${it.id}" }) { t ->
            LocalTrackRow(
                track = t,
                onClick = { onPlay(t, localTracks) },
                onRemove = { onRemoveLocal(t) },
            )
        }

        // Community uploads — public tracks other people added from their devices.
        if (communityTracks.isNotEmpty()) {
            item { SectionHeader("Unggahan komunitas") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(communityTracks, key = { "community_${it.id}" }) { t ->
                        TrackCard(t) { onPlay(t, communityTracks) }
                    }
                }
            }
        }

        if (browse.trending.isNotEmpty()) {
            item { SectionHeader("Sedang tren") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(browse.trending, key = { it.id }) { t ->
                        TrackCard(t) { onPlay(t, browse.trending) }
                    }
                }
            }
        }
        if (browse.playlists.isNotEmpty()) {
            item { SectionHeader("Playlist populer") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(browse.playlists, key = { it.id }) { p ->
                        PlaylistCard(p) { onOpenPlaylist(p) }
                    }
                }
            }
        }
        if (browse.artists.isNotEmpty()) {
            item { SectionHeader("Artis teratas") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(browse.artists, key = { it.id }) { a ->
                        ArtistCard(a) { onOpenArtist(a) }
                    }
                }
            }
        }
        if (browse.albums.isNotEmpty()) {
            item { SectionHeader("Album populer") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(browse.albums, key = { it.id }) { a ->
                        AlbumCard(a) { onOpenAlbum(a) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search body
// ---------------------------------------------------------------------------

@Composable
private fun MusicSearchBody(
    query: String,
    localTracks: List<MusicTrack>,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    onOpenArtist: (MusicArtist) -> Unit,
    onOpenAlbum: (MusicAlbum) -> Unit,
) {
    val tracks = remember { mutableStateListOf<MusicTrack>() }
    val artists = remember { mutableStateListOf<MusicArtist>() }
    val albums = remember { mutableStateListOf<MusicAlbum>() }
    val community = remember { mutableStateListOf<MusicTrack>() }
    var loading by remember { mutableStateOf(false) }

    // Your own device songs, matched by title/artist — searched LOCALLY so an
    // uploaded song is always findable here even if the community endpoint is down.
    val localMatches = remember(query, localTracks.size) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else localTracks.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
        }
    }

    // Debounced search: wait for the user to stop typing before hitting the API.
    LaunchedEffect(query) {
        if (query.isBlank()) { tracks.clear(); artists.clear(); albums.clear(); community.clear(); loading = false; return@LaunchedEffect }
        loading = true
        delay(350)
        runCatching { MusicClient.search(query) }.onSuccess { r ->
            tracks.clear(); tracks.addAll(r.tracks)
            artists.clear(); artists.addAll(r.artists)
            albums.clear(); albums.addAll(r.albums)
        }
        // Community results are best-effort — merge them in when the endpoint exists.
        runCatching { com.example.syntra.net.SyntraClient.searchMusic(query) }.onSuccess { c ->
            community.clear(); community.addAll(c)
        }
        loading = false
    }

    if (query.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cari lagu, artis, atau album", color = NexusTextSecondary, fontSize = 14.sp)
        }
        return
    }
    if (loading && localMatches.isEmpty() && tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && community.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 150.dp),
    ) {
        // Your own uploaded/device songs first — instant, always available.
        if (localMatches.isNotEmpty()) {
            item { SectionHeader("Dari perangkat") }
            items(localMatches, key = { "localsearch_${it.id}" }) { t -> TrackRow(t) { onPlay(t, localMatches) } }
        }
        if (community.isNotEmpty()) {
            item { SectionHeader("Dari komunitas") }
            items(community, key = { "community_${it.id}" }) { t -> TrackRow(t) { onPlay(t, community) } }
        }
        if (artists.isNotEmpty()) {
            item { SectionHeader("Artis") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(artists, key = { it.id }) { a -> ArtistCard(a) { onOpenArtist(a) } }
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item { SectionHeader("Lagu") }
            items(tracks, key = { it.id }) { t -> TrackRow(t) { onPlay(t, tracks) } }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader("Album") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albums, key = { it.id }) { a -> AlbumCard(a) { onOpenAlbum(a) } }
                }
            }
        }
        if (localMatches.isEmpty() && tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && community.isEmpty() && !loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Text("Tak ada hasil untuk \"$query\"", color = NexusTextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Detail screen (playlist / album / artist → track list)
// ---------------------------------------------------------------------------

@Composable
private fun MusicDetailScreen(
    detail: MusicDetail,
    onBack: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val tracks = remember(detail) { mutableStateListOf<MusicTrack>() }
    var loading by remember(detail) { mutableStateOf(true) }

    val title: String
    val subtitle: String
    val artwork: String?
    val round: Boolean
    when (detail) {
        is MusicDetail.Playlist -> { title = detail.item.title; subtitle = detail.item.subtitle; artwork = detail.item.pictureUrl; round = false }
        is MusicDetail.Album -> { title = detail.item.title; subtitle = detail.item.artist; artwork = detail.item.artworkUrl; round = false }
        is MusicDetail.Artist -> { title = detail.item.name; subtitle = "Artis"; artwork = detail.item.pictureUrl; round = true }
    }

    LaunchedEffect(detail) {
        loading = true
        val list = runCatching {
            when (detail) {
                is MusicDetail.Playlist -> MusicClient.playlistTracks(detail.item.id)
                is MusicDetail.Album -> MusicClient.albumTracks(detail.item.id)
                is MusicDetail.Artist -> MusicClient.artistTopTracks(detail.item.id)
            }
        }.getOrDefault(emptyList())
        tracks.clear(); tracks.addAll(list)
        loading = false
    }

    Box(Modifier.fillMaxSize().background(NexusBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp),
        ) {
            item {
                Column {
                    // Top bar with a back button over the header.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(start = 12.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clickable(
                                indication = null, interactionSource = remember { MutableInteractionSource() },
                                onClick = onBack,
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = NexusTextPrimary, modifier = Modifier.size(23.dp)) }
                    }
                    // Big header art + title.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtworkImage(
                            url = artwork,
                            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(if (round) 90.dp else 14.dp)),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(title, color = NexusTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(subtitle, color = NexusTextSecondary, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        // Play-all button.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)))
                                .clickable(
                                    indication = null, interactionSource = remember { MutableInteractionSource() },
                                ) { tracks.firstOrNull()?.let { onPlay(it, tracks.toList()) } }
                                .padding(horizontal = 32.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Putar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp)
                    }
                }
            } else {
                items(tracks, key = { it.id }) { t -> TrackRow(t) { onPlay(t, tracks.toList()) } }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards & rows
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = NexusTextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
    )
}

/** Device-music add banner — a big tappable card with the "+" at the start. */
@Composable
private fun LocalMusicEmpty(onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NexusSurface)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(NexusAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, null, tint = NexusAccentSoft, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Tambahkan lagu dari penyimpanan", color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("Putar file musik yang tersimpan di perangkatmu", color = NexusTextSecondary, fontSize = 12.sp)
        }
    }
}

/** A device-music row: artwork · title/artist · remove. */
@Composable
private fun LocalTrackRow(track: MusicTrack, onClick: () -> Unit, onRemove: () -> Unit) {
    val isCurrent = MusicPlayer.current?.id == track.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(url = track.artworkUrl, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (isCurrent) NexusAccentSoft else NexusTextPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(track.artist, color = NexusTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isCurrent) { NowPlayingBadge(); Spacer(Modifier.width(4.dp)) }
        Box(
            modifier = Modifier.size(40.dp).clickable(
                indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onRemove,
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "Hapus dari daftar", tint = NexusTextSecondary, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun TrackCard(track: MusicTrack, onClick: () -> Unit) {
    val isCurrent = MusicPlayer.current?.id == track.id
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
    ) {
        Box {
            ArtworkImage(url = track.artworkUrl, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)))
            if (isCurrent) NowPlayingBadge(Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(track.title, color = if (isCurrent) NexusAccentSoft else NexusTextPrimary, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, color = NexusTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistCard(p: MusicPlaylist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
    ) {
        ArtworkImage(url = p.pictureUrl, modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(8.dp))
        Text(p.title, color = NexusTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
    }
}

@Composable
private fun ArtistCard(a: MusicArtist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(url = a.pictureUrl, modifier = Modifier.size(120.dp).clip(CircleShape))
        Spacer(Modifier.height(8.dp))
        Text(a.name, color = NexusTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumCard(a: MusicAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
    ) {
        ArtworkImage(url = a.artworkUrl, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(8.dp))
        Text(a.title, color = NexusTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.artist, color = NexusTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackRow(track: MusicTrack, onClick: () -> Unit) {
    val isCurrent = MusicPlayer.current?.id == track.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(url = track.artworkUrl, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (isCurrent) NexusAccentSoft else NexusTextPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(track.artist, color = NexusTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isCurrent) NowPlayingBadge()
    }
}

/** A small equaliser-ish dot marking the currently playing track. */
@Composable
private fun NowPlayingBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(20.dp).background(NexusAccent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (MusicPlayer.isPlaying) Icons.Filled.MusicNote else Icons.Filled.Pause,
            null, tint = Color.White, modifier = Modifier.size(12.dp),
        )
    }
}

/** Artwork with a subtle placeholder while it loads / when absent. */
@Composable
private fun ArtworkImage(url: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(NexusSurface), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.MusicNote, null, tint = NexusTextSecondary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun MusicError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = NexusTextSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Gagal memuat musik", color = NexusTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Periksa koneksi lalu coba lagi.", color = NexusTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NexusAccent)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onRetry)
                .padding(horizontal = 28.dp, vertical = 11.dp),
        ) { Text("Coba lagi", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ---------------------------------------------------------------------------
// Upload / trim / preview — publish a device song to the public catalogue
// ---------------------------------------------------------------------------

/**
 * Full-screen flow to publish a song picked from device storage:
 *  1. edit title/artist (prefilled from the file's tags),
 *  2. pick the portion to keep with a two-handle range slider (max 10 min),
 *  3. preview exactly that portion,
 *  4. confirm — then the clip is trimmed (re-encoded to .m4a), uploaded, and
 *     registered as a public, searchable track.
 *
 * The original file is never uploaded — only the trimmed slice — so the user
 * always publishes just the part they chose.
 *
 * On confirm this screen does NOT do the (slow) trim/upload itself — it just hands
 * a [MusicPublishSpec] back and closes. The heavy work runs in the background in
 * MusicScreen (reels-style), so the user returns to the tab instantly and can keep
 * browsing while it publishes behind a small banner.
 */
@Composable
private fun MusicUploadScreen(
    uri: Uri,
    onClose: () -> Unit,
    onConfirm: (MusicPublishSpec) -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current

    var meta by remember(uri) { mutableStateOf<MusicTrack?>(null) }
    var title by remember(uri) { mutableStateOf("") }
    var artist by remember(uri) { mutableStateOf("") }
    // Selection in SECONDS; the max window is 10 minutes.
    val maxWindow = (AudioTrimmer.MAX_CLIP_MS / 1000).toFloat()
    var range by remember(uri) { mutableStateOf(0f..0f) }

    // A dedicated player for previewing the SELECTED portion of the original file.
    val player = remember(uri) { android.media.MediaPlayer() }
    var prepared by remember(uri) { mutableStateOf(false) }
    var playerDurSec by remember(uri) { mutableStateOf(0) }
    var previewPlaying by remember(uri) { mutableStateOf(false) }

    var showConfirm by remember { mutableStateOf(false) }

    // Probe tags + prepare the preview player once.
    LaunchedEffect(uri) {
        val probed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.example.syntra.net.LocalMusicStore.probe(context, uri) }.getOrNull()
        }
        if (probed != null) {
            meta = probed
            if (title.isBlank()) title = probed.title
            if (artist.isBlank()) artist = probed.artist
        }
        runCatching {
            player.setDataSource(context, uri)
            player.setOnPreparedListener { mp ->
                prepared = true
                playerDurSec = (mp.duration / 1000).coerceAtLeast(0)
            }
            player.prepareAsync()
        }
    }
    DisposableEffect(uri) { onDispose { runCatching { player.release() } } }

    // Once we know the real length, default the selection to the whole song (capped).
    val totalSec = maxOf(meta?.durationSec ?: 0, playerDurSec)
    LaunchedEffect(totalSec) {
        if (totalSec > 0 && range == 0f..0f) {
            range = 0f..minOf(totalSec.toFloat(), maxWindow)
        }
    }

    val startMs = (range.start * 1000).toInt()
    val endMs = (range.endInclusive * 1000).toInt()

    // Stop the preview automatically at the end of the selected window.
    LaunchedEffect(previewPlaying, startMs, endMs) {
        if (!previewPlaying) return@LaunchedEffect
        while (previewPlaying) {
            val pos = runCatching { player.currentPosition }.getOrDefault(0)
            if (pos >= endMs || pos < startMs - 500) {
                runCatching { player.pause(); player.seekTo(startMs) }
                previewPlaying = false
                break
            }
            delay(80)
        }
    }

    val togglePreview: () -> Unit = {
        if (prepared) {
            if (previewPlaying) {
                runCatching { player.pause() }; previewPlaying = false
            } else {
                MusicPlayer.pauseForExternalAudio()
                runCatching { player.seekTo(startMs); player.start() }
                previewPlaying = true
            }
        }
    }

    // Hand the choice back to MusicScreen and let it publish in the background.
    val doConfirm: () -> Unit = {
        showConfirm = false
        previewPlaying = false
        runCatching { player.pause() }
        onConfirm(
            MusicPublishSpec(
                uri = uri,
                startMs = (range.start * 1000).toLong(),
                endMs = (range.endInclusive * 1000).toLong(),
                title = title.trim().ifBlank { "Tanpa judul" },
                artist = artist.trim().ifBlank { "Dari perangkat" },
                artPath = meta?.artworkUrl,
            ),
        )
    }

    val clipLenSec = (range.endInclusive - range.start).toInt()

    Box(Modifier.fillMaxSize().background(NexusBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clickable(
                        indication = null, interactionSource = remember { MutableInteractionSource() },
                        onClick = onClose,
                    ),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Tutup", tint = NexusTextPrimary, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(4.dp))
                Text("Unggah lagu", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // Cover.
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArtworkImage(
                    url = meta?.artworkUrl,
                    modifier = Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)),
                )
            }

            // Title + artist fields.
            UploadField(label = "Judul", value = title, onValueChange = { title = it }, hint = "Judul lagu")
            UploadField(label = "Artis", value = artist, onValueChange = { artist = it }, hint = "Nama artis")

            // Trim controls.
            Text(
                "Pilih bagian yang diunggah",
                color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp),
            )
            Text(
                "Geser kedua ujung untuk menentukan dari mana sampai mana. Maksimal 10 menit.",
                color = NexusTextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            if (totalSec <= 0) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp)
                }
            } else {
                RangeSlider(
                    value = range,
                    onValueChange = { r ->
                        var s = r.start.coerceIn(0f, totalSec.toFloat())
                        var e = r.endInclusive.coerceIn(0f, totalSec.toFloat())
                        if (e - s > maxWindow) {
                            // Keep the window <= 10 min by pushing the handle that moved.
                            if (s != range.start) s = e - maxWindow else e = s + maxWindow
                        }
                        if (e < s) e = s
                        range = s..e
                    },
                    valueRange = 0f..totalSec.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = NexusAccent,
                        activeTrackColor = NexusAccent,
                        inactiveTrackColor = NexusStroke,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Mulai ${clock(startMs)}", color = NexusTextSecondary, fontSize = 12.sp)
                    Text("Selesai ${clock(endMs)}", color = NexusTextSecondary, fontSize = 12.sp)
                }
                Text(
                    "Durasi klip: ${clock(clipLenSec * 1000)}",
                    color = NexusAccentSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Preview the selected portion.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            Brush.linearGradient(listOf(NexusAccentSoft, NexusAccent)), CircleShape,
                        )
                        .clickable(
                            indication = null, interactionSource = remember { MutableInteractionSource() },
                            enabled = prepared && totalSec > 0,
                            onClick = togglePreview,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (previewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Pratinjau", tint = Color.White, modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    if (previewPlaying) "Memutar pratinjau…" else "Dengarkan pratinjau klip",
                    color = NexusTextPrimary, fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Publish button → confirmation.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)))
                    .clickable(
                        indication = null, interactionSource = remember { MutableInteractionSource() },
                        enabled = totalSec > 0 && clipLenSec > 0,
                        onClick = { showConfirm = true },
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Terbitkan ke publik", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(28.dp))
        }

        // Confirmation sheet.
        if (showConfirm) {
            UploadConfirmDialog(
                title = title.trim().ifBlank { "Tanpa judul" },
                artist = artist.trim().ifBlank { "Dari perangkat" },
                lengthLabel = clock(clipLenSec * 1000),
                onCancel = { showConfirm = false },
                onConfirm = doConfirm,
            )
        }
    }
}

/** What [MusicUploadScreen] hands back on confirm; MusicScreen does the upload. */
private data class MusicPublishSpec(
    val uri: Uri,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val artist: String,
    /** Embedded-cover file:// path, if the picked file had one. */
    val artPath: String?,
)

/** A themed labelled text field for the upload form. */
@Composable
private fun UploadField(label: String, value: String, onValueChange: (String) -> Unit, hint: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(label, color = NexusTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(NexusSurface)
                .border(1.dp, NexusStroke, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty()) Text(hint, color = NexusTextSecondary, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(NexusAccentSoft),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "Really publish?" — the approval step before anything leaves the device. */
@Composable
private fun UploadConfirmDialog(
    title: String,
    artist: String,
    lengthLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(NexusSurface)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(22.dp),
        ) {
            Text("Terbitkan lagu ini?", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "\"$title\" — $artist ($lengthLabel) akan terlihat publik dan bisa dicari oleh siapa saja.",
                color = NexusTextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onCancel)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("Batal", color = NexusTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onConfirm)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) { Text("Terbitkan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Mini-player + Now-playing (mounted at the app root by MainActivity)
// ---------------------------------------------------------------------------

/**
 * Compact player bar above the nav. Renders nothing when no track is loaded.
 * Tap → expand to now-playing. Swipe left/right → dismiss AND stop the music.
 */
@Composable
fun MusicMiniPlayer(modifier: Modifier = Modifier, onExpand: () -> Unit) {
    val track = MusicPlayer.current ?: return

    // Drive the progress bar while playing.
    LaunchedEffect(MusicPlayer.isPlaying, track.id) {
        while (MusicPlayer.isPlaying) { MusicPlayer.tick(); delay(200) }
    }

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var barWidth by remember { mutableStateOf(1) }
    // A new track shouldn't inherit a half-finished drag.
    LaunchedEffect(track.id) { offsetX.snapTo(0f) }

    Column(
        modifier = modifier.fillMaxWidth().onSizeChanged { barWidth = it.width.coerceAtLeast(1) },
    ) {
        // Thin progress line on top of the bar.
        Box(Modifier.fillMaxWidth().height(2.dp).background(NexusStroke)) {
            Box(Modifier.fillMaxWidth(MusicPlayer.progress).height(2.dp).background(NexusAccentSoft))
        }
        val dismissAt = barWidth * 0.32f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (kotlin.math.abs(offsetX.value) / barWidth).coerceIn(0f, 1f) }
                .background(Color(0xFF17171F))
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(offsetX.value) > dismissAt) {
                                // Fling off-screen, then STOP the music (not pause).
                                val target = if (offsetX.value > 0) barWidth.toFloat() else -barWidth.toFloat()
                                scope.launch { offsetX.animateTo(target); MusicPlayer.stop() }
                            } else {
                                scope.launch { offsetX.animateTo(0f) }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                }
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onExpand)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(url = track.artworkUrl, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = NexusTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlayerIconButton(
                icon = if (MusicPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                size = 40.dp, iconSize = 24.dp,
            ) { MusicPlayer.togglePlayPause() }
        }
    }
}

/**
 * Full-screen now-playing, Spotify-style: big art, title, like, seek bar, shuffle/
 * repeat + transport. Swipe DOWN (or the chevron) collapses back to the mini-player.
 */
@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val track = MusicPlayer.current
    if (track == null) { onClose(); return }
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var liked by remember(track.id) { mutableStateOf(false) }

    LaunchedEffect(MusicPlayer.isPlaying, track.id) {
        while (MusicPlayer.isPlaying) { MusicPlayer.tick(); delay(200) }
    }

    // Swipe-down-to-collapse: drag the whole sheet down; past a threshold it
    // dismisses to the mini-player (music keeps playing), otherwise it springs back.
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var sheetHeight by remember { mutableStateOf(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sheetHeight = it.height.coerceAtLeast(1) }
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .background(Brush.verticalGradient(listOf(Color(0xFF23202E), Color(0xFF0C0C12))))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY.value > sheetHeight * 0.18f) onClose()
                        else scope.launch { offsetY.animateTo(0f) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    // Only downward drags collapse; clamp at the top.
                    scope.launch { offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f)) }
                }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: collapse chevron · label · more.
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerIconButton(Icons.Filled.KeyboardArrowDown, size = 40.dp, iconSize = 28.dp) { onClose() }
                Spacer(Modifier.weight(1f))
                Text("SEDANG DIPUTAR", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                PlayerIconButton(Icons.Filled.MoreVert, size = 40.dp, iconSize = 22.dp) { /* menu lanjutan */ }
            }
            // Square cover, centred in the flexible middle so the controls below are
            // always on screen (a fillMaxWidth-only image ate the whole height before).
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArtworkImage(
                    url = track.artworkUrl,
                    modifier = Modifier.fillMaxWidth(0.88f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)),
                )
            }
            // Title + artist + like.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(track.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlayerIconButton(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    size = 44.dp, iconSize = 26.dp,
                    tint = if (liked) NexusAccentSoft else Color.White,
                ) { liked = !liked }
            }
            Spacer(Modifier.height(18.dp))
            NowPlayingSeekBar()
            Spacer(Modifier.height(14.dp))
            // Controls: shuffle · prev · play · next · repeat.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(Icons.Filled.Shuffle, size = 46.dp, iconSize = 24.dp,
                    tint = if (MusicPlayer.shuffle) NexusAccentSoft else Color.White.copy(alpha = 0.7f)) {
                    MusicPlayer.toggleShuffle()
                }
                PlayerIconButton(Icons.Filled.SkipPrevious, size = 52.dp, iconSize = 34.dp,
                    tint = if (MusicPlayer.hasPrevious) Color.White else Color.White.copy(alpha = 0.3f)) {
                    MusicPlayer.previous(context)
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Brush.linearGradient(listOf(NexusAccentSoft, NexusAccent)), CircleShape)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { MusicPlayer.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (MusicPlayer.preparing) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(if (MusicPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Putar/Jeda",
                            tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
                PlayerIconButton(Icons.Filled.SkipNext, size = 52.dp, iconSize = 34.dp,
                    tint = if (MusicPlayer.hasNext || MusicPlayer.shuffle) Color.White else Color.White.copy(alpha = 0.3f)) {
                    MusicPlayer.next(context)
                }
                PlayerIconButton(
                    icon = if (MusicPlayer.repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    size = 46.dp, iconSize = 24.dp,
                    tint = if (MusicPlayer.repeatOne) NexusAccentSoft else Color.White.copy(alpha = 0.7f),
                ) { MusicPlayer.toggleRepeat() }
            }
            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(12.dp))
        }
    }
}

@Composable
private fun NowPlayingSeekBar() {
    var width by remember { mutableStateOf(1) }
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectTapGestures { off -> MusicPlayer.seekToFraction(off.x / width) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume(); MusicPlayer.seekToFraction(change.position.x / width)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)))
            Box(Modifier.fillMaxWidth(MusicPlayer.progress).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(MusicPlayer.positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Text(clock(MusicPlayer.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(size).clickable(
            indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize)) }
}

private fun clock(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
