package com.example.syntra

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetNotification
import com.example.syntra.net.NotificationBadge
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.launch

/**
 * The notification inbox.
 *
 * The socket has always delivered `notification.new` while the app is open, but that
 * is a live signal, not a record — anything that arrived while you were away simply
 * never existed as far as the app was concerned. Someone could reply to your comment
 * or tag you in a reel and you would have no way to find out. This is the history.
 *
 * Opening the screen marks everything read, because the badge exists to say "there is
 * something you have not looked at" and you are, right now, looking at it. Individual
 * rows are still marked on tap so the read state is right even if the bulk call fails.
 */
@Composable
fun NotificationsScreen(onClose: () -> Unit, onOpenReel: (String) -> Unit, onOpenUser: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<NetNotification>() }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    suspend fun load() {
        if (!ApiConfig.ENABLED) { loading = false; return }
        runCatching { SyntraClient.getNotifications() }
            .onSuccess { fresh -> items.clear(); items.addAll(fresh); failed = false }
            .onFailure { failed = items.isEmpty() }
        loading = false
    }

    LaunchedEffect(Unit) {
        load()
        // Clear the badge. Fire-and-forget: a failure here must not stop the list
        // being usable, and the next open will try again.
        runCatching { SyntraClient.markNotificationsRead() }
        NotificationBadge.unread = 0
    }

    androidx.activity.compose.BackHandler(onBack = onClose)

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
            Text("Notifikasi", color = NexusTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
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

                failed -> NotificationsEmpty(
                    "Gagal memuat notifikasi.\nTarik ke bawah untuk mencoba lagi.",
                )

                items.isEmpty() -> NotificationsEmpty(
                    "Belum ada notifikasi.\nSuka, komentar, dan sebutan akan muncul di sini.",
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { n ->
                        NotificationRow(
                            n = n,
                            onClick = {
                                scope.launch { runCatching { SyntraClient.markNotificationsRead(n.id) } }
                                val i = items.indexOfFirst { it.id == n.id }
                                if (i >= 0) items[i] = items[i].copy(isRead = true)
                                // Where a notification LEADS is the whole point of it;
                                // one that only announces something is a dead end.
                                when {
                                    n.subjectType == "reel" && n.subjectId.isNotBlank() -> onOpenReel(n.subjectId)
                                    n.actorUsername.isNotBlank() -> onOpenUser(n.actorUsername)
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NotificationsEmpty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Notifications, null,
                tint = NexusTextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NotificationRow(n: NetNotification, onClick: () -> Unit) {
    val who = n.actorName.ifBlank { n.actorUsername }.ifBlank { "Seseorang" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Unread rows carry a faint accent wash rather than a dot: the whole row
            // is the thing you have not read, and a dot at the edge is easy to miss
            // in a list you are scanning quickly.
            .background(if (n.isRead) Color.Transparent else NexusAccent.copy(alpha = 0.07f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (n.actorAvatarUrl != null) {
                AsyncImage(
                    model = n.actorAvatarUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(NexusSurfaceElevated),
                )
            } else {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(NexusSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        who.take(1).uppercase(),
                        color = NexusTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // The kind of notification, as a badge on the avatar — so the list can be
            // read at a glance without parsing every sentence.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(NexusBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(n.type),
                    contentDescription = null,
                    tint = tintFor(n.type),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildString {
                    append(who)
                    append(' ')
                    append(phraseFor(n.type))
                },
                color = NexusTextPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (n.createdAt.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(relativeNotifTime(n.createdAt), color = NexusTextSecondary, fontSize = 11.sp)
            }
        }
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    "follow" -> Icons.Filled.PersonAdd
    "like" -> Icons.Filled.Favorite
    "comment", "story_reply" -> Icons.Filled.ChatBubbleOutline
    "mention" -> Icons.Filled.AlternateEmail
    "room_live" -> Icons.Filled.Mic
    else -> Icons.Filled.Notifications
}

private fun tintFor(type: String): Color = when (type) {
    "like" -> Color(0xFFFF5D7A)
    "follow" -> NexusAccentSoft
    else -> NexusTextSecondary
}

private fun phraseFor(type: String): String = when (type) {
    "follow" -> "mulai mengikuti kamu."
    "like" -> "menyukai postingan kamu."
    "comment" -> "mengomentari postingan kamu."
    "mention" -> "menyebut kamu."
    "story_reply" -> "membalas story kamu."
    "room_live" -> "sedang membuka room."
    else -> "mengirim pemberitahuan."
}

/** "baru saja" / "5 mnt" / "3 jam" / "2 hr" — same shape as the comment timestamps. */
private fun relativeNotifTime(iso: String): String {
    val then = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return ""
    val secs = java.time.Duration.between(then, java.time.Instant.now()).seconds
    return when {
        secs < 60 -> "baru saja"
        secs < 3600 -> "${secs / 60} mnt"
        secs < 86_400 -> "${secs / 3600} jam"
        secs < 604_800 -> "${secs / 86_400} hr"
        else -> "${secs / 604_800} mgg"
    }
}
