package com.example.syntra

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusOnline
import com.example.syntra.ui.theme.NexusRing
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetConversation
import com.example.syntra.net.NetMessage
import com.example.syntra.net.NetPresence
import com.example.syntra.net.NetStoryGroup
import com.example.syntra.net.NetStoryViewer
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

// A story can be a bundled drawable, a picked photo/video, or a remote URL from the backend.
private sealed interface StoryImage {
    data class Res(@DrawableRes val id: Int) : StoryImage
    data class Bitmap(val image: ImageBitmap) : StoryImage
    data class Video(val uri: Uri, val thumbnail: ImageBitmap) : StoryImage
    data class Url(val url: String, val isVideo: Boolean) : StoryImage
}

// Every entity carries a stable `id`. When wired to the backend this is the
// server id; for the optimistic messages/stories created locally it is a
// client id that gets swapped for the authoritative one on ack (ref -> ack).
// See docs/app-backend-alignment.md §6.
/** One segment of someone's story: its own media, its own viewed flag. */
private data class StoryItem(
    val id: String,
    val image: StoryImage,
    val viewed: Boolean = false,
    /** RFC3339 UTC; stories expire 24h after this. */
    val createdAt: String = "",
)

private data class ActivePerson(
    val id: String,
    val name: String,
    val items: List<StoryItem>,
    /** Only my own stories can be deleted. */
    val isMine: Boolean = false,
) {
    /** Cover shown on the row: first unseen segment, else the first one. */
    val photo: StoryImage get() = (items.firstOrNull { !it.viewed } ?: items.first()).image

    /** First segment not yet watched; 0 (replay from start) when all are watched. */
    fun firstUnwatched(): Int = items.indexOfFirst { !it.viewed }.let { if (it < 0) 0 else it }
    val posts: Int get() = items.size
}

enum class Presence { NONE, ONLINE, TYPING }

data class Conversation(
    val id: String,
    val name: String,
    val message: String,
    val time: String,
    // Gradient is a local decoration used only when there is no real avatar.
    // Derived from the id hash so a conversation always gets the same colours.
    val gradient: List<Color> = gradientFor(id),
    val unread: Int = 0,
    val presence: Presence = Presence.NONE,
    val sent: Boolean = false,
    // Other participant in a direct chat; used for presence queries.
    val counterpartId: String? = null,
    // Username of the other participant, when known — needed to block them.
    val counterpartUsername: String? = null,
    // Newest message the peer has read; drives the ✓✓ indicator.
    val counterpartLastReadId: String? = null,
    // Id of the last message — compared with counterpartLastReadId to know if my
    // last sent message has been read (blue ✓✓) or just delivered (grey).
    val lastMessageId: String? = null,
    // Real profile photo of the counterpart / group, when the server knows one.
    val avatarUrl: String? = null,
)

// Stable placeholder gradients, picked from the id hash (align. doc §6).
private val gradientPalettes = listOf(
    listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFEE5A6F), Color(0xFFF29263)),
    listOf(Color(0xFF485563), Color(0xFF29323C)),
    listOf(Color(0xFFDA22FF), Color(0xFF9733EE)),
    listOf(Color(0xFF2196F3), Color(0xFF3B68F5)),
)

private fun gradientFor(id: String): List<Color> =
    gradientPalettes[(id.hashCode() and Int.MAX_VALUE) % gradientPalettes.size]

private fun newLocalId(): String = "local-${System.currentTimeMillis()}"

// No sample stories: the story row is whatever GET /stories returns, nothing else.

/** Decode a picked gallery image into an [ImageBitmap] for use as a story. */
private fun loadStoryBitmap(context: Context, uri: Uri): ImageBitmap? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()

/** Grab the first frame of a video to use as its story thumbnail. */
private fun extractVideoThumbnail(context: Context, uri: Uri): ImageBitmap? =
    runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0)?.asImageBitmap()
        } finally {
            retriever.release()
        }
    }.getOrNull()

// Faint outline shown on a story avatar once its history has been watched.
private val StorySeenRing = Color(0xFF3A3A44)

/** Build a [StoryImage] from a picked photo or video URI. */
private fun loadStoryMedia(context: Context, uri: Uri): StoryImage? {
    val mime = context.contentResolver.getType(uri).orEmpty()
    return if (mime.startsWith("video")) {
        extractVideoThumbnail(context, uri)?.let { StoryImage.Video(uri, it) }
    } else {
        loadStoryBitmap(context, uri)?.let { StoryImage.Bitmap(it) }
    }
}

private val conversations = listOf(
    Conversation(
        id = "c_lena",
        name = "Lena Chen",
        message = "The system deployment...",
        time = "12:45 PM",
        unread = 3,
        presence = Presence.ONLINE,
    ),
    Conversation(
        id = "c_eng_sync",
        name = "Engineering Sync",
        message = "Marcus: Pull request #452...",
        time = "Yesterday",
        sent = true,
    ),
    Conversation(
        id = "c_marcus",
        name = "Marcus Wright",
        message = "Typing...",
        time = "10:12 AM",
        presence = Presence.TYPING,
    ),
    Conversation(
        id = "c_sarah",
        name = "Sarah Jenkins",
        message = "Check the new dashboard m...",
        time = "Mon",
    ),
    Conversation(
        id = "c_jasmine",
        name = "Jasmine Vo",
        message = "The client was very impres...",
        time = "Jan 12",
    ),
)

// ---------------------------------------------------------------------------
// Backend mapping (net models -> UI models)
// ---------------------------------------------------------------------------

private fun NetConversation.toUi() = Conversation(
    id = id,
    name = title.ifBlank { "(tanpa nama)" },
    message = lastPreview.ifBlank { previewFallback(lastType) },
    time = formatClock(lastAt),
    unread = unreadCount,
    presence = Presence.NONE, // updated live via presence.update
    sent = lastSenderId != null && lastSenderId == SyntraClient.myUserId,
    counterpartId = counterpartId,
    counterpartUsername = counterpartUsername,
    counterpartLastReadId = counterpartLastReadId,
    lastMessageId = lastMessageId,
    // Only a real URL is usable; a bare media id stays null and falls back to
    // the letter tile until the photo is resolved (see resolveAvatars).
    avatarUrl = avatarMediaId?.takeIf { it.startsWith("http") },
)

/** Null when the group carries no media — an empty ring would be a lie. */
private fun NetStoryGroup.toUi(): ActivePerson? {
    if (stories.isEmpty()) return null
    return ActivePerson(
        id = authorId,
        name = if (isCurrentUser) "Story kamu" else displayName.ifBlank { username },
        isMine = isCurrentUser,
        items = stories.map {
            StoryItem(
                id = it.id,
                image = StoryImage.Url(it.mediaUrl, it.mediaKind == "video"),
                viewed = it.viewed,
                createdAt = it.createdAt,
            )
        },
    )
}

private fun previewFallback(type: String): String = when (type) {
    "image" -> "📷 Foto"
    "video" -> "🎥 Video"
    "audio", "voice_note" -> "🎙️ Pesan suara"
    else -> ""
}

/** "Baru saja" / "12 menit lalu" / "3 jam lalu" — stories never live past 24h. */
private fun relativeTime(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val then = java.time.Instant.parse(iso)
        val minutes = java.time.Duration.between(then, java.time.Instant.now()).toMinutes()
        when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "$minutes menit lalu"
            else -> "${minutes / 60} jam lalu"
        }
    }.getOrDefault("")
}

private val idLocale = java.util.Locale.forLanguageTag("id-ID")

/**
 * Chat-list timestamp, WhatsApp-style and localised:
 * today → `15.27` (Indonesian uses a dot), yesterday → `Kemarin`, within a week →
 * the short day name (`Sen`, `Sel`…), older → `12/01/26`.
 */
private fun formatClock(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val zone = java.time.ZoneId.systemDefault()
        val then = java.time.Instant.parse(iso).atZone(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(
            then.toLocalDate(),
            java.time.ZonedDateTime.now(zone).toLocalDate(),
        )
        when {
            days <= 0L -> java.time.format.DateTimeFormatter.ofPattern("HH.mm").format(then)
            days == 1L -> "Kemarin"
            days < 7L -> java.time.format.DateTimeFormatter.ofPattern("EEE", idLocale)
                .format(then)
                .replaceFirstChar { it.uppercase() }
            else -> java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy").format(then)
        }
    }.getOrDefault("")
}

/** Uploads a locally created story to the backend (media 3-step + POST /stories). */
private suspend fun uploadStory(context: Context, media: StoryImage) {
    when (media) {
        is StoryImage.Bitmap -> {
            val bmp = media.image.asAndroidBitmap()
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            val id = SyntraClient.uploadMedia(
                "image", "jpg", "image/jpeg", out.toByteArray(), bmp.width, bmp.height,
            )
            SyntraClient.createStory(id)
        }
        is StoryImage.Video -> {
            val bytes = context.contentResolver.openInputStream(media.uri)?.use { it.readBytes() } ?: return
            val id = SyntraClient.uploadMedia("video", "mp4", "video/mp4", bytes)
            SyntraClient.createStory(id)
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    selectedTab: NexusTab = NexusTab.CHAT,
    onTabSelected: (NexusTab) -> Unit = {},
    onSignOut: () -> Unit = {},
    // Reports when a full-screen overlay is up, so the home pager can lock swiping.
    onOverlayChange: (Boolean) -> Unit = {},
) {
    // Index of the story currently open in the full-screen viewer (null = closed).
    var openedStory by remember { mutableStateOf<Int?>(null) }
    // Conversation currently open in the detail screen (null = on the list).
    var openedChat by remember { mutableStateOf<Conversation?>(null) }
    // Live data only. Sample chats are used solely when there is no backend to talk to;
    // the story row is never seeded — it shows exactly what GET /stories returns.
    val chats = remember {
        mutableStateListOf<Conversation>().also { if (!ApiConfig.ENABLED) it.addAll(conversations) }
    }
    val stories = remember { mutableStateListOf<ActivePerson>() }
    // Stories whose history has been fully watched (ring turns grey / disappears).
    val seenStories = remember { mutableStateListOf<ActivePerson>() }
    // Search state.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Whether the "Tambah status" sheet is open.
    var showAddStatus by remember { mutableStateOf(false) }
    var showTextStory by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Conversation the user long-pressed and may want to remove.
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    // Multi-select: long-press ticks a chat and swaps the overflow menu for actions.
    val selection = remember { mutableStateListOf<String>() }
    var archivedIds by remember { mutableStateOf(emptySet<String>()) }
    var pinnedIds by remember { mutableStateOf(emptySet<String>()) }
    var showArchived by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showManualScan by remember { mutableStateOf(false) }
    var showDiscover by remember { mutableStateOf(false) }
    var openProfileUser by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        archivedIds = ChatFlags.archived(context)
        pinnedIds = ChatFlags.pinned(context)
    }

    // Tell the host pager whenever something covers the whole screen.
    val overlayOpen = openedStory != null || openedChat != null ||
        showAddStatus || showNewGroup || showSettings || showTextStory ||
        showDiscover || openProfileUser != null || pendingPhoto != null
    LaunchedEffect(overlayOpen) { onOverlayChange(overlayOpen) }

    // Pull-to-refresh state for the chat list.
    var refreshing by remember { mutableStateOf(false) }
    var firstLoadDone by remember { mutableStateOf(false) }
    // username -> resolved photo URL, so the list endpoint's missing avatars are
    // only looked up once per session.
    val avatarCache = remember { mutableStateMapOf<String, String>() }

    // Reloads chats + stories from the backend. Shared by first load and pull-to-refresh.
    suspend fun refresh() {
        if (!ApiConfig.ENABLED) return
        runCatching {
            val convs = SyntraClient.getConversations()
            chats.clear(); chats.addAll(convs.map { it.toUi() })
            val groups = SyntraClient.getStories()
            stories.clear()
            seenStories.clear()
            val watchedOwn = ChatFlags.watchedOwnStories(context)
            val built = groups.mapNotNull { g ->
                g.toUi()?.let { person ->
                    // My own stories: the backend never marks them viewed, so apply the
                    // local "watched" set so the ring stays dimmed after a refresh.
                    if (person.isMine) {
                        person.copy(items = person.items.map {
                            if (watchedOwn.contains(it.id)) it.copy(viewed = true) else it
                        })
                    } else {
                        person
                    }
                }
            }
            // Ordering: my own story first, then people with UNWATCHED stories, then
            // the fully-watched ones — so fresh stories always sit up front.
            val ordered = built.sortedWith(
                compareByDescending<ActivePerson> { it.isMine }
                    .thenByDescending { it.items.any { item -> !item.viewed } },
            )
            ordered.forEach { person ->
                stories.add(person)
                if (person.items.all { it.viewed }) seenStories.add(person)
            }
            SyntraClient.subscribe(convs.map { "conversation:${it.id}" })
            SyntraClient.presenceQuery(convs.mapNotNull { it.counterpartId })
        }.onFailure { Toast.makeText(context, "Sync gagal: ${it.message}", Toast.LENGTH_SHORT).show() }
        // The conversation list may carry only a media id (or nothing) for the
        // photo. Resolve the real URL per person in the background so rows fill
        // in as they arrive — and so a changed profile photo shows up here.
        // Cache-first: only people we have never resolved cost a request, so a
        // reconnect or a new message doesn't re-query every contact. Pull-to-refresh
        // clears the cache when the user explicitly asks for fresh data.
        scope.launch {
            chats.toList().forEach { c ->
                val username = c.counterpartUsername
                if (username.isNullOrBlank()) return@forEach
                val cached = avatarCache[username]
                val url = cached ?: runCatching { SyntraClient.getUser(username) }
                    .getOrNull()
                    ?.avatarMediaId
                    ?.takeIf { it.startsWith("http") }
                    ?.also { avatarCache[username] = it }
                if (url != null) {
                    val i = chats.indexOfFirst { it.id == c.id }
                    if (i >= 0 && chats[i].avatarUrl != url) {
                        chats[i] = chats[i].copy(avatarUrl = url)
                    }
                }
            }
        }
        // Pull my own name/photo from the server so a change made on another
        // device (there is no realtime profile event) shows up here too.
        runCatching {
            val me = SyntraClient.getMyProfile()
            if (me.displayName.isNotBlank()) ProfileStore.setDisplayName(context, me.displayName)
            me.avatarMediaId?.takeIf { it.isNotBlank() }?.let { url ->
                ProfileStore.setAvatar(context, url, ProfileStore.avatarMediaId(context).orEmpty())
            }
        }
    }

    // --- Backend: load data + realtime updates (only when configured) --------
    if (ApiConfig.ENABLED) {
        LaunchedEffect(Unit) {
            runCatching {
                // AuthScreen already established the session; logging in again here
                // would silently replace the signed-in user with the dev account.
                if (!SyntraClient.hasSession) SyntraClient.login()
                SyntraClient.connect()
            }
            refresh()
            firstLoadDone = true
        }
        DisposableEffect(Unit) {
            val listener = object : SocketListener {
                override fun onMessageNew(message: NetMessage) {
                    val idx = chats.indexOfFirst { it.id == message.conversationId }
                    if (idx < 0) {
                        // First message of a conversation this device has never seen:
                        // pull it in so the new chat appears without a refresh.
                        scope.launch {
                            runCatching { SyntraClient.getConversations() }.onSuccess { convs ->
                                val fresh = convs.firstOrNull { it.id == message.conversationId }
                                if (fresh != null && chats.none { it.id == fresh.id }) {
                                    chats.add(0, fresh.toUi())
                                    SyntraClient.subscribe(listOf("conversation:${fresh.id}"))
                                    fresh.counterpartId?.let { SyntraClient.presenceQuery(listOf(it)) }
                                }
                            }
                        }
                        return
                    }
                    val c = chats[idx]
                    val mine = message.senderId == SyntraClient.myUserId
                    chats.removeAt(idx)
                    chats.add(
                        0,
                        c.copy(
                            message = message.body.ifBlank { previewFallback(message.type) },
                            time = formatClock(message.createdAt),
                            // No badge for my own messages or the chat I'm reading.
                            unread = when {
                                mine || openedChat?.id == c.id -> 0
                                else -> c.unread + 1
                            },
                            sent = mine,
                        ),
                    )
                }

                override fun onConversationUpdated(conversationId: String) {
                    // Group title/avatar/members changed — re-read just that row.
                    scope.launch {
                        runCatching { SyntraClient.getConversations() }.onSuccess { convs ->
                            val fresh = convs.firstOrNull { it.id == conversationId } ?: return@onSuccess
                            val i = chats.indexOfFirst { it.id == conversationId }
                            if (i >= 0) {
                                // Keep the live presence we already track locally.
                                chats[i] = fresh.toUi().copy(presence = chats[i].presence)
                            } else {
                                chats.add(0, fresh.toUi())
                            }
                        }
                    }
                }

                override fun onUserUpdated(userId: String, displayName: String, avatarUrl: String?) {
                    if (userId == SyntraClient.myUserId) {
                        // My own profile changed on another device — mirror it locally.
                        if (displayName.isNotBlank()) ProfileStore.setDisplayName(context, displayName)
                        if (!avatarUrl.isNullOrBlank()) {
                            ProfileStore.setAvatar(context, avatarUrl, ProfileStore.avatarMediaId(context).orEmpty())
                        }
                        return
                    }
                    // A contact changed name/photo. counterpartId is only set on direct
                    // chats, so updating name here never mislabels a group.
                    chats.forEachIndexed { i, c ->
                        if (c.counterpartId == userId) {
                            var u = c
                            if (displayName.isNotBlank()) u = u.copy(name = displayName)
                            if (!avatarUrl.isNullOrBlank()) u = u.copy(avatarUrl = avatarUrl)
                            chats[i] = u
                            if (!avatarUrl.isNullOrBlank()) c.counterpartUsername?.let { avatarCache[it] = avatarUrl }
                        }
                    }
                }

                override fun onReadReceipt(conversationId: String, userId: String, messageId: String) {
                    // Clear the badge only when *I* read it (possibly on another
                    // device). The peer reading must not clear my unread count.
                    if (userId != SyntraClient.myUserId) return
                    val idx = chats.indexOfFirst { it.id == conversationId }
                    if (idx >= 0 && chats[idx].unread > 0) {
                        chats[idx] = chats[idx].copy(unread = 0)
                    }
                }

                override fun onPresence(presence: NetPresence) {
                    val idx = chats.indexOfFirst { it.counterpartId == presence.userId }
                    if (idx >= 0) {
                        chats[idx] = chats[idx].copy(
                            presence = if (presence.online) Presence.ONLINE else Presence.NONE,
                        )
                    }
                }

                override fun onReconnect() {
                    scope.launch {
                        runCatching {
                            val convs = SyntraClient.getConversations()
                            chats.clear(); chats.addAll(convs.map { it.toUi() })
                            SyntraClient.subscribe(convs.map { "conversation:${it.id}" })
                        }
                    }
                }
            }
            SyntraClient.addListener(listener)
            onDispose { SyntraClient.removeListener(listener) }
        }
    }

    fun addStory(media: StoryImage) {
        // Local (optimistic) story; id is a client id until the backend acks it.
        val storyId = newLocalId()
        val item = StoryItem(storyId, media, viewed = true)
        // My stories are one circle with a ring segment per post — appending must
        // add a segment, not a second "Your story" bubble next to the first.
        val mineIdx = stories.indexOfFirst { it.isMine }
        if (mineIdx >= 0) {
            val mine = stories[mineIdx]
            val grown = mine.copy(items = mine.items + item)
            stories.removeAt(mineIdx)
            stories.add(0, grown)
        } else {
            stories.add(0, ActivePerson(storyId, "Story kamu", listOf(item), isMine = true))
        }
        showAddStatus = false
        if (ApiConfig.ENABLED) scope.launch {
            runCatching { uploadStory(context, media) }
                .onSuccess {
                    // Reconcile with the server so the story carries its real id +
                    // resolved media URL (and shows for the rest of the app).
                    runCatching { refresh() }
                }
                .onFailure { Toast.makeText(context, "Upload story gagal: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    val filtered = if (query.isBlank()) {
        chats
    } else {
        chats.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.message.contains(query, ignoreCase = true)
        }
    }

    // Local view flags: blocked chats disappear, archived hide behind a toggle,
    // pinned float to the top.
    val blockedNames = BlockStore.all(context)
    val notBlocked = filtered.filterNot { it.name in blockedNames }
    val archivedCount = notBlocked.count { it.id in archivedIds }
    val visible = notBlocked
        .filter { showArchived || it.id !in archivedIds }
        .sortedByDescending { it.id in pinnedIds }

    fun openChat(convo: Conversation) {
        // Detail keeps the original unread count so it can scroll to the first
        // unread message; the list badge clears instantly (no refresh).
        openedChat = convo
        val idx = chats.indexOfFirst { it.id == convo.id }
        if (idx >= 0 && chats[idx].unread > 0) {
            chats[idx] = chats[idx].copy(unread = 0)
        }
        if (ApiConfig.ENABLED) scope.launch {
            runCatching {
                SyntraClient.getMessages(convo.id).firstOrNull()?.let {
                    SyntraClient.messageRead(convo.id, it.id)
                }
            }
        }
    }

    fun openDirectWith(username: String) {
        scope.launch {
            runCatching {
                val user = SyntraClient.getUser(username)
                val convId = SyntraClient.createDirect(user.id)
                openedChat = Conversation(
                    id = convId,
                    name = user.displayName.ifBlank { user.username },
                    message = "",
                    time = "",
                    counterpartId = user.id,
                    counterpartUsername = user.username,
                )
            }.onFailure { Toast.makeText(context, "Buka chat gagal: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun startScan() {
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue ?: "(empty)"
                if (ApiConfig.ENABLED) {
                    // QR carries syntra://u/<username>
                    openDirectWith(value.substringAfterLast('/').ifBlank { value })
                } else {
                    Toast.makeText(context, "Scanned: $value", Toast.LENGTH_LONG).show()
                }
            }
            .addOnCanceledListener { /* user dismissed the scanner */ }
            .addOnFailureListener {
                // Scanner unavailable on this device (Play Services) — fall back to
                // typing the username manually so you can still start a chat.
                showManualScan = true
            }
    }

    // Device back peels off one layer at a time: selection, then search.
    BackHandler(enabled = selection.isNotEmpty()) { selection.clear() }
    BackHandler(enabled = searching && selection.isEmpty()) {
        searching = false
        query = ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NexusHeader(
                searching = searching,
                query = query,
                selectedCount = selection.size,
                onClearSelection = { selection.clear() },
                onQueryChange = { query = it },
                onStartSearch = { searching = true },
                onStopSearch = {
                    searching = false
                    query = ""
                },
                onScan = { startScan() },
                onDiscover = { showDiscover = true },
                onMenuItem = { label ->
                    val picked = chats.filter { it.id in selection }
                    when (label) {
                        "New group" -> showNewGroup = true
                        "Settings" -> showSettings = true
                        "Arsipkan" -> {
                            val toArchive = picked.none { it.id in archivedIds }
                            ChatFlags.setArchived(context, selection.toList(), toArchive)
                            archivedIds = ChatFlags.archived(context)
                            selection.clear()
                        }
                        "Sematkan" -> {
                            val toPin = picked.none { it.id in pinnedIds }
                            ChatFlags.setPinned(context, selection.toList(), toPin)
                            pinnedIds = ChatFlags.pinned(context)
                            selection.clear()
                        }
                        "Hapus percakapan" -> {
                            pendingDelete = picked.firstOrNull()
                        }
                        "Blokir" -> {
                            picked.forEach { BlockStore.block(context, it.name) }
                            selection.clear()
                            Toast.makeText(
                                context,
                                "Diblokir di perangkat ini. Server belum punya fitur blokir.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        else -> Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        // Explicit refresh: re-resolve photos so a changed avatar
                        // is picked up (normal syncs reuse the cache).
                        avatarCache.clear()
                        refresh()
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // First-load skeleton: shimmering placeholder rows instead of a
                // blank screen (or a heavy spinner) while chats stream in.
                if (!firstLoadDone && chats.isEmpty() && !searching) {
                    items(8) { ChatRowSkeleton() }
                    return@LazyColumn
                }
                if (!searching) {
                    item {
                        ActiveRow(
                            people = stories,
                            seen = seenStories,
                            onStoryClick = { openedStory = it },
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                itemsIndexed(visible, key = { _, convo -> convo.id }) { _, convo ->
                    // The row carries its own even vertical margin (card style), so no
                    // extra spacer between items — that produced uneven gaps before.
                    ConversationRow(
                        convo = convo,
                        selected = convo.id in selection,
                        pinned = convo.id in pinnedIds,
                        onClick = {
                            // While selecting, a tap toggles instead of opening.
                            if (selection.isEmpty()) {
                                openChat(convo)
                            } else if (!selection.remove(convo.id)) {
                                selection.add(convo.id)
                            }
                        },
                        onLongClick = { if (!selection.remove(convo.id)) selection.add(convo.id) },
                    )
                }
                if (archivedCount > 0 && !searching) {
                    item {
                        Text(
                            text = if (showArchived) "Sembunyikan arsip" else "Diarsipkan ($archivedCount)",
                            color = NexusAccentSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { showArchived = !showArchived }
                                .padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (searching && visible.isEmpty()) {
                    item {
                        Text(
                            text = "No conversations found",
                            color = NexusTextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            }
        }

        // Floating button: add a new story from the gallery.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(56.dp)
                .background(
                    brush = Brush.verticalGradient(listOf(NexusAccentSoft, NexusAccent)),
                    shape = RoundedCornerShape(18.dp),
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { showAddStatus = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add story",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }

        // Full-screen story viewer (WhatsApp-status style)
        openedStory?.let { index ->
            StoryViewer(
                people = stories,
                startIndex = index,
                onClose = { openedStory = null },
                onSeen = { i ->
                    stories.getOrNull(i)?.let { p -> if (p !in seenStories) seenStories.add(p) }
                },
                onViewed = { personIndex, segment ->
                    // Mark exactly the segment being watched; the call is idempotent.
                    val person = stories.getOrNull(personIndex) ?: return@StoryViewer
                    val item = person.items.getOrNull(segment) ?: return@StoryViewer
                    if (!item.viewed) {
                        stories[personIndex] = person.copy(
                            items = person.items.toMutableList().also {
                                it[segment] = item.copy(viewed = true)
                            },
                        )
                        // Never register a view on my own story — I'm not a viewer.
                        // Instead remember it locally so the ring stays dimmed after
                        // a refresh (the backend always reports my own as unviewed).
                        if (person.isMine) {
                            ChatFlags.markOwnStoryWatched(context, item.id)
                        } else if (ApiConfig.ENABLED) scope.launch {
                            runCatching { SyntraClient.viewStory(item.id) }
                        }
                    }
                },
                onDeleteStory = { personIndex, segment ->
                    val person = stories.getOrNull(personIndex) ?: return@StoryViewer
                    val item = person.items.getOrNull(segment) ?: return@StoryViewer
                    scope.launch {
                        runCatching { if (ApiConfig.ENABLED) SyntraClient.deleteStory(item.id) }
                            .onSuccess {
                                val left = person.items.filterNot { it.id == item.id }
                                if (left.isEmpty()) {
                                    stories.removeAt(personIndex)
                                    openedStory = null
                                } else {
                                    stories[personIndex] = person.copy(items = left)
                                }
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    "Hapus story gagal: ${it.message}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    }
                },
            )
        }

        // Full-screen conversation detail
        openedChat?.let { convo ->
            ChatDetailScreen(
                conversation = convo,
                onBack = { openedChat = null },
                onNewGroup = {
                    openedChat = null
                    showNewGroup = true
                },
            )
        }

        pendingDelete?.let { convo ->
            DeleteConversationDialog(
                convo = convo,
                onDismiss = { pendingDelete = null },
                onConfirm = {
                    pendingDelete = null
                    // Local only: the backend has no delete/leave endpoint for
                    // conversations yet, so say plainly what this does.
                    if (selection.isEmpty()) {
                        chats.remove(convo)
                    } else {
                        chats.removeAll { it.id in selection }
                        selection.clear()
                    }
                    Toast.makeText(
                        context,
                        "Percakapan disembunyikan di perangkat ini. Pesan baru akan memunculkannya lagi.",
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }

        if (showNewGroup) {
            NewGroupScreen(
                onClose = { showNewGroup = false },
                onCreated = { id, name ->
                    showNewGroup = false
                    val convo = Conversation(id = id, name = name, message = "", time = "")
                    chats.add(0, convo)
                    openedChat = convo
                },
            )
        }

        if (showSettings) {
            SettingsScreen(
                onClose = { showSettings = false },
                onSignedOut = {
                    showSettings = false
                    onSignOut()
                },
            )
        }

        if (showManualScan) {
            ManualUsernameDialog(
                onDismiss = { showManualScan = false },
                onSubmit = { uname -> showManualScan = false; openDirectWith(uname) },
            )
        }

        if (showDiscover) {
            DiscoverScreen(
                onClose = { showDiscover = false },
                onOpenProfile = { uname -> openProfileUser = uname },
            )
        }
        openProfileUser?.let { uname ->
            ProfileScreen(username = uname, onClose = { openProfileUser = null })
        }

        // "Tambah status" sheet (gallery grid + actions)
        if (showAddStatus) {
            AddStatusScreen(
                onClose = { showAddStatus = false },
                onSelectUri = { uri ->
                    // Photos go through a preview/edit screen first; videos post directly.
                    when (val m = loadStoryMedia(context, uri)) {
                        is StoryImage.Bitmap -> { showAddStatus = false; pendingPhoto = m.image.asAndroidBitmap() }
                        is StoryImage -> addStory(m)
                        else -> {}
                    }
                },
                onCaptureBitmap = { bmp -> showAddStatus = false; pendingPhoto = bmp },
                onTextStory = { showAddStatus = false; showTextStory = true },
            )
        }

        // Photo preview + light edit (caption) before posting.
        pendingPhoto?.let { bmp ->
            PhotoStoryPreview(
                photo = bmp,
                onCancel = { pendingPhoto = null },
                onDone = { edited ->
                    pendingPhoto = null
                    addStory(StoryImage.Bitmap(edited.asImageBitmap()))
                },
            )
        }

        // Text-story composer → renders the text to a bitmap and posts it as an
        // image story (reuses the normal upload path).
        if (showTextStory) {
            TextStoryScreen(
                onClose = { showTextStory = false },
                onDone = { bmp ->
                    showTextStory = false
                    addStory(StoryImage.Bitmap(bmp.asImageBitmap()))
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun NexusHeader(
    searching: Boolean,
    query: String,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onQueryChange: (String) -> Unit,
    onStartSearch: () -> Unit,
    onStopSearch: () -> Unit,
    onScan: () -> Unit,
    onDiscover: () -> Unit,
    onMenuItem: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(SyntraHeaderPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedCount > 0) {
            // Selection mode: the overflow menu becomes chat actions.
            HeaderIcon(Icons.Filled.Close, "Batal pilih", onClick = onClearSelection)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$selectedCount dipilih",
                color = NexusTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                HeaderIcon(Icons.Filled.MoreVert, "Aksi chat") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf("Arsipkan", "Sematkan", "Hapus percakapan", "Blokir").forEach { label ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (label == "Hapus percakapan" || label == "Blokir") {
                                        Color(0xFFFF5D5D)
                                    } else {
                                        NexusTextPrimary
                                    },
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onMenuItem(label)
                            },
                        )
                    }
                }
            }
        } else if (searching) {
            HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Close search", onClick = onStopSearch)
            Spacer(Modifier.width(8.dp))
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search conversations…", color = NexusTextSecondary, fontSize = 16.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = NexusTextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(NexusAccentSoft),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                HeaderIcon(Icons.Filled.Close, "Clear", size = 22.dp) { onQueryChange("") }
            }
        } else {
            SyntraTitle()
            Spacer(Modifier.weight(1f))
            // Order: find-people · search · scan · overflow
            HeaderIcon(Icons.Filled.PersonAddAlt, "Cari orang", size = 27.dp, onClick = onDiscover)
            HeaderIcon(Icons.Filled.Search, "Search", size = 28.dp, onClick = onStartSearch)
            HeaderIcon(Icons.Outlined.QrCodeScanner, "Scan", size = 27.dp, onClick = onScan)
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                HeaderIcon(Icons.Filled.MoreVert, "Menu", size = 28.dp) { menuOpen = true }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    listOf("New group", "Starred messages", "Settings").forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                menuOpen = false
                                onMenuItem(label)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A borderless, ripple-free tappable header icon with a comfortable touch target. */
@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = NexusTextPrimary, modifier = Modifier.size(size))
    }
}

// ---------------------------------------------------------------------------
// Active people row
// ---------------------------------------------------------------------------

@Composable
private fun ActiveRow(
    people: List<ActivePerson>,
    seen: List<ActivePerson>,
    onStoryClick: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(people, key = { _, person -> person.id }) { index, person ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StoryAvatar(
                    photo = person.photo,
                    size = 56.dp,
                    posts = person.posts,
                    // Per-segment: watched stories dim, unwatched stay lit. Watching
                    // updates each item's `viewed`, so this reflects progress live.
                    viewedCount = person.items.count { it.viewed },
                    onClick = { onStoryClick(index) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = person.name,
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Conversation row
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    convo: Conversation,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) NexusAccent.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            GradientAvatar(
                gradient = convo.gradient,
                initial = convo.name.first().toString(),
                size = 54.dp,
                photoUrl = convo.avatarUrl,
            )
            // Tick replaces the presence dot while this row is picked.
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(NexusAccent, CircleShape)
                        .border(2.dp, NexusBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check, null,
                        tint = Color.White, modifier = Modifier.size(12.dp),
                    )
                }
            }
            if (!selected && convo.presence != Presence.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .background(NexusBackground, CircleShape)
                        .padding(2.dp)
                        .background(
                            if (convo.presence == Presence.ONLINE) NexusOnline else NexusAccent,
                            CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = convo.name,
                    color = NexusTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (pinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Disematkan",
                        tint = NexusTextSecondary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(13.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = convo.time,
                    color = if (convo.unread > 0) NexusAccentSoft else NexusTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (convo.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (convo.sent) {
                    // Blue ✓✓ only when the peer has actually read my last message
                    // (lastMessageId <= counterpartLastReadId, UUIDv7 time-ordered);
                    // otherwise grey "delivered". Consistent with the chat detail.
                    val read = convo.lastMessageId != null &&
                        convo.counterpartLastReadId != null &&
                        convo.lastMessageId <= convo.counterpartLastReadId
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = if (read) "Dibaca" else "Terkirim",
                        tint = if (read) Color(0xFF7FE3FF) else NexusTextSecondary,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(15.dp),
                    )
                }
                Text(
                    text = convo.message,
                    color = if (convo.presence == Presence.TYPING) NexusAccentSoft else NexusTextSecondary,
                    fontStyle = if (convo.presence == Presence.TYPING) FontStyle.Italic else FontStyle.Normal,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (convo.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    // A perfect round badge (not an elongated pill), number centred.
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(NexusAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (convo.unread > 99) "99" else convo.unread.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            // Kill the font's built-in top/bottom padding and pin the
                            // line height to the glyph size so the digit sits dead-centre
                            // in the circle instead of drifting low.
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                lineHeight = 11.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Story avatar with a segmented ring (one segment per posted story)
// ---------------------------------------------------------------------------

@Composable
private fun StoryAvatar(
    photo: StoryImage,
    size: androidx.compose.ui.unit.Dp,
    posts: Int,
    viewedCount: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            val segments = posts.coerceAtLeast(1)
            // Gap (in degrees) between segments; a single story draws a full ring.
            val gap = if (segments == 1) 0f else 10f
            val sweep = (360f - gap * segments) / segments
            // A brand sweep gradient (purple → blue → purple) for unwatched segments —
            // reads as a live signal. Watched segments dim to a faint grey.
            val ringBrush = Brush.sweepGradient(
                listOf(NexusRing, NexusAccentSoft, NexusRing),
                center = Offset(this.size.width / 2f, this.size.height / 2f),
            )
            var start = -90f
            repeat(segments) { i ->
                // Stories are watched in order: the first [viewedCount] segments are
                // done (dim), the rest are still lit. Watch 1 of 3 → 2 stay lit.
                val watched = i < viewedCount
                if (watched) {
                    drawArc(
                        color = StorySeenRing,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                } else {
                    drawArc(
                        brush = ringBrush,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                start += sweep + gap
            }
        }
        // Inner photo, inset so there is a small gap between ring and photo.
        StoryPhoto(
            photo = photo,
            modifier = Modifier
                .size(size - 10.dp)
                .clip(CircleShape),
        )
    }
}

/** Renders a [StoryImage] from either a drawable resource or a picked bitmap. */
@Composable
private fun StoryPhoto(
    photo: StoryImage,
    modifier: Modifier = Modifier,
) {
    when (photo) {
        is StoryImage.Res -> Image(
            painter = painterResource(photo.id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        is StoryImage.Bitmap -> Image(
            bitmap = photo.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        is StoryImage.Video -> Image(
            // Static thumbnail; the full-screen viewer plays the actual video.
            bitmap = photo.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        is StoryImage.Url -> Box(modifier) {
            // Breathing placeholder while the photo streams in — never a black gap.
            ShimmerFill(Modifier.matchParentSize())
            AsyncImage(
                model = photo.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** True for any story whose media is a video (picked or remote). */
private fun StoryImage.isVideoStory(): Boolean =
    this is StoryImage.Video || (this is StoryImage.Url && this.isVideo)

/**
 * Plays a story video full-screen. Reports playback progress and fires [onFinished]
 * only when the video reaches its end, so it always plays to completion.
 */
@Composable
private fun StoryVideo(
    uri: Uri,
    paused: Boolean,
    onProgress: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the uri: without this the pooled VideoView keeps playing the previous
    // story's clip into the next segment, and its progress drives the wrong bar.
    key(uri) {
        var player by remember { mutableStateOf<MediaPlayer?>(null) }
        // Pausing has to go through the VideoView, not the raw MediaPlayer: the view
        // owns the playback state and would happily restart underneath us.
        var view by remember { mutableStateOf<VideoView?>(null) }
        var durationMs by remember { mutableIntStateOf(0) }
        var ready by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        val finished = remember { mutableStateOf(false) }

        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            durationMs = mp.duration
                            player = mp
                            ready = true
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            failed = true
                            // Don't strand the viewer on a clip that will never play.
                            if (!finished.value) {
                                finished.value = true
                                onFinished()
                            }
                            true
                        }
                        setOnCompletionListener {
                            onProgress(1f)
                            if (!finished.value) {
                                finished.value = true
                                onFinished()
                            }
                        }
                        setVideoURI(uri)
                        view = this
                    }
                },
                // The uri is fixed for this key, so nothing to re-apply here.
                update = {},
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )

            if (!ready) {
                StoryBuffering(failed = failed)
            }
        }

        // Hold-to-pause has to reach the player too, not just the progress bar.
        LaunchedEffect(ready, paused) {
            val v = view ?: return@LaunchedEffect
            if (!ready) return@LaunchedEffect
            runCatching { if (paused) v.pause() else v.start() }
        }

        // Drive the progress bar from the real playback position.
        LaunchedEffect(player, durationMs) {
            val mp = player ?: return@LaunchedEffect
            if (durationMs <= 0) return@LaunchedEffect
            while (true) {
                // Freeze the bar with the video instead of letting it run ahead.
                if (!paused) {
                    val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                    onProgress((pos.toFloat() / durationMs).coerceIn(0f, 1f))
                }
                delay(50)
            }
        }
    }
}

/** Shown while a story video is still buffering, with a slow breathing pulse. */
@Composable
private fun StoryBuffering(failed: Boolean) {
    val transition = rememberInfiniteTransition(label = "buffer")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(horizontal = 26.dp, vertical = 20.dp),
    ) {
        if (failed) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = Color(0xFFFF5D5D),
                modifier = Modifier.size(26.dp),
            )
        } else {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = pulse),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (failed) "Video gagal dimuat" else "Menyiapkan video…",
            color = Color.White.copy(alpha = if (failed) 1f else pulse),
            fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Reusable avatar
// ---------------------------------------------------------------------------

@Composable
internal fun GradientAvatar(
    gradient: List<Color>,
    initial: String,
    size: androidx.compose.ui.unit.Dp,
    ring: Boolean = false,
    /**
     * Real profile photo. When this is a usable URL it replaces the letter tile,
     * so changing a profile picture is reflected everywhere an avatar is drawn.
     */
    photoUrl: String? = null,
) {
    val base = Modifier.size(size)
    val ringed = if (ring) {
        base
            .border(2.dp, NexusRing, CircleShape)
            .padding(3.dp)
    } else {
        base
    }
    Box(
        modifier = ringed
            .background(Brush.verticalGradient(gradient), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // A bare media id is not something Coil can load — only take real URLs,
        // otherwise keep the letter tile rather than showing a broken image.
        if (photoUrl != null && photoUrl.startsWith("http")) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = initial.uppercase(),
                color = Color.White,
                fontSize = (size.value / 2.6f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Full-screen story viewer (WhatsApp-status style)
// ---------------------------------------------------------------------------

private const val STORY_DURATION_MS = 5000L
private const val STORY_TICK_MS = 40L

/** How far sideways counts as "show me a different person". */
private const val SWIPE_PERSON_PX = 120f

@Composable
private fun StoryViewer(
    people: List<ActivePerson>,
    startIndex: Int,
    onClose: () -> Unit,
    onSeen: (Int) -> Unit,
    onViewed: (personIndex: Int, segment: Int) -> Unit,
    onDeleteStory: (personIndex: Int, segment: Int) -> Unit,
) {
    var personIndex by remember { mutableIntStateOf(startIndex) }
    // Open on the first unwatched story — already-watched ones are skipped.
    var segment by remember { mutableIntStateOf(people[startIndex].firstUnwatched()) }
    // A video only counts as watched once it actually starts playing (below).
    var videoMarked by remember { mutableStateOf(false) }
    val person = people[personIndex]
    val progress = remember { Animatable(0f) }
    // Playback fraction for the current video story (0..1).
    var videoFraction by remember { mutableStateOf(0f) }
    // Held finger freezes the story, exactly like Instagram/WhatsApp.
    var paused by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showViewers by remember { mutableStateOf(false) }
    // storyId -> how many people watched it (GET /stories/me).
    val viewCounts = remember { mutableStateMapOf<String, Int>() }
    var viewers by remember { mutableStateOf<List<NetStoryViewer>?>(null) }
    // Reply to someone else's story = a direct message to its author.
    var replyText by remember { mutableStateOf("") }
    var replySending by remember { mutableStateOf(false) }
    val storyContext = LocalContext.current

    // Vertical drag offset (negative = dragging up) used for the scale-to-dismiss transition.
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissThreshold = 240f
    // Smooth entrance: fade + slight scale-up when the viewer opens.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, animationSpec = tween(240)) }

    fun goNext() {
        if (segment < person.posts - 1) {
            segment++
        } else {
            // Finished everyone's last segment for this person -> mark as seen.
            onSeen(personIndex)
            if (personIndex < people.lastIndex) {
                personIndex++
                segment = people[personIndex].firstUnwatched()
            } else {
                onClose()
            }
        }
    }

    fun goPrev() {
        when {
            segment > 0 -> segment--
            personIndex > 0 -> {
                personIndex--
                segment = 0
            }
            else -> Unit // stay on the first story, restart its timer
        }
    }

    /** Horizontal swipe jumps to a whole different person, not just a segment. */
    fun nextPerson() {
        if (personIndex < people.lastIndex) {
            onSeen(personIndex)
            personIndex++
            segment = people[personIndex].firstUnwatched()
        } else {
            onClose()
        }
    }

    fun prevPerson() {
        if (personIndex > 0) {
            personIndex--
            segment = 0
        } else {
            segment = 0
        }
    }

    // Media of the segment currently on screen (each segment has its own).
    val current = person.items.getOrNull(segment) ?: person.items.first()

    // Drive the timer for photo stories; video stories advance on completion instead.
    // Ticked by hand rather than animateTo so holding a finger down can pause it.
    LaunchedEffect(personIndex, segment) {
        progress.snapTo(0f)
        videoFraction = 0f
        videoMarked = false
        if (!current.image.isVideoStory()) {
            // Photos count as watched the moment they're shown.
            onViewed(personIndex, segment)
            var elapsed = 0L
            while (elapsed < STORY_DURATION_MS) {
                delay(STORY_TICK_MS)
                // Read state directly so pause / active typing freeze it live.
                if (paused || replyText.isNotBlank()) continue
                elapsed += STORY_TICK_MS
                progress.snapTo((elapsed.toFloat() / STORY_DURATION_MS).coerceAtMost(1f))
            }
            goNext()
        }
        // Videos are marked watched from onProgress once they truly start playing —
        // a video that never plays is NOT counted as viewed.
    }

    // View counts only exist for my own stories, so fetch them lazily.
    LaunchedEffect(person.id) {
        if (ApiConfig.ENABLED && person.isMine) {
            runCatching { SyntraClient.getMyStories() }
                .onSuccess { mine -> mine.forEach { viewCounts[it.id] = it.viewCount } }
        }
    }

    LaunchedEffect(showViewers, current.id) {
        if (showViewers && ApiConfig.ENABLED) {
            // Drop myself from the list — the owner isn't a viewer of their own story.
            viewers = runCatching { SyntraClient.getStoryViewers(current.id) }
                .getOrNull()
                ?.filter { it.userId != SyntraClient.myUserId }
        }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        // Only follow upward drags (clamp so it can't be pulled down).
                        scope.launch { dragOffset.snapTo((dragOffset.value + dy).coerceAtMost(0f)) }
                    },
                    onDragEnd = {
                        // Past the threshold: keep shrinking to small, then close.
                        if (dragOffset.value < -dismissThreshold) {
                            scope.launch {
                                dragOffset.animateTo(-1600f, animationSpec = tween(260))
                                onClose()
                            }
                        } else {
                            // Springy, natural snap back.
                            scope.launch {
                                dragOffset.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                )
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dragOffset.animateTo(0f, spring()) } },
                )
            },
    ) {
        // Scaled content: shrinks, fades and rounds as it is dragged up (big -> small),
        // and eases in when the viewer first opens.
        val dragUp = -dragOffset.value
        val fraction = (dragUp / 1600f).coerceIn(0f, 1f)
        val enterV = enter.value
        val scale = (1f - 0.7f * fraction) * (0.94f + 0.06f * enterV)
        val fade = (1f - 0.7f * fraction) * enterV
        val corner = 32f * fraction
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = dragOffset.value * 0.4f
                    alpha = fade
                    shape = RoundedCornerShape(corner.dp)
                    clip = corner > 0f
                },
        ) {
            // Story media: video plays to completion, photo/drawable shows as a still.
            when (val media = current.image) {
                is StoryImage.Video -> StoryVideo(
                    uri = media.uri,
                    paused = paused,
                    onProgress = { f ->
                        videoFraction = f
                        // First real frame of playback → now it's been watched.
                        if (f > 0f && !videoMarked) {
                            videoMarked = true
                            onViewed(personIndex, segment)
                        }
                    },
                    onFinished = { goNext() },
                    modifier = Modifier.fillMaxSize(),
                )
                is StoryImage.Url -> if (media.isVideo) {
                    StoryVideo(
                        uri = Uri.parse(media.url),
                        paused = paused,
                        onProgress = { f ->
                        videoFraction = f
                        // First real frame of playback → now it's been watched.
                        if (f > 0f && !videoMarked) {
                            videoMarked = true
                            onViewed(personIndex, segment)
                        }
                    },
                        onFinished = { goNext() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    StoryPhoto(media, Modifier.fillMaxSize())
                }
                else -> StoryPhoto(media, Modifier.fillMaxSize())
            }

            // Gestures over the media: tap edges to step, hold to pause, swipe
            // sideways to jump to another person's story.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(personIndex, segment) {
                        detectTapGestures(
                            onPress = { offset ->
                                // Hold pauses; on release, only a SHORT tap navigates —
                                // a long hold just resumes the same story (no "jump").
                                val downAt = System.currentTimeMillis()
                                paused = true
                                val released = tryAwaitRelease()
                                paused = false
                                val heldMs = System.currentTimeMillis() - downAt
                                if (released && heldMs < 250) {
                                    if (offset.x < size.width / 2f) goPrev() else goNext()
                                }
                            },
                        )
                    }
                    .pointerInput(personIndex) {
                        var dragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragX = 0f },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                dragX += dx
                            },
                            onDragEnd = {
                                if (dragX <= -SWIPE_PERSON_PX) nextPerson()
                                else if (dragX >= SWIPE_PERSON_PX) prevPerson()
                            },
                        )
                    },
            )

        // Top overlay: segmented progress bars + author + close.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                person.items.forEachIndexed { i, item ->
                    val fraction = when {
                        i < segment -> 1f
                        i == segment ->
                            if (current.image.isVideoStory()) videoFraction else progress.value
                        // Already watched in an earlier session: show it filled…
                        item.viewed -> 1f
                        else -> 0f
                    }
                    // …but grey, so "seen before" reads differently from "seen just now".
                    val fill = if (i < segment || i == segment) Color.White else {
                        if (item.viewed) Color.White.copy(alpha = 0.45f) else Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.28f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(50))
                                .background(fill),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoryPhoto(
                    photo = person.photo,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = person.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = relativeTime(current.createdAt),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (person.isMine) {
                    // Eye + count: who has watched this story.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                paused = true
                                showViewers = true
                            }
                            .padding(end = 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = "Penonton story",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            // 0 when nobody has watched yet (the author never counts
                            // themselves — the backend excludes author_id).
                            text = (viewCounts[current.id] ?: 0).toString(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Hapus story",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                paused = true
                                confirmDelete = true
                            },
                    )
                    Spacer(Modifier.width(18.dp))
                }
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onClose,
                        ),
                )
            }
        }
        } // end scaled content Box

        // Reply bar — only on someone else's story; sends a direct message.
        if (!person.isMine) {
            val authorId = person.id
            fun sendReply() {
                val text = replyText.trim()
                if (text.isEmpty() || replySending || !ApiConfig.ENABLED) return
                replySending = true
                scope.launch {
                    runCatching {
                        val convId = SyntraClient.createDirect(authorId)
                        // Embed the story image URL as a marker so the chat bubble can
                        // render a small blurred thumbnail of the exact story replied to.
                        // Format: STORYREPLY<0x1>url<0x1>text  (backend has no story-
                        // reply endpoint yet, so we ride on a normal text message).
                        val current = person.items.getOrNull(segment)
                        val storyUrl = (current?.image as? StoryImage.Url)?.url.orEmpty()
                        val body = if (storyUrl.isNotBlank()) {
                            "STORYREPLY$storyUrl$text"
                        } else {
                            text
                        }
                        SyntraClient.sendMessageRest(convId, body)
                    }.onSuccess {
                        replyText = ""
                        Toast.makeText(storyContext, "Balasan terkirim ke ${person.name}.", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(storyContext, "Gagal mengirim: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                    replySending = false
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Thumbnail of the exact segment being replied to, so it is clear
                // which photo/video the reply refers to. A video shows its poster
                // frame with a small play badge.
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    StoryPhoto(photo = current.image, modifier = Modifier.fillMaxSize())
                    if (current.image.isVideoStory()) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = "Balas ${person.name}…",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (replyText.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(NexusAccent, CircleShape)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { sendReply() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim balasan",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        if (showViewers) {
            StoryViewersDialog(
                count = viewCounts[current.id],
                viewers = viewers,
                onDismiss = {
                    showViewers = false
                    viewers = null
                    paused = false
                },
            )
        }

        if (confirmDelete) {
            StoryDeleteDialog(
                onDismiss = {
                    confirmDelete = false
                    paused = false
                },
                onConfirm = {
                    confirmDelete = false
                    paused = false
                    onDeleteStory(personIndex, segment)
                },
            )
        }
    }
}

/** Long-press action on a chat row. Works for direct chats and groups alike. */
@Composable
private fun DeleteConversationDialog(
    convo: Conversation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientAvatar(convo.gradient, convo.name.first().toString(), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = convo.name,
                        color = NexusTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Hapus percakapan", color = NexusTextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Percakapan ini akan dihapus dari daftar di perangkat ini. " +
                    "Riwayatnya masih tersimpan di server, jadi pesan baru akan " +
                    "memunculkannya kembali.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Batal",
                    color = NexusTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3A1620), RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onConfirm,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Hapus", color = Color(0xFFFF5D5D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Who watched this story. The count comes from `GET /stories/me`; the name list
 * needs `/stories/{id}/viewers`, which the server does not answer yet — so we show
 * the count and say plainly that names are unavailable rather than inventing them.
 */
@Composable
private fun StoryViewersDialog(
    count: Int?,
    viewers: List<NetStoryViewer>?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .padding(vertical = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 22.dp),
            ) {
                Icon(
                    Icons.Outlined.Visibility, null,
                    tint = NexusAccentSoft, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when (count) {
                        null -> "Dilihat"
                        else -> "Dilihat $count orang"
                    },
                    color = NexusTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(14.dp))

            when {
                !viewers.isNullOrEmpty() -> viewers.forEach { v ->
                    val name = v.displayName.ifBlank { v.username }.ifBlank { "Pengguna" }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        GradientAvatar(gradientFor(v.userId), name.first().toString(), 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, color = NexusTextPrimary, fontSize = 14.sp)
                            if (v.viewedAt.isNotBlank()) {
                                Text(
                                    relativeTime(v.viewedAt),
                                    color = NexusTextSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }

                count == 0 -> Text(
                    text = "Belum ada yang menonton story ini.",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )

                else -> Text(
                    text = "Server belum mengirim daftar namanya, jadi baru jumlahnya " +
                        "yang bisa ditampilkan.",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Tutup",
                color = NexusAccentSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
                    .padding(horizontal = 22.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun StoryDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Hapus story ini?", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Story akan dihapus sekarang, tidak menunggu 24 jam. " +
                    "Tindakan ini tidak bisa dibatalkan.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Batal",
                    color = NexusTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3A1620), RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onConfirm,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Hapus", color = Color(0xFFFF5D5D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

/** A shimmering placeholder chat row shown while the first load is in flight. */
@Composable
private fun ChatRowSkeleton() {
    val transition = rememberInfiniteTransition(label = "chat-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "sk-alpha",
    )
    val bar = NexusSurfaceElevated.copy(alpha = alpha)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(bar))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.45f).height(13.dp).clip(RoundedCornerShape(6.dp)).background(bar))
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(bar))
        }
    }
}

/** Fallback for when the QR scanner isn't available: type a username to start a chat. */
@Composable
private fun ManualUsernameDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
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
                .padding(36.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NexusSurfaceElevated)
                .border(1.dp, NexusStroke, RoundedCornerShape(20.dp))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(22.dp),
        ) {
            Text("Cari lewat username", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Scanner tidak tersedia di perangkat ini. Ketik username untuk memulai chat.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                    .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("@", color = NexusTextSecondary, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("username", color = NexusTextSecondary.copy(alpha = 0.6f), fontSize = 15.sp)
                    BasicTextField(
                        value = value,
                        onValueChange = { v -> value = v.filterNot { it.isWhitespace() }.lowercase() },
                        singleLine = true,
                        textStyle = TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(NexusAccentSoft),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (value.isBlank()) Color.White.copy(alpha = 0.08f) else NexusAccent)
                    .clickable(
                        enabled = value.isNotBlank(),
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSubmit(value.trim()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Mulai chat",
                    color = if (value.isBlank()) NexusTextSecondary else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 780)
@Composable
private fun ChatScreenPreview() {
    SyntraTheme {
        ChatScreen()
    }
}
