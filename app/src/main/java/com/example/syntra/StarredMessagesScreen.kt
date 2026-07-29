package com.example.syntra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetStarredMessage
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.launch

/**
 * Every message you have starred, across all conversations.
 *
 * Starring without this screen would be the worse half of a feature: you could mark
 * messages and then have no way to ever find them again. The three-dot menu has
 * offered "Starred messages" as a placeholder toast since the beginning.
 */
@Composable
fun StarredMessagesScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<NetStarredMessage>() }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    suspend fun load() {
        if (!ApiConfig.ENABLED) { loading = false; return }
        runCatching { SyntraClient.getStarredMessages() }
            .onSuccess { fresh -> items.clear(); items.addAll(fresh); failed = false }
            .onFailure { failed = items.isEmpty() }
        loading = false
    }

    LaunchedEffect(Unit) { load() }
    androidx.activity.compose.BackHandler(onBack = onClose)

    fun unstar(m: NetStarredMessage) {
        // Removing it here is the whole interaction on this screen, so it goes at
        // once and comes back if the server refuses.
        val at = items.indexOfFirst { it.id == m.id }
        if (at < 0) return
        items.removeAt(at)
        scope.launch {
            runCatching { SyntraClient.starMessage(m.id, false) }
                .onFailure { items.add(at.coerceAtMost(items.size), m) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Kembali",
                    tint = NexusTextPrimary, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("Pesan berbintang", color = NexusTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }

                failed -> StarredEmpty("Gagal memuat.\nTarik ke bawah untuk mencoba lagi.")

                items.isEmpty() -> StarredEmpty(
                    "Belum ada pesan berbintang.\nTekan lama sebuah pesan lalu pilih \"Tandai berbintang\".",
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { m ->
                        StarredRow(m = m, onUnstar = { unstar(m) })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StarredEmpty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.StarBorder, null,
                tint = NexusTextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StarredRow(m: NetStarredMessage, onUnstar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NexusSurfaceElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                m.body.ifBlank {
                    when (m.type) {
                        "image" -> "[Foto]"
                        "gif" -> "[GIF]"
                        "video" -> "[Video]"
                        "audio" -> "[Suara]"
                        else -> "[Lampiran]"
                    }
                },
                color = NexusTextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (m.createdAt.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(starredDate(m.createdAt), color = NexusTextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Filled.Star, "Hapus dari berbintang",
            tint = Color(0xFFFFC542),
            modifier = Modifier
                .size(20.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onUnstar,
                ),
        )
    }
}

/** "24 Jul 2026 · 09:00" — an absolute date, because a starred message is an archive. */
private fun starredDate(iso: String): String {
    val t = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return ""
    val local = java.time.LocalDateTime.ofInstant(t, java.time.ZoneId.systemDefault())
    val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    return "%d %s %d · %02d:%02d".format(
        local.dayOfMonth, months[local.monthValue - 1], local.year, local.hour, local.minute,
    )
}
