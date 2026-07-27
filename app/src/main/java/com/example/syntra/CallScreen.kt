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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.CallEngine
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusOnline
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

    val isBusy: Boolean get() = call != null

    fun startOutgoing(conversationId: String, peerName: String, peerId: String, video: Boolean) {
        if (call != null) return
        call = CallDescriptor(conversationId, peerName, peerId, video, incoming = false, incomingCallId = null)
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
    ) {
        if (call != null) return
        call = CallDescriptor(conversationId, peerName, peerId, video, incoming = true, incomingCallId = callId)
        minimized = false
        compact = asBanner
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

private val callBackdrop = listOf(Color(0xFF141726), Color(0xFF0B0C14))
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
        if (micOk && camOk) callScope.launch { connectNow() }
        else permLauncher.launch(permissions)
    }

    // Outgoing dials immediately; incoming waits for the user to accept.
    LaunchedEffect(Unit) { if (!incoming) proceed() }

    // Promote to "ongoing" the moment the other side is really in the room.
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
        while (true) {
            delay(3000)
            if (phase == CallPhase.ONGOING || phase == CallPhase.ENDED) break
            // Distinguish "call really ended" (a successful response of null) from a
            // transient network error — only the former should end the screen, or a
            // glitchy poll would kill a perfectly good ringing call.
            val result = runCatching { SyntraClient.getActiveCall(d.conversationId) }
            if (result.isSuccess && result.getOrNull() == null) {
                statusLine = "Panggilan berakhir"
                phase = CallPhase.ENDED
                break
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

    if (CallController.minimized) {
        MiniCallWindow(
            peerName = d.peerName,
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

    val showVideoStage = isVideo && phase == CallPhase.ONGOING && remoteVideo != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(callBackdrop)),
    ) {
        if (showVideoStage) {
            remoteVideo?.let { track -> VideoRenderer(track = track, modifier = Modifier.fillMaxSize()) }
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
                Icon(Icons.Rounded.KeyboardArrowDown, "Perkecil", tint = Color.White, modifier = Modifier.size(24.dp))
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
                CallHeaderPill(name = peerName.ifBlank { "Tanpa nama" }, subtitle = formatDuration(elapsed))
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
                    initial = peerName.firstOrNull()?.uppercase() ?: "?",
                    // Pulse while ringing, waiting to be answered, AND while connecting
                    // media — so "Menyambungkan…" reads as active loading, not frozen.
                    pulsing = phase == CallPhase.RINGING || phase == CallPhase.INCOMING ||
                        phase == CallPhase.CONNECTING,
                )
            }

            Spacer(Modifier.weight(1f))

            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
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
        if (isVideo && CallEngine.cameraEnabled && localVideo != null && phase == CallPhase.ONGOING) {
            localVideo?.let { track ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp)
                        .size(width = 112.dp, height = 156.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp)),
                ) {
                    VideoRenderer(track = track, mirror = true, modifier = Modifier.fillMaxSize())
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
    val winW = if (isVideo) 128.dp else 220.dp
    val winH = if (isVideo) 180.dp else 76.dp

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
                .background(Color(0xFF141726))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
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
            if (isVideo && remoteVideo != null) {
                VideoRenderer(track = remoteVideo, modifier = Modifier.fillMaxSize())
                // Bottom bar with timer + hang up over the video.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(statusLine, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    MiniHangUp(onHangUp)
                }
            } else {
                // Audio (or video not yet flowing): avatar + name + timer + hang up.
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Brush.verticalGradient(callAvatarGradient), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            peerName.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            peerName.ifBlank { "Tanpa nama" },
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(statusLine, color = NexusAccentSoft, fontSize = 12.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    MiniHangUp(onHangUp)
                }
            }
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

@Composable
private fun OngoingControls(
    isVideo: Boolean,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangUp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(44.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(44.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
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
                .size(58.dp)
                .background(Color(0xFFE5484D), CircleShape)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onHangUp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CallEnd, "Akhiri", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun CallHeaderPill(name: String, subtitle: String) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).background(Color(0xFF2FB463), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text("·", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
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
            .background(if (active) Color.White else Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) Color(0xFF141726) else Color.White,
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
private fun PulsingAvatar(initial: String, pulsing: Boolean) {
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
            modifier = Modifier.size(120.dp).background(Brush.verticalGradient(callAvatarGradient), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
