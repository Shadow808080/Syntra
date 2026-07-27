package com.example.syntra

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.example.syntra.net.AvatarCache
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.AppLock
import com.example.syntra.net.CallEngine
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusOnline
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Call orchestration.
//
// The call lives at the APP ROOT (CallHost), not inside a chat screen — so it
// survives navigating away, and can shrink into a draggable picture-in-picture
// window that floats over the whole app and expands back on tap.
//
// The Syntra backend only carries call *state* (POST /calls, answer, leave) and
// mints the LiveKit sfu_token; the media itself rides the SFU. When the media
// server is not configured the token comes back blank — we say so plainly and
// close instead of pretending a call connected.
// ---------------------------------------------------------------------------

/** One active/pending call. A plain class so each call is a distinct instance. */
class CallDescriptor(
    val conversationId: String,
    val peerName: String,
    val peerId: String,
    val video: Boolean,
    val incoming: Boolean,
    val incomingCallId: String?,
    /**
     * The peer's photo, when the screen that started the call already had it.
     *
     * Nullable, and resolved again from [AvatarCache] inside the call screen: a call
     * placed from a notification knows almost nothing about the person, and the call
     * screen was showing a bare LETTER for someone whose photo the app already had on
     * disk. Where there genuinely is no photo, the Syntra placeholder is used — never
     * an initial.
     */
    val peerAvatar: String? = null,
)

/**
 * App-wide holder for the current call. Any screen can start/answer a call; the
 * single [CallHost] at the app root renders it (full or minimized).
 */
object CallController {
    var call by mutableStateOf<CallDescriptor?>(null)
        private set
    var minimized by mutableStateOf(false)
        private set

    /**
     * The call is ringing as a small banner rather than a full screen.
     *
     * Set when a call arrives while the user is inside a voice room. Nothing about the
     * call itself is different — it simply has not been allowed to take the screen
     * away from the room yet. Answering clears this and the normal call UI takes over;
     * declining ends it and the room is never interrupted.
     */
    var compact by mutableStateOf(false)
        private set

    /**
     * Raised the moment a compact call is ACCEPTED, so the room screen can bow out
     * cleanly (leave the SFU, tell the backend) before the call grabs the audio
     * device. Two live audio sessions at once is how you get a call with no sound.
     */
    var leaveRoomForCall by mutableStateOf(false)

    /**
     * A SECOND call arriving while one is already active.
     *
     * Kept apart from [call] so the live call is never disturbed by it. It rings as a
     * banner — exactly the treatment a call gets while you are in a voice room — and
     * the user decides. Previously MainActivity dropped these on the floor
     * (`if (isBusy) return`), so a second caller rang into silence forever.
     */
    var secondary by mutableStateOf<CallDescriptor?>(null)
        private set

    val isBusy: Boolean get() = call != null

    fun startOutgoing(
        conversationId: String,
        peerName: String,
        peerId: String,
        video: Boolean,
        peerAvatar: String? = null,
    ) {
        if (call != null) return
        call = CallDescriptor(
            conversationId, peerName, peerId, video,
            incoming = false, incomingCallId = null, peerAvatar = peerAvatar,
        )
        minimized = false
        compact = false
    }

    fun incoming(
        conversationId: String,
        peerName: String,
        peerId: String,
        video: Boolean,
        callId: String,
        /** True to ring as a banner instead of taking the screen — see [compact]. */
        asBanner: Boolean = false,
        peerAvatar: String? = null,
    ) {
        if (call != null) return
        call = CallDescriptor(
            conversationId, peerName, peerId, video,
            incoming = true, incomingCallId = callId, peerAvatar = peerAvatar,
        )
        minimized = false
        compact = asBanner
    }

    /** A call arrived while one is in progress — ring it as a banner. */
    fun secondaryIncoming(
        conversationId: String,
        peerName: String,
        peerId: String,
        video: Boolean,
        callId: String,
    ) {
        if (call == null || secondary != null) return
        if (callId == call?.incomingCallId) return // the same call, re-announced
        secondary = CallDescriptor(
            conversationId, peerName, peerId, video,
            incoming = true, incomingCallId = callId,
        )
    }

    /** Accepting the second call ENDS the first — one media session at a time. */
    fun acceptSecondary() {
        val next = secondary ?: return
        secondary = null
        call = next
        minimized = false
        compact = false
    }

    fun dismissSecondary() {
        secondary = null
    }

    /** Banner "answer": promote to the full call and ask the room to stand down. */
    fun acceptCompact() {
        if (!compact) return
        compact = false
        leaveRoomForCall = true
    }

    fun minimize() { if (call != null) minimized = true }
    fun expand() { minimized = false }
    fun end() {
        call = null
        minimized = false
        compact = false
        leaveRoomForCall = false
    }
}

private enum class CallPhase { INCOMING, CONNECTING, RINGING, ONGOING, ENDED }

/**
 * Logs every transition into ENDED with its cause.
 *
 * "The call just ends" has been diagnosed twice from reasoning alone and both fixes
 * missed, because several independent paths can end a call and the screen looks
 * identical whichever fires. One tag, greppable, so the next call answers it.
 */
private const val CALL_TAG = "SyntraCall"

private fun callEndLog(why: String) {
    android.util.Log.w(CALL_TAG, "END: " + why)
}

/**
 * The call backdrop, derived from the theme.
 *
 * A getter, not a val, so it tracks the theme like every other brand colour. It used to
 * be a fixed near-black, which meant an audio call stayed dark on the light theme while
 * the rest of the app was white.
 */
private val callBackdrop: List<Color>
    get() = listOf(NexusSurfaceElevated, NexusBackground)
private val callAvatarGradient = listOf(Color(0xFF2E6BF0), Color(0xFF3B68F5))

/** Renders the current call, if any. Mount once at the app root, above everything. */
@Composable
fun CallHost() {
    val descriptor = CallController.call ?: return
    // Ringing inside a voice room: a banner, not a takeover. The room keeps its
    // screen and its audio until the user actually chooses the call.
    if (CallController.compact) {
        IncomingCallBanner(descriptor)
        return
    }
    // Key on the instance so a brand-new call rebuilds all call state from scratch
    // (a re-used descriptor with equal fields must NOT keep the old phase/timer).
    key(descriptor) {
        CallSession(descriptor)
    }
    // A second caller rings as a banner OVER the live call. It must be declared after
    // CallSession so it draws on top.
    CallController.secondary?.let { second ->
        SecondCallBanner(
            d = second,
            onAccept = { CallController.acceptSecondary() },
            onDecline = {
                val id = second.incomingCallId
                if (!id.isNullOrBlank() && ApiConfig.ENABLED) {
                    SyntraClient.fireAndForget { SyntraClient.declineCall(id, second.conversationId) }
                }
                CallController.dismissSecondary()
            },
        )
    }
}

/**
 * The small "someone is calling" strip shown while the user is in a voice room.
 *
 * Everything a full call screen would say, in one row: who, what kind, and the two
 * answers. It sits under the status bar so it never covers the room's own controls,
 * and it pulses gently so it is noticeable without being a modal.
 *
 * Declining hangs up properly (the caller stops ringing) rather than just dismissing
 * the banner locally — a silently-ignored call is worse than a rejected one.
 */
/**
 * A second caller ringing while a call is already up.
 *
 * Pinned under the status bar so it never covers the live call's own controls, and it
 * says plainly that accepting ends the current call — otherwise "Terima" looks like it
 * merges the two, which is not what happens: one media session at a time.
 */
@Composable
private fun SecondCallBanner(d: CallDescriptor, onAccept: () -> Unit, onDecline: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(NexusSurfaceElevated)
                .border(1.dp, NexusStroke, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (d.video) Icons.Rounded.Videocam else Icons.Rounded.Call,
                null, tint = NexusAccentSoft, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.peerName.ifBlank { "Panggilan masuk" },
                    color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Menerima akan mengakhiri panggilan ini",
                    color = NexusTextSecondary, fontSize = 11.sp, maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp).clip(CircleShape).background(DangerFill)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDecline),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.CallEnd, "Tolak", tint = Color(0xFFFF5D5D), modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp).clip(CircleShape).background(NexusOnline)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onAccept),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Call, "Jawab", tint = Color.White, modifier = Modifier.size(17.dp)) }
        }
    }
}

@Composable
private fun IncomingCallBanner(d: CallDescriptor) {
    val pulse = rememberInfiniteTransition(label = "ring-banner")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "ring-glow",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF16161E).copy(alpha = 0.97f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(NexusAccent.copy(alpha = glow)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (d.video) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = d.peerName.ifBlank { "Panggilan masuk" },
                    color = NexusTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (d.video) "Panggilan video masuk" else "Panggilan suara masuk",
                    color = NexusTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Decline.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DangerFill)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        val id = d.incomingCallId
                        if (!id.isNullOrBlank()) {
                            SyntraClient.fireAndForget {
                                SyntraClient.declineCall(id, d.conversationId)
                            }
                        }
                        CallController.end()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.CallEnd, "Tolak",
                    tint = Color(0xFFFF5D5D), modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Answer — hands over to the full call screen, and the room stands down.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(NexusOnline)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { CallController.acceptCompact() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Call, "Jawab",
                    tint = Color.White, modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun CallSession(d: CallDescriptor) {
    val context = LocalContext.current
    val incoming = d.incoming
    var phase by remember { mutableStateOf(if (incoming) CallPhase.INCOMING else CallPhase.CONNECTING) }
    var callId by remember { mutableStateOf(d.incomingCallId.orEmpty()) }
    val isVideo = d.video
    var elapsed by remember { mutableIntStateOf(0) }
    var statusLine by remember { mutableStateOf(if (incoming) "Panggilan masuk" else "Memanggil…") }
    var everConnected by remember { mutableStateOf(false) }

    // Photo resolution, best → worst: what the caller passed, then the persisted cache
    // (keyed by BOTH id and username elsewhere in the app), then a one-off fetch.
    var peerPhoto by remember(d) {
        mutableStateOf(d.peerAvatar ?: AvatarCache.get(context, d.peerId))
    }
    LaunchedEffect(d.peerId) {
        if (peerPhoto.isNullOrBlank() && d.peerId.isNotBlank() && ApiConfig.ENABLED) {
            runCatching { SyntraClient.getConversations() }
                .getOrNull()
                ?.firstOrNull { it.counterpartId == d.peerId }
                ?.avatarMediaId
                ?.takeIf { it.startsWith("http") }
                ?.let {
                    peerPhoto = it
                    AvatarCache.put(context, d.peerId, it)
                }
        }
    }

    // Who is in this call. Refreshed while it runs so the "n/5" and the invite button
    // reflect people joining and leaving.
    val members = remember(d) { mutableStateListOf<SyntraClient.CallMember>() }
    var showInvite by remember(d) { mutableStateOf(false) }
    LaunchedEffect(callId, CallEngine.remotePeers.size) {
        if (callId.isBlank() || !ApiConfig.ENABLED) return@LaunchedEffect
        runCatching { SyntraClient.callParticipants(callId) }
            .onSuccess { members.clear(); members.addAll(it) }
    }

    // Everyone on the call, lead tile first (active speaker, else the first person).
    // "Me" is included so the grid is the whole call and not just the others.
    val peers = CallEngine.remotePeers
    val bentoTiles = remember(
        peers, members.toList(), CallEngine.localVideo, CallEngine.cameraEnabled,
        CallEngine.localSpeaking,
    ) {
        val others = peers.map { peer ->
            // LiveKit's identity IS the user id, so using it raw put a UUID under every
            // face. Resolve it against the participant list for a real name, and against
            // the avatar cache for a real photo; GradientAvatar supplies Syntra's own
            // empty-profile mark when there genuinely is none.
            val m = members.firstOrNull { it.userId == peer.identity }
            val label = m?.displayName?.takeIf { it.isNotBlank() }
                ?: m?.username?.takeIf { it.isNotBlank() }
                ?: peer.name.takeIf { it.isNotBlank() && it != peer.identity }
                ?: "Pengguna"
            CallTile(
                name = label,
                video = peer.video,
                speaking = peer.speaking,
                isMe = false,
                photo = AvatarCache.get(context, peer.identity),
            )
        }
        val me = CallTile(
            name = "Anda",
            video = CallEngine.localVideo.takeIf { CallEngine.cameraEnabled },
            speaking = CallEngine.localSpeaking,
            isMe = true,
            photo = AvatarCache.get(context, SyntraClient.myUserId.orEmpty()),
        )
        // Speaker leads; otherwise keep a stable order so tiles do not shuffle on every
        // recomposition, which is far more distracting than a slightly stale layout.
        val ordered = others.sortedByDescending { it.speaking }
        ordered + me
    }
    // The grid takes over from THREE people (two others + me). A 1:1 call keeps the
    // full-bleed layout: a grid of two throws away the whole point of a video call.
    val isGroupCall = phase == CallPhase.ONGOING && peers.size >= 2

    val remoteJoined = CallEngine.remoteJoined
    val remoteVideo = CallEngine.remoteVideo
    val localVideo = CallEngine.localVideo

    val elapsedLatest by rememberUpdatedState(elapsed)
    val connectedLatest by rememberUpdatedState(everConnected)
    val videoLatest by rememberUpdatedState(isVideo)
    // Latest call id captured for the teardown leave (callId is set after start/answer).
    val callIdAtDispose by rememberUpdatedState(callId)

    // CRUCIAL: subscribe to the conversation channel that carries call.answered /
    // call.ended. Without this the caller never learns the callee declined/hung up,
    // stays stuck on "Memanggil…", and CallController stays busy — so the NEXT call
    // is a silent no-op (startOutgoing sees a call already in progress) and the peer
    // never rings. This one subscription fixes "declined but still calling" AND
    // "second call doesn't show up on their phone". Idempotent, so it's safe even
    // when the home/chat screen already subscribed.
    LaunchedEffect(d.conversationId) {
        if (ApiConfig.ENABLED && d.conversationId.isNotBlank()) {
            SyntraClient.subscribe(listOf("conversation:${d.conversationId}"))
        }
    }

    // Guard: connect exactly ONCE. Backend logs showed answer/start being hit ~15×
    // in two seconds — every call re-created the LiveKit session, so media never
    // stabilised ("gada menyambungkan"). A one-shot latch makes the answer/start
    // request fire a single time no matter how often connectNow is (re)triggered.
    var connectStarted by remember { mutableStateOf(false) }

    suspend fun connectNow() {
        if (connectStarted) return
        // Remounted while the media session is still up (app lock, config change):
        // adopt the live call instead of answering it a second time. Re-answering is
        // what made the first call visibly reconnect.
        if (CallEngine.isActive) {
            connectStarted = true
            phase = if (CallEngine.remoteJoined) CallPhase.ONGOING else CallPhase.CONNECTING
            if (phase == CallPhase.ONGOING) everConnected = true
            return
        }
        connectStarted = true
        if (!ApiConfig.ENABLED) {
            statusLine = "Server belum aktif"; phase = CallPhase.ENDED; return
        }
        runCatching {
            val call = if (incoming) {
                SyntraClient.answerCall(callId, d.conversationId)
            } else {
                SyntraClient.startCall(d.conversationId, if (isVideo) "video" else "audio")
                    .also { callId = it.callId }
            }
            if (call.sfuUrl.isBlank() || call.sfuToken.isBlank()) {
                error("Panggilan belum tersedia — server media belum dikonfigurasi.")
            }
            // Show the connecting/ringing UI IMMEDIATELY, BEFORE the media connect.
            // Tapping "Terima" must give instant feedback; the old order set the phase
            // only AFTER CallEngine.connect, so if the LiveKit handshake hung (common
            // on emulators / bad networks) the screen sat on the incoming buttons with
            // no reaction — looking like the button was disabled.
            phase = if (incoming) CallPhase.CONNECTING else CallPhase.RINGING
            statusLine = if (incoming) "Menyambungkan…" else "Memanggil…"
            // Bound the media handshake so it can't hang forever with no feedback. If
            // it doesn't complete, end the call with a clear message instead of a
            // frozen "Menyambungkan…".
            try {
                kotlinx.coroutines.withTimeout(20_000) {
                    CallEngine.connect(context, call.sfuUrl, call.sfuToken, isVideo)
                }
            } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                error("Gagal menyambung ke server media (jaringan / emulator?)")
            }
        }.onFailure {
            // A real screen-close cancellation must propagate; a media timeout is a
            // failure we surface, not a cancellation.
            if (it is kotlinx.coroutines.CancellationException &&
                it !is kotlinx.coroutines.TimeoutCancellationException
            ) throw it
            callEndLog("connectNow failed: ${it::class.java.simpleName}: ${it.message}")
            statusLine = it.message ?: "Panggilan gagal"
            Toast.makeText(context, statusLine, Toast.LENGTH_LONG).show()
            phase = CallPhase.ENDED
        }
    }

    val permissions = if (isVideo) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    // A scope tied to THIS call session (not to any state key). connectNow runs here
    // so it can't be cancelled mid-flight. The old code launched it from a
    // LaunchedEffect keyed on a boolean that connectNow itself reset — flipping the
    // key mid-connect cancelled answer_call/start_call halfway, which is exactly why
    // "Terima" looked dead AND why a cancelled outgoing call never sent leaveCall
    // (its callId was never assigned), leaving the callee's screen stuck ringing.
    // The in-app UI is now responsible for this call, so the notification must stop
    // ringing — otherwise both ring at once.
    LaunchedEffect(Unit) { com.example.syntra.net.Notifications.cancelIncomingCall(context) }

    val callScope = rememberCoroutineScope()
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val micOk = result[Manifest.permission.RECORD_AUDIO] != false
        if (micOk) {
            callScope.launch { connectNow() }
        } else {
            statusLine = "Izin mikrofon ditolak"
            Toast.makeText(context, statusLine, Toast.LENGTH_LONG).show()
            phase = CallPhase.ENDED
        }
    }

    // Proceed to connect — but only ASK for permissions we don't already have.
    // Relying on RequestMultiplePermissions.launch() to always fire its callback is
    // the bug behind "tekan Terima, tak terjadi apa-apa": when the permissions are
    // already granted the callback can silently not run, so connectNow() was never
    // called and the screen sat on the incoming/connecting state forever. Checking
    // the grant state directly and jumping straight to connectNow fixes that.
    fun proceed() {
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val camOk = !isVideo || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (micOk && camOk) {
            callScope.launch { connectNow() }
        } else {
            // The permission dialog stops this activity, which the app lock would
            // otherwise read as "the user left" and re-lock — dropping MainTabs, and
            // this whole call with it. Tell the lock it's our own dialog first.
            AppLock.expectSystemDialog()
            permLauncher.launch(permissions)
        }
    }

    // Outgoing dials immediately; incoming waits for the user to accept — unless they
    // already accepted on the notification, in which case showing the Terima button
    // again would be asking twice.
    LaunchedEffect(Unit) {
        if (!incoming) {
            proceed()
        } else if (PendingCallAnswer.autoAnswer) {
            PendingCallAnswer.autoAnswer = false
            proceed()
        }
    }

    // Promote to "ongoing" the moment the other side is really in the room.
    LaunchedEffect(CallEngine.connected) {
        android.util.Log.i(CALL_TAG, "engine.connected=${CallEngine.connected} phase=$phase")
    }

    LaunchedEffect(remoteJoined) {
        if (remoteJoined && phase != CallPhase.ONGOING && phase != CallPhase.ENDED) {
            phase = CallPhase.ONGOING; everConnected = true
        }
    }

    // Reliability net for a dropped WebSocket. The tunnel occasionally drops the
    // socket (close 1006), and a call.ended sent during that gap is lost — leaving
    // one side stuck "Memanggil…" after the other declined/cancelled. So while the
    // call is still pending, POLL the call's real status every 3s: if the backend
    // no longer has it active (declined / cancelled / missed), end this screen too.
    LaunchedEffect(phase) {
        val pending = phase == CallPhase.INCOMING || phase == CallPhase.RINGING || phase == CallPhase.CONNECTING
        if (!pending || d.conversationId.isBlank()) return@LaunchedEffect
        // Two consecutive "no active call" answers are required before this ends
        // anything. One is not evidence: the request is in flight for hundreds of
        // milliseconds, and around the moment of answering the backend legitimately
        // reports nothing yet — start_call has been issued but the row is not
        // 'ringing'/'ongoing' at the instant we ask.
        var missed = 0
        while (true) {
            delay(3000)
            if (phase == CallPhase.ONGOING || phase == CallPhase.ENDED) break
            // Distinguish "call really ended" (a successful response of null) from a
            // transient network error — only the former should end the screen, or a
            // glitchy poll would kill a perfectly good ringing call.
            val result = runCatching { SyntraClient.getActiveCall(d.conversationId) }

            // RE-CHECK AFTER THE AWAIT. This is the bug that dropped every answered
            // call a few seconds in: the phase is checked BEFORE the request, the
            // request then takes time, the user answers meanwhile — and the stale
            // result was applied anyway, ending a call that had just become live.
            // The device log caught it red-handed: "no active call (phase=ONGOING)".
            if (phase == CallPhase.ONGOING || phase == CallPhase.ENDED) break
            // Media is up and the far side is here: whatever the poll says, this call
            // is demonstrably alive. The socket net has done its job.
            if (CallEngine.isActive && CallEngine.remoteJoined) break

            if (result.isSuccess && result.getOrNull() == null) {
                missed++
                if (missed >= 2) {
                    callEndLog("poll: no active call twice in a row (phase=$phase)")
                    statusLine = "Panggilan berakhir"
                    phase = CallPhase.ENDED
                    break
                }
            } else if (result.isSuccess) {
                missed = 0
            }
        }
    }

    // Call timer.
    LaunchedEffect(phase) {
        if (phase == CallPhase.ONGOING) {
            statusLine = ""
            while (true) { delay(1000); elapsed++ }
        }
    }

    // A ring must not last forever. Outgoing: give up when nobody answers. Incoming:
    // auto-miss so it stops ringing if never picked up. Either way tell the far side.
    LaunchedEffect(phase) {
        val ringingOut = !incoming && phase == CallPhase.RINGING
        val ringingIn = incoming && phase == CallPhase.INCOMING
        if (ringingOut || ringingIn) {
            delay(35_000)
            if ((ringingOut && phase == CallPhase.RINGING) || (ringingIn && phase == CallPhase.INCOMING)) {
                callEndLog("ring timeout after 35s (incoming=$incoming)")
                statusLine = if (ringingIn) "Panggilan tak terjawab" else "Tidak dijawab"
                val id = callId
                if (id.isNotBlank() && ApiConfig.ENABLED) {
                    if (ringingIn) SyntraClient.fireAndForget { SyntraClient.declineCall(id, d.conversationId) }
                    else SyntraClient.fireAndForget { SyntraClient.leaveCall(id, d.conversationId) }
                }
                phase = CallPhase.ENDED
            }
        }
    }

    // Ringtone while waiting — on BOTH sides. Prepared off the main thread so it
    // never blocks; falls back to the notification sound if the ringtone is unset.
    val ringing = phase == CallPhase.INCOMING || phase == CallPhase.RINGING
    DisposableEffect(ringing) {
        var player: android.media.MediaPlayer? = null
        if (ringing) {
            val uri = android.media.RingtoneManager
                .getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE)
                ?: android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_NOTIFICATION)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            runCatching {
                player = android.media.MediaPlayer().apply {
                    setDataSource(context, uri)
                    isLooping = true
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setOnPreparedListener { runCatching { it.start() } }
                    prepareAsync()
                }
            }
        }
        onDispose { runCatching { player?.stop(); player?.release() } }
    }

    // Realtime call signalling: the far side answering, declining, or hanging up.
    DisposableEffect(Unit) {
        val listener = object : SocketListener {
            override fun onCallEnded(endedCallId: String, endedConversationId: String, reason: String) {
                // Only react to THIS call. Events carry the conversation now, so a
                // stray ended from another chat (or a late one from a previous call)
                // can't tear down the call currently on screen. Match on conversation
                // (always present) or the call id once we know it.
                if (endedConversationId.isNotBlank() && endedConversationId != d.conversationId) return
                if (endedCallId.isNotBlank() && callId.isNotBlank() && endedCallId != callId) return
                callEndLog("socket call.ended reason=$reason id=$endedCallId conv=$endedConversationId myCallId=$callId")
                statusLine = if (reason == "declined") "Panggilan ditolak" else "Panggilan berakhir"
                phase = CallPhase.ENDED
            }
            override fun onCallAnswered(answeredCallId: String, answeredConversationId: String) {
                if (answeredConversationId.isNotBlank() && answeredConversationId != d.conversationId) return
                if (!incoming && phase == CallPhase.RINGING) statusLine = "Menyambungkan…"
            }
        }
        SyntraClient.addListener(listener)
        onDispose {
            SyntraClient.removeListener(listener)
            // Only tear the CALL down when the call is genuinely over. This composable
            // can also be disposed while the call is still live — the app lock taking
            // the screen, a configuration change — and the old code hung up regardless,
            // then re-answered on remount, which is what "auto reconnect" was. If the
            // controller still holds this descriptor, we are being remounted, not ended:
            // leave the media session and the backend call alone.
            if (CallController.call === d) return@onDispose
            CallEngine.disconnect()
            // CRUCIAL: always tell the backend we left, on EVERY teardown path — not
            // just the hang-up button. Otherwise a call that ended some other way
            // (answer succeeded but SFU connect failed, the screen was torn down, etc.)
            // stays 'ongoing' forever and BLOCKS every future call to that person
            // (start_call joins the ghost instead of ringing). leaveCall is idempotent.
            val id = callIdAtDispose
            if (id.isNotBlank() && ApiConfig.ENABLED) {
                SyntraClient.fireAndForget { SyntraClient.leaveCall(id, d.conversationId) }
            }
            val direction = when {
                !incoming -> CallDirection.OUTGOING
                connectedLatest -> CallDirection.INCOMING
                else -> CallDirection.MISSED
            }
            CallLog.add(
                context,
                CallEntry(
                    // Millis alone collided when two calls ended in the same ms; add a
                    // random suffix so the call-log list keys are always unique.
                    id = "c-${System.currentTimeMillis()}-${(0..999999).random()}",
                    peerName = d.peerName,
                    peerId = d.peerId,
                    video = videoLatest,
                    direction = direction,
                    at = System.currentTimeMillis(),
                    durationSec = elapsedLatest,
                ),
            )
        }
    }

    // When the call ends, linger briefly on the status then tear down.
    LaunchedEffect(phase) {
        if (phase == CallPhase.ENDED) { delay(1200); CallController.end() }
    }

    fun hangUp() {
        val id = callId
        if (id.isNotBlank() && ApiConfig.ENABLED) {
            SyntraClient.fireAndForget { SyntraClient.leaveCall(id, d.conversationId) }
        }
        CallController.end()
    }

    fun decline() {
        val id = callId
        if (id.isNotBlank() && ApiConfig.ENABLED) {
            SyntraClient.fireAndForget { SyntraClient.declineCall(id, d.conversationId) }
        }
        CallController.end()
    }

    if (showInvite) {
        InvitePickerDialog(
            already = members.map { it.userId }.toSet(),
            onDismiss = { showInvite = false },
            onPick = { userId ->
                showInvite = false
                val id = callId
                if (id.isNotBlank()) {
                    callScope.launch {
                        runCatching { SyntraClient.inviteToCall(id, d.conversationId, userId, isVideo) }
                            .onSuccess {
                                Toast.makeText(context, "Undangan terkirim.", Toast.LENGTH_SHORT).show()
                                runCatching { SyntraClient.callParticipants(id) }
                                    .onSuccess { members.clear(); members.addAll(it) }
                            }
                            .onFailure {
                                // The cap and the block rules live in the database, so a
                                // refusal here is meaningful — surface it verbatim.
                                Toast.makeText(
                                    context,
                                    it.message ?: "Gagal mengundang.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    }
                }
            },
        )
    }

    if (CallController.minimized) {
        MiniCallWindow(
            peerName = d.peerName,
            peerPhoto = peerPhoto,
            tiles = bentoTiles,
            isGroupCall = isGroupCall,
            elapsed = elapsed,
            statusLine = if (phase == CallPhase.ONGOING) formatDuration(elapsed) else statusLine,
            isVideo = isVideo,
            remoteVideo = remoteVideo.takeIf { phase == CallPhase.ONGOING },
            onExpand = { CallController.expand() },
            onHangUp = { hangUp() },
        )
    } else {
        FullCallUi(
            peerName = d.peerName,
            peerPhoto = peerPhoto,
            members = members,
            bentoTiles = bentoTiles,
            isGroupCall = isGroupCall,
            // Invite is only offered once the call is actually up, and only while there
            // is room — the server enforces the cap, but a button that can only fail is
            // not worth showing.
            canInvite = phase == CallPhase.ONGOING && members.count { it.joined } < 5,
            onInvite = { showInvite = true },
            phase = phase,
            statusLine = statusLine,
            elapsed = elapsed,
            isVideo = isVideo,
            remoteVideo = remoteVideo,
            localVideo = localVideo,
            onMinimize = { CallController.minimize() },
            onAccept = { proceed() },
            onDecline = { decline() },
            onHangUp = { hangUp() },
        )
    }
}

// ---------------------------------------------------------------------------
// Full-screen call UI
// ---------------------------------------------------------------------------

@Composable
private fun FullCallUi(
    peerName: String,
    peerPhoto: String? = null,
    members: List<SyntraClient.CallMember> = emptyList(),
    bentoTiles: List<CallTile> = emptyList(),
    isGroupCall: Boolean = false,
    canInvite: Boolean = false,
    onInvite: () -> Unit = {},
    phase: CallPhase,
    statusLine: String,
    elapsed: Int,
    isVideo: Boolean,
    remoteVideo: VideoTrack?,
    localVideo: VideoTrack?,
    onMinimize: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
) {
    // Back behaviour depends on where the call is:
    //  - INCOMING (ringing at me)        → decline
    //  - ONGOING (connected)             → minimize to the floating window
    //  - RINGING / CONNECTING (pending)  → HANG UP / cancel
    // The old code minimized on RINGING too, so pressing Back while dialing left a
    // floating call stuck alive — CallController stayed busy and you couldn't start
    // a new call until you found and killed that window. Cancelling instead frees it.
    BackHandler {
        when (phase) {
            CallPhase.INCOMING -> onDecline()
            CallPhase.ONGOING -> onMinimize()
            else -> onHangUp()
        }
    }

    val showVideoStage = isVideo && phase == CallPhase.ONGOING && remoteVideo != null && !isGroupCall
    // 1:1 video only: tapping the corner window swaps which feed is full-screen.
    // Reset whenever the call's shape changes, so a swap can't survive into a state
    // where "the other feed" means something different.
    var swapped by remember(isGroupCall, showVideoStage) { mutableStateOf(false) }

    // WHAT THE TEXT SITS ON decides its colour, not the theme alone. Over live video
    // the surface is the video itself (plus a scrim), so white is correct on every
    // theme; over the backdrop it must follow the theme or it vanishes on light.
    val onCall = if (showVideoStage) Color.White else NexusTextPrimary
    val onCallDim = if (showVideoStage) Color.White.copy(alpha = 0.75f) else NexusTextSecondary
    val onCallHair = if (showVideoStage) Color.White.copy(alpha = 0.18f) else NexusStroke

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(callBackdrop)),
    ) {
        if (showVideoStage) {
            // Swapped: your own camera takes the stage and the peer moves to the
            // corner. Mirrored only when it is your own feed — a mirrored peer looks
            // subtly wrong and nobody can say why.
            val stageTrack = if (swapped) localVideo else remoteVideo
            stageTrack?.let { track ->
                VideoRenderer(track = track, mirror = swapped, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp).background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
                ),
            )
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(240.dp).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
                ),
            )
        }

        // Minimize-to-PiP button — ONLY once the call is actually connected. There's
        // nothing useful to float before that, and allowing minimize while dialing is
        // exactly what let a pending call get stranded off-screen.
        if (phase == CallPhase.ONGOING) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.28f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onMinimize,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Perkecil", tint = onCall, modifier = Modifier.size(24.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showVideoStage) {
                Spacer(Modifier.height(8.dp))
                CallHeaderPill(onSurface = onCall, name = peerName.ifBlank { "Tanpa nama" }, subtitle = formatDuration(elapsed))
            } else if (isGroupCall) {
                // Three or more: the grid IS the screen. A single big name and one
                // avatar cannot represent four people, and the 1:1 layout silently
                // showed only whoever happened to be first.
                Spacer(Modifier.height(56.dp))
                Text(
                    text = if (statusLine.isBlank()) formatDuration(elapsed) else statusLine,
                    color = NexusAccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                CallBentoGrid(tiles = bentoTiles, modifier = Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            } else {
                Spacer(Modifier.height(64.dp))
                Text(
                    text = peerName.ifBlank { "Tanpa nama" },
                    color = NexusTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = NexusTextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (phase == CallPhase.ONGOING && statusLine.isBlank()) formatDuration(elapsed) else statusLine,
                        color = NexusAccentSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(56.dp))
                PulsingAvatar(
                    photoUrl = peerPhoto,
                    // Pulse while ringing, waiting to be answered, AND while connecting
                    // media — so "Menyambungkan…" reads as active loading, not frozen.
                    pulsing = phase == CallPhase.RINGING || phase == CallPhase.INCOMING ||
                        phase == CallPhase.CONNECTING,
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // BESIDE the controls, not above them. Stacked, the strip and the
                // control bar were two floating pills fighting for the same corner of
                // the screen and overlapping on short displays.
                if (canInvite && phase == CallPhase.ONGOING) {
                    InviteSideButton(count = members.count { it.joined }, onClick = onInvite)
                    Spacer(Modifier.width(10.dp))
                }
                when (phase) {
                    CallPhase.INCOMING -> IncomingControls(video = isVideo, onAccept = onAccept, onDecline = onDecline)
                    CallPhase.ENDED -> Spacer(Modifier.height(40.dp))
                    else -> OngoingControls(
                        isVideo = isVideo,
                        onToggleMic = { CallEngine.fireMic() },
                        onToggleSpeaker = { CallEngine.setSpeaker(!CallEngine.speakerOn) },
                        onToggleCamera = { CallEngine.fireCamera(!CallEngine.cameraEnabled) },
                        onSwitchCamera = { CallEngine.switchCamera() },
                        onHangUp = onHangUp,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
        }

        // Self-preview picture-in-picture (video calls only).
        // Hidden in group mode: the bento grid already has a tile for you, so the
        // floating self-view was the same face twice, covering a real participant.
        if (isVideo && !isGroupCall && CallEngine.cameraEnabled && localVideo != null &&
            phase == CallPhase.ONGOING
        ) {
            // Whichever feed is NOT on the stage. Tapping swaps them — the standard
            // gesture on every video call app, and the only way to check your own
            // framing at a useful size without hunting for a setting.
            val cornerTrack = if (swapped) remoteVideo else localVideo
            cornerTrack?.let { track ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp)
                        .size(width = 112.dp, height = 156.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { swapped = !swapped },
                ) {
                    VideoRenderer(track = track, mirror = !swapped, modifier = Modifier.fillMaxSize())
                    // A small hint that the window is interactive: without it the swap
                    // is a gesture nobody discovers.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Cameraswitch, "Tukar tampilan",
                            tint = Color.White, modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Minimized floating call window (draggable, tap to expand)
// ---------------------------------------------------------------------------

@Composable
private fun MiniCallWindow(
    peerName: String,
    peerPhoto: String? = null,
    /** Everyone on the call, so the pill can follow whoever is speaking. */
    tiles: List<CallTile> = emptyList(),
    /** Three or more people: minimize to the pill, never a single video thumbnail. */
    isGroupCall: Boolean = false,
    elapsed: Int,
    statusLine: String,
    isVideo: Boolean,
    remoteVideo: VideoTrack?,
    onExpand: () -> Unit,
    onHangUp: () -> Unit,
) {
    val density = LocalDensity.current
    // The window is 128x180 (video) or a compact pill (audio). It starts near the
    // top-right and can be dragged ANYWHERE; position is clamped to stay on screen.
    // A group call minimizes to the PILL, so its size must follow that decision too —
    // otherwise the pill was being laid out inside a 128x180 video-shaped box and every
    // element inside it was crushed.
    val asVideo = isVideo && remoteVideo != null && !isGroupCall
    val winW = if (asVideo) 132.dp else 232.dp
    val winH = if (asVideo) 186.dp else 78.dp

    // Full-screen container that does NOT consume touches, so the app behind the
    // window stays fully interactive — only the window itself grabs gestures.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxXpx = with(density) { (maxWidth - winW).toPx() }
        val maxYpx = with(density) { (maxHeight - winH).toPx() }
        val startX = maxXpx - with(density) { 12.dp.toPx() }
        val startY = with(density) { 64.dp.toPx() }
        var offset by remember { mutableStateOf(Offset(startX.coerceAtLeast(0f), startY)) }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(width = winW, height = winH)
                .clip(RoundedCornerShape(18.dp))
                .background(NexusSurfaceElevated)
                .border(1.dp, NexusStroke, RoundedCornerShape(18.dp))
                .pointerInput(maxXpx, maxYpx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offset = Offset(
                            (offset.x + drag.x).coerceIn(0f, maxXpx.coerceAtLeast(0f)),
                            (offset.y + drag.y).coerceIn(0f, maxYpx.coerceAtLeast(0f)),
                        )
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onExpand,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // A group call minimizes to the SPEAKER PILL, exactly like an audio call.
            // A 128x180 thumbnail can only show one of four people, and picking one
            // arbitrarily is worse than showing whoever is actually talking.
            if (asVideo) {
                VideoRenderer(track = remoteVideo, modifier = Modifier.fillMaxSize())
                // Restructured. The old bar put the status text and the hang-up button
                // in ONE row across a 128dp-wide window: the button took ~34dp, the
                // padding another 16dp, and the timer was left fighting for what
                // remained — usually clipped to a couple of characters.
                //
                // Now the timer sits on its own line above, and the button gets the
                // full width beneath it. Nothing competes, and nothing truncates.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                            ),
                        )
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Speaker name when someone is talking, otherwise the timer: in a
                    // thumbnail this small, only one of them can be legible at a time.
                    val speaking = tiles.firstOrNull { it.speaking }
                    Text(
                        speaking?.name ?: statusLine,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    MiniHangUp(onHangUp)
                }
            } else {
                // Audio (or video not yet flowing): avatar + name + timer + hang up.
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Follows the VOICE. A minimized call showed one fixed face and
                    // name, so in a group you had no idea who was talking without
                    // opening it again.
                    // Everyone, yourself included — "who is talking" has no reason to
                    // exclude you, and excluding you made the pill freeze on the peer
                    // whenever you were the one speaking.
                    val speaker = tiles.firstOrNull { it.speaking }
                    GradientAvatar(
                        gradient = callAvatarGradient,
                        initial = "",
                        size = 44.dp,
                        photoUrl = speaker?.photo ?: peerPhoto,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            speaker?.name ?: peerName.ifBlank { "Tanpa nama" },
                            color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        // The wave replaces the status line while someone is talking:
                        // "00:42" and "sedang bicara" compete for the same slot, and the
                        // wave answers the more useful question without any words.
                        if (speaker != null) {
                            SpeakerWave(modifier = Modifier.height(14.dp))
                        } else {
                            Text(statusLine, color = NexusAccentSoft, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    MiniHangUp(onHangUp)
                }
            }
        }
    }
}

/**
 * A live voice waveform.
 *
 * Bars driven by summed sines at unrelated frequencies rather than random heights:
 * random reads as flicker, and a single sine reads as a mechanical bounce. Two
 * non-harmonic components per bar give the irregular-but-continuous motion that
 * actually looks like a voice, and it never visibly repeats.
 *
 * Phase is shared and each bar is offset along it, so the movement travels across the
 * group instead of every bar pumping in unison.
 */
@Composable
private fun SpeakerWave(modifier: Modifier = Modifier, bars: Int = 5) {
    val t = rememberInfiniteTransition(label = "speaker-wave")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        // One whole period with LinearEasing, so the loop point is the identical frame.
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "speaker-wave-phase",
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(bars) { i ->
            val o = i * 0.9f
            val a = kotlin.math.sin(phase + o)
            val b = kotlin.math.sin(phase * 1.7f + o * 1.3f)
            // 0.25..1.0 — never fully collapses, so the group keeps its shape.
            val level = (0.25f + 0.375f * (a + b * 0.6f).coerceIn(-1.2f, 1.2f) / 1.2f + 0.375f)
                .coerceIn(0.25f, 1f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(level)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NexusAccentSoft),
            )
        }
    }
}

@Composable
private fun MiniHangUp(onHangUp: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Color(0xFFE5484D), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onHangUp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.CallEnd, "Akhiri", tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------
// LiveKit video surface
// ---------------------------------------------------------------------------

@Composable
private fun VideoRenderer(
    track: VideoTrack,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
) {
    androidx.compose.runtime.key(track) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextureViewRenderer(ctx).apply {
                    CallEngine.initRenderer(this)
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

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

/**
 * A bento grid of everyone on the call.
 *
 * Used from three people upward. A 1:1 call keeps the full-bleed layout — a grid of two
 * is just two rectangles, and it throws away the one thing a video call should give you,
 * which is the other person as large as the screen allows.
 *
 * The proportions are deliberately UNEQUAL. A uniform grid says everyone matters
 * equally at every moment, which is false in a conversation: whoever is speaking is
 * what you want to look at. The lead tile is the active speaker, and it is roughly
 * twice the area of the rest, so attention follows the voice without anyone vanishing.
 *
 *   3 people → lead left, two stacked right
 *   4 people → lead across the top, three along the bottom
 *   5 people → lead top-left, two beside it, two underneath
 */
@Composable
private fun CallBentoGrid(
    tiles: List<CallTile>,
    modifier: Modifier = Modifier,
) {
    if (tiles.isEmpty()) return
    val gap = 6.dp
    val lead = tiles.first()
    val rest = tiles.drop(1)
    Column(modifier = modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        when (tiles.size) {
            in 0..2 -> {
                // Two tiles: split the height evenly. Still not a "grid" — just both.
                tiles.forEach { CallTileView(it, Modifier.weight(1f).fillMaxWidth()) }
            }
            3 -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                CallTileView(lead, Modifier.weight(1.35f).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    rest.forEach { CallTileView(it, Modifier.weight(1f).fillMaxWidth()) }
                }
            }
            4 -> {
                CallTileView(lead, Modifier.weight(1.25f).fillMaxWidth())
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rest.forEach { CallTileView(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
            else -> {
                Row(Modifier.weight(1.3f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    CallTileView(lead, Modifier.weight(1.5f).fillMaxHeight())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                        rest.take(2).forEach { CallTileView(it, Modifier.weight(1f).fillMaxWidth()) }
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rest.drop(2).take(2).forEach { CallTileView(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

/** One person in the bento grid. */
private data class CallTile(
    val name: String,
    val video: VideoTrack?,
    val speaking: Boolean,
    val isMe: Boolean,
    val photo: String? = null,
)

@Composable
private fun CallTileView(tile: CallTile, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NexusSurfaceElevated)
            // The speaking ring is the grid's only moving part. With several faces on
            // screen and no audio cue, it is genuinely hard to tell who is talking.
            .border(
                width = if (tile.speaking) 2.dp else 0.dp,
                color = if (tile.speaking) NexusAccent else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        if (tile.video != null) {
            VideoRenderer(track = tile.video, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GradientAvatar(
                    gradient = callAvatarGradient,
                    initial = "",
                    size = 56.dp,
                    photoUrl = tile.photo,
                )
            }
        }
        // Name plate: a scrim behind it, because a tile can be video (any colour) or a
        // flat surface, and the label has to stay readable on both.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (tile.isMe) "Anda" else tile.name.ifBlank { "Pengguna" },
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The add-someone control, sized to sit BESIDE the call controls.
 *
 * A vertical pill matching the control bar's height rather than a full-width strip: the
 * strip stacked above the controls and the two overlapped on short screens. The count
 * rides along because "can I add one more?" is the only question this button raises,
 * and 5 is the cap.
 */
@Composable
private fun InviteSideButton(count: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(NexusTextPrimary.copy(alpha = 0.08f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(NexusAccent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PersonAdd, "Ajak orang", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text("$count/5", color = NexusTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Picks someone to pull into the call, from the people you already have chats with.
 *
 * Deliberately sourced from conversations rather than a global search: a call is not a
 * place to meet strangers, and everyone here is someone you have already spoken to.
 * People already in the call are filtered out so the list only offers what can happen.
 */
@Composable
private fun InvitePickerDialog(
    already: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var people by remember { mutableStateOf<List<com.example.syntra.net.NetConversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        runCatching { SyntraClient.getConversations() }
            .onSuccess { list ->
                people = list
                    .filter { it.type == "direct" }
                    .filter { !it.counterpartId.isNullOrBlank() && it.counterpartId !in already }
                    .filterNot { com.example.syntra.net.BlockMask.hidden(ctx, it.counterpartUsername, it.counterpartId) }
            }
        loading = false
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(NexusSurfaceElevated)
                .padding(18.dp),
        ) {
            Text("Ajak ke panggilan", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Maksimal 5 orang dalam satu panggilan.",
                color = NexusTextSecondary, fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            when {
                loading -> Text("Memuat…", color = NexusTextSecondary, fontSize = 13.sp)
                people.isEmpty() -> Text(
                    "Tidak ada orang lain untuk diajak.",
                    color = NexusTextSecondary, fontSize = 13.sp,
                )
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(people.size) { i ->
                        val c = people[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { c.counterpartId?.let(onPick) }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GradientAvatar(
                                gradient = callAvatarGradient,
                                initial = "",
                                size = 34.dp,
                                photoUrl = c.avatarMediaId?.takeIf { it.startsWith("http") },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                c.title.ifBlank { c.counterpartUsername.orEmpty() },
                                color = NexusTextPrimary, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OngoingControls(
    isVideo: Boolean,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangUp: () -> Unit,
) {
    // Aurora behind the controls: two slow counter-drifting bands of the theme accent,
    // drawn INSIDE the pill and clipped to it, under a glass wash. The bar used to be a
    // flat 7%-white rectangle — correct, and completely lifeless.
    val aurora = rememberInfiniteTransition(label = "call-aurora")
    val drift by aurora.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6200, easing = LinearEasing), RepeatMode.Restart),
        label = "call-aurora-drift",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(44.dp))
            .drawBehind {
                // Two offset radial washes sliding in opposite directions. Radial rather
                // than linear so the light has a source instead of being a gradient
                // smeared across the bar.
                val w = size.width
                val h = size.height
                val x1 = w * (0.30f + 0.22f * kotlin.math.sin(drift))
                val x2 = w * (0.70f + 0.22f * kotlin.math.sin(drift + 2.2f))
                drawRect(Color.White.copy(alpha = 0.06f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NexusAccent.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(x1, h * 0.35f),
                        radius = h * 1.5f,
                    ),
                    radius = h * 1.5f,
                    center = Offset(x1, h * 0.35f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NexusAccentSoft.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(x2, h * 0.75f),
                        radius = h * 1.3f,
                    ),
                    radius = h * 1.3f,
                    center = Offset(x2, h * 0.75f),
                )
                // Top sheen, so the pill reads as glass over the light rather than paint.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.16f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.10f),
                    ),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(44.dp)),
    ) {
    // Spacing and sizes shrink as controls are added. A video call carries four
    // buttons; with the invite pill beside it the row ran past the screen edge and the
    // end-call button — always last — was the one pushed out of reach.
    val dense = isVideo
    Row(
        modifier = Modifier
            .padding(horizontal = if (dense) 12.dp else 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallControl(
            icon = if (CallEngine.micEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
            description = if (CallEngine.micEnabled) "Bisukan" else "Bunyikan",
            active = !CallEngine.micEnabled,
            onClick = onToggleMic,
        )
        if (isVideo) {
            CallControl(
                icon = if (CallEngine.cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                description = "Kamera",
                active = !CallEngine.cameraEnabled,
                onClick = onToggleCamera,
            )
            CallControl(icon = Icons.Rounded.Cameraswitch, description = "Balik kamera", onClick = onSwitchCamera)
        } else {
            CallControl(
                icon = if (CallEngine.speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                description = "Pengeras suara",
                active = CallEngine.speakerOn,
                onClick = onToggleSpeaker,
            )
        }
        Box(
            modifier = Modifier
                .size(if (dense) 52.dp else 58.dp)
                .background(Color(0xFFE5484D), CircleShape)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onHangUp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.CallEnd, "Akhiri",
                tint = Color.White, modifier = Modifier.size(if (dense) 23.dp else 26.dp),
            )
        }
    }
    }
}

@Composable
private fun CallHeaderPill(name: String, subtitle: String, onSurface: Color = Color.White) {
    // The pill is drawn over the video stage when there is one, and over the plain
    // backdrop otherwise — so its scrim and text follow whichever it is sitting on.
    val overVideo = onSurface == Color.White
    Row(
        modifier = Modifier
            .background(
                if (overVideo) Color.Black.copy(alpha = 0.32f) else NexusSurface,
                RoundedCornerShape(50),
            )
            .border(
                1.dp,
                if (overVideo) Color.White.copy(alpha = 0.12f) else NexusStroke,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).background(Color(0xFF2FB463), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(name, color = onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text("·", color = onSurface.copy(alpha = 0.5f), fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(subtitle, color = onSurface.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}

@Composable
private fun IncomingControls(video: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundActionButton(icon = Icons.Rounded.CallEnd, background = Color(0xFFE5484D), onClick = onDecline)
            Spacer(Modifier.height(8.dp))
            Text("Tolak", color = NexusTextSecondary, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundActionButton(
                icon = if (video) Icons.Rounded.Videocam else Icons.Rounded.Call,
                background = Color(0xFF2FB463),
                onClick = onAccept,
            )
            Spacer(Modifier.height(8.dp))
            Text("Terima", color = NexusTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CallControl(icon: ImageVector, description: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .background(
                if (active) NexusTextPrimary else NexusTextPrimary.copy(alpha = 0.14f),
                CircleShape,
            )
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) NexusBackground else NexusTextPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RoundActionButton(icon: ImageVector, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(background, CircleShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PulsingAvatar(photoUrl: String?, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "ring")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "scale",
    )
    Box(contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(modifier = Modifier.size((132 * scale).dp).background(NexusAccent.copy(alpha = 0.12f), CircleShape))
        }
        Box(
            contentAlignment = Alignment.Center,
        ) {
            // The shared avatar: real photo when there is one, Syntra's own empty-profile
            // mark when there isn't. A letter tile was never the fallback anywhere else
            // in the app — the call screen was the one place still doing it.
            GradientAvatar(
                gradient = callAvatarGradient,
                initial = "",
                size = 120.dp,
                photoUrl = photoUrl,
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
