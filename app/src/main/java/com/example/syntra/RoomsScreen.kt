package com.example.syntra

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.rememberAvatarUrl
import com.example.syntra.net.NetRoom
import com.example.syntra.net.NetRoomParticipant
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusOnline
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

/**
 * A room as the list screen shows it. Everything here comes from `GET /api/v1/rooms`;
 * only [accent] is local decoration, derived from the id so a room keeps its colour.
 */
data class Room(
    val id: String,
    val title: String,
    val topic: String,
    val hostName: String,
    val hostId: String,
    val participantCount: Int,
    val speakerCount: Int,
    val accent: Color,
    /** Host's profile photo + background — the card avatar and card background. */
    val hostAvatarUrl: String? = null,
    val hostCoverUrl: String? = null,
)

private val roomAccents = listOf(
    Color(0xFF3B68F5), Color(0xFF19C39A), Color(0xFFB265FF),
    Color(0xFFF2994A), Color(0xFFFF5D8F), Color(0xFF6C5CE7),
)

private fun accentFor(id: String): Color =
    roomAccents[(id.hashCode() and Int.MAX_VALUE) % roomAccents.size]

private fun NetRoom.toUi() = Room(
    id = id,
    title = title,
    topic = topic,
    hostName = hostName.ifBlank { hostUsername },
    hostId = hostId,
    participantCount = participantCount,
    speakerCount = speakerCount,
    accent = accentFor(id),
    hostAvatarUrl = hostAvatarMediaId?.takeIf { it.startsWith("http") },
    hostCoverUrl = hostCoverUrl,
)

private val filters = listOf("Semua", "Tech", "Music", "Chill")

/**
 * How many rooms get their avatar stack fetched. Faces are card decoration, so there
 * is no reason to spend a request on a room the user has not scrolled to.
 */
private const val FACE_FETCH_LIMIT = 12

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    modifier: Modifier = Modifier,
    selectedTab: NexusTab = NexusTab.ROOMS,
    onTabSelected: (NexusTab) -> Unit = {},
    onOverlayChange: (Boolean) -> Unit = {},
    /** False while this tab is off-screen, so it stops syncing in the background. */
    visible: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFilter by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Room currently joined and shown full-screen (null = browsing the list).
    var openedRoom by remember { mutableStateOf<Room?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    LaunchedEffect(openedRoom) { onOverlayChange(openedRoom != null) }
    val allRooms = remember { mutableStateListOf<Room>() }
    val roomFaces = remember { mutableStateMapOf<String, List<NetRoomParticipant>>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // From meta.sfu_ready — joining without a media server produces silence.
    var sfuReady by remember { mutableStateOf(false) }

    /**
     * Fetches the avatar-stack faces for [ids], in parallel, and applies them in ONE
     * batch.
     *
     * The old code walked the rooms in a `forEach` and awaited each request in turn:
     * with a dozen rooms that is a dozen serial round-trips, and — worse — each reply
     * wrote straight into a snapshot map, so the whole list recomposed once per
     * response. That is what made this screen feel like it was chewing on something.
     *
     * Only the first [FACE_FETCH_LIMIT] rooms are fetched: faces are decoration on a
     * card, and nobody needs the avatar stack of a room they have not scrolled to.
     */
    suspend fun loadFaces(ids: List<String>) = coroutineScope {
        val wanted = ids.take(FACE_FETCH_LIMIT)
        if (wanted.isEmpty()) return@coroutineScope
        val fetched = wanted
            .map { id -> async { id to runCatching { SyntraClient.getRoomParticipants(id) }.getOrNull() } }
            .awaitAll()
        // One write, one recomposition.
        roomFaces.putAll(fetched.mapNotNull { (id, list) -> list?.let { id to it } })
    }

    suspend fun reload() {
        loading = true
        error = null
        runCatching { SyntraClient.getRooms() }
            .onSuccess { result ->
                allRooms.clear()
                allRooms.addAll(result.rooms.map { it.toUi() })
                sfuReady = result.sfuReady
                loadFaces(result.rooms.map { it.id })
            }
            .onFailure {
                error = when {
                    !ApiConfig.ENABLED ->
                        "Backend belum dikonfigurasi. Isi ApiConfig lalu setel ENABLED = true."
                    !SyntraClient.hasSession ->
                        "Belum masuk. Semua endpoint butuh JWT — keluar lalu masuk kembali."
                    else -> it.message ?: "Gagal memuat room."
                }
            }
        loading = false
    }

    /** Refreshes the list in place, without the spinner — used for live updates. */
    suspend fun syncQuietly() {
        runCatching { SyntraClient.getRooms() }.onSuccess { result ->
            val fresh = result.rooms.map { it.toUi() }
            val membershipChanged = fresh.map { it.id } != allRooms.map { it.id }
            val countsChanged = fresh.map { it.participantCount } != allRooms.map { it.participantCount }
            // Only touch the list when something actually changed, so the UI
            // doesn't churn every tick.
            if (membershipChanged || countsChanged) {
                allRooms.clear()
                allRooms.addAll(fresh)
            }
            sfuReady = result.sfuReady
            // Faces only when the people in the rooms could have changed. Re-fetching
            // every participant list every 15 seconds — as this used to — is a lot of
            // traffic to redraw avatars that are almost always identical.
            if (membershipChanged || countsChanged) {
                loadFaces(result.rooms.map { it.id })
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!ApiConfig.ENABLED) {
            loading = false
            error = "Backend belum dikonfigurasi. Isi ApiConfig lalu setel ENABLED = true."
        } else {
            reload()
        }
    }

    // Live list. The backend broadcasts room.created to the global rooms:all feed;
    // subscribing to it makes a friend's new room appear on its own, instantly. The
    // slow poll below stays only as a safety net for anything missed off-socket.
    LaunchedEffect(Unit) {
        if (ApiConfig.ENABLED) SyntraClient.subscribe(listOf("rooms:all"))
    }
    LaunchedEffect(visible, openedRoom) {
        if (!ApiConfig.ENABLED || !visible || openedRoom != null) return@LaunchedEffect
        while (true) {
            delay(15000)
            syncQuietly()
        }
    }

    DisposableEffect(Unit) {
        if (!ApiConfig.ENABLED) return@DisposableEffect onDispose {}
        val listener = object : SocketListener {
            override fun onRoomEnded(roomId: String) {
                // Host closed it: drop the card immediately.
                allRooms.removeAll { it.id == roomId }
                roomFaces.remove(roomId)
            }

            override fun onRoomCreated(roomId: String) {
                // A new public room went live — pull it in right away instead of
                // waiting for the poll (this is the "had to refresh" fix).
                scope.launch { syncQuietly() }
            }

            override fun onRoomParticipants(roomId: String, participants: List<NetRoomParticipant>) {
                roomFaces[roomId] = participants
                val i = allRooms.indexOfFirst { it.id == roomId }
                if (i >= 0) {
                    allRooms[i] = allRooms[i].copy(
                        participantCount = participants.size,
                        speakerCount = participants.count { p -> p.role != "listener" },
                    )
                }
            }

            override fun onReconnect() {
                scope.launch { syncQuietly() }
            }
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    // Topic chip AND the search box both narrow the same list.
    val visibleRooms = allRooms
        .filter { selectedFilter == 0 || it.topic.contains(filters[selectedFilter], ignoreCase = true) }
        .filter { r ->
            val q = searchQuery.trim()
            q.isEmpty() ||
                r.title.contains(q, ignoreCase = true) ||
                r.topic.contains(q, ignoreCase = true) ||
                r.hostName.contains(q, ignoreCase = true)
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        // The same golden aurora that lights the room itself, quieter here — so
        // entering a room is a continuation of this screen rather than a jump to an
        // unrelated one.
        RoomsAuroraWaves(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.34f),
            intensity = 0.55f,
        )

        Column(modifier = Modifier.fillMaxSize()) {
            RoomsHeader(
                liveCount = allRooms.size,
                searchOpen = searchOpen,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    // Closing the box must also drop the filter, or the list stays
                    // narrowed by a query nobody can see any more.
                    if (!searchOpen) searchQuery = ""
                },
            )
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { scope.launch { reload() } },
                modifier = Modifier.weight(1f),
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    FilterRow(selected = selectedFilter, onSelect = { selectedFilter = it })
                }
                item { Spacer(Modifier.height(8.dp)) }

                when {
                    // The pull-to-refresh spinner already says "loading"; a second
                    // message under it would just be noise.
                    loading && allRooms.isEmpty() -> item { RoomsPlaceholder("Memuat room…") }
                    error != null -> item { RoomsPlaceholder(error!!) }
                    visibleRooms.isEmpty() -> item {
                        RoomsPlaceholder(
                            when {
                                searchQuery.isNotBlank() -> "Tidak ada room yang cocok dengan \"$searchQuery\"."
                                selectedFilter != 0 -> "Belum ada room di kategori ${filters[selectedFilter]}."
                                else -> "Belum ada room yang aktif.\nBuat satu lewat tombol + di bawah."
                            },
                        )
                    }
                    // Keyed by room id: without it every list change re-creates every
                    // card (losing their avatar images and restarting their
                    // animations), which is a lot of work to redraw the same rooms.
                    else -> itemsIndexed(
                        items = visibleRooms,
                        key = { _, room -> room.id },
                    ) { index, room ->
                        RoomCard(
                            room = room,
                            joinable = sfuReady,
                            faces = roomFaces[room.id].orEmpty(),
                            onJoin = { if (sfuReady) openedRoom = room },
                        )
                        if (index != visibleRooms.lastIndex) {
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }

                if (!loading && error == null && !sfuReady && visibleRooms.isNotEmpty()) {
                    item { RoomsPlaceholder("Media server belum siap — bergabung dinonaktifkan.") }
                }
            }
            }
        }

        // Floating "create room" button — round, with a mic icon (its function:
        // start a voice room), distinct from the home story button.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(56.dp)
                .background(
                    brush = Brush.verticalGradient(listOf(NexusAccentSoft, NexusAccent)),
                    shape = CircleShape,
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    if (!ApiConfig.ENABLED) {
                        Toast.makeText(context, "Backend belum dikonfigurasi.", Toast.LENGTH_SHORT).show()
                    } else {
                        // Never create a room as a side effect of a tap — that is how
                        // empty "Room baru" ghosts end up on everyone's list.
                        showCreate = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Buat voice room",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }

        if (showCreate) {
            CreateRoomDialog(
                onDismiss = { showCreate = false },
                onCreate = { title, topic ->
                    showCreate = false
                    scope.launch {
                        runCatching { SyntraClient.createRoom(title, topic).first }
                            .onSuccess { id ->
                                // Show it immediately at the top instead of waiting for a
                                // refresh, then walk the host straight into their own room.
                                val fresh = Room(
                                    id = id,
                                    title = title,
                                    topic = topic,
                                    hostName = "Kamu",
                                    hostId = SyntraClient.myUserId.orEmpty(),
                                    participantCount = 1,
                                    speakerCount = 1,
                                    accent = accentFor(id),
                                    hostAvatarUrl = ProfileStore.avatarUrl(context),
                                    hostCoverUrl = ProfileStore.coverUrl(context),
                                )
                                allRooms.add(0, fresh)
                                openedRoom = fresh
                                reload()
                            }
                            .onFailure {
                                Toast.makeText(context, "Buat room gagal: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
            )
        }

        // Full-screen voice room once joined
        openedRoom?.let { room ->
            RoomDetailScreen(
                room = room,
                onLeave = {
                    openedRoom = null
                    scope.launch { reload() }
                },
            )
        }
    }
}

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Buat room", color = NexusTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Room langsung tayang dan bisa dimasuki siapa saja.",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(18.dp))
            DialogField(title, { title = it }, "Judul room")
            Spacer(Modifier.height(10.dp))
            DialogField(topic, { topic = it }, "Topik (mis. teknologi)")
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Batal",
                    color = NexusTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                val ready = title.isNotBlank()
                Box(
                    modifier = Modifier
                        .background(
                            brush = if (ready) {
                                Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent))
                            } else {
                                Brush.horizontalGradient(listOf(NexusSurfaceElevated, NexusSurfaceElevated))
                            },
                            shape = RoundedCornerShape(50),
                        )
                        .clickable(
                            enabled = ready,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onCreate(title.trim(), topic.trim().ifBlank { "umum" }) }
                        .padding(horizontal = 24.dp, vertical = 11.dp),
                ) {
                    Text(
                        "Buat",
                        color = if (ready) Color.White else NexusTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusSurfaceElevated, RoundedCornerShape(14.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = NexusTextSecondary, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(NexusAccentSoft),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoomsPlaceholder(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp),
    )
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

/**
 * The Rooms header: title + live count on one line, actions on the right, and a
 * search field that slides open beneath.
 *
 * The old version split one idea across two blocks — a bare "Rooms" heading with two
 * DEAD icons, then a full sentence of marketing copy on its own row underneath. That
 * sentence told the user nothing they couldn't see (they are looking at a list of
 * rooms) while eating the space where the useful fact belongs: how many are live right
 * now. Both icons do something now.
 */
@Composable
private fun RoomsHeader(
    liveCount: Int,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Rooms",
                    color = NexusTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                // The subtitle now carries a FACT that changes, not a slogan.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (liveCount > 0) {
                        LivePulseDot()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (liveCount > 0) {
                            "$liveCount room sedang berlangsung"
                        } else {
                            "Belum ada room aktif"
                        },
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Search only. The bell that used to sit here duplicated a switch that
            // already lives in Settings → Notifikasi, and a header action that just
            // opens a settings toggle earns its space nowhere.
            HeaderIcon(
                icon = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                description = if (searchOpen) "Tutup pencarian" else "Cari room",
                active = searchOpen,
                onClick = onToggleSearch,
            )
        }

        // Search field, revealed in place rather than on a separate screen — the list
        // stays visible and filters as you type.
        androidx.compose.animation.AnimatedVisibility(visible = searchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, end = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusSurfaceElevated)
                    .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search, null,
                    tint = NexusTextSecondary, modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Cari judul, topik, atau host…",
                            color = NexusTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = NexusTextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(NexusAccentSoft),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close, "Hapus",
                        tint = NexusTextSecondary,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onQueryChange("") },
                    )
                }
            }
        }
    }
}

/** A tappable header action with a proper hit target and a pressed state. */
@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) NexusAccent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = if (active) NexusAccentSoft else NexusTextPrimary,
            modifier = Modifier.size(21.dp),
        )
    }
}

// Same construction as the room's aurora: taken from the user's chosen theme accent
// rather than fixed, so the list and the room are lit in one colour and Settings
// actually governs it.
private val ListAuroraGold: Color get() = NexusAccent
private val ListAuroraAmber: Color get() = shiftListHue(NexusAccent, 18f)
private val ListAuroraRose: Color get() = shiftListHue(NexusAccent, -26f)

private fun shiftListHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Golden light rising from the bottom of the rooms list, in slow overlapping waves.
 *
 * Same construction as the room's own aurora: three sine bands at different speeds,
 * whose periods don't divide evenly, so the crests never re-align and the motion never
 * visibly loops. One Canvas, no layout.
 */
@Composable
private fun RoomsAuroraWaves(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val t = rememberInfiniteTransition(label = "rooms-aurora")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Restart),
        label = "rooms-aurora-phase",
    )
    data class Layer(val color: Color, val reach: Float, val amp: Float, val freq: Float, val speed: Float)
    val layers = listOf(
        Layer(ListAuroraGold, 0.90f, 0.17f, 1.0f, 1.0f),
        Layer(ListAuroraAmber, 0.62f, 0.13f, 1.6f, -0.7f),
        Layer(ListAuroraRose, 0.40f, 0.10f, 2.2f, 0.44f),
    )
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        layers.forEach { l ->
            val baseY = h * (1f - l.reach)
            val waveH = h * l.amp
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h)
                lineTo(0f, baseY)
                val steps = 24
                for (i in 0..steps) {
                    val x = w * i / steps
                    val tt = i.toFloat() / steps
                    val y = baseY + waveH * kotlin.math.sin(tt * l.freq * 2f * Math.PI.toFloat() + phase * l.speed)
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, l.color.copy(alpha = 0.26f * intensity)),
                    startY = baseY - waveH,
                    endY = h,
                ),
            )
        }
    }
}

/** The small breathing red dot that marks "these are live right now". */
@Composable
private fun LivePulseDot() {
    val pulse = rememberInfiniteTransition(label = "live-dot")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "live-dot-alpha",
    )
    Box(
        Modifier
            .size(6.dp)
            .background(Color(0xFFFF5D5D).copy(alpha = alpha), CircleShape),
    )
}

// ---------------------------------------------------------------------------
// Filter chips
// ---------------------------------------------------------------------------

@Composable
private fun FilterRow(selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(filters) { index, label ->
            val isSelected = index == selected
            val base = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onSelect(index) }
            Box(
                modifier = if (isSelected) {
                    base.background(Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)))
                } else {
                    base
                        .background(NexusSurface)
                        .border(1.dp, NexusStroke, RoundedCornerShape(50))
                }.padding(horizontal = 18.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else NexusTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Room card
// ---------------------------------------------------------------------------

@Composable
private fun RoomCard(
    room: Room,
    joinable: Boolean,
    faces: List<NetRoomParticipant>,
    onJoin: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    // Same last-known-good treatment as the avatar: the rooms list frequently comes
    // back with no cover on it, and the card would drop to the flat accent gradient
    // mid-scroll. Namespaced so a cover never collides with a profile photo.
    val cover = rememberAvatarUrl("cover:${room.hostId}", incoming = room.hostCoverUrl)
    val hasCover = !cover.isNullOrBlank()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .border(1.dp, room.accent.copy(alpha = 0.30f), shape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onJoin,
            ),
    ) {
        // Card background: the host's PROFILE BACKGROUND (cover) if they have one,
        // otherwise the room's accent gradient. Never the profile photo.
        if (hasCover) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Accent + dark wash keeps the text readable over any cover.
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        listOf(room.accent.copy(alpha = 0.60f), Color.Black.copy(alpha = 0.58f)),
                    ),
                ),
            )
        } else {
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(listOf(room.accent, room.accent.copy(alpha = 0.6f))),
                ),
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Top: tag (topic, else LIVE) on the left, a "join" hint on the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (room.topic.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(room.topic, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                } else {
                    LivePill(accent = room.accent)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gabung", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            // Middle: host avatar (photo) + username/title, and a right-side badge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoomHostAvatar(room)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${room.hostName.ifBlank { "host" }}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = room.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Right badge: a round accent circle + a count pill (crown/level slot).
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(NexusAccentSoft, NexusAccent))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.People, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.32f))
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    ) {
                        Text("${room.participantCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** A pulsing "LIVE" pill — a small red dot that breathes next to the label. */
@Composable
private fun LivePill(accent: Color) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "live-pulse",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFFFF3B48).copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { alpha = pulse }
                .background(Color(0xFFFF3B48), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text("LIVE", color = Color(0xFFFF6B72), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** The host's avatar — profile photo when available, else a gradient initial — with
 *  a small "live" corner dot. */
@Composable
private fun RoomHostAvatar(room: Room) {
    Box(contentAlignment = Alignment.Center) {
        // Through the shared store: the rooms endpoint often returns the host with no
        // usable avatar, which is why these went blank whenever the list refreshed.
        val photo = rememberAvatarUrl(room.hostId, incoming = room.hostAvatarUrl)
        if (!photo.isNullOrBlank()) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            )
        } else {
            GradientAvatar(
                gradient = listOf(room.accent, room.accent.copy(alpha = 0.55f)),
                initial = room.hostName.ifBlank { "?" }.first().toString(),
                size = 46.dp,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(16.dp)
                .background(NexusOnline, CircleShape)
                .border(2.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(9.dp))
        }
    }
}

/**
 * Overlapping avatars of the people currently inside. Shows exactly as many faces
 * as there are participants (two joined → two stacked), up to four, then "+N".
 */
@Composable
private fun AvatarStack(faces: List<NetRoomParticipant>, total: Int) {
    val shown = faces.take(4)
    val extra = (total - shown.size).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            shown.forEachIndexed { i, p ->
                val name = p.displayName.ifBlank { p.username }.ifBlank { "?" }
                Box(
                    modifier = Modifier
                        .padding(start = (i * 20).dp)
                        .size(32.dp)
                        .background(NexusSurface, CircleShape)
                        .padding(1.5.dp),
                ) {
                    GradientAvatar(
                        gradient = stackGradient(p.userId),
                        initial = name.first().toString(),
                        size = 29.dp,
                        // The rooms list payload carries no avatar at all, so these
                        // faces were ALWAYS letters. Whatever photo any other screen
                        // has already learned is used here instead.
                        photoUrl = rememberAvatarUrl(p.userId, p.username, incoming = p.avatarMediaId),
                    )
                }
            }
        }
        if (extra > 0) {
            Spacer(Modifier.width((shown.size * 20 - 20 + 12).dp.coerceAtLeast(8.dp)))
            Text("+$extra", color = NexusTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private val stackGradients = listOf(
    listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFEE5A6F), Color(0xFFF29263)),
    listOf(Color(0xFFDA22FF), Color(0xFF9733EE)),
)

private fun stackGradient(id: String): List<Color> =
    stackGradients[(id.hashCode() and Int.MAX_VALUE) % stackGradients.size]

@Composable
private fun CategoryTag(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun JoinButton(enabled: Boolean, onJoin: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                brush = if (enabled) {
                    Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent))
                } else {
                    Brush.horizontalGradient(listOf(NexusSurfaceElevated, NexusSurfaceElevated))
                },
                shape = RoundedCornerShape(50),
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onJoin,
            )
            .padding(horizontal = 22.dp, vertical = 9.dp),
    ) {
        Text(
            text = "Join",
            color = if (enabled) Color.White else NexusTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 800)
@Composable
private fun RoomsScreenPreview() {
    SyntraTheme {
        RoomsScreen()
    }
}
