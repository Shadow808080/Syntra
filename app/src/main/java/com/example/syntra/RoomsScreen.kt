package com.example.syntra

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.syntra.net.NetRoom
import com.example.syntra.net.NetRoomParticipant
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
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
)

private val filters = listOf("Semua", "Tech", "Music", "Chill")

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

    suspend fun reload() {
        loading = true
        error = null
        runCatching { SyntraClient.getRooms() }
            .onSuccess { result ->
                allRooms.clear()
                allRooms.addAll(result.rooms.map { it.toUi() })
                sfuReady = result.sfuReady
                // Who is actually inside, for the avatar stack on each card.
                result.rooms.forEach { r ->
                    runCatching { SyntraClient.getRoomParticipants(r.id) }
                        .onSuccess { roomFaces[r.id] = it }
                }
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
            // Only touch the list when something actually changed, so the UI
            // doesn't churn every tick.
            if (fresh.map { it.id } != allRooms.map { it.id } ||
                fresh.map { it.participantCount } != allRooms.map { it.participantCount }
            ) {
                allRooms.clear()
                allRooms.addAll(fresh)
            }
            sfuReady = result.sfuReady
            result.rooms.forEach { r ->
                runCatching { SyntraClient.getRoomParticipants(r.id) }
                    .onSuccess { roomFaces[r.id] = it }
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

    // Live list. The server pushes room.ended/participants (handled below), but has
    // no "room created" event — so while this tab is on screen we re-sync quietly
    // to make new rooms appear on their own, without the user pulling to refresh.
    LaunchedEffect(visible, openedRoom) {
        if (!ApiConfig.ENABLED || !visible || openedRoom != null) return@LaunchedEffect
        while (true) {
            delay(8000)
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

    val visibleRooms = if (selectedFilter == 0) {
        allRooms
    } else {
        allRooms.filter { it.topic.contains(filters[selectedFilter], ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RoomsHeader()
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { scope.launch { reload() } },
                modifier = Modifier.weight(1f),
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item { HubTitle() }
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
                        RoomsPlaceholder("Belum ada room yang aktif.")
                    }
                    else -> itemsIndexed(visibleRooms) { index, room ->
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

        // Floating "create room" button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(56.dp)
                .background(
                    brush = Brush.verticalGradient(listOf(NexusAccentSoft, NexusAccent)),
                    shape = RoundedCornerShape(18.dp),
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
                imageVector = Icons.Filled.Add,
                contentDescription = "New room",
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
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
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
                                Brush.horizontalGradient(listOf(Color(0xFF2A2A32), Color(0xFF2A2A32)))
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
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
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

@Composable
private fun RoomsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(SyntraHeaderPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyntraTitle()
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Search, "Search", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Icon(Icons.Outlined.Notifications, "Notifications", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------------------
// Title + subtitle
// ---------------------------------------------------------------------------

@Composable
private fun HubTitle() {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "Voice Hub",
            color = NexusTextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Join real-time conversations with experts and creators across the globe.",
            color = NexusAccentSoft,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            // A megaphone-mouth wash: bright at the top-left "horn", fading into the
            // surface — the card reads like a megaphone calling people over.
            .background(
                Brush.linearGradient(
                    listOf(room.accent.copy(alpha = 0.24f), NexusSurface),
                ),
            )
            .border(1.dp, room.accent.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onJoin,
            ),
    ) {
        // Big faint megaphone bleeding off the right edge — the visual hallmark.
        Icon(
            imageVector = Icons.Filled.Campaign,
            contentDescription = null,
            tint = room.accent.copy(alpha = 0.10f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(148.dp)
                .offset(x = 26.dp, y = (-20).dp)
                .graphicsLayer { rotationZ = -18f },
        )

        Column(modifier = Modifier.padding(16.dp)) {
            // Author (host) up top: their avatar sits at the mouth of a megaphone
            // badge, next to a "calling you" line, with the listener count on the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                MegaphoneAuthor(room)
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.hostName.ifBlank { "Host" },
                        color = NexusTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "memanggil kamu untuk gabung",
                        color = room.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = NexusTextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = room.participantCount.toString(),
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (room.topic.isNotBlank()) {
                CategoryTag(room.topic, room.accent)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = room.title,
                color = NexusTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${room.speakerCount} speaker · ${room.participantCount} peserta",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            // Bottom: overlapping avatars of who is inside + Join
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (faces.isNotEmpty()) {
                    AvatarStack(faces, room.participantCount)
                } else {
                    Text(
                        text = "Jadi yang pertama gabung",
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.weight(1f))
                JoinButton(enabled = joinable, onJoin = onJoin)
            }
        }
    }
}

/** The room author's avatar, framed as the mouth of a small megaphone badge. */
@Composable
private fun MegaphoneAuthor(room: Room) {
    Box(contentAlignment = Alignment.Center) {
        GradientAvatar(
            gradient = listOf(room.accent, room.accent.copy(alpha = 0.55f)),
            initial = room.hostName.ifBlank { "?" }.first().toString(),
            size = 46.dp,
        )
        // Little megaphone chip clipped to the avatar's corner.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 3.dp, y = 3.dp)
                .size(20.dp)
                .background(room.accent, CircleShape)
                .border(2.dp, NexusSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Campaign,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
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
                    Brush.horizontalGradient(listOf(Color(0xFF2A2A32), Color(0xFF2A2A32)))
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
