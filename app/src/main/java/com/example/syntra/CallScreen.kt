package com.example.syntra

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.CallEngine
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Call screen — full-screen voice & video call UI, driven by CallEngine.
//
// The Syntra backend only carries call *state* (POST /calls, answer, leave) and
// mints the LiveKit sfu_token; the media itself rides the SFU. When the media
// server is not configured the token comes back blank — we say so plainly and
// close instead of pretending a call connected.
// ---------------------------------------------------------------------------

private enum class CallPhase { INCOMING, CONNECTING, RINGING, ONGOING, ENDED }

private val callBackdrop = listOf(Color(0xFF141726), Color(0xFF0B0C14))
private val callAvatarGradient = listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5))

@Composable
fun CallScreen(
    peerName: String,
    conversationId: String,
    video: Boolean,
    onClose: () -> Unit,
    incoming: Boolean = false,
    incomingCallId: String? = null,
    peerId: String = "",
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(if (incoming) CallPhase.INCOMING else CallPhase.CONNECTING) }
    var callId by remember { mutableStateOf(incomingCallId.orEmpty()) }
    var isVideo by remember { mutableStateOf(video) }
    var elapsed by remember { mutableIntStateOf(0) }
    var statusLine by remember { mutableStateOf(if (incoming) "Panggilan masuk" else "Memanggil…") }
    var everConnected by remember { mutableStateOf(false) }

    // The engine drives these; reading them here recomposes as the far side joins.
    val remoteJoined = CallEngine.remoteJoined
    val remoteVideo = CallEngine.remoteVideo
    val localVideo = CallEngine.localVideo

    // Log the call once the screen leaves the composition, with the truth of what
    // happened (answered / missed / how long it lasted).
    val elapsedLatest by rememberUpdatedState(elapsed)
    val connectedLatest by rememberUpdatedState(everConnected)
    val videoLatest by rememberUpdatedState(isVideo)

    // Actually place / answer the call once permissions are in hand.
    suspend fun connectNow() {
        if (!ApiConfig.ENABLED) {
            statusLine = "Server belum aktif"
            phase = CallPhase.ENDED
            return
        }
        runCatching {
            val call = if (incoming) {
                SyntraClient.answerCall(callId, conversationId)
            } else {
                SyntraClient.startCall(conversationId, if (isVideo) "video" else "audio")
                    .also { callId = it.callId }
            }
            if (call.sfuUrl.isBlank() || call.sfuToken.isBlank()) {
                error("Panggilan belum tersedia — server media belum dikonfigurasi.")
            }
            CallEngine.connect(context, call.sfuUrl, call.sfuToken, isVideo)
            phase = if (incoming) CallPhase.CONNECTING else CallPhase.RINGING
            statusLine = if (incoming) "Menyambungkan…" else "Memanggil…"
        }.onFailure {
            statusLine = it.message ?: "Panggilan gagal"
            Toast.makeText(context, statusLine, Toast.LENGTH_LONG).show()
            phase = CallPhase.ENDED
        }
    }

    // Permission gate: video needs camera + mic, audio needs mic.
    val permissions = if (video) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    var launchAfterPermission by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val micOk = result[Manifest.permission.RECORD_AUDIO] != false
        if (micOk) {
            launchAfterPermission = true
        } else {
            statusLine = "Izin mikrofon ditolak"
            Toast.makeText(context, statusLine, Toast.LENGTH_LONG).show()
            phase = CallPhase.ENDED
        }
    }

    LaunchedEffect(launchAfterPermission) {
        if (launchAfterPermission) {
            launchAfterPermission = false
            connectNow()
        }
    }

    // Outgoing calls dial immediately; incoming waits for the user to accept.
    LaunchedEffect(Unit) {
        if (!incoming) permLauncher.launch(permissions)
    }

    // Promote to "ongoing" the moment the other side is really in the room.
    LaunchedEffect(remoteJoined) {
        if (remoteJoined && phase != CallPhase.ONGOING && phase != CallPhase.ENDED) {
            phase = CallPhase.ONGOING
            everConnected = true
        }
    }

    // Call timer.
    LaunchedEffect(phase) {
        if (phase == CallPhase.ONGOING) {
            statusLine = ""
            while (true) {
                delay(1000)
                elapsed++
            }
        }
    }

    // Realtime call signalling: the far side answering, declining, or hanging up.
    DisposableEffect(Unit) {
        val listener = object : SocketListener {
            override fun onCallEnded(reason: String) {
                statusLine = if (reason == "declined") "Panggilan ditolak" else "Panggilan berakhir"
                phase = CallPhase.ENDED
            }
        }
        SyntraClient.addListener(listener)
        onDispose {
            SyntraClient.removeListener(listener)
            CallEngine.disconnect()
            // Record the attempt honestly.
            val direction = when {
                !incoming -> CallDirection.OUTGOING
                connectedLatest -> CallDirection.INCOMING
                else -> CallDirection.MISSED
            }
            CallLog.add(
                context,
                CallEntry(
                    id = "c-${System.currentTimeMillis()}",
                    peerName = peerName,
                    peerId = peerId,
                    video = videoLatest,
                    direction = direction,
                    at = System.currentTimeMillis(),
                    durationSec = elapsedLatest,
                ),
            )
        }
    }

    // When the call ends, linger briefly on the status then close.
    LaunchedEffect(phase) {
        if (phase == CallPhase.ENDED) {
            delay(1200)
            onClose()
        }
    }

    fun hangUp() {
        val id = callId
        if (id.isNotBlank() && ApiConfig.ENABLED) {
            // Fire-and-forget: leaving is best-effort, the UI closes regardless.
            SyntraClient.fireAndForget { SyntraClient.leaveCall(id, conversationId) }
        }
        onClose()
    }

    fun decline() {
        val id = callId
        if (id.isNotBlank() && ApiConfig.ENABLED) {
            SyntraClient.fireAndForget { SyntraClient.declineCall(id, conversationId) }
        }
        onClose()
    }

    BackHandler { hangUp() }

    val showVideoStage = isVideo && phase == CallPhase.ONGOING && remoteVideo != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(callBackdrop)),
    ) {
        // Remote video fills the screen once it arrives.
        if (showVideoStage) {
            remoteVideo?.let { track ->
                VideoRenderer(track = track, modifier = Modifier.fillMaxSize())
            }
            // Gentle top scrim so the name stays readable over bright video.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (showVideoStage) 20.dp else 64.dp))

            Text(
                text = peerName.ifBlank { "Tanpa nama" },
                color = NexusTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (phase == CallPhase.ONGOING && statusLine.isBlank()) {
                    formatDuration(elapsed)
                } else {
                    statusLine
                },
                color = NexusAccentSoft,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            if (!showVideoStage) {
                Spacer(Modifier.height(60.dp))
                PulsingAvatar(
                    initial = peerName.firstOrNull()?.uppercase() ?: "?",
                    pulsing = phase == CallPhase.RINGING || phase == CallPhase.INCOMING,
                )
            }

            Spacer(Modifier.weight(1f))

            when (phase) {
                CallPhase.INCOMING -> IncomingControls(
                    video = isVideo,
                    onAccept = { permLauncher.launch(permissions) },
                    onDecline = { decline() },
                )
                CallPhase.ENDED -> Spacer(Modifier.height(40.dp))
                else -> OngoingControls(
                    isVideo = isVideo,
                    onToggleMic = { CallEngine.fireMic() },
                    onToggleSpeaker = { CallEngine.setSpeaker(!CallEngine.speakerOn) },
                    onToggleCamera = { CallEngine.fireCamera(!CallEngine.cameraEnabled) },
                    onSwitchCamera = { CallEngine.switchCamera() },
                    onHangUp = { hangUp() },
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        // Self-preview picture-in-picture (video calls only).
        if (isVideo && CallEngine.cameraEnabled && localVideo != null && phase == CallPhase.ONGOING) {
            localVideo?.let { track ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp)
                        .size(width = 108.dp, height = 150.dp)
                        .background(Color.Black, RoundedCornerShape(16.dp)),
                ) {
                    VideoRenderer(
                        track = track,
                        mirror = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black, RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
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
    // Keyed on the track so a new track builds a fresh renderer instead of
    // leaking the old one into the new stream.
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallControl(
            icon = if (CallEngine.micEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
            label = if (CallEngine.micEnabled) "Bisu" else "Suara",
            active = !CallEngine.micEnabled,
            onClick = onToggleMic,
        )
        if (isVideo) {
            CallControl(
                icon = if (CallEngine.cameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                label = "Kamera",
                active = !CallEngine.cameraEnabled,
                onClick = onToggleCamera,
            )
            CallControl(
                icon = Icons.Filled.Cameraswitch,
                label = "Balik",
                onClick = onSwitchCamera,
            )
        } else {
            CallControl(
                icon = Icons.Filled.VolumeUp,
                label = "Speaker",
                active = CallEngine.speakerOn,
                onClick = onToggleSpeaker,
            )
        }
        EndCallButton(onClick = onHangUp)
    }
}

@Composable
private fun IncomingControls(
    video: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundActionButton(
                icon = Icons.Filled.CallEnd,
                background = Color(0xFFE5484D),
                onClick = onDecline,
            )
            Spacer(Modifier.height(8.dp))
            Text("Tolak", color = NexusTextSecondary, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundActionButton(
                icon = if (video) Icons.Filled.Videocam else Icons.Filled.Call,
                background = Color(0xFF2FB463),
                onClick = onAccept,
            )
            Spacer(Modifier.height(8.dp))
            Text("Terima", color = NexusTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CallControl(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(
                    if (active) Color.White else Color.White.copy(alpha = 0.14f),
                    CircleShape,
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF141726) else Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = NexusTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun EndCallButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundActionButton(
            icon = Icons.Filled.CallEnd,
            background = Color(0xFFE5484D),
            onClick = onClick,
        )
        Spacer(Modifier.height(6.dp))
        Text("Akhiri", color = NexusTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun RoundActionButton(
    icon: ImageVector,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(background, CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
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
            Box(
                modifier = Modifier
                    .size((132 * scale).dp)
                    .background(NexusAccent.copy(alpha = 0.12f), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Brush.verticalGradient(callAvatarGradient), CircleShape),
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
