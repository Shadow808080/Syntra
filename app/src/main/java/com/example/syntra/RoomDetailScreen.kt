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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
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
    // Camera video tracks per user id, and whether MY camera is on. A room becomes a
    // video room the moment anyone turns their camera on.
    val videoTracks by VoiceEngine.videoTracks.collectAsState()
    val cameraOn by VoiceEngine.cameraOn.collectAsState()

    // Bottom sheets reachable from the control bar / top bar.
    var showMore by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }

    // Elapsed time since the room UI went live, shown as a call timer in the top bar.
    var elapsed by remember(room.id) { mutableIntStateOf(0) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Izin mikrofon ditolak.", Toast.LENGTH_SHORT).show()
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch { VoiceEngine.setCameraEnabled(true) }
        } else {
            Toast.makeText(context, "Izin kamera ditolak.", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasMic() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun hasCamera() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA,
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
            // fireAndForget, NOT scope.launch: onLeave() below tears this screen down
            // and cancels its scope, which would kill the leave request mid-flight.
            // Then the backend never learns the host left, so the room only ends when
            // LiveKit's disconnect webhook fires 6–10s later — that's the "friend still
            // in the room" lag. A detached scope guarantees the leave actually sends.
            SyntraClient.fireAndForget { SyntraClient.leaveRoom(room.id) }
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

    // Room timer — starts ticking once we're connected.
    LaunchedEffect(joinState) {
        if (joinState == JoinState.CONNECTED) {
            while (true) {
                delay(1000)
                elapsed += 1
            }
        }
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

    fun toggleCamera() {
        if (!canPublish) {
            Toast.makeText(context, "Kamu belum jadi speaker.", Toast.LENGTH_SHORT).show()
            return
        }
        if (cameraOn) {
            scope.launch { VoiceEngine.setCameraEnabled(false) }
            return
        }
        if (!hasCamera()) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        scope.launch { VoiceEngine.setCameraEnabled(true) }
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
                elapsed = elapsed,
                onMinimize = { requestLeave() },
                onPeople = { showPeople = true },
            )

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
                    RoomStage(
                        speakers = speakers,
                        listeners = listeners,
                        audioLevels = audioLevels,
                        videoTracks = videoTracks,
                        myId = SyntraClient.myUserId,
                        isHost = isHost,
                        onManage = { manageTarget = it },
                    )
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
                cameraOn = cameraOn,
                onToggleMute = { toggleMute() },
                onToggleCamera = { toggleCamera() },
                onToggleChat = { showChat = !showChat },
                onMore = { showMore = true },
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

        // People list (top-right button).
        if (showPeople) {
            RoomPeopleSheet(
                speakers = speakers,
                listeners = listeners,
                isHost = isHost,
                onManage = { showPeople = false; manageTarget = it },
                onDismiss = { showPeople = false },
            )
        }

        // "More" — volume, loudspeaker, switch camera, chat, and (host) end room.
        if (showMore) {
            RoomMoreSheet(
                volume = volume,
                loudspeaker = loudspeaker,
                cameraOn = cameraOn,
                chatOpen = showChat,
                isHost = isHost,
                onVolume = { applyVolume(it) },
                onToggleLoudspeaker = {
                    loudspeaker = !loudspeaker
                    VoiceEngine.setLoudspeaker(loudspeaker)
                },
                onSwitchCamera = { VoiceEngine.switchCamera() },
                onToggleChat = { showMore = false; showChat = !showChat },
                onEndRoom = { showMore = false; confirmLeave = true },
                onDismiss = { showMore = false },
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

/** The people list opened from the top-right button — speakers then listeners. */
@Composable
private fun RoomPeopleSheet(
    speakers: List<NetRoomParticipant>,
    listeners: List<NetRoomParticipant>,
    isHost: Boolean,
    onManage: (NetRoomParticipant) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            ) {
                Icon(Icons.Filled.Groups, null, tint = NexusAccentSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Peserta", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${speakers.size + listeners.size}", color = NexusTextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                if (speakers.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = 22.dp)) { SectionLabel("Speaker · ${speakers.size}") }
                    speakers.forEach { p ->
                        RoomPersonRow(p, manageable = isHost && p.role != "host", onClick = { onManage(p) })
                    }
                }
                if (listeners.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = 22.dp)) { SectionLabel("Pendengar · ${listeners.size}") }
                    listeners.forEach { p ->
                        RoomPersonRow(p, manageable = isHost, onClick = { onManage(p) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomPersonRow(p: NetRoomParticipant, manageable: Boolean, onClick: () -> Unit) {
    val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = manageable,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        RoomAvatar(p = p, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = when (p.role) {
                    "host" -> "Host"
                    "moderator" -> "Moderator"
                    "listener" -> "Pendengar"
                    else -> "Speaker"
                },
                color = NexusTextSecondary,
                fontSize = 11.sp,
            )
        }
        if (p.role != "listener") {
            Icon(
                imageVector = if (p.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = null,
                tint = if (p.isMuted) NexusTextSecondary else NexusOnline,
                modifier = Modifier.size(18.dp),
            )
        }
        if (manageable) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.MoreHoriz, "Kelola", tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** The "more" sheet from the control bar: volume, loudspeaker, camera, chat, end. */
@Composable
private fun RoomMoreSheet(
    volume: Float,
    loudspeaker: Boolean,
    cameraOn: Boolean,
    chatOpen: Boolean,
    isHost: Boolean,
    onVolume: (Float) -> Unit,
    onToggleLoudspeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleChat: () -> Unit,
    onEndRoom: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp),
        ) {
            Text(
                "Opsi room",
                color = NexusTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(6.dp))
            VolumeBar(
                volume = volume,
                loudspeaker = loudspeaker,
                onVolume = onVolume,
                onToggleLoudspeaker = onToggleLoudspeaker,
            )
            Spacer(Modifier.height(6.dp))
            SheetAction(
                if (chatOpen) "Tutup chat" else "Buka chat",
                Icons.Filled.Chat,
                NexusTextPrimary,
                onToggleChat,
            )
            if (cameraOn) {
                SheetAction("Ganti kamera", Icons.Filled.Cameraswitch, NexusTextPrimary, onSwitchCamera)
            }
            if (isHost) {
                SheetAction("Akhiri room", Icons.Filled.CallEnd, Color(0xFFFF5D5D), onEndRoom)
            }
            SheetAction("Tutup", Icons.Filled.Close, NexusTextSecondary, onDismiss)
        }
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
private fun RoomTopBar(
    room: Room,
    count: Int,
    elapsed: Int,
    onMinimize: () -> Unit,
    onPeople: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Row 1 — back · live timer (center) · people. Mirrors a call screen.
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundControl(Icons.Filled.ExpandMore, "Minimize", size = 40.dp, onClick = onMinimize)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).background(Color(0xFFFF5D5D), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = formatElapsed(elapsed),
                    color = NexusTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            // People button, with the count as a small badge.
            Box(contentAlignment = Alignment.TopEnd) {
                RoundControl(Icons.Filled.PersonAdd, "Peserta", size = 40.dp, onClick = onPeople)
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .background(NexusAccent, CircleShape)
                            .border(2.dp, Color(0xFF17131F), CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Row 2 — compact identity: title + optional topic chip.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = room.title,
                color = NexusTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (room.topic.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(room.accent.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(room.topic, color = room.accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Seconds → "M:SS" or "H:MM:SS" for the live room timer. */
private fun formatElapsed(total: Int): String {
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
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
 * The room "stage": speakers as a 2-column grid of video tiles (camera feed, or a
 * photo/gradient fallback when the camera is off), with listeners tucked into a
 * compact avatar strip at the bottom. This is the layout that turns a voice room
 * into a video room the moment anyone turns their camera on.
 */
@Composable
private fun RoomStage(
    speakers: List<NetRoomParticipant>,
    listeners: List<NetRoomParticipant>,
    audioLevels: Map<String, Float>,
    videoTracks: Map<String, VideoTrack>,
    myId: String?,
    isHost: Boolean,
    onManage: (NetRoomParticipant) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (speakers.isEmpty() && listeners.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Belum ada peserta.",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            }
        }
        items(speakers, key = { it.userId }) { p ->
            RoomVideoTile(
                p = p,
                level = audioLevels[p.userId] ?: 0f,
                video = videoTracks[p.userId],
                isMe = p.userId == myId,
                manageable = isHost && p.role != "host",
                onManage = { onManage(p) },
            )
        }
        if (listeners.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                RoomListenerStrip(listeners = listeners, videoTracks = videoTracks)
            }
        }
    }
}

/** A compact, horizontally scrolling strip of everyone who is only listening. */
@Composable
private fun RoomListenerStrip(listeners: List<NetRoomParticipant>, videoTracks: Map<String, VideoTrack>) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        SectionLabel("Mendengarkan · ${listeners.size}")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listeners.forEach { p ->
                val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                    RoomAvatar(p = p, size = 48.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name.substringBefore(' '),
                        color = NexusTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * One speaker tile. Full-bleed camera video when they're publishing; otherwise a
 * profile photo (or gradient + initial). A modern speaking indicator — an animated
 * glowing border plus a mini equalizer in the name chip — replaces the old ring.
 */
@Composable
private fun RoomVideoTile(
    p: NetRoomParticipant,
    level: Float,
    video: VideoTrack?,
    isMe: Boolean,
    manageable: Boolean,
    onManage: () -> Unit,
) {
    val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
    val micOn = p.role != "listener" && !p.isMuted
    val loud = (level.coerceIn(0f, 1f) * 2.4f).coerceIn(0f, 1f)
    val glow by animateFloatAsState(
        targetValue = if (micOn) loud else 0f,
        animationSpec = tween(120),
        label = "tile-glow",
    )
    val cover = p.coverUrl?.takeIf { it.isNotBlank() }
    val grad = gradientForId(p.userId)
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(shape)
            .background(Color(0xFF14141B))
            .clickable(
                enabled = manageable,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onManage,
            ),
    ) {
        // Background: live camera → the person's PROFILE BACKGROUND (cover) →
        // gradient with initial. Never the profile photo, so a camera-off tile shows
        // the real profile background.
        when {
            video != null -> VideoTileRenderer(track = video, mirror = isMe, modifier = Modifier.fillMaxSize())
            cover != null -> AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(grad)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.first().uppercase(),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // Bottom scrim so the name chip stays legible over any background.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)),
            ),
        )
        // Modern speaking indicator: an animated glowing border, brighter as they
        // get louder. Replaces the old radial "wave ring" with something at home on
        // a video tile.
        if (micOn && glow > 0.01f) {
            SpeakingBorder(glow = glow, colors = grad, shape = shape, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().border(1.dp, NexusStroke, shape))
        }

        // Name chip (bottom-start) — small avatar + first name.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoomAvatar(p = p, size = 22.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                name.substringBefore(' '),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (micOn) {
                Spacer(Modifier.width(6.dp))
                MiniEqualizer(level = glow)
            }
        }

        // Mic badge (bottom-end).
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(26.dp)
                .background(if (micOn) NexusOnline else Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }

        // Manage "..." (top-end) for hosts; raised hand otherwise.
        if (manageable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreHoriz, "Kelola", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }

        // Role badge (top-start).
        val roleLabel = when (p.role) {
            "host" -> "HOST"
            "moderator" -> "MOD"
            else -> null
        }
        if (roleLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NexusAccent.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(roleLabel, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Avatar helper for room UI: photo when available, gradient + initial otherwise. */
@Composable
private fun RoomAvatar(p: NetRoomParticipant, size: androidx.compose.ui.unit.Dp) {
    val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
    val avatar = p.avatarMediaId?.takeIf { it.startsWith("http") }
    if (avatar != null) {
        AsyncImage(
            model = avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        GradientAvatar(gradient = gradientForId(p.userId), initial = name.first().toString(), size = size)
    }
}

/**
 * Renders a LiveKit video track into the tile. Keyed by track so a re-published
 * camera swaps the surface cleanly; releases the renderer on dispose.
 */
@Composable
private fun VideoTileRenderer(track: VideoTrack, mirror: Boolean, modifier: Modifier = Modifier) {
    key(track) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextureViewRenderer(ctx).apply {
                    VoiceEngine.initRenderer(this)
                    setMirror(mirror)
                    runCatching { track.addRenderer(this) }
                }
            },
            onRelease = { view ->
                runCatching { track.removeRenderer(view) }
                runCatching { view.release() }
            },
        )
    }
}

/**
 * A modern speaking indicator: an animated rounded-rect glow that traces the tile
 * edge, its width and brightness rising with [glow] (0..1). The old ChatGPT-style
 * radial bars didn't sit well on a video tile; this reads as "live" at a glance.
 */
@Composable
private fun SpeakingBorder(
    glow: Float,
    colors: List<Color>,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "speak")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "speak-shimmer",
    )
    val strokeW = 2f + 3.5f * glow
    val alpha = (0.45f + 0.5f * glow).coerceIn(0f, 1f)
    val brush = Brush.sweepGradient(
        listOf(
            colors.first().copy(alpha = alpha),
            colors.last().copy(alpha = alpha),
            NexusOnline.copy(alpha = alpha),
            colors.first().copy(alpha = alpha),
        ),
    )
    Canvas(modifier = modifier) {
        val inset = strokeW
        val r = 20.dp.toPx()
        // A soft outer halo that breathes with the shimmer.
        drawRoundRect(
            color = NexusOnline.copy(alpha = 0.10f * glow * (0.7f + 0.3f * shimmer)),
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW * 3f),
        )
        drawRoundRect(
            brush = brush,
            topLeft = androidx.compose.ui.geometry.Offset(inset / 2f, inset / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - inset, size.height - inset),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW),
        )
    }
}

/** Three little bars that dance with the voice — a modern mini "wave" for the chip. */
@Composable
private fun MiniEqualizer(level: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "eq-phase",
    )
    Canvas(modifier = modifier.size(width = 14.dp, height = 12.dp)) {
        val bars = 3
        val gap = size.width / (bars * 2f)
        val barW = gap
        val amp = 0.25f + level * 0.75f
        for (i in 0 until bars) {
            val h = size.height * (0.35f + 0.65f * amp * (kotlin.math.sin(phase + i * 1.3f) * 0.5f + 0.5f))
            val x = gap + i * (barW + gap)
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(x, size.height),
                end = androidx.compose.ui.geometry.Offset(x, size.height - h),
                strokeWidth = barW,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
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
    cameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleChat: () -> Unit,
    onMore: () -> Unit,
    onLeave: () -> Unit,
) {
    val micLive = canPublish && !muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16161C))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Mic — filled green when live, red when muted, dimmed when listener-only.
        CircleControl(
            icon = if (micLive) Icons.Filled.Mic else Icons.Filled.MicOff,
            description = "Mikrofon",
            background = when {
                !canPublish -> Color(0xFF24242C)
                micLive -> NexusOnline
                else -> Color(0xFF3A1620)
            },
            tint = when {
                !canPublish -> NexusTextSecondary
                micLive -> Color.White
                else -> Color(0xFFFF5D5D)
            },
            onClick = onToggleMute,
        )
        // Camera — turns the room into a video room.
        CircleControl(
            icon = if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            description = "Kamera",
            background = if (cameraOn) NexusAccent else Color(0xFF24242C),
            tint = if (cameraOn) Color.White else NexusTextPrimary,
            onClick = onToggleCamera,
        )
        // Hang up — the big red center button.
        CircleControl(
            icon = Icons.Filled.CallEnd,
            description = "Keluar",
            size = 64.dp,
            iconSize = 28.dp,
            background = Color(0xFFFF3B48),
            tint = Color.White,
            onClick = onLeave,
        )
        // Chat.
        CircleControl(
            icon = Icons.Filled.Chat,
            description = "Chat",
            background = Color(0xFF24242C),
            tint = NexusTextPrimary,
            onClick = onToggleChat,
        )
        // More — volume, loudspeaker, switch camera, end room.
        CircleControl(
            icon = Icons.Filled.MoreHoriz,
            description = "Lainnya",
            background = Color(0xFF24242C),
            tint = NexusTextPrimary,
            onClick = onMore,
        )
    }
}

/** A circular control button used across the room control bar. */
@Composable
private fun CircleControl(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    iconSize: androidx.compose.ui.unit.Dp = 23.dp,
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
        Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
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
