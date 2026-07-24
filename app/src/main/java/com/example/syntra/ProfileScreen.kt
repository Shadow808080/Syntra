package com.example.syntra

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetReel
import com.example.syntra.net.NetUser
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ProfileTab { SHORTS, SAVED }

/**
 * A TikTok-style profile page. [username] null means "me" (own profile — shows
 * the Saved tab and lets you delete your shorts); a non-null username shows
 * someone else's public profile.
 */
@Composable
fun ProfileScreen(username: String?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var user by remember(username) { mutableStateOf<NetUser?>(null) }
    var loading by remember(username) { mutableStateOf(true) }
    val shorts = remember(username) { mutableStateListOf<NetReel>() }
    val saved = remember(username) { mutableStateListOf<NetReel>() }
    var tab by remember(username) { mutableStateOf(ProfileTab.SHORTS) }
    var pendingDelete by remember { mutableStateOf<NetReel?>(null) }
    // When set, opens the full-screen swipeable reel viewer at this index.
    var viewerAt by remember { mutableStateOf<Int?>(null) }
    var following by remember(username) { mutableStateOf(false) }
    var blocked by remember(username) { mutableStateOf(false) }

    val isMe = username == null

    suspend fun load() {
        try {
            user = if (isMe) SyntraClient.getMyProfile() else SyntraClient.getUser(username!!)
            user?.let { following = it.followStatus == "accepted" || it.followStatus == "pending" }
            val myShorts = if (isMe) SyntraClient.getMyReels() else SyntraClient.getUserReels(username!!)
            shorts.clear(); shorts.addAll(myShorts)
            if (isMe) {
                val sv = SyntraClient.getSavedReels()
                saved.clear(); saved.addAll(sv)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            // Leave whatever loaded; the header still shows what we have.
        }
        loading = false
    }

    androidx.compose.runtime.LaunchedEffect(username) {
        if (ApiConfig.ENABLED) load() else loading = false
    }

    Box(Modifier.fillMaxSize().background(NexusBackground)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            // Leave room at the bottom so the last row of shorts isn't hidden
            // behind the app's bottom navigation bar.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            // --- Header spans all three columns ---
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Column {
                    ProfileTopBar(title = user?.username ?: "", onClose = onClose)
                    ProfileHeader(user = user, isMe = isMe)
                    if (!isMe && user != null) {
                        ProfileActions(
                            following = following,
                            blocked = blocked,
                            onToggleFollow = {
                                val u = username ?: return@ProfileActions
                                if (following) { following = false; scope.launch { runCatching { SyntraClient.unfollow(u) } } }
                                else { following = true; scope.launch { runCatching { SyntraClient.follow(u) } } }
                            },
                            onToggleBlock = {
                                val u = username ?: return@ProfileActions
                                if (blocked) { blocked = false; scope.launch { runCatching { SyntraClient.unblockUser(u) } } }
                                else {
                                    blocked = true; following = false
                                    scope.launch { runCatching { SyntraClient.blockUser(u) } }
                                }
                            },
                        )
                    }
                    ProfileTabs(
                        tab = tab,
                        showSaved = isMe,
                        onSelect = { tab = it },
                    )
                }
            }

            val list = if (tab == ProfileTab.SAVED) saved else shorts
            if (loading) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    }
                }
            } else if (list.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (tab == ProfileTab.SAVED) "Belum ada video tersimpan" else "Belum ada Shorts",
                            color = NexusTextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                itemsIndexed(list, key = { _, r -> r.id }) { index, reel ->
                    ReelThumb(
                        reel = reel,
                        deletable = isMe && tab == ProfileTab.SHORTS,
                        onDelete = { pendingDelete = reel },
                        onOpen = { viewerAt = index },
                    )
                }
            }
        }
    }

    // Full-screen swipeable reel viewer (opened by tapping a thumbnail).
    viewerAt?.let { start ->
        val list = if (tab == ProfileTab.SAVED) saved else shorts
        if (list.isNotEmpty()) {
            ReelViewer(reels = list.toList(), startIndex = start, onClose = { viewerAt = null })
        }
    }

    pendingDelete?.let { reel ->
        DeleteShortDialog(
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    runCatching { SyntraClient.deleteReel(reel.id) }
                        .onSuccess { shorts.removeAll { it.id == reel.id }; saved.removeAll { it.id == reel.id } }
                }
            },
        )
    }
}

@Composable
private fun ProfileTopBar(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (title.isBlank()) "Profil" else "@$title",
            color = NexusTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProfileHeader(user: NetUser?, isMe: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5))))
                .border(2.dp, NexusStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = user?.avatarMediaId
            if (!avatar.isNullOrBlank() && avatar.startsWith("http")) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = (user?.displayName?.firstOrNull() ?: user?.username?.firstOrNull() ?: 'S').uppercase(),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = user?.displayName?.ifBlank { user.username } ?: "…",
            color = NexusTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        if (user != null) {
            Spacer(Modifier.height(2.dp))
            Text("@${user.username}", color = NexusTextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        // Stats
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stat(count = user?.followingCount ?: 0, label = "Mengikuti")
            Stat(count = user?.followerCount ?: 0, label = "Pengikut")
        }
    }
}

@Composable
private fun Stat(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = NexusTextSecondary, fontSize = 12.sp)
    }
}

/** Follow/Unfollow + Block actions on another person's profile. */
@Composable
private fun ProfileActions(
    following: Boolean,
    blocked: Boolean,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Follow / Following (primary)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (following) Color.Transparent else NexusAccent)
                .border(1.dp, if (following) NexusStroke else Color.Transparent, RoundedCornerShape(12.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onToggleFollow,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (following) "Mengikuti" else "Ikuti",
                color = if (following) NexusTextPrimary else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Block / Unblock (secondary)
        Box(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, NexusStroke, RoundedCornerShape(12.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onToggleBlock,
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (blocked) "Buka blokir" else "Blokir",
                color = if (blocked) NexusTextSecondary else Color(0xFFFF6B6B),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProfileTabs(tab: ProfileTab, showSaved: Boolean, onSelect: (ProfileTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NexusStroke, RoundedCornerShape(0.dp)),
    ) {
        TabCell(Icons.Filled.GridView, active = tab == ProfileTab.SHORTS, modifier = Modifier.weight(1f)) {
            onSelect(ProfileTab.SHORTS)
        }
        if (showSaved) {
            TabCell(Icons.Filled.Bookmark, active = tab == ProfileTab.SAVED, modifier = Modifier.weight(1f)) {
                onSelect(ProfileTab.SAVED)
            }
        }
    }
}

@Composable
private fun TabCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) NexusTextPrimary else NexusTextSecondary, modifier = Modifier.size(22.dp))
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(NexusAccentSoft),
            )
        }
    }
}

@Composable
private fun ReelThumb(reel: NetReel, deletable: Boolean, onDelete: () -> Unit, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(0.66f)
            .background(NexusSurface)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen,
            ),
    ) {
        AsyncImage(
            model = reel.mediaUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        // View count, bottom-left, like TikTok.
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(2.dp))
            Text("${reel.viewCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        if (deletable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDelete,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Delete, "Hapus", tint = Color(0xFFFF6B6B), modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun DeleteShortDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(40.dp)
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(20.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(20.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Hapus Short ini?", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Video akan dihapus permanen dan hilang dari feed semua orang.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text("Batal", color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color(0xFFFF5D5D))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onConfirm,
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text("Hapus", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
