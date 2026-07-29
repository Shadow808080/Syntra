package com.example.syntra

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.example.syntra.net.AppLock
import com.example.syntra.net.UploadCenter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.HideImage
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import com.example.syntra.net.AudioTrimmer
import com.example.syntra.net.MusicAlbum
import com.example.syntra.net.MusicArtist
import com.example.syntra.net.LikedMusicStore
import com.example.syntra.net.MusicBrowse
import com.example.syntra.net.MusicClient
import com.example.syntra.net.MusicPlayer
import com.example.syntra.net.MusicPlaylist
import androidx.compose.ui.graphics.toArgb
import com.example.syntra.net.DeviceAudio
import com.example.syntra.net.MusicTrack
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
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

    /** The user's own device-storage songs, shown as an album-style page. */
    data class Local(val tracks: List<MusicTrack>) : MusicDetail
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

    // The music actually on this phone, read from MediaStore. A query, not a
    // collection — nothing here is stored by us and nothing is uploaded.
    val localTracks = remember { mutableStateListOf<MusicTrack>() }
    var scanningDevice by remember { mutableStateOf(false) }

    suspend fun scanDevice() {
        if (!DeviceAudio.hasPermission(context)) { localTracks.clear(); return }
        scanningDevice = true
        val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DeviceAudio.list(context)
        }
        localTracks.clear(); localTracks.addAll(found)
        scanningDevice = false
    }

    val askAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) scope.launch { scanDevice() } }

    // Scan on entry when we already have the grant, so the card shows a real count
    // instead of asking for permission before anyone has expressed interest.
    LaunchedEffect(Unit) { scanDevice() }
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
    // The latch is set only AFTER a load actually SUCCEEDS. Setting it up-front is what
    // made the tab "sangat rawan gagal": `visible` is `pager.currentPage == MUSIC`, and
    // currentPage flips the moment a swipe crosses half-way — and animateScrollToPage
    // SWEEPS THROUGH the intermediate pages. So jumping Chat → Rooms, or dragging
    // toward Music and letting it spring back, made this effect start, burn the latch,
    // and then get cancelled mid-request. On the next real visit the guard returned
    // immediately: no data, and `loading` stuck true forever, which the UI renders as a
    // permanent spinner with no retry button (the loading branch wins over failed).
    //
    // Latching on success instead means a cancelled or failed attempt simply doesn't
    // count, and opening the tab tries again.
    var refreshing by remember { mutableStateOf(false) }
    // Which community track is waiting for a hand-picked cover. The image is copied to
    // this device only — see TrackArtStore for why it is never uploaded.
    var artTarget by remember { mutableStateOf<String?>(null) }
    val pickArt = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val id = artTarget
        artTarget = null
        if (uri != null && id != null) {
            if (com.example.syntra.net.TrackArtStore.put(context, id, uri) == null) {
                Toast.makeText(context, "Gagal membaca gambar.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var browseLoaded by remember { mutableStateOf(false) }

    /** Re-fetches the catalogue and the community rail. Shared by refresh and retry. */
    suspend fun reloadMusic() {
        val result = runCatching { MusicClient.browse() }
        (result.exceptionOrNull() as? kotlinx.coroutines.CancellationException)?.let { throw it }
        result
            .onSuccess {
                browse = it
                failed = it.isEmpty
                browseLoaded = !it.isEmpty
            }
            .onFailure { failed = true }
        runCatching { com.example.syntra.net.SyntraClient.getMusicFeed() }
            .onSuccess { communityTracks.clear(); communityTracks.addAll(it) }
        // Re-scan the phone as well, so a song added to the device since the last
        // look shows up. Nothing to do with the community feed above it.
        scanDevice()
        loading = false
    }

    LaunchedEffect(visible) {
        if (!visible || browseLoaded) return@LaunchedEffect
        val result = runCatching { MusicClient.browse() }
        // runCatching swallows CancellationException too. A cancelled effect must not
        // report failure or clear `loading` — it must leave everything untouched so the
        // next visit retries cleanly.
        (result.exceptionOrNull() as? kotlinx.coroutines.CancellationException)?.let { throw it }
        result
            .onSuccess {
                browse = it
                failed = it.isEmpty
                // An empty catalogue is Deezer throttling us, not a real answer — don't
                // latch, so simply reopening the tab retries.
                browseLoaded = !it.isEmpty
            }
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
    // Driven by UploadCenter so the status banner is still there — and still
    // running — when the user returns to the tab mid-publish.
    val publishing = UploadCenter.musicBusy
    var publishStatus by remember { mutableStateOf("") }
    var publishOk by remember { mutableStateOf(true) }
    val publishMusic: (MusicPublishSpec) -> Unit = { spec ->
        // App-scoped: trimming + uploading a song takes far longer than a glance at
        // another tab, and rememberCoroutineScope() cancelled the whole thing the
        // moment this screen left composition.
        UploadCenter.startMusic(label = "Menerbitkan lagu", etaMs = 8000L) {
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

                // Publishing is the POINT of this feature — the track is meant to be
                // searchable by other people — so a failure here must NOT be swallowed.
                //
                // This used to be runCatching{...}.getOrNull() with a local-only
                // fallback, so a failed publish produced a song that appeared in your
                // own library and looked completely successful. music_tracks was empty
                // while the UI had been reporting success the whole time. If the server
                // rejects it, say so and fail the upload.
                publishStatus = "Menerbitkan…"
                val published = try {
                    com.example.syntra.net.SyntraClient.postMusic(
                        audioMediaId, spec.title, spec.artist, lenMs, coverMediaId,
                    )
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    android.util.Log.e("SyntraMusic", "publish failed", t)
                    kotlinx.coroutines.withContext(io) { runCatching { clip.delete() } }
                    error("Gagal menerbitkan lagu: ${t.message ?: t::class.java.simpleName}")
                }
                kotlinx.coroutines.withContext(io) { runCatching { clip.delete() } }
                published
            }
            result
                .onSuccess { _ ->
                    // Publishing puts a track in the PUBLIC catalogue. It used to also
                    // insert it into the device list, which is what made "lagu dari
                    // penyimpanan" look like it only worked after an upload. The device
                    // list is MediaStore; publishing does not change what is on the phone.
                    reloadCommunity()
                    publishOk = true
                    publishStatus = "Lagu diterbitkan"
                }
                .onFailure {
                    publishOk = false
                    publishStatus = "Gagal mengunggah: ${it.message}"
                }
            // The banner lingers briefly on the outcome; UploadCenter clears the
            // running state itself once this block returns.
            delay(if (result.isSuccess) 1600 else 3500)
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
                onPublish = { pickAudio.launch(arrayOf("audio/*")) },
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
                failed -> MusicError { scope.launch { loading = true; failed = false; reloadMusic() } }
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            reloadMusic()
                            refreshing = false
                        }
                    },
                ) {
                MusicBrowseBody(
                    browse = browse,
                    localTracks = localTracks,
                    communityTracks = communityTracks,
                    onSetCommunityArt = { t ->
                        artTarget = t.id
                        AppLock.expectSystemDialog()
                        pickArt.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onDeleteCommunity = { t ->
                        scope.launch {
                            val ok = runCatching { com.example.syntra.net.SyntraClient.deleteMusic(t.id) }.isSuccess
                            if (ok) {
                                communityTracks.removeAll { it.id == t.id }
                                Toast.makeText(context, "Lagu dihapus.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Gagal menghapus lagu.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onEditCommunityTitle = { t, newTitle ->
                        val clean = newTitle.trim()
                        if (clean.isNotBlank()) scope.launch {
                            val ok = runCatching {
                                com.example.syntra.net.SyntraClient.updateMusicTitle(t.id, clean)
                            }.isSuccess
                            if (ok) {
                                val i = communityTracks.indexOfFirst { it.id == t.id }
                                if (i >= 0) communityTracks[i] = communityTracks[i].copy(title = clean)
                            } else {
                                Toast.makeText(context, "Gagal mengubah judul.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onPlay = play,
                    onPublish = { pickAudio.launch(arrayOf("audio/*")) },
                    onOpenPlaylist = { detail = MusicDetail.Playlist(it) },
                    onOpenAlbum = { detail = MusicDetail.Album(it) },
                    onOpenArtist = { detail = MusicDetail.Artist(it) },
                    onOpenLocal = {
                    // Ask only when the folder is actually opened — a permission prompt
                    // on entering the Music tab is a demand before an intention.
                    if (DeviceAudio.hasPermission(context)) {
                        scope.launch { scanDevice() }
                        detail = MusicDetail.Local(localTracks)
                    } else {
                        askAudioPermission.launch(DeviceAudio.permission)
                    }
                },
                    // An artist chip drops you into search for that name — the liked
                    // list only knows the artist's name, not the catalogue id needed
                    // to open their page directly.
                    onSearchArtist = { name -> query = name; searching = true },
                )
                }
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
    /** Starts the publish-to-community flow. */
    onPublish: () -> Unit,
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
            // Publish, as a headphone wearing a "+". It sat in the list as a full-width
            // card, which put a one-off action in the middle of content you scroll past
            // every time. Up here beside search it is available without being in the way.
            Box(
                modifier = Modifier.size(42.dp).clickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() },
                    onClick = onPublish,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Headphones, "Terbitkan lagu", tint = NexusTextPrimary, modifier = Modifier.size(24.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 5.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        // Ringed in the bar's own colour so the badge reads as attached
                        // to the headphones rather than floating over them.
                        .background(NexusBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, null, tint = NexusAccentSoft, modifier = Modifier.size(12.dp))
                }
            }
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
    onSetCommunityArt: (MusicTrack) -> Unit = {},
    onDeleteCommunity: (MusicTrack) -> Unit = {},
    onEditCommunityTitle: (MusicTrack, String) -> Unit = { _, _ -> },
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    /** Opens the picker that starts the PUBLISH flow — unrelated to the device list. */
    onPublish: () -> Unit,
    onOpenPlaylist: (MusicPlaylist) -> Unit,
    onOpenAlbum: (MusicAlbum) -> Unit,
    onOpenArtist: (MusicArtist) -> Unit,
    onSearchArtist: (String) -> Unit = {},
    onOpenLocal: () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { LikedMusicStore.ensure(context) }
    val liked = LikedMusicStore.tracks
    // Community long-press menu + rename dialog.
    var communityMenuFor by remember { mutableStateOf<MusicTrack?>(null) }
    var communityRenameFor by remember { mutableStateOf<MusicTrack?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 150.dp),
    ) {
        // Quick access, right at the top: the music you already told us you like.
        // Everything below this point is someone else's idea of what to play — this
        // row is the only part of the screen that is yours, so it goes first.
        if (liked.isNotEmpty()) {
            item {
                QuickAccessRow(
                    liked = liked,
                    artists = LikedMusicStore.topArtists(context),
                    onPlayLiked = { onPlay(liked.first(), liked.toList()) },
                    onShuffleLiked = { onPlay(liked.random(), liked.shuffled()) },
                    onArtist = onSearchArtist,
                )
            }
            item { SectionHeader("Lagu yang disukai") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(liked, key = { "liked_${it.id}" }) { t ->
                        TrackCard(t) { onPlay(t, liked.toList()) }
                    }
                }
            }
        }

        // Device music — ONE card, always present, opening the phone's own library.
        //
        // There used to be two: a "+ tambahkan dari penyimpanan" banner that opened a
        // file picker one song at a time, and a library card shown only once something
        // had been added. Since the thing that populated that list was the PUBLIC
        // publish flow, the local feature appeared to come alive only after an upload
        // it has nothing to do with. Now it is a straight read of MediaStore.
        item { DeviceMusicCard(count = localTracks.size, onOpen = onOpenLocal) }

        // Community uploads — public tracks other people added from their devices.
        //
        // The publish action is the headphone-with-a-plus in the top bar now — a
        // one-off action does not belong as a full-width card in the middle of content
        // you scroll past every time.
        if (communityTracks.isNotEmpty()) {
            item { SectionHeader("Unggahan komunitas") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(communityTracks, key = { "community_${it.id}" }) { t ->
                        // Long-press opens the menu (ubah cover / edit judul / hapus).
                        TrackCard(t, onSetArt = { communityMenuFor = t }) {
                            onPlay(t, communityTracks)
                        }
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

    // Community track long-press menu + rename dialog.
    communityMenuFor?.let { t ->
        MusicTrackMenu(
            track = t,
            owned = t.authorId.isNotBlank() && t.authorId == com.example.syntra.net.SyntraClient.myUserId,
            onChangeCover = { communityMenuFor = null; onSetCommunityArt(t) },
            onRename = { communityMenuFor = null; communityRenameFor = t },
            onDelete = { communityMenuFor = null; onDeleteCommunity(t) },
            onDismiss = { communityMenuFor = null },
        )
    }
    communityRenameFor?.let { t ->
        EditTitleDialog(
            initial = t.title,
            onConfirm = { newTitle -> onEditCommunityTitle(t, newTitle); communityRenameFor = null },
            onDismiss = { communityRenameFor = null },
        )
    }
}

/**
 * The quick-access strip: two big actions over your liked songs, then a chip per
 * artist you like most.
 *
 * Deliberately derived rather than curated — the app has no playlist feature, so the
 * only honest categories are the ones your own likes describe. An artist chip searches
 * for them, which is the fastest route back to "more of this".
 */
@Composable
private fun QuickAccessRow(
    liked: List<MusicTrack>,
    artists: List<String>,
    onPlayLiked: () -> Unit,
    onShuffleLiked: () -> Unit,
    onArtist: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickTile(
                title = "Disukai",
                subtitle = "${liked.size} lagu",
                icon = Icons.Filled.Favorite,
                accent = NexusAccent,
                modifier = Modifier.weight(1f),
                onClick = onPlayLiked,
            )
            QuickTile(
                title = "Acak",
                subtitle = "Dari lagu sukaanmu",
                icon = Icons.Filled.Shuffle,
                accent = ShortsTealMusic,
                modifier = Modifier.weight(1f),
                onClick = onShuffleLiked,
            )
        }
        if (artists.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(artists, key = { "likedartist_$it" }) { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.07f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onArtist(name) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.MusicNote, null,
                            tint = NexusAccentSoft, modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            name,
                            color = NexusTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val ShortsTealMusic = Color(0xFF20D5C4)

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = NexusTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = NexusTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

    // Device songs are already in hand (live list) — no network fetch, and their rows
    // keep the remove button. Everything else fetches its tracks below.
    val localList = (detail as? MusicDetail.Local)?.tracks
    val shown: List<MusicTrack> = localList ?: tracks

    val title: String
    val subtitle: String
    val artwork: String?
    val round: Boolean
    when (detail) {
        is MusicDetail.Playlist -> { title = detail.item.title; subtitle = detail.item.subtitle; artwork = detail.item.pictureUrl; round = false }
        is MusicDetail.Album -> { title = detail.item.title; subtitle = detail.item.artist; artwork = detail.item.artworkUrl; round = false }
        is MusicDetail.Artist -> { title = detail.item.name; subtitle = "Artis"; artwork = detail.item.pictureUrl; round = true }
        is MusicDetail.Local -> {
            title = "Lagu di perangkat"
            subtitle = "${detail.tracks.size} lagu tersimpan di perangkat ini"
            artwork = detail.tracks.firstOrNull()?.artworkUrl
            round = false
        }
    }

    LaunchedEffect(detail) {
        if (localList != null) { loading = false; return@LaunchedEffect }
        loading = true
        val list = runCatching {
            when (detail) {
                is MusicDetail.Playlist -> MusicClient.playlistTracks(detail.item.id)
                is MusicDetail.Album -> MusicClient.albumTracks(detail.item.id)
                is MusicDetail.Artist -> MusicClient.artistTopTracks(detail.item.id)
                is MusicDetail.Local -> emptyList()
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
                                ) { shown.firstOrNull()?.let { onPlay(it, shown.toList()) } }
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
            } else if (localList != null) {
                // Device songs are a READ of MediaStore, so there is nothing to remove
                // — the file belongs to the phone, not to us. The × used to delete an
                // entry from our own curated list; keeping it here would either do
                // nothing or imply we had deleted someone's music.
                items(shown, key = { it.id }) { t -> TrackRow(t) { onPlay(t, shown.toList()) } }
            } else {
                items(shown, key = { it.id }) { t -> TrackRow(t) { onPlay(t, shown.toList()) } }
            }
        }
    }
}

/** Long-press sheet for a community track: change cover, rename (owner), delete (owner). */
@Composable
private fun MusicTrackMenu(
    track: MusicTrack,
    owned: Boolean,
    onChangeCover: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(20.dp))
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = track.title,
                color = NexusTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            )
            MusicMenuRow(Icons.Filled.Image, "Ubah cover musik", onChangeCover)
            // Edit judul & hapus hanya untuk lagu milik sendiri (backend menolak yang lain).
            if (owned) {
                MusicMenuRow(Icons.Filled.Edit, "Edit judul musik", onRename)
                MusicMenuRow(Icons.Filled.Delete, "Hapus musik", onDelete, danger = true)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MusicMenuRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) Color(0xFFFF5D5D) else NexusTextPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = if (danger) tint else NexusAccentSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}

/** Rename dialog for a device song's title. */
@Composable
private fun EditTitleDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Edit judul musik", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(NexusAccentSoft),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NexusSurface)
                    .border(1.dp, NexusStroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Batal",
                    color = NexusTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(NexusAccent, RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { if (text.isNotBlank()) onConfirm(text) },
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Simpan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
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
private fun DeviceMusicCard(count: Int, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NexusSurface)
            .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A folder with a note in the corner — it has to say "files on this phone"
        // before it says "music", because everything else on this screen is music
        // and none of it is local.
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(NexusAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Folder, null, tint = NexusAccentSoft, modifier = Modifier.size(25.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(NexusSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = NexusAccentSoft, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Lagu di perangkat", color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                // The card is here from the first launch, so it has to make sense
                // before anything has been scanned.
                if (count > 0) "$count lagu di penyimpanan telepon" else "Buka folder musik di teleponmu",
                color = NexusTextSecondary,
                fontSize = 12.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NexusTextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The artwork to draw for [track]: the user's own picture if they set one, else the
 * track's real cover.
 *
 * Every surface that draws a track goes through here. Reading `track.artworkUrl`
 * directly is what made a chosen background appear on the card but nowhere else — the
 * mini-player, the row and the now-playing sheet each kept showing the original.
 */
@Composable
private fun rememberTrackArt(track: MusicTrack?): String? {
    val ctx = LocalContext.current
    if (track == null) return null
    return com.example.syntra.net.TrackArtStore.get(ctx, track.id) ?: track.artworkUrl
}

@Composable
private fun TrackCard(
    track: MusicTrack,
    /** Long-press to choose your own cover. Null = not offered for this rail. */
    onSetArt: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val isCurrent = MusicPlayer.current?.id == track.id
    val ctx = LocalContext.current
    // A locally-chosen picture wins over whatever the track carries. Read straight from
    // the store (Compose state) so picking one repaints immediately.
    val localArt = com.example.syntra.net.TrackArtStore.get(ctx, track.id)
    Column(
        modifier = Modifier
            .width(140.dp)
            .then(
                if (onSetArt != null) {
                    Modifier.combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onLongClick = onSetArt,
                        onClick = onClick,
                    )
                } else {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick,
                    )
                },
            ),
    ) {
        Box {
            ArtworkImage(
                url = localArt ?: track.artworkUrl,
                seed = track.id,
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
            )
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
        ArtworkImage(url = p.pictureUrl, seed = p.id, modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)))
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
        ArtworkImage(url = a.pictureUrl, seed = a.id, modifier = Modifier.size(120.dp).clip(CircleShape))
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
        ArtworkImage(url = a.artworkUrl, seed = a.id, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)))
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
        ArtworkImage(url = rememberTrackArt(track), seed = track.id, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
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
private fun ArtworkImage(url: String?, modifier: Modifier = Modifier, seed: String = "") {
    Box(
        // A cover of its own when there is no cover: a gradient in the theme with the
        // note on top, instead of a flat grey square with a grey glyph. A shelf of
        // identical grey squares reads as a list that failed to load; a shelf of
        // different-coloured ones reads as a shelf of different songs.
        //
        // The tint is derived from the track key, so the same song keeps the same
        // colour everywhere it appears — card, row, mini-player, now-playing.
        modifier = modifier.background(if (url.isNullOrBlank()) defaultArtBrush(seed) else SolidColor(NexusSurface)),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Filled.MusicNote, null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize(0.34f),
            )
        }
    }
}

/** One of a few theme-derived gradients, picked deterministically from [seed]. */
private fun defaultArtBrush(seed: String): Brush {
    val shift = if (seed.isBlank()) 0f else ((seed.hashCode() and 0x7FFFFFFF) % 5) * 26f - 52f
    val top = shiftMusicHue(NexusAccentSoft, shift)
    val bottom = shiftMusicHue(NexusAccent, shift)
    return Brush.linearGradient(listOf(top, bottom))
}

private fun shiftMusicHue(color: Color, degrees: Float): Color {
    if (degrees == 0f) return color
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
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
    // Reading the file's length is what unlocks the publish button. BOTH ways of
    // getting it used to fail silently — the tag probe via runCatching{}.getOrNull(),
    // and MediaPlayer with no error listener — so an unreadable file left the button
    // permanently disabled with nothing on screen explaining why. Tapping "Terbitkan"
    // simply did nothing, forever.
    var probing by remember(uri) { mutableStateOf(true) }
    var probeError by remember(uri) { mutableStateOf<String?>(null) }

    // Probe tags + prepare the preview player once.
    LaunchedEffect(uri) {
        probing = true
        probeError = null
        val probed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.example.syntra.net.LocalMusicStore.probe(context, uri) }
                .onFailure { android.util.Log.w("SyntraMusic", "probe failed", it) }
                .getOrNull()
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
                probing = false
            }
            // Without this the player could fail forever in silence.
            player.setOnErrorListener { _, what, extra ->
                android.util.Log.e("SyntraMusic", "MediaPlayer error what=$what extra=$extra")
                probing = false
                if ((meta?.durationSec ?: 0) <= 0) {
                    probeError = "File ini tidak bisa dibaca sebagai audio."
                }
                true
            }
            player.prepareAsync()
        }.onFailure {
            android.util.Log.e("SyntraMusic", "setDataSource failed", it)
            probing = false
            if ((meta?.durationSec ?: 0) <= 0) {
                probeError = "File ini tidak bisa dibuka: ${it.message ?: "format tidak didukung"}"
            }
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
                    .alpha(if (totalSec > 0 && clipLenSec > 0) 1f else 0.45f)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            probing -> "Membaca file…"
                            totalSec <= 0 -> "Durasi tidak terbaca"
                            else -> "Terbitkan ke publik"
                        },
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Say WHY, right under the button, when it cannot be used.
            probeError?.let { why ->
                Spacer(Modifier.height(8.dp))
                Text(
                    why,
                    color = Color(0xFFFF5D5D),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                )
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
                .background(NexusSurface)
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
            ArtworkImage(url = rememberTrackArt(track), seed = track.id, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = NexusTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlayerIconButton(
                icon = if (MusicPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                size = 40.dp, iconSize = 24.dp,
                // The default white belongs to the now-playing sheet, which is always
                // dark because it sits over artwork. This bar follows the theme, so on
                // the light theme a white glyph was invisible on a white surface.
                tint = NexusTextPrimary,
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
    // "Ubah background" lives here as well as on a long-press: a long-press is not
    // discoverable, and this is the screen where the picture is actually large enough
    // to care about. The image is stored on THIS DEVICE only — see TrackArtStore.
    val artCtx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val nowTrack = MusicPlayer.current
    val pickTrackArt = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val id = nowTrack?.id
        if (uri != null && id != null) {
            if (com.example.syntra.net.TrackArtStore.put(artCtx, id, uri) == null) {
                Toast.makeText(artCtx, "Gagal membaca gambar.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val track = MusicPlayer.current
    if (track == null) { onClose(); return }
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    // Reads the real store, so the heart shows the truth when you come back to a track
    // instead of resetting to empty — and so "Disukai" on the home has something in it.
    // Loaded here too: now-playing can be reached from the mini-player without the
    // music home ever having been opened.
    LaunchedEffect(Unit) { LikedMusicStore.ensure(context) }
    val liked = LikedMusicStore.tracks.any { it.id == track.id }

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
            .background(Brush.verticalGradient(listOf(NexusSurfaceElevated, NexusBackground)))
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
                Text("SEDANG DIPUTAR", color = NexusTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box {
                    PlayerIconButton(Icons.Filled.MoreVert, size = 40.dp, iconSize = 22.dp) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Ubah background", color = NexusTextPrimary) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Wallpaper, null,
                                    tint = NexusTextSecondary, modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                AppLock.expectSystemDialog()
                                pickTrackArt.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                        if (nowTrack != null &&
                            com.example.syntra.net.TrackArtStore.get(artCtx, nowTrack.id) != null
                        ) {
                            DropdownMenuItem(
                                text = { Text("Hapus background", color = NexusTextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.HideImage, null,
                                        tint = NexusTextSecondary, modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    com.example.syntra.net.TrackArtStore.clear(artCtx, nowTrack.id)
                                },
                            )
                        }
                    }
                }
            }
            // Square cover, centred in the flexible middle so the controls below are
            // always on screen (a fillMaxWidth-only image ate the whole height before).
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArtworkImage(
                    url = rememberTrackArt(track),
                    seed = track.id,
                    modifier = Modifier.fillMaxWidth(0.88f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)),
                )
            }
            // Title + artist + like.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = NexusTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(track.artist, color = NexusTextSecondary, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlayerIconButton(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    size = 44.dp, iconSize = 26.dp,
                    tint = if (liked) NexusAccentSoft else NexusTextPrimary,
                ) { LikedMusicStore.toggle(context, track) }
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
                    tint = if (MusicPlayer.shuffle) NexusAccentSoft else NexusTextSecondary) {
                    MusicPlayer.toggleShuffle()
                }
                PlayerIconButton(Icons.Filled.SkipPrevious, size = 52.dp, iconSize = 34.dp,
                    tint = if (MusicPlayer.hasPrevious) NexusTextPrimary else NexusTextPrimary.copy(alpha = 0.3f)) {
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
                        CircularProgressIndicator(color = NexusTextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(if (MusicPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Putar/Jeda",
                            tint = NexusTextPrimary, modifier = Modifier.size(38.dp))
                    }
                }
                PlayerIconButton(Icons.Filled.SkipNext, size = 52.dp, iconSize = 34.dp,
                    tint = if (MusicPlayer.hasNext || MusicPlayer.shuffle) NexusTextPrimary else NexusTextPrimary.copy(alpha = 0.3f)) {
                    MusicPlayer.next(context)
                }
                PlayerIconButton(
                    icon = if (MusicPlayer.repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    size = 46.dp, iconSize = 24.dp,
                    tint = if (MusicPlayer.repeatOne) NexusAccentSoft else NexusTextSecondary,
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
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(NexusTextPrimary.copy(alpha = 0.18f)))
            Box(Modifier.fillMaxWidth(MusicPlayer.progress).height(4.dp).clip(RoundedCornerShape(50)).background(NexusAccent))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(MusicPlayer.positionMs), color = NexusTextSecondary, fontSize = 11.sp)
            Text(clock(MusicPlayer.durationMs), color = NexusTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    // Follows the theme by default. It used to default to white, which only worked
    // while every surface that hosted this button was permanently dark.
    tint: Color = NexusTextPrimary,
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
