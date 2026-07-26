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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    // OpenDocument (not GetContent) so the read grant can be persisted across restarts.
    val pickAudio = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val updated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.syntra.net.LocalMusicStore.add(context, uri)
            }
            localTracks.clear(); localTracks.addAll(updated)
        }
    }

    // Selecting any song plays it AND opens the dedicated now-playing page.
    val play: (MusicTrack, List<MusicTrack>) -> Unit = { track, queue ->
        MusicPlayer.play(context, track, queue)
        MusicUi.showNowPlaying = true
    }

    // Load the charts once.
    LaunchedEffect(Unit) {
        runCatching { MusicClient.browse() }
            .onSuccess { browse = it; failed = it.isEmpty }
            .onFailure { failed = true }
        loading = false
    }

    // A detail page counts as a full-screen overlay (hide the bottom bar).
    LaunchedEffect(detail) { onOverlayChange(detail != null) }

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
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    onOpenArtist: (MusicArtist) -> Unit,
    onOpenAlbum: (MusicAlbum) -> Unit,
) {
    val tracks = remember { mutableStateListOf<MusicTrack>() }
    val artists = remember { mutableStateListOf<MusicArtist>() }
    val albums = remember { mutableStateListOf<MusicAlbum>() }
    var loading by remember { mutableStateOf(false) }

    // Debounced search: wait for the user to stop typing before hitting the API.
    LaunchedEffect(query) {
        if (query.isBlank()) { tracks.clear(); artists.clear(); albums.clear(); loading = false; return@LaunchedEffect }
        loading = true
        delay(350)
        runCatching { MusicClient.search(query) }.onSuccess { r ->
            tracks.clear(); tracks.addAll(r.tracks)
            artists.clear(); artists.addAll(r.artists)
            albums.clear(); albums.addAll(r.albums)
        }
        loading = false
    }

    if (query.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cari lagu, artis, atau album", color = NexusTextSecondary, fontSize = 14.sp)
        }
        return
    }
    if (loading && tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 150.dp),
    ) {
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
        if (tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && !loading) {
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
