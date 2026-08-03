package com.example.syntra

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary

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
            Icon(Icons.Filled.Send, "Kirim", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Go-live setup screen opened from the "+" sheet — a placeholder that shows the
 * intended pre-broadcast form (title + start) without actually opening a camera or
 * a stream.
 */
@Composable
fun GoLiveScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
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
        ) { Text("Beri judul siaranmu…", color = NexusTextSecondary, fontSize = 14.sp) }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2A2A33))
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Fitur menyiarkan segera hadir", color = NexusTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
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
