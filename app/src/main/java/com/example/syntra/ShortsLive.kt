package com.example.syntra

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.delay

/**
 * LIVE — UI SCAFFOLD ONLY (no real streaming yet).
 *
 * This is the front-end shell for a future live feature: a browsable grid of
 * ongoing streams (the "Live" tab in the Shorts header) and the two entry points a
 * real feature needs — a viewer screen (tap a tile) and a go-live setup screen (the
 * "+" create sheet). Everything here runs on placeholder data ([LiveSamples]); the
 * actual broadcast/watch plumbing is a backend job (a LiveKit room per broadcaster,
 * a `/lives` listing, viewer tokens) that Rooms' audio stack can be extended into.
 *
 * Kept in its own file so the (already large) ShortsScreen only has to hold a couple
 * of flags and hand off rendering.
 */
data class LiveStream(
    val id: String,
    val host: String,
    val title: String,
    val viewers: Int,
    val category: String,
)

/** Placeholder streams shown until a real `/lives` endpoint exists. */
internal object LiveSamples {
    val streams = listOf(
        LiveStream("l1", "rani.mp", "Ngobrol santai malam mingguan ✨", 1240, "Obrolan"),
        LiveStream("l2", "dev.arya", "Live coding: bikin fitur Syntra", 320, "Teknologi"),
        LiveStream("l3", "chef.wulan", "Masak bareng: nasi goreng spesial", 2115, "Kuliner"),
        LiveStream("l4", "gilang.beats", "Sesi produksi musik lo-fi 🎧", 540, "Musik"),
        LiveStream("l5", "tania.art", "Menggambar digital dari nol", 178, "Seni"),
        LiveStream("l6", "fajar.games", "Push rank ditemani penonton", 3890, "Game"),
    )
}

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

/** The "Live" tab body: a 2-column grid of ongoing streams (placeholder data). */
@Composable
fun LiveGrid(onOpen: (LiveStream) -> Unit) {
    val streams = remember { LiveSamples.streams }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(NexusBackground),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 74.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(streams, key = { it.id }) { s -> LiveTile(s, onOpen = { onOpen(s) }) }
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
        // LIVE badge (top-left) + viewer count (top-right).
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
        // Host + title (bottom).
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

/**
 * Viewer screen opened by tapping a tile — a full-screen placeholder that lays out
 * the intended live UI (host bar, stage, comment input) but shows a "coming soon"
 * stage instead of a real video track.
 */
@Composable
fun LiveViewerScreen(stream: LiveStream, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(liveGradient(stream.id)))) {
        // Top: host + LIVE + close.
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
            ) {
                Text(stream.host.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("@${stream.host}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge()
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(compactViewers(stream.viewers), color = Color.White, fontSize = 12.sp)
                }
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Tutup", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }

        // Centre stage placeholder.
        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Videocam, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text("Siaran langsung segera hadir", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Ini pratinjau tampilan. Pemutaran siaran nyata menyusul saat backend live siap.",
                color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
            )
        }

        // Bottom mock comment bar (disabled).
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { Text("Tambahkan komentar…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Go-live flow opened from the "+" sheet. Two steps, both UI-only:
 *  1. SETUP — name the stream + a camera-preview placeholder + "Mulai siaran".
 *  2. BROADCAST — [LiveBroadcastScreen], the screen shown while actually live.
 * No camera or stream is opened yet; the broadcast step is a faithful shell.
 */
@Composable
fun GoLiveScreen(onClose: () -> Unit) {
    var broadcasting by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }

    if (broadcasting) {
        LiveBroadcastScreen(
            title = title.ifBlank { "Siaran langsung" },
            onEnd = onClose,
        )
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
                Text("Pratinjau kamera", color = Color.White, fontSize = 13.sp)
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
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE5484D))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { broadcasting = true }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mulai siaran", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Broadcast — the screen shown WHILE the user is live (UI scaffold)
// ---------------------------------------------------------------------------

private data class LiveComment(val id: Long, val user: String, val text: String)

/** Placeholder audience chatter that trickles in while "live". */
private val sampleChatter = listOf(
    "budi_92" to "halo bang! 👋",
    "sinta.ay" to "suaranya jernih bgt",
    "roni" to "dari Surabaya nyimak 🔥",
    "mega.w" to "first! ❤️",
    "yoga_p" to "mantap kontennya",
    "dewi" to "salam kenal semuaa",
    "arif.hd" to "gaskeun 🚀",
    "nabila" to "lucu bangett 😂",
    "topan" to "kualitas videonya bagus",
    "vina.s" to "izin share ya kak",
    "galih" to "hadir full sampai habis",
    "citra_a" to "request lagu dong 🎶",
)

/**
 * What the broadcaster sees while streaming: a camera-preview placeholder with the
 * live overlay — timer + viewer count up top, audience comments trickling up from
 * the bottom-left, tap-to-heart floating reactions, and the controls (mic, flip
 * camera, and a prominent End). Everything is simulated; no camera or network yet.
 */
@Composable
fun LiveBroadcastScreen(title: String, onEnd: () -> Unit) {
    var confirmEnd by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(true) }
    var frontCam by remember { mutableStateOf(true) }
    var viewers by remember { mutableStateOf(1) }
    var likes by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(0) }
    val comments = remember { mutableStateListOf<LiveComment>() }
    val hearts = remember { mutableStateListOf<Long>() }
    var nextId by remember { mutableStateOf(0L) }
    var draft by remember { mutableStateOf("") }

    fun sendComment() {
        val text = draft.trim()
        if (text.isEmpty()) return
        comments.add(LiveComment(nextId++, "Kamu", text))
        if (comments.size > 40) comments.removeAt(0)
        draft = ""
    }

    BackHandler { confirmEnd = true }

    // Elapsed timer.
    LaunchedEffect(Unit) {
        while (true) { delay(1000); seconds++ }
    }
    // Viewers climb (with a little jitter) as if people are joining.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            viewers = (viewers + (1..7).random()).coerceAtMost(99_999)
            if ((0..4).random() == 0 && viewers > 3) viewers -= (1..2).random()
        }
    }
    // Comments trickle in.
    LaunchedEffect(Unit) {
        while (true) {
            delay((1400..2600).random().toLong())
            val (u, t) = sampleChatter.random()
            comments.add(LiveComment(nextId++, u, t))
            if (comments.size > 40) comments.removeAt(0)
        }
    }

    fun spawnHeart() {
        likes++
        hearts.add(nextId++)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        // Camera-preview placeholder.
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(liveGradient(if (frontCam) "front" else "back"))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Videocam, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    if (frontCam) "Pratinjau kamera depan" else "Pratinjau kamera belakang",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                )
            }
        }

        // Floating hearts (bottom-right).
        hearts.forEach { id ->
            key(id) { FloatingHeart(onDone = { hearts.remove(id) }) }
        }

        // Top overlay: host + LIVE + timer, and viewer count + close.
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
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { confirmEnd = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Akhiri", tint = Color.White, modifier = Modifier.size(20.dp)) }
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
        }

        // Comments — newest at the bottom, trickling up above the control bar.
        LazyColumn(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.42f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 12.dp, bottom = 76.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(comments.asReversed(), key = { it.id }) { c -> LiveCommentRow(c) }
        }

        // Bottom control bar: editable comment box + mic + flip + heart. The whole bar
        // rises with the keyboard (union of nav-bar and IME insets: whichever is taller,
        // never both stacked). While typing, the send button replaces the three controls
        // so the field has room.
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Send appears only when there's something to send.
                if (draft.isNotBlank()) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5484D))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { sendComment() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
            // Mic / flip / heart hide while typing to give the field room.
            if (draft.isBlank()) {
                Spacer(Modifier.width(8.dp))
                LiveControlButton(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, if (micOn) "Bisukan" else "Nyalakan mik", active = !micOn) { micOn = !micOn }
                Spacer(Modifier.width(8.dp))
                LiveControlButton(Icons.Filled.Cameraswitch, "Balik kamera") { frontCam = !frontCam }
                Spacer(Modifier.width(8.dp))
                LiveControlButton(Icons.Filled.Favorite, "Suka", tint = Color(0xFFFF5D8F)) { spawnHeart() }
            }
        }
    }

    if (confirmEnd) {
        EndLiveDialog(
            viewers = viewers,
            likes = likes,
            duration = liveClock(seconds),
            onDismiss = { confirmEnd = false },
            onConfirm = onEnd,
        )
    }
}

private fun liveClock(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun LiveCommentRow(c: LiveComment) {
    // The host's own messages read as "you": accent name + a tinted bubble.
    val isHost = c.user == "Kamu"
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(liveGradient(c.user))),
            contentAlignment = Alignment.Center,
        ) { Text(c.user.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isHost) Color(0xFFE5484D).copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.32f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                c.user,
                color = if (isHost) Color(0xFFFF9DAE) else Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(c.text, color = Color.White, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
