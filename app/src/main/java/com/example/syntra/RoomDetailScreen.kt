package com.example.syntra

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.ApiException
import com.example.syntra.net.NetRoomMessage
import com.example.syntra.net.NetRoomParticipant
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.net.VoiceEngine
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusOnline
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One line of ephemeral room chat. Never persisted — see docs/voice-rooms.md. */
private data class RoomChatLine(val senderId: String, val body: String, val mine: Boolean)

/** Gate in front of the room: nobody sees it until the audio session is really up. */
private enum class JoinState { CONNECTING, CONNECTED, FAILED }

/** Slow safety net only — room.participants events carry the real updates. */
private const val PARTICIPANT_POLL_MS = 20_000L

/**
 * A live voice room.
 *
 * All state comes from the backend: participants via `GET /rooms/{id}/participants`
 * (the server broadcasts no membership events, so this is polled), chat via the
 * `room.chat`/`room.message` frames, and the audio itself through LiveKit using the
 * token from `join`.
 */
@Composable
fun RoomDetailScreen(room: Room, onLeave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val participants = remember(room.id) { mutableStateListOf<NetRoomParticipant>() }
    val chat = remember(room.id) { mutableStateListOf<RoomChatLine>() }
    var chatInput by remember { mutableStateOf("") }
    var showChat by remember { mutableStateOf(false) }

    var joinState by remember(room.id) { mutableStateOf(JoinState.CONNECTING) }
    var joinError by remember(room.id) { mutableStateOf<String?>(null) }
    var myRole by remember { mutableStateOf("listener") }
    var canPublish by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(true) }
    var handRaised by remember { mutableStateOf(false) }
    // Set once the host grants speaking rights, so we can tell the user why the
    // microphone suddenly works.
    var promoted by remember(room.id) { mutableStateOf(false) }
    var confirmLeave by remember(room.id) { mutableStateOf(false) }
    var manageTarget by remember(room.id) { mutableStateOf<NetRoomParticipant?>(null) }
    var roomEnded by remember(room.id) { mutableStateOf(false) }
    // Speaker loudness. Voice rooms are useless if you cannot hear them.
    var volume by remember { mutableStateOf(0.8f) }
    // Honours the "Pengeras suara di room" preference from Settings.
    var loudspeaker by remember {
        mutableStateOf(SettingsStore.getBool(context, SettingsStore.LOUD_SPEAKER, true))
    }

    val chatListState = rememberLazyListState()

    // Live microphone loudness (0..1) per user id, straight from LiveKit.
    val audioLevels by VoiceEngine.audioLevels.collectAsState()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Izin mikrofon ditolak.", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasMic() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * join → LiveKit connect. The room UI stays behind a gate until this finishes,
     * so nobody sees a room they are not actually connected to. Re-run after a
     * promotion: the old token was minted with `can_publish: false`.
     */
    suspend fun joinAndConnect(reconnecting: Boolean = false) {
        if (!reconnecting) joinState = JoinState.CONNECTING
        joinError = null
        runCatching {
            val session = SyntraClient.joinRoom(room.id)
            myRole = session.role
            canPublish = session.canPublish
            SyntraClient.roomJoinTopic(room.id)
            participants.clear()
            participants.addAll(SyntraClient.getRoomParticipants(room.id))
            if (session.sfuToken.isBlank() || session.sfuUrl.isBlank()) {
                throw IllegalStateException("Media server belum siap, suara tidak akan terdengar.")
            }
            VoiceEngine.connect(context, session.sfuUrl, session.sfuToken)
            VoiceEngine.setLoudspeaker(loudspeaker)
        }.onSuccess {
            joinState = JoinState.CONNECTED
        }.onFailure {
            joinError = it.message ?: "Gagal bergabung."
            joinState = JoinState.FAILED
        }
    }

    fun leave() {
        VoiceEngine.disconnect()
        if (ApiConfig.ENABLED) {
            SyntraClient.roomLeaveTopic(room.id)
            scope.launch { runCatching { SyntraClient.leaveRoom(room.id) } }
        }
        onLeave()
    }

    val isHost = myRole == "host"

    fun requestLeave() {
        // Leaving as host ends the room for everyone, so make that explicit.
        if (isHost) confirmLeave = true else leave()
    }

    BackHandler { requestLeave() }

    if (ApiConfig.ENABLED) {
        LaunchedEffect(room.id) { joinAndConnect() }

        // The server pushes room.participants on every change, so this is only a slow
        // safety net for anything missed while the socket was down.
        LaunchedEffect(room.id) {
            while (true) {
                delay(PARTICIPANT_POLL_MS)
                runCatching { SyntraClient.getRoomParticipants(room.id) }
                    .onFailure { e ->
                        if ((e as? ApiException)?.code == "not_found") roomEnded = true
                    }
                    .onSuccess { fresh ->
                        participants.clear()
                        participants.addAll(fresh)
                    }
            }
        }

        DisposableEffect(room.id) {
            val listener = object : SocketListener {
                override fun onRoomMessage(message: NetRoomMessage) {
                    if (message.roomId != room.id) return
                    chat.add(
                        RoomChatLine(
                            senderId = message.senderId,
                            body = message.body,
                            mine = message.senderId == SyntraClient.myUserId,
                        ),
                    )
                }

                override fun onRoomParticipants(roomId: String, list: List<NetRoomParticipant>) {
                    if (roomId != room.id) return
                    participants.clear()
                    participants.addAll(list)
                }

                override fun onRoomSpeakRequest(roomId: String, userId: String, name: String) {
                    // Only hosts/moderators receive this; the queue reads has_raised_hand,
                    // so just surface a nudge in case the list is off-screen.
                    if (roomId == room.id) {
                        Toast.makeText(context, "$name minta izin bicara", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onRoomRoleChanged(
                    roomId: String,
                    userId: String,
                    role: String,
                    needsRejoin: Boolean,
                ) {
                    if (roomId != room.id || userId != SyntraClient.myUserId) return
                    val wasListener = myRole == "listener"
                    myRole = role
                    if (needsRejoin) {
                        // The old token was minted with the old can_publish.
                        scope.launch { joinAndConnect(reconnecting = true) }
                    }
                    if (wasListener && role != "listener") {
                        handRaised = false
                        promoted = true
                    }
                }

                override fun onRoomEnded(roomId: String) {
                    if (roomId == room.id) roomEnded = true
                }

                override fun onReconnect() {
                    // Room chat that passed while offline is gone by design; just resubscribe.
                    scope.launch { runCatching { SyntraClient.roomJoinTopic(room.id) } }
                }
            }
            SyntraClient.addListener(listener)
            onDispose {
                SyntraClient.removeListener(listener)
                VoiceEngine.disconnect()
            }
        }
    } else {
        LaunchedEffect(Unit) {
            joinError = "Backend belum dikonfigurasi. Isi ApiConfig lalu setel ENABLED = true."
            joinState = JoinState.FAILED
        }
    }

    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) chatListState.animateScrollToItem(chat.lastIndex)
    }

    fun toggleMute() {
        if (!canPublish) {
            Toast.makeText(context, "Kamu belum jadi speaker.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasMic()) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val next = !muted
        muted = next
        scope.launch {
            VoiceEngine.setMicrophoneEnabled(!next)
            runCatching { SyntraClient.setRoomMuted(room.id, next) }
        }
    }

    fun toggleHand() {
        handRaised = !handRaised
        if (handRaised) scope.launch {
            runCatching { SyntraClient.raiseHand(room.id) }
                .onSuccess {
                    // The host sees this through `has_raised_hand` on the next poll.
                    Toast.makeText(context, "Permintaan bicara terkirim.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun setRole(target: NetRoomParticipant, role: String) {
        manageTarget = null
        scope.launch {
            runCatching { SyntraClient.setRoomRole(room.id, target.userId, role) }
                .onSuccess {
                    runCatching { participants.clear(); participants.addAll(SyntraClient.getRoomParticipants(room.id)) }
                    val who = target.displayName.ifBlank { target.username }
                    val what = if (role == "listener") "dijadikan pendengar" else "diizinkan bicara"
                    Toast.makeText(context, "$who $what.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun applyVolume(value: Float) {
        volume = value
        VoiceEngine.setVolume(value)
    }

    fun sendChat() {
        val text = chatInput.trim()
        if (text.isEmpty() || !ApiConfig.ENABLED) return
        SyntraClient.roomChat(room.id, text)
        chatInput = ""
    }

    val speakers = participants.filter { it.role != "listener" }
    val listeners = participants.filter { it.role == "listener" }

    // Crossfade between the join gate and the live room so entering feels smooth
    // instead of snapping. Hold everything back until the audio session is up —
    // showing the room while still connecting makes a silent room look broken.
    androidx.compose.animation.Crossfade(
        targetState = joinState == JoinState.CONNECTED,
        animationSpec = tween(320),
        label = "room-join",
    ) { connected ->
        if (!connected) {
            JoinGate(
                room = room,
                state = joinState,
                error = joinError,
                onRetry = { scope.launch { joinAndConnect() } },
                onCancel = { leave() },
            )
            return@Crossfade
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF17131F), Color(0xFF121212), Color(0xFF121212))),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RoomTopBar(
                room = room,
                count = participants.size,
                live = true,
                onMinimize = { requestLeave() },
            )

            VolumeBar(
                volume = volume,
                loudspeaker = loudspeaker,
                onVolume = { applyVolume(it) },
                onToggleLoudspeaker = {
                    loudspeaker = !loudspeaker
                    VoiceEngine.setLoudspeaker(loudspeaker)
                },
            )

            // No hand-raise queue: everyone joins as a speaker and can just
            // unmute, so there's nothing to approve.

            androidx.compose.animation.Crossfade(
                targetState = showChat,
                animationSpec = tween(260),
                modifier = Modifier.weight(1f),
                label = "room-body",
            ) { chatMode ->
            if (chatMode) {
                RoomChatPane(
                    lines = chat,
                    listState = chatListState,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (participants.isEmpty()) {
                        item(span = { GridItemSpan(4) }) {
                            Text(
                                text = "Belum ada peserta.",
                                color = NexusTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                            )
                        }
                    }
                    if (speakers.isNotEmpty()) {
                        item(span = { GridItemSpan(4) }) { SectionLabel("Speaker · ${speakers.size}") }
                        items(speakers) { p ->
                            ParticipantTile(
                                p,
                                big = true,
                                level = audioLevels[p.userId] ?: 0f,
                                manageable = isHost && p.role != "host",
                            ) {
                                manageTarget = p
                            }
                        }
                    }
                    if (listeners.isNotEmpty()) {
                        item(span = { GridItemSpan(4) }) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                SectionLabel("Pendengar · ${listeners.size}")
                            }
                        }
                        items(listeners) { p ->
                            ParticipantTile(
                                p,
                                big = false,
                                level = audioLevels[p.userId] ?: 0f,
                                manageable = isHost,
                            ) { manageTarget = p }
                        }
                    }
                }
            }
            } // end Crossfade room-body

            if (showChat) {
                RoomChatInput(
                    value = chatInput,
                    onValueChange = { if (it.length <= 500) chatInput = it },
                    onSend = { sendChat() },
                )
            }

            RoomControlBar(
                muted = muted,
                canPublish = canPublish,
                handRaised = handRaised,
                chatOpen = showChat,
                onToggleMute = { toggleMute() },
                onToggleHand = { toggleHand() },
                onToggleChat = { showChat = !showChat },
                onLeave = { requestLeave() },
            )
        }

        // Host is about to end the room for everyone.
        if (confirmLeave) {
            RoomAlert(
                title = "Akhiri room?",
                message = "Kamu pemilik room ini. Jika keluar, room akan ditutup dan " +
                    "semua peserta dikeluarkan. Chat room juga hilang permanen.",
                confirmText = "Akhiri room",
                onConfirm = {
                    confirmLeave = false
                    leave()
                },
                onDismiss = { confirmLeave = false },
            )
        }

        // The room disappeared while we were inside it.
        if (roomEnded) {
            RoomAlert(
                title = "Room telah berakhir",
                message = "Pemilik menutup room ini.",
                confirmText = "Keluar",
                dismissText = null,
                onConfirm = {
                    roomEnded = false
                    leave()
                },
                onDismiss = {},
            )
        }

        manageTarget?.let { target ->
            ManageParticipantSheet(
                participant = target,
                onDismiss = { manageTarget = null },
                onMakeSpeaker = { setRole(target, "speaker") },
                onMakeListener = { setRole(target, "listener") },
            )
        }
    }
    } // end Crossfade
}

// ---------------------------------------------------------------------------
// Volume + dialogs
// ---------------------------------------------------------------------------

/** What the host sees when listeners ask for the mic. */
@Composable
private fun HandRaiseQueue(
    requests: List<NetRoomParticipant>,
    onApprove: (NetRoomParticipant) -> Unit,
    onOpen: (NetRoomParticipant) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(Color(0xFF3A2E16), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Icon(Icons.Filled.PanTool, null, tint = Color(0xFFFFC46B), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "${requests.size} orang minta izin bicara",
                color = Color(0xFFFFC46B),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        requests.forEach { p ->
            val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onOpen(p) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                GradientAvatar(gradientForId(p.userId), name.first().toString(), 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(name, color = NexusTextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(NexusOnline, RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onApprove(p) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("Izinkan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VolumeBar(
    volume: Float,
    loudspeaker: Boolean,
    onVolume: (Float) -> Unit,
    onToggleLoudspeaker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (loudspeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Hearing,
            contentDescription = "Pengeras suara",
            tint = if (loudspeaker) NexusAccentSoft else NexusTextSecondary,
            modifier = Modifier
                .size(22.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onToggleLoudspeaker,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Slider(
            value = volume,
            onValueChange = onVolume,
            colors = SliderDefaults.colors(
                thumbColor = NexusAccentSoft,
                activeTrackColor = NexusAccent,
                inactiveTrackColor = Color(0xFF2B2B34),
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RoomAlert(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String? = "Batal",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text(title, color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = NexusTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (dismissText != null) {
                    Text(
                        text = dismissText,
                        color = NexusTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onDismiss,
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3A1620), RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onConfirm,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        confirmText,
                        color = Color(0xFFFF5D5D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Host-only: promote a listener to speaker, or send a speaker back to listening. */
@Composable
private fun ManageParticipantSheet(
    participant: NetRoomParticipant,
    onDismiss: () -> Unit,
    onMakeSpeaker: () -> Unit,
    onMakeListener: () -> Unit,
) {
    val name = participant.displayName.ifBlank { participant.username }.ifBlank { "Pengguna" }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(vertical = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 22.dp),
            ) {
                GradientAvatar(
                    gradient = gradientForId(participant.userId),
                    initial = name.first().toString(),
                    size = 42.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name, color = NexusTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when (participant.role) {
                            "listener" -> "Pendengar"
                            "moderator" -> "Moderator"
                            else -> "Speaker"
                        },
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (participant.role == "listener") {
                SheetAction("Izinkan bicara", Icons.Filled.Mic, NexusOnline, onMakeSpeaker)
            } else {
                SheetAction("Jadikan pendengar", Icons.Filled.MicOff, Color(0xFFFF5D5D), onMakeListener)
            }
            SheetAction("Batal", Icons.Filled.CallEnd, NexusTextSecondary, onDismiss)
        }
    }
}

@Composable
private fun SheetAction(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------------------
// Join gate
// ---------------------------------------------------------------------------

@Composable
private fun JoinGate(
    room: Room,
    state: JoinState,
    error: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF17131F), Color(0xFF121212), Color(0xFF121212))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 34.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .background(room.accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (state == JoinState.CONNECTING) {
                    CircularProgressIndicator(
                        color = room.accent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    Icon(
                        Icons.Filled.CallEnd, null,
                        tint = Color(0xFFFF5D5D), modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = room.title,
                color = NexusTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = when (state) {
                    JoinState.CONNECTING ->
                        "Menyambungkan ke room…\nMenunggu izin dan jalur suara siap."
                    else -> error ?: "Gagal bergabung."
                },
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            if (state == JoinState.FAILED) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)),
                            RoundedCornerShape(50),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onRetry,
                        )
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    Text("Coba lagi", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = "Batal",
                color = NexusTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onCancel,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** Explains why the mic is or isn't available, driven by the host's decision. */
@Composable
private fun SpeakingStatusBanner(
    role: String,
    handRaised: Boolean,
    promoted: Boolean,
    onDismissPromoted: () -> Unit,
) {
    val (text, tint, bg) = when {
        promoted -> Triple(
            "Host mengizinkanmu bicara. Mikrofon sudah aktif.",
            Color(0xFF6BE39A), Color(0xFF14301F),
        )
        role != "listener" -> return
        handRaised -> Triple(
            "Menunggu izin host untuk bicara…",
            Color(0xFFFFC46B), Color(0xFF3A2E16),
        )
        else -> Triple(
            "Kamu mendengarkan. Angkat tangan untuk minta izin bicara.",
            NexusTextSecondary, Color(0xFF1D1D24),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(
                enabled = promoted,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismissPromoted,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = tint, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun RoomTopBar(room: Room, count: Int, live: Boolean, onMinimize: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundControl(Icons.Filled.ExpandMore, "Minimize", size = 38.dp, onClick = onMinimize)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (live) Color(0xFFFF5D5D) else NexusTextSecondary, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (live) "LIVE" else "OFF",
                    color = NexusTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.Headphones, null, tint = NexusTextSecondary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("$count", color = NexusTextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(38.dp))
        }
        Spacer(Modifier.height(14.dp))
        if (room.topic.isNotBlank()) {
            Box(
                modifier = Modifier
                    .background(room.accent.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(room.topic, color = room.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = room.title,
            color = NexusTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------
// Participants
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

/**
 * A ring of thin bars around an avatar that pulse with the voice — the ChatGPT
 * "talking" look. Each bar has its own phase so the ring ripples; amplitude
 * follows [level] (0..1) with a low idle shimmer so a live-but-quiet mic still
 * breathes gently.
 */
@Composable
private fun VoiceWaveRing(level: Float, ringSize: androidx.compose.ui.unit.Dp) {
    val bars = 28
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "wave-phase",
    )
    Canvas(modifier = Modifier.size(ringSize)) {
        val radius = size.minDimension / 2f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val idle = 0.12f
        val amp = idle + level * 0.88f
        val baseLen = radius * 0.10f
        val strokeW = 3.2f
        for (i in 0 until bars) {
            val angle = (i.toFloat() / bars) * 2f * Math.PI.toFloat()
            // Per-bar wave — two offset sines make it feel organic, not uniform.
            val wave = (kotlin.math.sin(phase + i * 0.7f) * 0.5f + 0.5f) *
                (kotlin.math.sin(phase * 0.6f + i * 0.3f) * 0.5f + 0.5f)
            val len = baseLen + radius * 0.28f * amp * wave
            val inner = radius - baseLen * 0.3f
            val outer = inner + len
            val sx = center.x + kotlin.math.cos(angle) * inner
            val sy = center.y + kotlin.math.sin(angle) * inner
            val ex = center.x + kotlin.math.cos(angle) * outer
            val ey = center.y + kotlin.math.sin(angle) * outer
            drawLine(
                color = NexusOnline.copy(alpha = 0.35f + 0.5f * amp),
                start = androidx.compose.ui.geometry.Offset(sx, sy),
                end = androidx.compose.ui.geometry.Offset(ex, ey),
                strokeWidth = strokeW,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ParticipantTile(
    p: NetRoomParticipant,
    big: Boolean,
    level: Float = 0f,
    manageable: Boolean = false,
    onManage: () -> Unit = {},
) {
    val size = if (big) 64.dp else 48.dp
    val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
    // Mic on (server state) vs. how loud they are right now (LiveKit, 0..1).
    val micOn = p.role != "listener" && !p.isMuted
    // Normalise: real speech rarely fills 0..1, so lift the low end and clamp,
    // then smooth so the ring glides with the voice instead of jittering.
    val loudness = (level.coerceIn(0f, 1f) * 2.4f).coerceIn(0f, 1f)
    val glow by animateFloatAsState(
        targetValue = if (micOn) loudness else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "loudness",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = manageable,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onManage,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft breathing glow behind the avatar, scaled by loudness.
            if (glow > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(size + 4.dp + 26.dp * glow)
                        .background(NexusOnline.copy(alpha = 0.10f + 0.28f * glow), CircleShape),
                )
            }
            // ChatGPT-style voice bars arranged around the avatar — each bar
            // dances to the live audio level (with a subtle idle shimmer when mic
            // is on but quiet), so an active speaker reads instantly.
            if (micOn) {
                VoiceWaveRing(level = glow, ringSize = size + 20.dp)
            }
            GradientAvatar(
                gradient = gradientForId(p.userId),
                initial = name.first().toString(),
                size = size,
            )
            if (p.role != "listener") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .background(if (micOn) NexusOnline else Color(0xFF3A3A44), CircleShape)
                        .border(2.dp, NexusSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (p.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            if (p.hasRaisedHand && p.role == "listener") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(Color(0xFFF2994A), CircleShape)
                        .border(2.dp, NexusSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PanTool, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name.substringBefore(' '),
            color = NexusTextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        when (p.role) {
            "host" -> Text("HOST", color = NexusAccentSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            "moderator" -> Text("MOD", color = NexusAccentSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val tileGradients = listOf(
    listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFEE5A6F), Color(0xFFF29263)),
    listOf(Color(0xFF485563), Color(0xFF29323C)),
    listOf(Color(0xFFDA22FF), Color(0xFF9733EE)),
    listOf(Color(0xFF2196F3), Color(0xFF3B68F5)),
)

private fun gradientForId(id: String): List<Color> =
    tileGradients[(id.hashCode() and Int.MAX_VALUE) % tileGradients.size]

// ---------------------------------------------------------------------------
// Ephemeral chat
// ---------------------------------------------------------------------------

@Composable
private fun RoomChatPane(
    lines: List<RoomChatLine>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "Chat room bersifat sementara —\npesan hilang saat room berakhir.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(lines) { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start,
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (line.mine) NexusAccent else Color(0xFF23232B),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(line.body, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun RoomChatInput(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF1E1E26), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty()) {
                Text("Pesan room…", color = NexusTextSecondary, fontSize = 14.sp)
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
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(NexusAccent, CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send, "Kirim",
                tint = Color.White, modifier = Modifier.size(19.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom controls
// ---------------------------------------------------------------------------

@Composable
private fun RoomControlBar(
    muted: Boolean,
    canPublish: Boolean,
    handRaised: Boolean,
    chatOpen: Boolean,
    onToggleMute: () -> Unit,
    onToggleHand: () -> Unit,
    onToggleChat: () -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16161C))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF3A1620), RoundedCornerShape(50))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onLeave,
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CallEnd, "Leave", tint = Color(0xFFFF5D5D), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Keluar", color = Color(0xFFFF5D5D), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        RoundControl(
            icon = if (chatOpen) Icons.Filled.People else Icons.Filled.Chat,
            description = if (chatOpen) "Peserta" else "Chat",
            background = Color(0xFF24242C),
            onClick = onToggleChat,
        )
        // No raise-hand button: everyone can speak the moment they join.
        // Mic: green + "ON" when live, red-outlined + "OFF" when muted. The label
        // removes any doubt about which state you are in.
        val live = canPublish && !muted
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = when {
                            !canPublish -> Color(0xFF24242C)
                            live -> NexusOnline
                            else -> Color(0xFF3A1620)
                        },
                        shape = CircleShape,
                    )
                    .border(
                        width = 2.dp,
                        color = when {
                            !canPublish -> Color(0xFF33333C)
                            live -> NexusOnline
                            else -> Color(0xFFFF5D5D)
                        },
                        shape = CircleShape,
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onToggleMute,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (live) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (live) "Matikan mikrofon" else "Nyalakan mikrofon",
                    tint = when {
                        !canPublish -> NexusTextSecondary
                        live -> Color.White
                        else -> Color(0xFFFF5D5D)
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    !canPublish -> "TERKUNCI"
                    live -> "MIC ON"
                    else -> "MIC OFF"
                },
                color = when {
                    !canPublish -> NexusTextSecondary
                    live -> NexusOnline
                    else -> Color(0xFFFF5D5D)
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp = 46.dp,
    background: Color = Color(0xFF24242C),
    tint: Color = NexusTextPrimary,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(background, CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}
