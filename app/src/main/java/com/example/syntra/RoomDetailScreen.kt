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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import com.example.syntra.net.rememberAvatarUrl
import com.example.syntra.net.ApiException
import com.example.syntra.net.NetRoomMessage
import com.example.syntra.net.NetRoomParticipant
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.net.VoiceEngine
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusOnline
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
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
    // Messages that arrived while the stage was showing, so the switch can say how
    // much you've missed instead of the chat quietly filling up out of sight.
    var unreadChat by remember(room.id) { mutableIntStateOf(0) }

    var joinState by remember(room.id) { mutableStateOf(JoinState.CONNECTING) }
    var joinError by remember(room.id) { mutableStateOf<String?>(null) }
    var myRole by remember { mutableStateOf("listener") }
    var canPublish by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(true) }
    var handRaised by remember { mutableStateOf(false) }
    // Non-null while the "ask the host" sheet is open, and remembered after sending so
    // the approval can turn on exactly what was asked for. The backend's raise-hand is
    // a plain flag with no kind attached, so the mic-vs-camera distinction is kept here.
    var askTarget by remember(room.id) { mutableStateOf<SpeakRequest?>(null) }
    var pendingRequest by remember(room.id) { mutableStateOf<SpeakRequest?>(null) }
    // Raised when the host moves me back to listening, so the mic/camera going dead is
    // explained rather than just happening.
    var demotedNotice by remember(room.id) { mutableStateOf(false) }
    // A role change needs a new SFU token, and a new token means the media session is
    // rebuilt — about a second of silence. The screen itself no longer flinches (the
    // join gate is skipped and the participant list is left alone), but the audio gap
    // is real, so it gets a small honest strip instead of looking like a crash.
    var refreshingSession by remember(room.id) { mutableStateOf(false) }
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
    /**
     * Replace the participant list.
     *
     * De-duplicates by user id, which is not paranoia: the stage grid keys its tiles
     * by user id, and during a role change the server can briefly report the same
     * person twice (once under each role). That took the whole screen down with
     * `Key "…" was already used` — the crash on approving a speak request.
     *
     * It also leaves the list ALONE when nothing actually changed, so a routine
     * refresh doesn't clear and refill the stage — that empty-then-refill flash is
     * what read as "the room restarted".
     */
    fun setParticipants(list: List<NetRoomParticipant>) {
        val fresh = list.distinctBy { it.userId }
        if (fresh.size == participants.size && fresh.zip(participants).all { (a, b) -> a == b }) return
        participants.clear()
        participants.addAll(fresh)
    }

    suspend fun joinAndConnect(reconnecting: Boolean = false) {
        if (!reconnecting) joinState = JoinState.CONNECTING else refreshingSession = true
        joinError = null
        runCatching {
            val session = SyntraClient.joinRoom(room.id)
            myRole = session.role
            canPublish = session.canPublish
            SyntraClient.roomJoinTopic(room.id)
            setParticipants(SyntraClient.getRoomParticipants(room.id))
            if (session.sfuToken.isBlank() || session.sfuUrl.isBlank()) {
                throw IllegalStateException("Media server belum siap, suara tidak akan terdengar.")
            }
            VoiceEngine.connect(context, session.sfuUrl, session.sfuToken)
            VoiceEngine.setLoudspeaker(loudspeaker)
        }.also {
            refreshingSession = false
        }.onSuccess {
            joinState = JoinState.CONNECTED
        }.onFailure {
            // A failed RECONNECT must not throw the user back to the join gate — they
            // are already in the room, and the old session is usually still carrying
            // audio. Say what happened and stay put.
            if (reconnecting) {
                Toast.makeText(context, "Gagal menyegarkan sesi: ${it.message}", Toast.LENGTH_SHORT).show()
            } else {
                joinError = it.message ?: "Gagal bergabung."
                joinState = JoinState.FAILED
            }
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

    // Tell the rest of the app we're in a room, so an incoming call rings as a banner
    // instead of seizing the screen (see AppForeground.inVoiceRoom).
    DisposableEffect(Unit) {
        com.example.syntra.net.AppForeground.inVoiceRoom = true
        onDispose { com.example.syntra.net.AppForeground.inVoiceRoom = false }
    }

    // The user answered a banner call: leave the room FIRST so the SFU session is torn
    // down and the audio device is free before the call claims it.
    LaunchedEffect(CallController.leaveRoomForCall) {
        if (CallController.leaveRoomForCall) {
            CallController.leaveRoomForCall = false
            leave()
        }
    }

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
                    .onSuccess { fresh -> setParticipants(fresh) }
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
                    setParticipants(list)
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
                    // Publishing rights follow the role IMMEDIATELY. This used to wait
                    // for a rejoin that only happens when the server asks for one, so a
                    // demoted speaker kept `canPublish = true` and their controls stayed
                    // live.
                    canPublish = role != "listener"
                    if (needsRejoin) {
                        // The old token was minted with the old can_publish.
                        scope.launch { joinAndConnect(reconnecting = true) }
                    }
                    if (!wasListener && role == "listener") {
                        // DEMOTED. Cut the mic and the camera here and now — the server
                        // revoking permission does not stop tracks already publishing,
                        // so without this the person carried on being seen and heard
                        // after being made a listener.
                        pendingRequest = null
                        handRaised = false
                        muted = true
                        demotedNotice = true
                        scope.launch {
                            runCatching { VoiceEngine.setMicrophoneEnabled(false) }
                            runCatching { VoiceEngine.setCameraEnabled(false) }
                            runCatching { SyntraClient.setRoomMuted(room.id, true) }
                        }
                    }
                    if (wasListener && role != "listener") {
                        handRaised = false
                        promoted = true
                        // Granted — turn on the thing they actually asked for, so an
                        // approval is one step, not "you may speak, now find the
                        // button again". Deferred until the republish finishes, since
                        // the old token could not publish anything.
                        val wanted = pendingRequest
                        pendingRequest = null
                        if (wanted != null) {
                            scope.launch {
                                // Give the rejoin (new token with can_publish) a moment.
                                if (needsRejoin) delay(1200)
                                when (wanted) {
                                    SpeakRequest.MIC -> if (hasMic()) {
                                        muted = false
                                        VoiceEngine.setMicrophoneEnabled(true)
                                        runCatching { SyntraClient.setRoomMuted(room.id, false) }
                                    }
                                    SpeakRequest.CAMERA -> if (hasCamera()) {
                                        VoiceEngine.setCameraEnabled(true)
                                    }
                                }
                            }
                        }
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
        if (!showChat) unreadChat += 1
    }
    // Opening the chat clears the badge.
    LaunchedEffect(showChat) { if (showChat) unreadChat = 0 }

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
            // A listener pressing the mic is ASKING to speak. It used to be a dead
            // toast telling them what they already knew; now it opens the request.
            askTarget = SpeakRequest.MIC
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
            askTarget = SpeakRequest.CAMERA
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

    /** Ask the host for permission to [what]. Same signal either way; the kind is ours. */
    fun sendSpeakRequest(what: SpeakRequest) {
        askTarget = null
        pendingRequest = what
        handRaised = true
        scope.launch {
            runCatching { SyntraClient.raiseHand(room.id) }
                .onFailure {
                    pendingRequest = null
                    handRaised = false
                    Toast.makeText(context, "Gagal mengirim izin: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun cancelSpeakRequest() {
        pendingRequest = null
        handRaised = false
    }

    fun setRole(target: NetRoomParticipant, role: String) {
        manageTarget = null
        scope.launch {
            runCatching { SyntraClient.setRoomRole(room.id, target.userId, role) }
                .onSuccess {
                    runCatching { setParticipants(SyntraClient.getRoomParticipants(room.id)) }
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
            // Warm base: a hint of gold at the very bottom rather than flat black, so
            // the aurora above it has something to sit in instead of appearing to
            // float on nothing.
            .background(
                Brush.verticalGradient(
                    listOf(NexusSurfaceElevated, NexusBackground, NexusSurface),
                ),
            ),
    ) {
        // The room's own light, rising from the bottom edge. Behind everything, so it
        // never competes with a speaker tile or the controls.
        AuroraWaves(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f),
            intensity = 0.85f,
        )

        Column(modifier = Modifier.fillMaxSize()) {
            val handRequests = participants.filter { it.hasRaisedHand && it.role == "listener" }

            RoomTopBar(
                room = room,
                count = participants.size,
                elapsed = elapsed,
                requests = if (isHost) handRequests.size else 0,
                onMinimize = { requestLeave() },
                onPeople = { showPeople = true },
            )

            // Host side: everyone currently asking for the mic, with approve/decline
            // right there. Sits above the body so a request can't be missed.
            if (isHost && handRequests.isNotEmpty()) {
                HandRaiseQueue(
                    requests = handRequests,
                    onApprove = { setRole(it, "speaker") },
                    onOpen = { manageTarget = it },
                )
            }

            // Requester side: proof that the ask is in flight.
            pendingRequest?.let { what ->
                SpeakRequestPending(what = what, onCancel = { cancelSpeakRequest() })
            }

            if (refreshingSession) SessionRefreshStrip()

            // Stage / chat switch, ALWAYS on screen. It used to be a single icon in the
            // control bar (duplicated inside the "More" sheet), so once you were in the
            // chat there was no visible way back to the people — you had to remember
            // which icon you pressed. A segmented control shows both states at once and
            // makes the return trip the same one tap as the way in.
            RoomModeSwitch(
                chatOpen = showChat,
                unread = unreadChat,
                onSelect = { chat -> showChat = chat },
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
                        onSetRole = { target, role -> setRole(target, role) },
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
                loudspeaker = loudspeaker,
                onToggleMute = { toggleMute() },
                onToggleCamera = { toggleCamera() },
                onToggleLoudspeaker = {
                    loudspeaker = !loudspeaker
                    VoiceEngine.setLoudspeaker(loudspeaker)
                },
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

        // Moved back to listening by the host.
        if (demotedNotice) {
            RoomAlert(
                title = "Kamu jadi pendengar",
                message = "Pemilik room memindahkanmu ke pendengar. Mikrofon dan " +
                    "kamera dimatikan. Kamu bisa minta izin bicara lagi kapan saja.",
                confirmText = "Mengerti",
                dismissText = null,
                onConfirm = { demotedNotice = false },
                onDismiss = { demotedNotice = false },
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

        // Listener tapped mic or camera — ask the host instead of refusing.
        askTarget?.let { what ->
            SpeakRequestSheet(
                what = what,
                hostName = room.hostName,
                onSend = { sendSpeakRequest(what) },
                onDismiss = { askTarget = null },
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
                onSetRole = { target, role -> setRole(target, role) },
                onDismiss = { showPeople = false },
            )
        }

        // "More" — volume, loudspeaker, switch camera, and (host) end room.
        if (showMore) {
            RoomMoreSheet(
                volume = volume,
                loudspeaker = loudspeaker,
                cameraOn = cameraOn,
                isHost = isHost,
                onVolume = { applyVolume(it) },
                onToggleLoudspeaker = {
                    loudspeaker = !loudspeaker
                    VoiceEngine.setLoudspeaker(loudspeaker)
                },
                onSwitchCamera = { VoiceEngine.switchCamera() },
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

/**
 * What a listener is asking the host for.
 *
 * The backend's raise-hand endpoint carries no "kind", so this only ever lives on the
 * requester's device — long enough to word the sheet correctly and to switch the right
 * thing on when the host says yes.
 */
private enum class SpeakRequest { MIC, CAMERA }

/**
 * The permission sheet a listener sees after tapping mic or camera.
 *
 * Previously both buttons answered with "Kamu belum jadi speaker." and nothing else —
 * technically true and completely useless, since it named the problem and offered no
 * way out. This asks the host on their behalf.
 */
@Composable
private fun SpeakRequestSheet(
    what: SpeakRequest,
    hostName: String,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mic = what == SpeakRequest.MIC
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(20.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(20.dp))
                .padding(22.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (mic) Icons.Filled.Mic else Icons.Filled.Videocam,
                    null,
                    tint = NexusAccentSoft,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (mic) "Minta izin bicara" else "Minta izin nyalakan kamera",
                color = NexusTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Kamu sedang jadi pendengar. ${hostName.ifBlank { "Pemilik room" }} " +
                    "harus menyetujui dulu sebelum " +
                    (if (mic) "mikrofonmu" else "kameramu") + " bisa aktif.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Batal",
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
                Box(
                    modifier = Modifier
                        .background(NexusAccent, RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onSend,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Kirim permintaan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * "Waiting for the host" strip, shown to the requester until they're promoted.
 *
 * Without it a listener has no idea whether their request went anywhere — the old
 * flow fired a toast and then looked exactly like it had before.
 */
@Composable
private fun SpeakRequestPending(what: SpeakRequest, onCancel: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "ask-pending")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ask-pulse",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NexusAccent.copy(alpha = 0.14f))
            .border(1.dp, NexusAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (what == SpeakRequest.MIC) Icons.Filled.Mic else Icons.Filled.Videocam,
            null,
            tint = NexusAccentSoft.copy(alpha = alpha),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Menunggu izin pemilik room",
                color = NexusTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (what == SpeakRequest.MIC) "Permintaan bicara terkirim" else "Permintaan kamera terkirim",
                color = NexusTextSecondary,
                fontSize = 11.sp,
            )
        }
        Text(
            "Batal",
            color = NexusTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onCancel,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * The always-visible "Panggung / Obrolan" switch.
 *
 * Two segments with a sliding pill behind the active one, so which mode you're in and
 * how to leave it are the same control. The chat side carries an unread count, because
 * the whole reason people got stranded on one side was not knowing anything was
 * happening on the other.
 */
@Composable
private fun RoomModeSwitch(chatOpen: Boolean, unread: Int, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(3.dp),
    ) {
        RoomModeTab(
            label = "Panggung",
            icon = Icons.Filled.Mic,
            active = !chatOpen,
            modifier = Modifier.weight(1f),
        ) { onSelect(false) }
        RoomModeTab(
            label = "Obrolan",
            icon = Icons.Filled.Chat,
            active = chatOpen,
            badge = if (!chatOpen && unread > 0) unread else 0,
            modifier = Modifier.weight(1f),
        ) { onSelect(true) }
    }
}

@Composable
private fun RoomModeTab(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    // The pill fades rather than snaps, so the switch reads as one surface moving.
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) NexusAccent.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = tween(200),
        label = "room-tab-bg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) Color.White else NexusTextSecondary,
        animationSpec = tween(200),
        label = "room-tab-fg",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badge > 0) {
            Spacer(Modifier.width(6.dp))
            CountBadge(badge, NexusAccent)
        }
    }
}

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
                Text(
                    text = name,
                    color = NexusTextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(NexusOnline, RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onApprove(p) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Izinkan",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
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
                inactiveTrackColor = NexusSurfaceElevated,
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
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
                        .background(DangerFill, RoundedCornerShape(50))
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
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
    /** Host-only: flip this person between speaker and listener in one tap. */
    onSetRole: (NetRoomParticipant, String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
            // People asking for the mic float to the TOP of the card, in their own
            // section. Buried among the listeners they were indistinguishable from
            // everyone else, so requests sat unanswered.
            val asking = listeners.filter { it.hasRaisedHand }
            val quiet = listeners.filterNot { it.hasRaisedHand }
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                if (asking.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = 22.dp)) {
                        SectionLabel("Minta bicara · ${asking.size}")
                    }
                    asking.forEach { p ->
                        RoomPersonRow(
                            p = p,
                            manageable = isHost,
                            highlight = true,
                            onClick = { onManage(p) },
                            onSetRole = if (isHost) ({ onSetRole(p, "speaker") }) else null,
                        )
                    }
                }
                if (speakers.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = 22.dp)) { SectionLabel("Speaker · ${speakers.size}") }
                    speakers.forEach { p ->
                        RoomPersonRow(
                            p = p,
                            manageable = isHost && p.role != "host",
                            onClick = { onManage(p) },
                            // The host can't be demoted out of their own room.
                            onSetRole = if (isHost && p.role != "host") {
                                ({ onSetRole(p, "listener") })
                            } else {
                                null
                            },
                        )
                    }
                }
                if (quiet.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = 22.dp)) { SectionLabel("Pendengar · ${quiet.size}") }
                    quiet.forEach { p ->
                        RoomPersonRow(
                            p = p,
                            manageable = isHost,
                            onClick = { onManage(p) },
                            onSetRole = if (isHost) ({ onSetRole(p, "speaker") }) else null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One person in the participants card.
 *
 * [onSetRole] is the host-only button beside the avatar that flips speaker ⇄ listener
 * in a single tap. Promoting somebody used to mean tapping the row, reading a sheet
 * and picking an action — three steps to answer a request that is a yes/no question.
 * The row still opens the full sheet for anything else.
 */
@Composable
private fun RoomPersonRow(
    p: NetRoomParticipant,
    manageable: Boolean,
    highlight: Boolean = false,
    onClick: () -> Unit,
    onSetRole: (() -> Unit)? = null,
) {
    val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
    val listener = p.role == "listener"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            // A person who is asking gets a tinted row, so the queue reads at a glance.
            .background(if (highlight) NexusAccent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(
                enabled = manageable,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            RoomAvatar(p = p, size = 40.dp)
            // Raised hand sits on the avatar itself for anyone waiting on an answer.
            if (p.hasRaisedHand && listener) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(NexusAccent, CircleShape)
                        .border(2.dp, NexusSurfaceElevated, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PanTool, null,
                        tint = Color.White, modifier = Modifier.size(8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = when {
                    p.hasRaisedHand && listener -> "Minta jadi pembicara"
                    p.role == "host" -> "Host"
                    p.role == "moderator" -> "Moderator"
                    listener -> "Pendengar"
                    else -> "Speaker"
                },
                color = if (p.hasRaisedHand && listener) NexusAccentSoft else NexusTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!listener) {
            Icon(
                imageVector = if (p.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = null,
                tint = if (p.isMuted) NexusTextSecondary else NexusOnline,
                modifier = Modifier.size(18.dp),
            )
        }
        // The one-tap role button — creator only. Icon + a SHORT word: the full
        // "Jadikan pembicara" wrapped or shoved the name off the row on a narrow
        // phone, and the icon already carries the meaning.
        onSetRole?.let { action ->
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (listener) NexusAccent else Color.White.copy(alpha = 0.08f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = action,
                    )
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Icon(
                    if (listener) Icons.Filled.Mic else Icons.Filled.Hearing,
                    if (listener) "Jadikan pembicara" else "Jadikan pendengar",
                    tint = if (listener) Color.White else NexusTextSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (listener) "Bicara" else "Dengar",
                    color = if (listener) Color.White else NexusTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The "more" sheet from the control bar: volume, loudspeaker, camera, end. */
@Composable
private fun RoomMoreSheet(
    volume: Float,
    loudspeaker: Boolean,
    cameraOn: Boolean,
    isHost: Boolean,
    onVolume: (Float) -> Unit,
    onToggleLoudspeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndRoom: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
            // Chat lives on the switch above the room body, not buried in here.
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
                Brush.verticalGradient(listOf(NexusSurfaceElevated, NexusBackground, NexusBackground)),
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
            NexusTextSecondary, NexusSurfaceElevated,
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

// ---------------------------------------------------------------------------
// Aurora
// ---------------------------------------------------------------------------

/**
 * The room's aurora colours, derived from whichever theme the user picked in Settings.
 *
 * These are computed rather than fixed: a hardcoded gold looked deliberate on the dark
 * default and wrong on Ocean or Forest, where it clashed with everything else on the
 * screen. Reading [NexusAccent] means the room is lit in the user's own colour, and
 * switching theme repaints it with no extra wiring.
 *
 * Three steps around the accent's hue — the accent itself, a lighter tint, and a
 * warmer/deeper neighbour — which is enough separation for the wave bands to read as
 * distinct layers without becoming a rainbow.
 */
private val AuroraGold: Color get() = NexusAccent
private val AuroraYellow: Color get() = NexusAccentSoft
private val AuroraAmber: Color get() = shiftHue(NexusAccent, 18f)
private val AuroraRose: Color get() = shiftHue(NexusAccent, -26f)

/** Rotates [color]'s hue by [degrees], keeping saturation and brightness. */
private fun shiftHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Readable ink for a filled control: near-black on a light accent, white on a dark
 * one. The old code assumed a bright gold and hardcoded dark brown, which vanished
 * the moment the theme supplied a deep blue.
 */
private fun onAccentInk(bg: Color): Color =
    if (bg.luminance() > 0.55f) Color(0xFF1A1206) else Color.White

/**
 * A slow band of golden light that rises from the bottom edge in overlapping waves.
 *
 * Three sine layers drifting at different speeds and amplitudes: because their periods
 * don't divide evenly the crests never re-align, so the motion reads as organic rather
 * than as a looping animation. Each band is filled with a vertical gradient that fades
 * to nothing at the top, which is what makes it glow instead of looking like a shape.
 *
 * One Canvas, no layout, no per-frame allocation beyond the Path — cheap enough to sit
 * under a live video grid on a low-end phone.
 */
@Composable
private fun AuroraWaves(
    modifier: Modifier = Modifier,
    /** Overall opacity, so the same effect can be loud on the bar and quiet behind content. */
    intensity: Float = 1f,
) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
        label = "aurora-phase",
    )
    // Each layer: colour, how far up it reaches, wave height, frequency, drift speed.
    val layers = listOf(
        Quintuple(AuroraGold, 0.92f, 0.16f, 1.1f, 1.0f),
        Quintuple(AuroraAmber, 0.66f, 0.13f, 1.7f, -0.72f),
        Quintuple(AuroraRose, 0.44f, 0.10f, 2.3f, 0.45f),
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        layers.forEach { (color, reach, amp, freq, speed) ->
            val baseY = h * (1f - reach)
            val waveH = h * amp
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h)
                lineTo(0f, baseY)
                // 24 segments is smooth at this scale and keeps the path cheap.
                val steps = 24
                for (i in 0..steps) {
                    val x = w * i / steps
                    val tt = i.toFloat() / steps
                    val y = baseY + waveH * kotlin.math.sin(tt * freq * 2f * Math.PI.toFloat() + phase * speed)
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = 0.30f * intensity)),
                    startY = baseY - waveH,
                    endY = h,
                ),
            )
        }
    }
}

/** Five-field tuple for the wave layer table above. */
private data class Quintuple(
    val color: Color,
    val reach: Float,
    val amp: Float,
    val freq: Float,
    val speed: Float,
)

/**
 * "Applying your new role" strip.
 *
 * Shown while the SFU token is re-minted after a promotion or demotion. Everything
 * else on screen stays exactly where it was — this is the whole visible cost of a
 * role change now, instead of the screen appearing to rejoin the room.
 */
@Composable
private fun SessionRefreshStrip() {
    val shimmer = rememberInfiniteTransition(label = "session-refresh")
    val x by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "session-shimmer",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // A dot that travels, rather than a spinner — quieter, and it costs one Canvas.
        Canvas(Modifier.size(width = 34.dp, height = 6.dp)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
            val w = size.width * 0.42f
            drawRoundRect(
                color = NexusAccentSoft,
                topLeft = androidx.compose.ui.geometry.Offset((size.width + w) * x - w, 0f),
                size = androidx.compose.ui.geometry.Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Menyiapkan izin barumu…",
            color = NexusTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A count badge that is actually round.
 *
 * The old one wrapped a Text in padding, so its height came from the font's line
 * metrics — which include ascent/descent space the digit never fills. That is why it
 * looked squashed and off-centre. Here the box is given an explicit size and the text
 * has its font padding removed, so the glyph sits dead centre of a true circle. It
 * widens (into a pill) only when the number needs two or three characters.
 */
@Composable
private fun CountBadge(value: Int, color: Color) {
    val label = if (value > 99) "99+" else value.toString()
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .background(color, CircleShape)
            .border(2.dp, NexusSurfaceElevated, CircleShape)
            .padding(horizontal = if (label.length > 1) 5.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeight = 10.sp,
            ),
        )
    }
}

@Composable
private fun RoomTopBar(
    room: Room,
    count: Int,
    elapsed: Int,
    /** Pending speak requests — host only; 0 for everyone else. */
    requests: Int = 0,
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
            // People button. Normally a plain head-count; the moment someone asks to
            // speak it becomes a PULSING RAISED HAND with the number of requests, so
            // the host can see there is something waiting without opening anything.
            // (Requests used to be invisible from here — you had to already be looking
            // at the participants list.)
            Box(contentAlignment = Alignment.TopEnd) {
                if (requests > 0) {
                    val pulse = rememberInfiniteTransition(label = "req-badge")
                    val glow by pulse.animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                        label = "req-glow",
                    )
                    RoundControl(
                        Icons.Filled.PanTool,
                        "Permintaan bicara",
                        size = 40.dp,
                        background = NexusAccent.copy(alpha = glow),
                        tint = Color.White,
                        onClick = onPeople,
                    )
                    CountBadge(requests, Color(0xFFFF5D5D))
                } else {
                    RoundControl(Icons.Filled.PersonAdd, "Peserta", size = 40.dp, onClick = onPeople)
                    if (count > 0) CountBadge(count, NexusAccent)
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
                        // Capped, so a long topic shrinks its own chip instead of
                        // squeezing the room title out of the row.
                        .widthIn(max = 130.dp)
                        .background(room.accent.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = room.topic,
                        color = room.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    /** Host-only one-tap role change, straight from the stage. */
    onSetRole: (NetRoomParticipant, String) -> Unit = { _, _ -> },
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
                RoomListenerStrip(
                    listeners = listeners,
                    videoTracks = videoTracks,
                    isHost = isHost,
                    onPromote = { onSetRole(it, "speaker") },
                )
            }
        }
    }
}

/**
 * A compact, horizontally scrolling strip of everyone who is only listening.
 *
 * For the host, each avatar carries a small promote button in its corner — the
 * fastest possible "yes" to someone asking for the mic, without opening a list. People
 * with a hand up are sorted to the front and marked.
 */
@Composable
private fun RoomListenerStrip(
    listeners: List<NetRoomParticipant>,
    videoTracks: Map<String, VideoTrack>,
    isHost: Boolean = false,
    onPromote: (NetRoomParticipant) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        SectionLabel("Mendengarkan · ${listeners.size}")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hands up first — the people the host actually needs to act on.
            listeners.sortedByDescending { it.hasRaisedHand }.forEach { p ->
                val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        RoomAvatar(p = p, size = 48.dp)
                        if (isHost) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        if (p.hasRaisedHand) NexusAccent else NexusSurfaceElevated,
                                        CircleShape,
                                    )
                                    .border(2.dp, NexusBackground, CircleShape)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { onPromote(p) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    "Jadikan pembicara",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        } else if (p.hasRaisedHand) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(NexusAccent, CircleShape)
                                    .border(2.dp, NexusBackground, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.PanTool, null,
                                    tint = Color.White, modifier = Modifier.size(9.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name.substringBefore(' '),
                        color = if (p.hasRaisedHand) NexusAccentSoft else NexusTextSecondary,
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
            .background(NexusSurface)
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

        // Name chip (bottom-start) — small avatar + first name. Width-capped: a long
        // name would otherwise stretch the chip past the edge of its own tile and
        // collide with the mic badge opposite it.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .fillMaxWidth(0.72f)
                .wrapContentWidth(Alignment.Start)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoomAvatar(p = p, size = 22.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = name.substringBefore(' '),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
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

/**
 * Avatar helper for room UI: photo when available, gradient + initial otherwise.
 *
 * The photo comes through [rememberAvatarUrl], so a participant payload that arrives
 * without an avatar (the realtime pushes routinely do) keeps showing the picture we
 * already know instead of collapsing to a letter every time someone joins or leaves.
 */
@Composable
private fun RoomAvatar(p: NetRoomParticipant, size: androidx.compose.ui.unit.Dp) {
    val name = p.displayName.ifBlank { p.username }.ifBlank { "Pengguna" }
    // Both ids: a room payload gives a user id, but the photo may have been learned
    // from the chat list, which only ever knew the username.
    val avatar = rememberAvatarUrl(p.userId, p.username, incoming = p.avatarMediaId)
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
                        // Capped so a long message wraps into a bubble instead of
                        // stretching the full width of the screen.
                        .fillMaxWidth(0.82f)
                        .wrapContentWidth(if (line.mine) Alignment.End else Alignment.Start)
                        .background(
                            if (line.mine) NexusAccent else NexusSurfaceElevated,
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
                .background(NexusSurfaceElevated, RoundedCornerShape(24.dp))
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

/**
 * The bottom controls: mic · camera · leave · more.
 *
 * Four buttons, floating on a translucent bar rather than a flat block. The active
 * ones carry a soft breathing aura — a live mic is the single most important piece of
 * state in a voice room and it now says so without a label. Every state change is
 * animated (colour, scale, icon), so toggling reads as one continuous object rather
 * than a redraw.
 *
 * There is no role button here. Asking to speak lives on the mic itself — pressing a
 * mic you aren't allowed to use IS the request — which is one control instead of two
 * that mean nearly the same thing.
 */
@Composable
private fun RoomControlBar(
    muted: Boolean,
    canPublish: Boolean,
    cameraOn: Boolean,
    loudspeaker: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleLoudspeaker: () -> Unit,
    onMore: () -> Unit,
    onLeave: () -> Unit,
) {
    val micLive = canPublish && !muted
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                // The card itself: a deep tinted panel, lit from the lower-left by the
                // theme accent, with a hairline edge so it lifts off the stage.
                .background(
                    Brush.linearGradient(
                        listOf(
                            AuroraRose.copy(alpha = 0.30f),
                            NexusBackground.copy(alpha = 0.96f),
                            AuroraGold.copy(alpha = 0.16f),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(32.dp))
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Grab handle. Purely an affordance — it says the panel is a surface that
            // sits on top of the room rather than part of its frame.
            Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Every control is labelled. In a room the cost of a wrong guess is
                // being heard when you thought you were muted, so the state is spelled
                // out rather than left to icon shape alone.
                LabelledControl(
                    icon = if (micLive) Icons.Filled.Mic else Icons.Filled.MicOff,
                    label = when {
                        !canPublish -> "Minta Bicara"
                        micLive -> "Mic On"
                        else -> "Mic Off"
                    },
                    tint = when {
                        !canPublish -> NexusTextSecondary
                        micLive -> AuroraYellow
                        else -> Color(0xFFFF6B8A)
                    },
                    active = micLive,
                    onClick = onToggleMute,
                )
                LabelledControl(
                    icon = if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    label = if (cameraOn) "Video On" else "Video Off",
                    tint = if (cameraOn) AuroraYellow else Color(0xFFFF6B8A),
                    active = cameraOn,
                    onClick = onToggleCamera,
                )
                EndCallControl(onClick = onLeave)
                LabelledControl(
                    icon = if (loudspeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Hearing,
                    label = if (loudspeaker) "Speaker" else "Earpiece",
                    tint = if (loudspeaker) AuroraYellow else Color.White,
                    active = loudspeaker,
                    onClick = onToggleLoudspeaker,
                )
                LabelledControl(
                    icon = Icons.Filled.MoreHoriz,
                    label = "Lainnya",
                    tint = Color.White,
                    active = false,
                    onClick = onMore,
                )
            }
        }
    }
}

/**
 * One labelled round control in the room panel: a translucent disc with a hairline
 * ring, and its state written underneath.
 *
 * [active] adds a faint accent wash and a brighter ring — enough to read "this is on"
 * at a glance without turning the button into a solid block of colour, which is what
 * made the previous bar look like a row of unrelated toys.
 */
@Composable
private fun LabelledControl(
    icon: ImageVector,
    label: String,
    tint: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ctl-press",
    )
    val ring by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) tint.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.13f),
        animationSpec = tween(240),
        label = "ctl-ring",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(
                indication = null,
                interactionSource = interaction,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (active) tint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f),
                )
                .border(1.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.Crossfade(
                targetState = icon,
                animationSpec = tween(180),
                label = "ctl-icon",
            ) { current ->
                Icon(current, label, tint = tint, modifier = Modifier.size(23.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The hang-up button: bigger than its neighbours, solid red, and wrapped in a slow
 * breathing halo.
 *
 * Unlabelled on purpose — a red phone-down glyph at this size is unambiguous in every
 * language, and leaving the label off is what lets it sit taller than the row and read
 * as the one destructive action.
 */
@Composable
private fun EndCallControl(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "end-press",
    )
    val pulse = rememberInfiniteTransition(label = "end-call")
    val breath by pulse.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "end-breath",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize().graphicsLayer { alpha = breath }) {
                val r = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFF3B58).copy(alpha = 0.45f), Color.Transparent),
                        radius = r,
                    ),
                    radius = r,
                )
                drawCircle(
                    color = Color(0xFFFF3B58).copy(alpha = 0.45f),
                    radius = r * 0.80f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                )
            }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF6303F))
                    .clickable(
                        indication = null,
                        interactionSource = interaction,
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CallEnd, "Keluar",
                    tint = Color.White, modifier = Modifier.size(26.dp),
                )
            }
        }
        // Keeps the icon rows aligned even though this one carries no label.
        Spacer(Modifier.height(8.dp))
        Text("", fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * A round control with an animated halo behind it and a press-spring.
 *
 * The halo only draws when [aura] is opaque, so an inactive button costs nothing —
 * important, since this bar sits over live video on a cheap phone. Colours animate
 * rather than snap, which is what makes toggling the mic feel like one object
 * changing state instead of two different buttons swapping places.
 */
@Composable
private fun AuraControl(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    aura: Color,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    iconSize: androidx.compose.ui.unit.Dp = 23.dp,
    /** 0..1 — how strong the halo gets at its peak. */
    auraStrength: Float = 1f,
    onClick: () -> Unit,
) {
    val active = aura.alpha > 0f
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = background,
        animationSpec = tween(280),
        label = "aura-bg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = tint,
        animationSpec = tween(280),
        label = "aura-fg",
    )
    // Breathing halo, only while the control is active.
    val pulse = rememberInfiniteTransition(label = "aura")
    val breath by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura-breath",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "aura-press",
    )

    Box(
        modifier = Modifier.size(size + 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            // A halo plus a SWEEPING ring. The sweep gradient rotates, so the ring has
            // a bright arc travelling round it — that slow highlight is what makes the
            // control feel lit from somewhere rather than just tinted.
            val spin by pulse.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
                label = "aura-spin",
            )
            Canvas(Modifier.matchParentSize().graphicsLayer { alpha = breath * auraStrength }) {
                val r = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(aura.copy(alpha = 0.36f), Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                )
                rotate(degrees = spin) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                aura.copy(alpha = 0.10f),
                                AuroraYellow.copy(alpha = 0.65f),
                                aura.copy(alpha = 0.25f),
                                Color.Transparent,
                                aura.copy(alpha = 0.10f),
                            ),
                        ),
                        radius = r * 0.74f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(size)
                .background(bg, CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = interaction,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Crossfade the glyph so mic ⇄ mic-off doesn't pop.
            androidx.compose.animation.Crossfade(
                targetState = icon,
                animationSpec = tween(200),
                label = "aura-icon",
            ) { current ->
                Icon(current, description, tint = fg, modifier = Modifier.size(iconSize))
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp = 46.dp,
    background: Color = NexusSurfaceElevated,
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
