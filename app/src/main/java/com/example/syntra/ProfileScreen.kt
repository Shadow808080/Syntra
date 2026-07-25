package com.example.syntra

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetStory
import com.example.syntra.net.NetStoryGroup
import com.example.syntra.net.NetReel
import com.example.syntra.net.NetUser
import com.example.syntra.net.SyntraClient
import com.example.syntra.net.VideoCache
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
fun ProfileScreen(
    username: String?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // When set, a direct chat with this person is open on top of the profile —
    // works no matter where the profile was opened from (Shorts, feed, etc.).
    var openChatConvo by remember(username) { mutableStateOf<Conversation?>(null) }

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
    // This person's active story (if any) — drives the avatar ring and the tap
    // behaviour (story vs. full-screen photo).
    var story by remember(username) { mutableStateOf<NetStoryGroup?>(null) }
    // Avatar-tap outcomes.
    var showPhoto by remember { mutableStateOf(false) }
    var showStory by remember { mutableStateOf(false) }
    var showAvatarChoice by remember { mutableStateOf(false) }

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
            // Find this person's story among the ones visible to me (followed + self).
            val uid = user?.id
            if (uid != null) {
                story = runCatching { SyntraClient.getStories() }.getOrNull()
                    ?.firstOrNull { it.authorId == uid && it.stories.isNotEmpty() }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            // Leave whatever loaded; the header still shows what we have.
        }
        loading = false
    }

    val avatarUrl = user?.avatarMediaId?.takeIf { it.startsWith("http") }
    val hasPhoto = avatarUrl != null
    val hasStory = story?.stories?.isNotEmpty() == true

    // Tapping the avatar: photo only → view photo; story only → view story; both →
    // ask; neither → nothing.
    fun onAvatarTap() {
        when {
            hasStory && hasPhoto -> showAvatarChoice = true
            hasStory -> showStory = true
            hasPhoto -> showPhoto = true
        }
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
                    ProfileHeader(
                        user = user,
                        isMe = isMe,
                        hasStory = hasStory,
                        storyAllViewed = story?.allViewed == true,
                        onAvatarTap = { onAvatarTap() },
                    )
                    if (!isMe && user != null) {
                        ProfileActions(
                            following = following,
                            blocked = blocked,
                            // Chat is always offered for another user (never yourself).
                            canMessage = !blocked,
                            onMessage = {
                                val u = user ?: return@ProfileActions
                                scope.launch {
                                    runCatching {
                                        val convId = SyntraClient.createDirect(u.id)
                                        openChatConvo = Conversation(
                                            id = convId,
                                            name = u.displayName.ifBlank { u.username },
                                            message = "",
                                            time = "",
                                            counterpartId = u.id,
                                            counterpartUsername = u.username,
                                        )
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "Buka chat gagal: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
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

    // Avatar-tap: choose between story and photo when both exist.
    if (showAvatarChoice) {
        AvatarChoiceSheet(
            onDismiss = { showAvatarChoice = false },
            onStory = { showAvatarChoice = false; showStory = true },
            onPhoto = { showAvatarChoice = false; showPhoto = true },
        )
    }

    // Full-screen profile photo.
    if (showPhoto && avatarUrl != null) {
        ProfilePhotoViewer(url = avatarUrl, onClose = { showPhoto = false })
    }

    // Full-screen story viewer for this person.
    if (showStory) {
        story?.let { g ->
            ProfileStoryViewer(
                group = g,
                onClose = { showStory = false },
                onViewed = { id -> scope.launch { runCatching { SyntraClient.viewStory(id) } } },
            )
        }
    }

    // Direct chat opened from the profile's chat icon — full-screen over the profile.
    openChatConvo?.let { convo ->
        ChatDetailScreen(conversation = convo, onBack = { openChatConvo = null })
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
private fun ProfileHeader(
    user: NetUser?,
    isMe: Boolean,
    hasStory: Boolean,
    storyAllViewed: Boolean,
    onAvatarTap: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar — wrapped in a story ring when there's an active story. Bright
        // gradient ring = unwatched, dim ring = fully watched. Tap runs onAvatarTap.
        val ringMod = when {
            hasStory && !storyAllViewed -> Modifier.background(
                Brush.sweepGradient(listOf(NexusAccentSoft, NexusAccent, NexusAccentSoft)),
                CircleShape,
            )
            hasStory -> Modifier.background(NexusStroke, CircleShape)
            else -> Modifier
        }
        Box(
            modifier = Modifier
                .size(if (hasStory) 100.dp else 92.dp)
                .then(ringMod)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onAvatarTap,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .padding(if (hasStory) 4.dp else 0.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5))))
                    .border(2.dp, NexusBackground, CircleShape),
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

/**
 * Icon-forward actions on another person's profile: a primary Follow pill (icon +
 * short label), then round icon buttons for Chat and Block — mostly icons, per the
 * design direction.
 */
@Composable
private fun ProfileActions(
    following: Boolean,
    blocked: Boolean,
    canMessage: Boolean,
    onMessage: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Follow / Following (primary) — icon + short label.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (following) Color.Transparent else NexusAccent)
                .border(1.dp, if (following) NexusStroke else Color.Transparent, RoundedCornerShape(12.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onToggleFollow,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (following) Icons.Filled.Check else Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = if (following) NexusTextPrimary else Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (following) "Mengikuti" else "Ikuti",
                color = if (following) NexusTextPrimary else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Chat (icon only) — opens a direct chat with this person.
        if (canMessage) {
            ProfileIconButton(
                icon = Icons.Filled.ChatBubbleOutline,
                tint = NexusTextPrimary,
                onClick = onMessage,
            )
        }
        // Block / Unblock (icon only).
        ProfileIconButton(
            icon = if (blocked) Icons.Filled.LockOpen else Icons.Filled.Block,
            tint = if (blocked) NexusTextSecondary else Color(0xFFFF6B6B),
            onClick = onToggleBlock,
        )
    }
}

/** Square icon action used across the profile action row. */
@Composable
private fun ProfileIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, NexusStroke, RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
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

// ---------------------------------------------------------------------------
// Avatar tap: choice sheet, photo viewer, story viewer
// ---------------------------------------------------------------------------

/** Shown when a person has BOTH a story and a profile photo — pick which to open. */
@Composable
private fun AvatarChoiceSheet(onDismiss: () -> Unit, onStory: () -> Unit, onPhoto: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(20.dp),
        ) {
            Text("Buka", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceTile(Icons.Filled.PlayArrow, "Story", NexusAccent, Modifier.weight(1f), onStory)
                ChoiceTile(Icons.Filled.Photo, "Foto profil", Color.White.copy(alpha = 0.10f), Modifier.weight(1f), onPhoto)
            }
        }
    }
}

@Composable
private fun ChoiceTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bg: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, NexusStroke, RoundedCornerShape(16.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Full-screen profile photo with a close button. */
@Composable
private fun ProfilePhotoViewer(url: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Foto profil",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, "Tutup", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Minimal full-screen story viewer for one person: segmented progress bar, tap
 * right/left to advance/rewind, auto-advance on a timer, image or video. Marks
 * each segment viewed as it shows.
 */
@Composable
private fun ProfileStoryViewer(
    group: NetStoryGroup,
    onClose: () -> Unit,
    onViewed: (storyId: String) -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val stories = group.stories
    if (stories.isEmpty()) { onClose(); return }
    var index by remember { mutableIntStateOf(0) }
    val current = stories.getOrNull(index) ?: run { onClose(); return }
    val isVideo = current.mediaKind == "video"

    // Mark viewed + auto-advance. Images use a fixed 5s; videos use their duration.
    LaunchedEffect(index) {
        onViewed(current.id)
        val dur = if (isVideo && current.durationMs > 0) current.durationMs else 5000L
        kotlinx.coroutines.delay(dur)
        if (index < stories.lastIndex) index++ else onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(stories.size) {
                detectTapGestures { offset ->
                    if (offset.x < size.width * 0.35f) {
                        if (index > 0) index-- else onClose()
                    } else {
                        if (index < stories.lastIndex) index++ else onClose()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isVideo) {
            // Resolve to the on-disk cached copy first (download once); a story
            // video re-watched — or the story re-opened — then costs no more egress.
            var vsrc by remember(current.id) { mutableStateOf<String?>(null) }
            LaunchedEffect(current.id) { vsrc = VideoCache.resolve(context, current.mediaUrl) }
            vsrc?.let { src ->
                androidx.compose.runtime.key(current.id) {
                    AndroidView(
                        factory = { ctx ->
                            android.view.TextureView(ctx).apply {
                                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                        runCatching {
                                            val mp = android.media.MediaPlayer()
                                            tag = mp
                                            mp.setSurface(android.view.Surface(st))
                                            mp.setDataSource(src)
                                            mp.isLooping = true
                                            mp.setOnPreparedListener { it.start() }
                                            mp.prepareAsync()
                                        }
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) = Unit
                                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                                        (tag as? android.media.MediaPlayer)?.let { runCatching { it.release() } }
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) = Unit
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            AsyncImage(
                model = current.mediaUrl,
                contentDescription = "Story",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Segmented progress bar at the top: filled = watched/current, dim = ahead.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            stories.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= index) Color.White else Color.White.copy(alpha = 0.35f)),
                )
            }
        }

        // Close button.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 22.dp, end = 10.dp)
                .size(40.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, "Tutup", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}
