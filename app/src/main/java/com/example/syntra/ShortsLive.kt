package com.example.syntra

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.syntra.net.LiveEngine
import com.example.syntra.net.NetLive
import com.example.syntra.net.NetLiveGift
import com.example.syntra.net.NetLiveMessage
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import java.util.concurrent.atomic.AtomicLong
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * LIVE — now wired to the backend.
 *
 * A live is one host publishing camera video to many viewers over LiveKit (the same
 * SFU voice rooms use). The backend `/lives` endpoints mint the sfu_token; video flows
 * host↔SFU↔viewer and never through Syntra. [LiveEngine] drives the media session.
 *
 * Real: the browsable grid ([LiveGrid] ← GET /lives), going live ([GoLiveScreen] →
 * POST /lives + publish camera), watching ([LiveViewerScreen] → join + subscribe), and
 * the viewer count. Still local scaffold (a future backend job): the ephemeral comments,
 * the coin-powered GIF gifts, and floating hearts.
 */
data class LiveStream(
    val id: String,
    val host: String,
    val title: String,
    val viewers: Int,
    val category: String,
    val hostId: String = "",
    val avatarUrl: String? = null,
)

/** Map a backend live onto the UI model the Live screens already speak. */
internal fun NetLive.toStream() = LiveStream(
    id = id,
    host = hostUsername.ifBlank { hostName },
    title = title,
    viewers = viewerCount,
    category = category,
    hostId = hostId,
    avatarUrl = hostAvatarUrl,
)

/** A stable, id-derived gradient so a stream tile always looks the same. */
private fun liveGradient(seed: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF6D5BFF), Color(0xFF9A4DFF)),
        listOf(Color(0xFFFF5D8F), Color(0xFFFF8A5D)),
        listOf(Color(0xFF19B3A6), Color(0xFF2E7BFF)),
        listOf(Color(0xFFFF6A6A), Color(0xFFB14DFF)),
        listOf(Color(0xFF2E9BFF), Color(0xFF5BE0C6)),
        listOf(Color(0xFFF7A93B), Color(0xFFFF5D5D)),
    )
    return palettes[(seed.hashCode() and Int.MAX_VALUE) % palettes.size]
}

private fun compactViewers(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> String.format(java.util.Locale.US, "%.1f", n / 1000f).removeSuffix(".0").replace('.', ',') + "rb"
    else -> String.format(java.util.Locale.US, "%.1f", n / 1_000_000f).removeSuffix(".0").replace('.', ',') + "jt"
}

/** The "Live" tab body: a 2-column grid of the streams that are actually live now. */
@Composable
fun LiveGrid(onOpen: (LiveStream) -> Unit) {
    var streams by remember { mutableStateOf<List<LiveStream>?>(null) }

    // Load on open and refresh every 10s so newly-started (and ended) streams appear
    // without the user having to leave and come back.
    LaunchedEffect(Unit) {
        while (true) {
            val fresh = runCatching { SyntraClient.getLives().lives.map { it.toStream() } }.getOrNull()
            if (fresh != null) streams = fresh
            delay(10_000)
        }
    }

    val list = streams
    when {
        list == null -> Box(
            Modifier.fillMaxSize().background(NexusBackground),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = NexusAccent) }

        list.isEmpty() -> Column(
            modifier = Modifier.fillMaxSize().background(NexusBackground).padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Videocam, null, tint = NexusTextSecondary, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(14.dp))
            Text("Belum ada yang siaran", color = NexusTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Mulai siaranmu sendiri lewat tombol +", color = NexusTextSecondary, fontSize = 13.sp)
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().background(NexusBackground),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 74.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { s -> LiveTile(s, onOpen = { onOpen(s) }) }
        }
    }
}

@Composable
private fun LiveTile(stream: LiveStream, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(liveGradient(stream.id)))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveBadge()
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(compactViewers(stream.viewers), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .padding(10.dp),
        ) {
            Text(
                stream.title,
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text("@${stream.host} · ${stream.category}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE5484D))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(9.dp))
        Spacer(Modifier.width(4.dp))
        Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Renders a LiveKit [VideoTrack] full-bleed — shared by the host self-preview and
 *  the viewer stage. Keyed on the track so a new publication swaps renderers cleanly. */
@Composable
private fun LiveVideoRenderer(track: VideoTrack, modifier: Modifier = Modifier, mirror: Boolean = false) {
    key(track) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextureViewRenderer(ctx).apply {
                    LiveEngine.initRenderer(this)
                    setMirror(mirror)
                    runCatching { setEnableHardwareScaler(true) }
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
// Viewer — watching someone else's live
// ---------------------------------------------------------------------------

/**
 * Viewer screen opened by tapping a tile. Joins the live on the backend, connects to
 * LiveKit as a silent subscriber, and renders the host's camera track. Comments and
 * GIF gifts are still local scaffold; the video and viewer count are real.
 */
@Composable
fun LiveViewerScreen(stream: LiveStream, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewers by remember { mutableStateOf(stream.viewers) }

    fun leave() {
        scope.launch { runCatching { SyntraClient.leaveLive(stream.id) } }
        LiveEngine.disconnect()
        onClose()
    }
    BackHandler(onBack = { leave() })

    // Join → connect as viewer. Then poll: viewer count updates, and an empty result
    // (404) means the host ended it, so we close.
    LaunchedEffect(stream.id) {
        val join = runCatching { SyntraClient.joinLive(stream.id) }.getOrNull()
        if (join != null) {
            // Subscribe only AFTER join records us in live_viewers, or the topic
            // authorization (IsLiveViewer) would deny it.
            SyntraClient.subscribe(listOf("live:${stream.id}"))
            if (join.sfuToken.isNotBlank()) {
                runCatching { LiveEngine.connect(context, join.sfuUrl, join.sfuToken, asHost = false) }
            }
        }
        while (true) {
            delay(5_000)
            val live = runCatching { SyntraClient.getLive(stream.id) }
            live.onSuccess { viewers = it.viewerCount }
                .onFailure { leave(); return@LaunchedEffect }
        }
    }
    DisposableEffect(Unit) { onDispose { LiveEngine.disconnect() } }

    ViewerBody(stream = stream, viewers = viewers, onClose = { leave() })
}

@Composable
private fun ViewerBody(stream: LiveStream, viewers: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val floatingGifts = remember { mutableStateListOf<FloatingGift>() }
    val giftIds = remember { AtomicLong(0L) }
    var showGiftPicker by remember { mutableStateOf(false) }
    var gifts by remember { mutableStateOf(liveGifts) }
    // Load the wallet balance + gift catalog from the backend.
    LaunchedEffect(Unit) {
        runCatching { SyntraClient.getWallet() }.onSuccess { LiveCoins.balance = it }
        runCatching { SyntraClient.getGifts() }.onSuccess { list -> if (list.isNotEmpty()) gifts = list.map { it.toLiveGift() } }
    }
    // Spend coins to send a gift — the SERVER is authoritative (deducts atomically and
    // broadcasts). The gift floats when the live.gift echo arrives, so we don't float
    // optimistically here.
    fun sendGift(gift: LiveGift) {
        scope.launch {
            runCatching { SyntraClient.sendGift(stream.id, gift.id) }
                .onSuccess { LiveCoins.balance = it.balance; showGiftPicker = false }
                .onFailure {
                    val msg = if (it is com.example.syntra.net.ApiException && it.code == "insufficient_coins") "Koin tidak cukup" else "Gagal mengirim GIF"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Realtime comments over WebSocket: a typeable field + list. Sending goes out as a
    // live.comment; everyone subscribed to live:<id> receives it back as live.message.
    val comments = remember { mutableStateListOf<LiveComment>() }
    var draft by remember { mutableStateOf("") }
    val commentIds = remember { AtomicLong(1_000_000L) }
    val commentListState = rememberLazyListState()
    val commentFocus = remember { FocusRequester() }
    fun sendComment() {
        val text = draft.trim()
        if (text.isEmpty()) return
        comments.add(LiveComment(commentIds.incrementAndGet(), "Kamu", text))
        if (comments.size > 40) comments.removeAt(0)
        SyntraClient.liveComment(stream.id, text)
        draft = ""
    }
    LaunchedEffect(comments.lastOrNull()?.id) {
        if (comments.isNotEmpty()) runCatching { commentListState.animateScrollToItem(0) }
    }
    // Append incoming comments (skipping our own echo — added optimistically). The
    // subscribe itself happens in LiveViewerScreen, after join records us as a viewer.
    DisposableEffect(stream.id) {
        val listener = object : SocketListener {
            override fun onLiveMessage(message: NetLiveMessage) {
                if (message.liveId != stream.id || message.senderId == SyntraClient.myUserId) return
                comments.add(LiveComment(commentIds.incrementAndGet(), message.senderUsername.ifBlank { "penonton" }, message.body))
                if (comments.size > 40) comments.removeAt(0)
            }
            // Float every gift, including our own (TikTok shows your own gift too).
            override fun onLiveGift(gift: NetLiveGift) {
                if (gift.liveId != stream.id) return
                floatingGifts.add(FloatingGift(giftIds.incrementAndGet(), gift.emoji))
            }
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        // Real host video (or a "connecting" state until they publish).
        val remote = LiveEngine.remoteVideo
        if (remote != null) {
            LiveVideoRenderer(remote, Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(liveGradient(stream.id))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("Menghubungkan ke siaran…", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // Floating GIF gifts the viewer has sent.
        floatingGifts.forEach { g ->
            key(g.id) { FloatingGiftView(emoji = g.emoji, onDone = { floatingGifts.remove(g) }) }
        }

        // Top: host + LIVE + viewers + close.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Text(stream.host.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("@${stream.host}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge()
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(compactViewers(viewers), color = Color.White, fontSize = 12.sp)
                }
            }
            Box(
                Modifier
                    .size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Tutup", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }

        // Comments — newest at the bottom, trickling up above the input bar.
        LazyColumn(
            state = commentListState,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.4f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 12.dp, bottom = 76.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(comments.asReversed(), key = { it.id }) { c -> LiveCommentRow(c) }
        }

        // Bottom bar: editable comment field + coin-powered GIF sender. Rises with the
        // keyboard; the GIF button hides while typing to give the field room.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 7.dp)) {
                    if (draft.isEmpty()) {
                        Text("Tambahkan komentar…", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(200) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendComment() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(commentFocus),
                    )
                }
                if (draft.isNotBlank()) {
                    Box(
                        Modifier
                            .size(34.dp).clip(CircleShape).background(Color(0xFFE5484D))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { sendComment() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
            if (draft.isBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(46.dp).clip(CircleShape).background(Color(0xFFFFC24D))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showGiftPicker = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CardGiftcard, "Kirim GIF", tint = Color(0xFF141726), modifier = Modifier.size(24.dp)) }
            }
        }
    }

    if (showGiftPicker) {
        GiftPickerSheet(
            balance = LiveCoins.balance,
            gifts = gifts,
            onSend = { gift -> sendGift(gift) },
            onTopUp = { scope.launch { runCatching { SyntraClient.topUpWallet(100) }.onSuccess { LiveCoins.balance = it } } },
            onDismiss = { showGiftPicker = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Go live — setup then broadcast
// ---------------------------------------------------------------------------

/**
 * Go-live flow opened from the "+" sheet:
 *  1. SETUP — name the stream + "Mulai siaran".
 *  2. BROADCAST — creates the live on the backend, publishes the camera over LiveKit,
 *     then shows [LiveBroadcastScreen].
 * Needs CAMERA + RECORD_AUDIO at runtime; asks before starting.
 */
@Composable
fun GoLiveScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var liveId by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun startBroadcast() {
        if (starting) return
        starting = true
        error = null
        scope.launch {
            val result = runCatching {
                val (live, join) = SyntraClient.createLive(title.ifBlank { "Siaran langsung" })
                val j = join ?: throw IllegalStateException("Media server belum siap")
                if (j.sfuToken.isBlank()) throw IllegalStateException("Media server belum dikonfigurasi")
                LiveEngine.connect(context, j.sfuUrl, j.sfuToken, asHost = true)
                live.id
            }
            starting = false
            result.onSuccess { liveId = it }
                .onFailure { error = it.message ?: "Gagal memulai siaran" }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val cam = grants[Manifest.permission.CAMERA] ?: false
        val mic = grants[Manifest.permission.RECORD_AUDIO] ?: false
        if (cam && mic) startBroadcast() else error = "Butuh izin kamera & mikrofon untuk siaran"
    }

    fun onStartClicked() {
        val camOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (camOk && micOk) startBroadcast()
        else permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    liveId?.let { id ->
        LiveBroadcastScreen(liveId = id, title = title.ifBlank { "Siaran langsung" }, onEnd = onClose)
        return
    }

    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = NexusTextPrimary,
                modifier = Modifier.size(26.dp).clickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClose,
                ),
            )
            Spacer(Modifier.width(12.dp))
            Text("Mulai Live", color = NexusTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(liveGradient("golive"))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Kamera menyala saat kamu mulai siaran", color = Color.White, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Judul siaran", color = NexusTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NexusSurface)
                .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            if (title.isEmpty()) Text("Beri judul siaranmu…", color = NexusTextSecondary, fontSize = 14.sp)
            BasicTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                singleLine = true,
                textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(NexusTextPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE5484D))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onStartClicked() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (starting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mulai siaran", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Broadcast — the screen shown WHILE the user is live
// ---------------------------------------------------------------------------

private data class LiveComment(val id: Long, val user: String, val text: String)

/**
 * A GIF/gift the audience can send. [emoji] stands in for the real animated GIF until
 * a GIF source (GIPHY/backend) is wired; [cost] is the price in coins.
 */
private data class LiveGift(val id: String, val emoji: String, val name: String, val cost: Int)

/** Map a backend gift onto the picker model. [id] is the server uuid used to send. */
private fun com.example.syntra.net.NetGift.toLiveGift() =
    LiveGift(id = id, emoji = emoji, name = name, cost = cost)

/** Fallback catalog shown only if GET /gifts fails; ids are codes so sending would fail. */
private val liveGifts = listOf(
    LiveGift("rose", "🌹", "Mawar", 1),
    LiveGift("heart", "💖", "Hati", 5),
    LiveGift("clap", "👏", "Tepuk", 8),
    LiveGift("fire", "🔥", "Api", 12),
    LiveGift("party", "🎉", "Pesta", 20),
    LiveGift("crown", "👑", "Mahkota", 50),
    LiveGift("unicorn", "🦄", "Unicorn", 99),
    LiveGift("rocket", "🚀", "Roket", 199),
    LiveGift("diamond", "💎", "Berlian", 500),
)

/**
 * The signed-in user's coin wallet — a process-wide placeholder until the backend owns
 * the balance (top-ups, spend history). Sending a GIF spends from here.
 */
internal object LiveCoins {
    var balance by mutableStateOf(120)
}

/** One in-flight floating GIF, animating up over the stream. */
private data class FloatingGift(val id: Long, val emoji: String)

/** A transient "X sent a GIF" banner shown just above the comments for ~2s. */
private data class GiftAlert(val id: Long, val user: String, val emoji: String, val name: String)

/**
 * What the broadcaster sees while streaming: the real camera preview (LiveKit) with the
 * live overlay — timer + real viewer count up top, audience comments trickling up from
 * the bottom-left, floating hearts + incoming GIF gifts, pause, and controls. The video
 * and viewer count are real; comments, hearts, and incoming gifts are local scaffold.
 */
@Composable
fun LiveBroadcastScreen(liveId: String, title: String, onEnd: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var confirmEnd by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var frontCam by remember { mutableStateOf(true) }
    var viewers by remember { mutableStateOf(0) }
    var likes by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(0) }
    val comments = remember { mutableStateListOf<LiveComment>() }
    val hearts = remember { mutableStateListOf<Long>() }
    val floatingGifts = remember { mutableStateListOf<FloatingGift>() }
    var giftAlert by remember { mutableStateOf<GiftAlert?>(null) }
    var nextId by remember { mutableStateOf(0L) }
    val commentIds = remember { AtomicLong(1_000_000L) }
    val giftIds = remember { AtomicLong(500_000L) }
    var draft by remember { mutableStateOf("") }
    var actionFor by remember { mutableStateOf<LiveComment?>(null) }
    var pinned by remember { mutableStateOf<LiveComment?>(null) }
    val commentFocus = remember { FocusRequester() }
    var focusTick by remember { mutableStateOf(0) }
    LaunchedEffect(focusTick) { if (focusTick > 0) runCatching { commentFocus.requestFocus() } }
    val commentListState = rememberLazyListState()
    // Auto-scroll to the newest comment. Keyed on the newest id (not size) so it keeps
    // working after the list caps at 40 and the size stops changing.
    LaunchedEffect(comments.lastOrNull()?.id) {
        if (comments.isNotEmpty()) runCatching { commentListState.animateScrollToItem(0) }
    }
    // Gift banner lives ~2 seconds, then disappears.
    LaunchedEffect(giftAlert?.id) {
        if (giftAlert != null) { delay(2000); giftAlert = null }
    }

    // End the broadcast for real: tell the backend, drop the media session, leave.
    fun endBroadcast() {
        scope.launch { runCatching { SyntraClient.endLive(liveId) } }
        LiveEngine.disconnect()
        onEnd()
    }

    fun sendComment() {
        val text = draft.trim()
        if (text.isEmpty()) return
        comments.add(LiveComment(commentIds.incrementAndGet(), "Kamu", text))
        if (comments.size > 40) comments.removeAt(0)
        SyntraClient.liveComment(liveId, text)
        draft = ""
    }
    fun replyTo(c: LiveComment) {
        actionFor = null
        if (!draft.trimStart().startsWith("@${c.user}")) draft = "@${c.user} "
        focusTick++
    }
    fun togglePin(c: LiveComment) {
        pinned = if (pinned?.id == c.id) null else c
        actionFor = null
    }

    BackHandler { confirmEnd = true }

    // Elapsed timer. Frozen while paused.
    LaunchedEffect(Unit) {
        while (true) { delay(1000); if (!paused) seconds++ }
    }
    // Real viewer count — poll the backend.
    LaunchedEffect(liveId) {
        while (true) {
            delay(5000)
            runCatching { SyntraClient.getLive(liveId) }.onSuccess { viewers = it.viewerCount }
        }
    }
    // Real comments over WebSocket: subscribe to the live channel and append incoming
    // viewer comments (skipping our own echo — added optimistically in sendComment).
    DisposableEffect(liveId) {
        SyntraClient.subscribe(listOf("live:$liveId"))
        val listener = object : SocketListener {
            override fun onLiveMessage(message: NetLiveMessage) {
                if (message.liveId != liveId || message.senderId == SyntraClient.myUserId) return
                comments.add(LiveComment(commentIds.incrementAndGet(), message.senderUsername.ifBlank { "penonton" }, message.body))
                if (comments.size > 40) comments.removeAt(0)
            }
            // A viewer sent a real GIF gift: float it and show the banner above comments.
            override fun onLiveGift(gift: NetLiveGift) {
                if (gift.liveId != liveId) return
                floatingGifts.add(FloatingGift(giftIds.incrementAndGet(), gift.emoji))
                giftAlert = GiftAlert(giftIds.incrementAndGet(), gift.senderUsername.ifBlank { "penonton" }, gift.emoji, gift.name)
            }
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    fun spawnHeart() {
        likes++
        hearts.add(nextId++)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        // Real camera preview via LiveKit. [frontCam] mirrors the self-view; the flip
        // button drives [LiveEngine.switchCamera].
        val local = LiveEngine.localVideo
        if (local != null) {
            LiveVideoRenderer(local, Modifier.fillMaxSize(), mirror = frontCam)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Floating hearts (bottom-right).
        hearts.forEach { id ->
            key(id) { FloatingHeart(onDone = { hearts.remove(id) }) }
        }
        // Floating GIF gifts drifting up the centre.
        floatingGifts.forEach { g ->
            key(g.id) { FloatingGiftView(emoji = g.emoji, onDone = { floatingGifts.remove(g) }) }
        }

        // Top overlay: host + LIVE + timer, viewer count, and pause.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("A", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Kamu", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(liveClock(seconds), color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    LiveBadge()
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(compactViewers(viewers), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { paused = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Pause, "Jeda", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            pinned?.let { p ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, Color(0xFFFFC24D).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { pinned = null }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PushPin, null, tint = Color(0xFFFFC24D), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("@${p.user} · disematkan", color = Color(0xFFFFC24D), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(p.text, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Close, "Lepas sematan", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
        }

        // Comments (newest at the bottom) with the incoming-GIF banner pinned just
        // above them — like TikTok's gift alerts.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 12.dp, bottom = 76.dp),
        ) {
            giftAlert?.let { g ->
                GiftAlertBanner(g)
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn(
                state = commentListState,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.42f),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(comments.asReversed(), key = { it.id }) { c ->
                    LiveCommentRow(
                        c,
                        isPinned = pinned?.id == c.id,
                        onClick = if (c.user == "Kamu") null else { { actionFor = c } },
                    )
                }
            }
        }

        // Bottom control bar: editable comment box + mic + flip + heart. While typing,
        // the send button replaces the three controls so the field has room.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 7.dp)) {
                    if (draft.isEmpty()) {
                        Text("Tambahkan komentar…", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(200) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendComment() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(commentFocus),
                    )
                }
                if (draft.isNotBlank()) {
                    Box(
                        Modifier
                            .size(34.dp).clip(CircleShape).background(Color(0xFFE5484D))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { sendComment() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
            if (draft.isBlank()) {
                val micOn = LiveEngine.micEnabled
                Spacer(Modifier.width(8.dp))
                LiveControlButton(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, if (micOn) "Bisukan" else "Nyalakan mik", active = !micOn) { LiveEngine.fireMic() }
                Spacer(Modifier.width(8.dp))
                LiveControlButton(Icons.Filled.Cameraswitch, "Balik kamera") { frontCam = !frontCam; LiveEngine.switchCamera() }
                Spacer(Modifier.width(8.dp))
                LiveControlButton(Icons.Filled.Favorite, "Suka", tint = Color(0xFFFF5D8F)) { spawnHeart() }
            }
        }

        // Paused overlay — covers the stream (counters & comments frozen while up).
        if (paused) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
                Spacer(Modifier.height(16.dp))
                Text("Siaran dijeda", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Penonton melihat layar jeda. Lanjutkan kapan saja.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { paused = false }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF141726), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Lanjutkan siaran", color = Color(0xFF141726), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { endBroadcast() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Hentikan siaran", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    actionFor?.let { c ->
        CommentActionSheet(
            comment = c,
            isPinned = pinned?.id == c.id,
            onReply = { replyTo(c) },
            onTogglePin = { togglePin(c) },
            onDismiss = { actionFor = null },
        )
    }

    if (confirmEnd) {
        EndLiveDialog(
            viewers = viewers,
            likes = likes,
            duration = liveClock(seconds),
            onDismiss = { confirmEnd = false },
            onConfirm = { endBroadcast() },
        )
    }
}

private fun liveClock(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun LiveCommentRow(c: LiveComment, isPinned: Boolean = false, onClick: (() -> Unit)? = null) {
    val isHost = c.user == "Kamu"
    val bubble = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(if (isHost) Color(0xFFE5484D).copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.32f))
        .then(
            if (onClick != null) {
                Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            } else Modifier,
        )
        .padding(horizontal = 10.dp, vertical = 6.dp)
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(liveGradient(c.user))),
            contentAlignment = Alignment.Center,
        ) { Text(c.user.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Column(bubble) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.user,
                    color = if (isHost) Color(0xFFFF9DAE) else Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                )
                if (isPinned) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Filled.PushPin, "Disematkan", tint = Color(0xFFFFC24D), modifier = Modifier.size(11.dp))
                }
            }
            Text(c.text, color = Color.White, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** TikTok-style "@user sent a GIF" banner, shown just above the comment stream. */
@Composable
private fun GiftAlertBanner(g: GiftAlert) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF141726).copy(alpha = 0.9f))
            .border(1.dp, Color(0xFFFFC24D).copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(liveGradient(g.user))),
            contentAlignment = Alignment.Center,
        ) { Text(g.user.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Text("@${g.user}", color = Color(0xFFFFC24D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(5.dp))
        Text("mengirim", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(g.emoji, fontSize = 18.sp)
    }
}

/** Balas / Sematkan sheet for a tapped audience comment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentActionSheet(
    comment: LiveComment,
    isPinned: Boolean,
    onReply: () -> Unit,
    onTogglePin: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NexusSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(liveGradient(comment.user))),
                    contentAlignment = Alignment.Center,
                ) { Text(comment.user.take(1).uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("@${comment.user}", color = NexusTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(comment.text, color = NexusTextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(6.dp))
            CreateRow(Icons.AutoMirrored.Filled.Reply, "Balas", "Balas @${comment.user}") { onReply() }
            CreateRow(
                Icons.Filled.PushPin,
                if (isPinned) "Lepas sematan" else "Sematkan komentar",
                if (isPinned) "Berhenti menampilkan di atas" else "Tampilkan di atas untuk semua penonton",
            ) { onTogglePin() }
        }
    }
}

@Composable
private fun LiveControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFFE5484D) else Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (active) Color.White else tint, modifier = Modifier.size(22.dp)) }
}

/** A single heart that rises and fades, then removes itself. */
@Composable
private fun FloatingHeart(onDone: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val drift = remember { (-1f..1f).random() }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, animationSpec = tween(2200))
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        Icon(
            Icons.Filled.Favorite,
            null,
            tint = Color(0xFFFF5D8F).copy(alpha = (1f - rise.value).coerceIn(0f, 1f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 90.dp)
                .size(28.dp)
                .graphicsLayer {
                    translationY = -rise.value * 320f
                    translationX = drift * 60f * rise.value
                    val sc = 0.7f + 0.5f * rise.value
                    scaleX = sc
                    scaleY = sc
                },
        )
    }
}

/** A GIF gift that floats up the centre of the stream, then removes itself. */
@Composable
private fun FloatingGiftView(emoji: String, onDone: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val drift = remember { (-1f..1f).random() }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, animationSpec = tween(2600))
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        Text(
            emoji,
            fontSize = 56.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
                .graphicsLayer {
                    translationY = -rise.value * 360f
                    translationX = drift * 80f * rise.value
                    val sc = 0.6f + 0.7f * rise.value
                    scaleX = sc
                    scaleY = sc
                    alpha = (1f - (rise.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                },
        )
    }
}

/**
 * GIF/gift picker — spend coins to send a GIF. Shows the wallet balance and an "Isi
 * koin" top-up (placeholder), then a grid of gifts; ones you can't afford are dimmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiftPickerSheet(
    balance: Int,
    gifts: List<LiveGift>,
    onSend: (LiveGift) -> Unit,
    onTopUp: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NexusSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Kirim GIF", color = NexusTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NexusBackground)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onTopUp)
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🪙", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(balance.toString(), color = NexusTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFFFC24D)),
                        contentAlignment = Alignment.Center,
                    ) { Text("+", color = Color(0xFF141726), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Text(
                "Butuh koin untuk mengirim GIF. Ketuk 🪙 untuk isi ulang.",
                color = NexusTextSecondary, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(320.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(gifts, key = { it.id }) { gift ->
                    val affordable = balance >= gift.cost
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(NexusBackground)
                            .then(if (affordable) Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSend(gift) } else Modifier)
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(gift.emoji, fontSize = 30.sp, modifier = Modifier.graphicsLayer { alpha = if (affordable) 1f else 0.4f })
                        Spacer(Modifier.height(6.dp))
                        Text(gift.name, color = if (affordable) NexusTextPrimary else NexusTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🪙", fontSize = 10.sp)
                            Spacer(Modifier.width(3.dp))
                            Text(gift.cost.toString(), color = if (affordable) Color(0xFFFFC24D) else NexusTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun ClosedFloatingPointRange<Float>.random(): Float =
    start + (endInclusive - start) * Math.random().toFloat()

/** Confirm-then-summary sheet shown when ending the broadcast. */
@Composable
private fun EndLiveDialog(
    viewers: Int,
    likes: Int,
    duration: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(20.dp))
                .background(NexusSurface)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Akhiri siaran?", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Penonton akan diberi tahu siaran berakhir.", color = NexusTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusBackground)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LiveStat(duration, "Durasi")
                LiveStat(compactViewers(viewers), "Penonton")
                LiveStat(compactViewers(likes), "Suka")
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE5484D))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onConfirm)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Akhiri siaran", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Lanjut siaran", color = NexusTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun LiveStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = NexusTextSecondary, fontSize = 11.sp)
    }
}

/** The "+" create menu on the Shorts header: upload a video, or go live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsCreateSheet(onDismiss: () -> Unit, onUploadVideo: () -> Unit, onGoLive: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NexusSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            CreateRow(Icons.Outlined.FileUpload, "Unggah video", "Pilih klip dari galeri", onUploadVideo)
            CreateRow(Icons.Filled.Videocam, "Mulai Live", "Siarkan langsung ke pengikutmu", onGoLive)
        }
    }
}

@Composable
private fun CreateRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(NexusBackground),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = NexusTextPrimary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NexusTextSecondary, fontSize = 12.sp)
        }
    }
}
