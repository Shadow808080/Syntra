package com.example.syntra

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.toArgb
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
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusOnline
import com.example.syntra.ui.theme.NexusRing
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.AvatarCache
import com.example.syntra.net.BlockStore
import com.example.syntra.net.MessageCache
import com.example.syntra.net.NetConversation
import com.example.syntra.net.NetMessage
import com.example.syntra.net.NetPresence
import com.example.syntra.net.NetStoryGroup
import com.example.syntra.net.NetStoryViewer
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.net.VideoCache
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
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
    /** Music stuck to this story (overlays.music) — actually played in the viewer. */
    val music: com.example.syntra.net.StoryMusic? = null,
)

@androidx.compose.runtime.Immutable
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

// @Immutable: every field is a `val` and a change produces a NEW instance (data
// `copy`), never an in-place mutation. Without this the `gradient: List<Color>`
// makes the compiler treat Conversation as unstable, so ConversationRow could
// never skip and re-ran on every list recomposition — the main chat-list jank.
@androidx.compose.runtime.Immutable
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
    // Newest message the peer has read; drives the blue ✓✓ indicator.
    val counterpartLastReadId: String? = null,
    // Newest message that reached the peer's device; drives grey ✓✓ vs a single ✓.
    val counterpartLastDeliveredId: String? = null,
    // Id of the last message — compared with the two marks above to decide whether
    // my last sent message is only sent (✓), delivered (✓✓), or read (blue ✓✓).
    val lastMessageId: String? = null,
    // Real profile photo of the counterpart / group, when the server knows one.
    val avatarUrl: String? = null,
    // True for group conversations — drives the group-specific overflow menu and
    // the group settings screen (members, add, kick, leave).
    val isGroup: Boolean = false,
)

// Stable placeholder gradients, picked from the id hash (align. doc §6).
private val gradientPalettes = listOf(
    listOf(Color(0xFF2E6BF0), Color(0xFF3B68F5)),
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
    message = unwrapMarkers(lastPreview).ifBlank { previewFallback(lastType) },
    time = formatClock(lastAt),
    unread = unreadCount,
    presence = Presence.NONE, // updated live via presence.update
    sent = lastSenderId != null && lastSenderId == SyntraClient.myUserId,
    counterpartId = counterpartId,
    counterpartUsername = counterpartUsername,
    counterpartLastReadId = counterpartLastReadId,
    counterpartLastDeliveredId = counterpartLastDeliveredId,
    lastMessageId = lastMessageId,
    // Only a real URL is usable; a bare media id stays null and falls back to
    // the letter tile until the photo is resolved (see resolveAvatars).
    avatarUrl = avatarMediaId?.takeIf { it.startsWith("http") },
    isGroup = type == "group",
)

/**
 * The newer of two nullable UUIDv7 ids. Ids sort lexicographically in time order,
 * so plain string comparison picks the newer; nulls lose. Used to advance the
 * read/delivered marks on a chat row without ever moving them backwards.
 */
private fun maxUuid(a: String?, b: String?): String? = when {
    a == null -> b
    b == null -> a
    a >= b -> a
    else -> b
}

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
                music = it.music,
            )
        },
    )
}

private fun previewFallback(type: String): String = when (type) {
    "image" -> "[Foto]"
    "gif" -> "[GIF]"
    "video" -> "[Video]"
    "audio", "voice_note" -> "[Pesan suara]"
    "media" -> "[Media]"
    else -> ""
}

// Chat bodies can carry app-internal markers (a big-emoji sticker, a view-once
// photo, a story reply). Those must never leak raw into the home preview — they're
// turned into a short label instead. The separator is 0x01 (see ChatDetailScreen).
private const val STICKER_MARKER = "STICKER"
private const val VIEW_ONCE_MARKER = "VIEWONCE"
private const val STORY_REPLY_MARKER = "STORYREPLY"

/** Home-list preview for a view-once photo, and for one whose single view is spent. */
private const val VIEW_ONCE_PREVIEW = "[Foto 1x]"
private const val VIEW_ONCE_OPENED_PREVIEW = "[sudah dibuka]"

private const val MARKER_SEP = '\u0001'

/** Turn a raw stored body into a home-list preview, unwrapping our markers. */
private fun unwrapMarkers(body: String): String = when {
    body.startsWith(STICKER_MARKER + MARKER_SEP) -> "[Stiker]"
    body.startsWith(VIEW_ONCE_MARKER + MARKER_SEP) -> VIEW_ONCE_PREVIEW
    body.startsWith(STORY_REPLY_MARKER + MARKER_SEP) -> "[Balasan story]"
    else -> body
}

/**
 * Preview text for a LIVE incoming message. The socket carries a generic
 * `type` = "media" for attachments, so when there's no body we infer the kind
 * from the attachment URL's extension to still show [Foto]/[GIF]/[Video]/[Pesan suara].
 */
private fun livePreview(m: NetMessage): String {
    if (m.body.isNotBlank()) return unwrapMarkers(m.body)
    val url = m.attachments.firstOrNull().orEmpty().substringBefore('?').lowercase()
    return when {
        url.isBlank() -> previewFallback(m.type)
        url.endsWith(".m4a") || url.endsWith(".mp3") || url.endsWith(".aac") || url.endsWith(".ogg") || url.endsWith(".wav") -> "[Pesan suara]"
        url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".webm") || url.endsWith(".mkv") || url.endsWith(".3gp") -> "[Video]"
        url.endsWith(".gif") -> "[GIF]"
        url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".webp") -> "[Foto]"
        else -> "[Media]"
    }
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

/** Downloads a remote image into a Bitmap (for a music story's album art). */
private suspend fun loadRemoteBitmap(context: Context, url: String): android.graphics.Bitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val loader = coil.ImageLoader(context)
            val req = coil.request.ImageRequest.Builder(context).data(url).allowHardware(false).build()
            (loader.execute(req) as? coil.request.SuccessResult)?.drawable
                ?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
        }.getOrNull()
    }

/** Uploads a locally created story to the backend (media 3-step + POST /stories). */
private suspend fun uploadStory(
    context: Context,
    media: StoryImage,
    music: com.example.syntra.net.StoryMusic? = null,
) {
    when (media) {
        is StoryImage.Bitmap -> {
            val bmp = media.image.asAndroidBitmap()
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            val id = SyntraClient.uploadMedia(
                "image", "jpg", "image/jpeg", out.toByteArray(), bmp.width, bmp.height,
            )
            SyntraClient.createStory(id, music = music)
        }
        is StoryImage.Video -> {
            val bytes = context.contentResolver.openInputStream(media.uri)?.use { it.readBytes() } ?: return
            val id = SyntraClient.uploadMedia("video", "mp4", "video/mp4", bytes)
            SyntraClient.createStory(id, music = music)
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
    // Deep-link from a message notification: MainActivity parks the target chat id in
    // ChatNavRequest; when it appears, open that chat. If it isn't in the loaded list
    // yet (cold start), pull conversations first, then open it.
    LaunchedEffect(ChatNavRequest.conversationId, chats.size) {
        val cid = ChatNavRequest.conversationId ?: return@LaunchedEffect
        val hit = chats.firstOrNull { it.id == cid }
            ?: runCatching { SyntraClient.getConversations() }.getOrNull()
                ?.firstOrNull { it.id == cid }?.toUi()
        if (hit != null) {
            openedChat = hit
            ChatNavRequest.conversationId = null
        }
    }
    // Typing sync: remembers each row's real online state while it shows "typing…"
    // (so it restores correctly on stop), and the last typing time for auto-clear.
    val onlineWhenIdle = remember { mutableStateMapOf<String, Boolean>() }
    val typingClears = remember { mutableStateMapOf<String, Long>() }
    // Safety net: if a typing-stop event is ever missed, drop the "typing…" state a
    // few seconds after the last typing signal so a row can't be stuck typing.
    LaunchedEffect(typingClears.toMap()) {
        val stuck = typingClears.filterValues { System.currentTimeMillis() - it > 6000 }
        if (stuck.isEmpty()) {
            if (typingClears.isNotEmpty()) { delay(3000) }
            return@LaunchedEffect
        }
        stuck.keys.forEach { cid ->
            val idx = chats.indexOfFirst { it.id == cid }
            if (idx >= 0 && chats[idx].presence == Presence.TYPING) {
                chats[idx] = chats[idx].copy(
                    presence = if (onlineWhenIdle[cid] == true) Presence.ONLINE else Presence.NONE,
                )
            }
            typingClears.remove(cid)
        }
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
    var showMusicStoryPicker by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pendingVideo by remember { mutableStateOf<android.net.Uri?>(null) }
    // Conversation the user long-pressed and may want to remove.
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    // Multi-select: long-press ticks a chat and swaps the overflow menu for actions.
    val selection = remember { mutableStateListOf<String>() }
    var archivedIds by remember { mutableStateOf(emptySet<String>()) }
    var pinnedIds by remember { mutableStateOf(emptySet<String>()) }
    var showArchived by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDiscover by remember { mutableStateOf(false) }
    var openProfileUser by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collage: pick several photos, compose them into one portrait bitmap, then send
    // it through the normal photo-story preview.
    val collagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val bitmaps = uris.mapNotNull { runCatching { loadStoryBitmap(context, it)?.asAndroidBitmap() }.getOrNull() }
            if (bitmaps.isNotEmpty()) pendingPhoto = buildCollage(bitmaps)
        }
    }

    LaunchedEffect(Unit) {
        archivedIds = ChatFlags.archived(context)
        pinnedIds = ChatFlags.pinned(context)
    }

    // Tell the host pager whenever something covers the whole screen.
    val overlayOpen = openedStory != null || openedChat != null ||
        showAddStatus || showNewGroup || showSettings || showTextStory ||
        showDiscover || openProfileUser != null || pendingPhoto != null ||
        pendingVideo != null
    LaunchedEffect(overlayOpen) { onOverlayChange(overlayOpen) }

    // Pull-to-refresh state for the chat list.
    var refreshing by remember { mutableStateOf(false) }
    var firstLoadDone by remember { mutableStateOf(false) }
    // username -> resolved photo URL, so the list endpoint's missing avatars are
    // only looked up once per session.
    val avatarCache = remember { mutableStateMapOf<String, String>() }

    // Re-applies a previously-resolved counterpart photo. The /conversations endpoint
    // does NOT return the peer's avatar, so every list rebuild (refresh, a new message,
    // a socket event) would otherwise drop a photo we already resolved — the "avatar
    // keeps disappearing on the home" bug. Run every fresh Conversation through this so
    // the cached photo sticks across rebuilds instead of falling back to the blank tile.
    fun Conversation.withCachedAvatar(): Conversation {
        // BOTH ids. The rooms screens only ever see a user id, so storing a photo
        // under the username alone left them with letter tiles for people whose
        // picture was sitting right there in the chat list.
        val keys = listOfNotNull(counterpartUsername, counterpartId).filter { it.isNotBlank() }
        // Blocked: strip the photo. Their name stays (you need to know whose chat this
        // is to unblock them), but their face does not — even from a chat you had
        // before the block, which is the case the user actually notices.
        if (BlockStore.isBlocked(context, counterpartUsername, counterpartId)) {
            return copy(avatarUrl = null)
        }
        if (!avatarUrl.isNullOrBlank()) {
            keys.forEach { AvatarCache.put(context, it, avatarUrl) }
            return this
        }
        if (keys.isEmpty()) return this
        // Session map first (hot), then the persisted store — this is the lookup that
        // used to start empty on every re-entry and leave the home full of blanks.
        val cached = keys.firstNotNullOfOrNull { avatarCache[it] ?: AvatarCache.get(context, it) }
        return if (!cached.isNullOrBlank()) copy(avatarUrl = cached) else this
    }

    // Reloads chats + stories from the backend. Shared by first load and pull-to-refresh.
    suspend fun refresh() {
        if (!ApiConfig.ENABLED) return
        runCatching {
            val convs = SyntraClient.getConversations()
            chats.clear(); chats.addAll(convs.map { it.toUi().withCachedAvatar() })
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
        // Avatars are NO LONGER pre-fetched for every conversation here — that fired
        // a getUser request per contact the moment the list opened ("downloads
        // everything at once"). Instead each row resolves its own photo lazily, only
        // when it scrolls into view (see resolveAvatarFor + ConversationRow). Until
        // then the row shows its letter tile, so the list feels instant.

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

    // Resolve ONE row's avatar on demand — called when that row scrolls into view, so
    // opening the list never fires a request per conversation up front. Cache-first; a
    // known-missing result is cached as "" so a row scrolling in and out doesn't
    // re-request. Pull-to-refresh clears the cache to pick up a changed photo.
    fun resolveAvatarFor(convo: Conversation) {
        val username = convo.counterpartUsername
        if (username.isNullOrBlank()) return
        // Persisted first: a photo resolved in an earlier session means no lookup now.
        val cached = avatarCache[username] ?: AvatarCache.get(context, username)
        if (cached != null) {
            if (cached.isNotBlank() && convo.avatarUrl != cached) {
                val i = chats.indexOfFirst { it.id == convo.id }
                if (i >= 0) chats[i] = chats[i].copy(avatarUrl = cached)
            }
            return
        }
        scope.launch {
            val url = runCatching { SyntraClient.getUser(username) }.getOrNull()
                ?.avatarMediaId?.takeIf { it.startsWith("http") }
            avatarCache[username] = url ?: ""
            AvatarCache.put(context, username, url)
            if (!url.isNullOrBlank()) {
                val i = chats.indexOfFirst { it.id == convo.id }
                if (i >= 0 && chats[i].avatarUrl != url) chats[i] = chats[i].copy(avatarUrl = url)
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
                                    chats.add(0, fresh.toUi().withCachedAvatar())
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
                            message = livePreview(message),
                            time = formatClock(message.createdAt),
                            // CRUCIAL for the checkmark: advance lastMessageId to THIS
                            // message. Without it the row keeps comparing an older,
                            // already-read message id against counterpartLastReadId and
                            // stays stuck on blue ✓✓ even though the new message is
                            // unread. A fresh message id (> the peer's read/delivered
                            // marks) correctly reads as single ✓ until receipts arrive.
                            lastMessageId = message.id,
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
                                chats[i] = fresh.toUi().withCachedAvatar().copy(presence = chats[i].presence)
                            } else {
                                chats.add(0, fresh.toUi().withCachedAvatar())
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
                    // Record it by user id too, so the rooms screens (which only know
                    // ids) pick the new photo up as well.
                    AvatarCache.put(context, userId, avatarUrl)
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
                    val idx = chats.indexOfFirst { it.id == conversationId }
                    if (idx < 0) return
                    if (userId == SyntraClient.myUserId) {
                        // I read it (possibly on another device): clear my badge.
                        if (chats[idx].unread > 0) chats[idx] = chats[idx].copy(unread = 0)
                    } else {
                        // The peer read my last message: turn the row's ✓✓ blue live.
                        // Read implies delivered, so advance both marks (monotonic).
                        val c = chats[idx]
                        chats[idx] = c.copy(
                            counterpartLastReadId = maxUuid(c.counterpartLastReadId, messageId),
                            counterpartLastDeliveredId = maxUuid(c.counterpartLastDeliveredId, messageId),
                        )
                    }
                }

                override fun onDeliveredReceipt(conversationId: String, userId: String, messageId: String) {
                    // The peer's device received my message: turn the row's single ✓
                    // into grey ✓✓ live, without waiting for a reload. Ignore my own.
                    if (userId == SyntraClient.myUserId) return
                    val idx = chats.indexOfFirst { it.id == conversationId }
                    if (idx < 0) return
                    val c = chats[idx]
                    chats[idx] = c.copy(
                        counterpartLastDeliveredId = maxUuid(c.counterpartLastDeliveredId, messageId),
                    )
                }

                override fun onPresence(presence: NetPresence) {
                    val idx = chats.indexOfFirst { it.counterpartId == presence.userId }
                    if (idx >= 0) {
                        // Don't stomp a live "typing" state with a presence tick; keep
                        // TYPING until the typing-stop event clears it.
                        if (chats[idx].presence == Presence.TYPING) {
                            onlineWhenIdle[chats[idx].id] = presence.online
                        } else {
                            chats[idx] = chats[idx].copy(
                                presence = if (presence.online) Presence.ONLINE else Presence.NONE,
                            )
                        }
                    }
                }

                override fun onTyping(conversationId: String, userId: String, typing: Boolean) {
                    if (userId == SyntraClient.myUserId) return
                    val idx = chats.indexOfFirst { it.id == conversationId }
                    if (idx < 0) return
                    if (typing) {
                        // Remember the underlying online state so we can restore it when
                        // typing stops, then show "sedang mengetik…" on the row live.
                        if (chats[idx].presence != Presence.TYPING) {
                            onlineWhenIdle[conversationId] = chats[idx].presence == Presence.ONLINE
                        }
                        chats[idx] = chats[idx].copy(presence = Presence.TYPING)
                        typingClears[conversationId] = System.currentTimeMillis()
                    } else {
                        val wasOnline = onlineWhenIdle[conversationId] == true
                        chats[idx] = chats[idx].copy(
                            presence = if (wasOnline) Presence.ONLINE else Presence.NONE,
                        )
                    }
                }

                override fun onStoryNew(storyId: String, authorId: String) {
                    // A followed user posted a story — refresh the rail live so their
                    // ring appears without the user pulling to refresh.
                    if (authorId == SyntraClient.myUserId) return
                    scope.launch { refresh() }
                }

                override fun onReconnect() {
                    scope.launch {
                        runCatching {
                            val convs = SyntraClient.getConversations()
                            chats.clear(); chats.addAll(convs.map { it.toUi().withCachedAvatar() })
                            SyntraClient.subscribe(convs.map { "conversation:${it.id}" })
                        }
                    }
                    // Re-seed presence after a reconnect so online dots aren't stale.
                    scope.launch { runCatching { SyntraClient.presenceQuery(chats.mapNotNull { it.counterpartId }) } }
                }
            }
            SyntraClient.addListener(listener)
            onDispose { SyntraClient.removeListener(listener) }
        }
    }

    fun addStory(media: StoryImage, music: com.example.syntra.net.StoryMusic? = null) {
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
            runCatching { uploadStory(context, media, music) }
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

    // Local view flags: archived hide behind a toggle, pinned float to the top.
    //
    // Blocked conversations deliberately STAY in the list. Unblocking is offered
    // inside the chat itself, so hiding the row would strand the user with no way to
    // undo except Settings. What the block removes is their PHOTO (see
    // `withCachedAvatar` / Conversation.blocked), not the row.
    val notBlocked = filtered
    val archivedCount = notBlocked.count { it.id in archivedIds }
    // Archived conversations are NOT mixed into the home list any more. They used to
    // be revealed in place by a text link at the very BOTTOM of the list — which meant
    // you had to scroll past every chat to find the thing that hides chats, and once
    // expanded, archived rows sat indistinguishably among the live ones.
    val visible = notBlocked
        .filter { it.id !in archivedIds }
        .sortedByDescending { it.id in pinnedIds }
    val archivedChats = notBlocked
        .filter { it.id in archivedIds }
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
                            picked.forEach { convo ->
                                BlockStore.add(context, convo.counterpartUsername, convo.counterpartId)
                                convo.counterpartUsername?.takeIf { it.isNotBlank() }?.let { u ->
                                    SyntraClient.fireAndForget { SyntraClient.blockUser(u) }
                                }
                            }
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
                // No hide-on-scroll here: the bottom bar must stay put on the home
                // list (that auto-hide is a Shorts-only behaviour). ShortsScreen drives
                // BottomBarVisibility itself.
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // First-load skeleton: shimmering placeholder rows instead of a
                // blank screen (or a heavy spinner) while chats stream in.
                if (!firstLoadDone && chats.isEmpty() && !searching) {
                    items(8) { ChatRowSkeleton() }
                    return@LazyColumn
                }
                // Archive entry — an icon row at the TOP, the way every messenger
                // does it, so the way into the archive is the first thing you see
                // rather than the last.
                if (archivedCount > 0 && !searching) {
                    item(key = "archive-entry") {
                        ArchiveEntryRow(
                            count = archivedCount,
                            onClick = { showArchived = true },
                        )
                    }
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
                itemsIndexed(
                    visible,
                    key = { _, convo -> convo.id },
                    // All body rows share one layout type, so Compose reuses the same
                    // node when recycling during a fling instead of rebuilding it.
                    contentType = { _, _ -> "conversation" },
                ) { _, convo ->
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
                        onFirstVisible = { resolveAvatarFor(convo) },
                    )
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
                // Fresh account: no chats to show. A warm invite beats a blank void.
                if (!searching && firstLoadDone && visible.isEmpty()) {
                    item {
                        ChatHomeEmpty(
                            noStories = stories.none { !it.isMine },
                            onDiscover = { showDiscover = true },
                        )
                    }
                }
            }
            }
        }

        // Floating action stack, bottom-right: "find people" sits ABOVE the camera.
        // People-add moved here out of the header (it was one icon among four up top);
        // as a secondary FAB it's neutral-toned so the gradient camera stays primary.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // People+ (find people) — secondary FAB, above the camera.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(NexusSurface, CircleShape)
                    .border(1.dp, NexusStroke, CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { showDiscover = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAddAlt,
                    contentDescription = "Cari orang",
                    // Neutral, not accent. This is the SECONDARY action sitting right
                    // above the camera FAB — two blue circles stacked read as equals,
                    // and the eye had no way to tell which one was the main one.
                    tint = NexusTextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
            // Camera (add story) — primary FAB.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.verticalGradient(listOf(NexusAccentSoft, NexusAccent)),
                        shape = CircleShape,
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { showAddStatus = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AddAPhoto,
                    contentDescription = "Tambah story",
                    tint = Color.White,
                    modifier = Modifier.size(23.dp),
                )
            }
        }

        // Stray-tap guard. Every full-screen page here (story viewer, chat detail,
        // discover, settings, profile…) is drawn as an overlay ON TOP of the chat
        // list — but a bare overlay background does NOT consume touches in Compose, so
        // a tap on any empty gap would fall through and hit a chat row behind it
        // ("dipencet lain malah halaman lain"). This invisible layer sits just under
        // the overlays and swallows anything they don't handle themselves.
        if (overlayOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures {} },
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
                    // The backend DOES have this — DELETE /conversations/{id} (the
                    // delete_conversation RPC). It was never called, so the row was
                    // only dropped from the in-memory list and the next refresh
                    // brought it straight back.
                    val targets =
                        if (selection.isEmpty()) listOf(convo) else chats.filter { it.id in selection }
                    selection.clear()
                    // AWAIT the server, and only drop the row once it agrees.
                    //
                    // This used to be fireAndForget, which swallows every error: the
                    // row vanished, the request 404'd, and the next refresh brought the
                    // conversation back. The delete looked like it worked right up
                    // until it visibly didn't — the worst possible failure mode.
                    scope.launch {
                        val failed = mutableListOf<Conversation>()
                        targets.forEach { c ->
                            val ok = !ApiConfig.ENABLED ||
                                runCatching { SyntraClient.deleteConversation(c.id) }.isSuccess
                            if (ok) {
                                MessageCache.clearConversation(context, c.id)
                                chats.remove(c)
                            } else {
                                failed += c
                            }
                        }
                        val msg = when {
                            failed.isEmpty() -> "Percakapan dihapus."
                            failed.size == targets.size -> "Gagal menghapus percakapan."
                            else -> "${failed.size} percakapan gagal dihapus."
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        if (showArchived) {
            ArchivedChatsScreen(
                chats = archivedChats,
                onOpen = { showArchived = false; openChat(it) },
                onUnarchive = { convo ->
                    ChatFlags.setArchived(context, listOf(convo.id), false)
                    archivedIds = ChatFlags.archived(context)
                    // Leaving the screen when it empties, so the user isn't left
                    // staring at an "empty archive" they have to back out of.
                    if (archivedIds.isEmpty()) showArchived = false
                },
                onUnarchiveAll = {
                    ChatFlags.setArchived(context, archivedChats.map { it.id }, false)
                    archivedIds = ChatFlags.archived(context)
                    showArchived = false
                },
                onClose = { showArchived = false },
            )
        }

        if (showNewGroup) {
            NewGroupScreen(
                onClose = { showNewGroup = false },
                onCreated = { id, name ->
                    showNewGroup = false
                    val convo = Conversation(id = id, name = name, message = "", time = "", isGroup = true)
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
                    // Photos → preview/edit; videos → trim (≤15s); both before posting.
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    if (mime.startsWith("video")) {
                        showAddStatus = false; pendingVideo = uri
                    } else {
                        loadStoryBitmap(context, uri)?.let { showAddStatus = false; pendingPhoto = it.asAndroidBitmap() }
                    }
                },
                onCaptureBitmap = { bmp -> showAddStatus = false; pendingPhoto = bmp },
                onTextStory = { showAddStatus = false; showTextStory = true },
                onMusicStory = { showAddStatus = false; showMusicStoryPicker = true },
                onCollage = { showAddStatus = false; collagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                ) },
            )
        }

        // "Musik": pick a song, render a music-story card, post it (with the track).
        if (showMusicStoryPicker) {
            MusicPickerSheet(
                onDismiss = { showMusicStoryPicker = false },
                onPick = { music ->
                    showMusicStoryPicker = false
                    scope.launch {
                        val art = music.artworkUrl?.let { runCatching { loadRemoteBitmap(context, it) }.getOrNull() }
                        val card = buildMusicStory(music, art)
                        addStory(StoryImage.Bitmap(card.asImageBitmap()), music)
                    }
                },
            )
        }

        // Photo preview + light edit (caption) before posting. Back → reopen the
        // picker so you can choose a different photo.
        pendingPhoto?.let { bmp ->
            PhotoStoryPreview(
                photo = bmp,
                onCancel = { pendingPhoto = null; showAddStatus = true },
                onDone = { edited, music ->
                    pendingPhoto = null
                    addStory(StoryImage.Bitmap(edited.asImageBitmap()), music)
                },
            )
        }

        // Video preview + trim to ≤15s before posting. Back → reopen the picker.
        pendingVideo?.let { uri ->
            VideoTrimScreen(
                uri = uri,
                onCancel = { pendingVideo = null; showAddStatus = true },
                onDone = { trimmed ->
                    pendingVideo = null
                    extractVideoThumbnail(context, trimmed)?.let {
                        addStory(StoryImage.Video(trimmed, it))
                    }
                },
            )
        }

        // Text-story composer → renders the text to a bitmap and posts it as an
        // image story (reuses the normal upload path).
        if (showTextStory) {
            TextStoryScreen(
                // Back returns to the media picker (foto/video/teks), not all the way out.
                onClose = { showTextStory = false; showAddStatus = true },
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
            // Order: search · overflow. "Find people" moved to a FAB above the camera
            // (bottom-right), so it isn't crowded in with the top-bar icons.
            HeaderIcon(Icons.Filled.Search, "Search", size = 28.dp, onClick = onStartSearch)
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                HeaderIcon(Icons.Filled.MoreVert, "Menu", size = 28.dp) { menuOpen = true }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    listOf("New group", "Settings").forEach { label ->
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
    // No stories at all → render nothing (the FAB is the way to post one). Avoids a
    // lone "Cerita" header hovering over an empty rail.
    if (people.isEmpty()) return
    // How many people (not counting you) still have an unwatched story — the number
    // that makes the rail worth glancing at.
    val freshCount = people.count { !it.isMine && it.items.any { s -> !s.viewed } }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section label — gives the rail a title instead of floating loose avatars.
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cerita",
                color = NexusTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            if (freshCount > 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NexusAccent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "$freshCount baru",
                        color = NexusAccentSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        // The rail sits over a slow, wavy AURORA that flows the full width BEHIND the
        // avatars (drawn first, so it never covers the story UI — only peeks through
        // the gaps between profiles).
        Box(modifier = Modifier.fillMaxWidth()) {
            StoryAuroraBackground(modifier = Modifier.matchParentSize())
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
            itemsIndexed(people, key = { _, person -> person.id }) { index, person ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StoryAvatar(
                        photo = person.photo,
                        size = 58.dp,
                        posts = person.posts,
                        // Per-segment: watched stories dim, unwatched stay lit. Watching
                        // updates each item's `viewed`, so this reflects progress live.
                        viewedCount = person.items.count { it.viewed },
                        onClick = { onStoryClick(index) },
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = if (person.isMine) "Kamu" else person.name,
                        color = if (person.isMine) NexusTextPrimary else NexusTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (person.isMine) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(64.dp),
                    )
                }
            }
            }
        }
    }
}

/**
 * A slow, wavy aurora that flows the full width behind the story rail. Three soft,
 * translucent ribbons drift on their own phases so they weave and undulate. Drawn
 * behind the avatars (very low alpha), it peeks through the gaps without ever
 * covering the story UI.
 */
@Composable
private fun StoryAuroraBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "story-aurora")
    val twoPi = (2.0 * Math.PI).toFloat()
    val p1 by t.animateFloat(0f, twoPi, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "a1")
    val p2 by t.animateFloat(0f, twoPi, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "a2")
    val p3 by t.animateFloat(0f, twoPi, infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "a3")

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun ribbon(centerY: Float, amp: Float, waves: Float, phase: Float, thickness: Float): androidx.compose.ui.graphics.Path {
            val path = androidx.compose.ui.graphics.Path()
            val steps = 40
            for (i in 0..steps) {
                val x = w * i / steps
                val y = centerY + (kotlin.math.sin(phase + i.toFloat() / steps * waves * twoPi) * amp)
                if (i == 0) path.moveTo(x, y - thickness / 2f) else path.lineTo(x, y - thickness / 2f)
            }
            for (i in steps downTo 0) {
                val x = w * i / steps
                val y = centerY + (kotlin.math.sin(phase + i.toFloat() / steps * waves * twoPi) * amp)
                path.lineTo(x, y + thickness / 2f)
            }
            path.close()
            return path
        }

        // Three woven aurora ribbons. Soft vertical gradients (transparent → tint →
        // transparent) make each ribbon a glow rather than a hard band.
        //
        // The tints are DERIVED from the theme accent, not fixed. They used to be a
        // hardcoded blue/teal/periwinkle, so the header kept glowing blue behind a
        // Forest or Sunset theme — the one part of the screen that ignored the user's
        // choice. Two of the three are hue-shifted off the accent so the ribbons still
        // read as separate layers instead of one flat wash.
        val a1 = NexusAccentSoft
        val a2 = shiftHue(NexusAccent, 34f)
        val a3 = NexusAccent
        drawPath(
            ribbon(h * 0.42f, h * 0.16f, 1.4f, p1, h * 0.5f),
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, a1.copy(alpha = 0.22f), Color.Transparent),
            ),
        )
        drawPath(
            ribbon(h * 0.58f, h * 0.20f, 1.1f, p2, h * 0.55f),
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, a2.copy(alpha = 0.18f), Color.Transparent),
            ),
        )
        drawPath(
            ribbon(h * 0.5f, h * 0.18f, 1.7f, p3, h * 0.42f),
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, a3.copy(alpha = 0.2f), Color.Transparent),
            ),
        )
    }
}

/** Rotates a colour's hue, keeping saturation and value — used to derive the aurora's
 *  secondary ribbon tints from whatever accent the theme supplies. */
private fun shiftHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// ---------------------------------------------------------------------------
// Conversation row
// ---------------------------------------------------------------------------

/** Corner presence badge on an avatar: a coloured dot with a card-coloured gap ring. */
@Composable
private fun BoxScope.PresenceDot(color: Color, ringColor: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(15.dp)
            .background(ringColor, CircleShape)
            .padding(2.5.dp)
            .background(color, CircleShape),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    convo: Conversation,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFirstVisible: () -> Unit = {},
) {
    // Fires when this row enters composition (i.e. scrolls into view). Used to resolve
    // the avatar lazily instead of pre-fetching every conversation's photo up front.
    LaunchedEffect(convo.id) { if (convo.avatarUrl.isNullOrBlank()) onFirstVisible() }

    val unread = convo.unread > 0
    val online = convo.presence == Presence.ONLINE
    val typing = convo.presence == Presence.TYPING

    // A view-once photo previews as "[Foto 1x]" until its single view is spent, then
    // as "[sudah dibuka]" — matching the bubble inside the chat. Read straight from the
    // (observable) store, NOT via remember, so it flips the moment the photo is opened
    // — on the recipient's device, and on the sender's when the peer opens it.
    val context = LocalContext.current
    val lastId = convo.lastMessageId
    val preview = if (convo.message == VIEW_ONCE_PREVIEW && lastId != null &&
        com.example.syntra.net.ViewOnceStore.isSpent(context, lastId, convo.sent)
    ) {
        VIEW_ONCE_OPENED_PREVIEW
    } else {
        convo.message
    }

    // Flat, plain rows — no card fill, no border. Only a picked row (selection mode)
    // gets a soft accent wash so it's clearly marked.
    val rowBg = if (selected) NexusAccent.copy(alpha = 0.14f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(rowBg)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar + presence badge. An unmistakable green dot at the corner when
            // the person is online (live-updated via presence.update / presence.query).
            Box(contentAlignment = Alignment.Center) {
                GradientAvatar(
                    gradient = convo.gradient,
                    initial = convo.name.first().toString(),
                    size = 54.dp,
                    photoUrl = convo.avatarUrl,
                )
                when {
                    selected -> Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .background(NexusAccent, CircleShape)
                            .border(2.dp, NexusBackground, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    // Green = online now. Accent = away/typing but present. The gap
                    // ring is the page background (rows are flat, no card behind).
                    online -> PresenceDot(NexusOnline, NexusBackground)
                    typing -> PresenceDot(NexusAccentSoft, NexusBackground)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = convo.name,
                        color = NexusTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // weight(1f) pushes the time to the far right — name left, time
                        // right (justify-between); the name ellipsizes if it's too long.
                        modifier = Modifier.weight(1f),
                    )
                    if (pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Disematkan",
                            tint = NexusTextSecondary,
                            modifier = Modifier.padding(start = 6.dp).size(13.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = convo.time,
                        color = if (unread) NexusAccentSoft else NexusTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (typing) "sedang mengetik…" else preview,
                        color = if (typing) NexusAccentSoft else if (unread) NexusTextPrimary.copy(alpha = 0.85f) else NexusTextSecondary,
                        fontStyle = if (typing) FontStyle.Italic else FontStyle.Normal,
                        fontSize = 14.sp,
                        fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // weight(1f) pushes whatever sits to the right (delivery check or
                        // the unread badge) to the far edge — justified against the text.
                        modifier = Modifier.weight(1f),
                    )
                    if (unread) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
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
                                style = androidx.compose.ui.text.TextStyle(
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                    lineHeight = 11.sp,
                                ),
                            )
                        }
                    } else if (convo.sent && !typing) {
                        // My last message's delivery state, pushed to the far right so it
                        // sits justified against the message text (WhatsApp-style).
                        //   read (blue ✓✓) = peer's read mark covers my last message
                        //   delivered (grey ✓✓) = peer online now, or a delivered mark
                        //   sent (single ✓) = only reached the server
                        val lastId = convo.lastMessageId
                        val read = lastId != null && convo.counterpartLastReadId != null &&
                            lastId <= convo.counterpartLastReadId
                        val delivered = online ||
                            (lastId != null && convo.counterpartLastDeliveredId != null &&
                                lastId <= convo.counterpartLastDeliveredId)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = if (read || delivered) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = when {
                                read -> "Dibaca"; delivered -> "Sampai"; else -> "Terkirim"
                            },
                            tint = if (read) Color(0xFF7FE3FF) else NexusTextSecondary,
                            modifier = Modifier.size(15.dp),
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
    // A story with anything still unwatched is "alive": it gets a pulsing aura and a
    // slowly orbiting gradient ring. Fully-watched ones fall calm (a plain dim ring),
    // so the live ones genuinely stand out on the rail.
    val hasUnwatched = viewedCount < posts

    // Tactile press: the avatar dips and springs back — a soft, connective motion.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "story-press",
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clickable(
                indication = null,
                interactionSource = interaction,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (hasUnwatched) {
            FreshStoryRing(segments = posts, viewedCount = viewedCount, modifier = Modifier.fillMaxSize())
        } else {
            SeenStoryRing(segments = posts, modifier = Modifier.fillMaxSize())
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

/**
 * The "alive" story ring: a soft accent aura that breathes, wrapped by a segmented
 * brand-gradient ring that orbits slowly. Watched segments stay dim; unwatched ones
 * carry the gradient — so partial progress still reads while the whole thing glows.
 */
@Composable
private fun FreshStoryRing(
    segments: Int,
    viewedCount: Int,
    modifier: Modifier = Modifier,
) {
    val t = rememberInfiniteTransition(label = "story-aura")
    // No spinning — just a calm, slow breath for the aura glow.
    val pulse by t.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            tween(1900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "story-pulse",
    )

    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val stroke = 3.dp.toPx()
        val arcSize = Size(w - stroke, h - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val segs = segments.coerceAtLeast(1)
        val gap = if (segs == 1) 0f else 10f
        val sweep = (360f - gap * segs) / segs

        // Aura — a soft accent glow that hugs the ring and fades both ways, breathing.
        drawCircle(
            brush = Brush.radialGradient(
                0.5f to Color.Transparent,
                0.85f to NexusAccentSoft.copy(alpha = pulse * 0.55f),
                1.0f to Color.Transparent,
                center = center,
                radius = w / 2f,
            ),
            radius = w / 2f,
            center = center,
        )

        // Static segmented gradient ring (no rotation) — the aura does the moving.
        val ringBrush = Brush.sweepGradient(
            listOf(NexusRing, NexusAccentSoft, NexusRing),
            center = center,
        )
        var start = -90f
        repeat(segs) { i ->
            val watched = i < viewedCount
            if (watched) {
                drawArc(
                    color = StorySeenRing,
                    startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            } else {
                drawArc(
                    brush = ringBrush,
                    startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            start += sweep + gap
        }
    }
}

/** The calm, fully-watched ring: plain dim segments, no aura, no motion. */
@Composable
private fun SeenStoryRing(segments: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 3.dp.toPx()
        val arcSize = Size(w - stroke, h - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val segs = segments.coerceAtLeast(1)
        val gap = if (segs == 1) 0f else 10f
        val sweep = (360f - gap * segs) / segs
        var start = -90f
        repeat(segs) {
            drawArc(
                color = StorySeenRing,
                startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            start += sweep + gap
        }
    }
}

/** Renders a [StoryImage] from either a drawable resource or a picked bitmap. */
@Composable
private fun StoryPhoto(
    photo: StoryImage,
    modifier: Modifier = Modifier,
    // Full-screen story media passes Fit so the WHOLE picture is contained (never
    // cropped); avatars keep the default Crop.
    contentScale: ContentScale = ContentScale.Crop,
) {
    when (photo) {
        is StoryImage.Res -> Image(
            painter = painterResource(photo.id),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
        is StoryImage.Bitmap -> Image(
            bitmap = photo.image,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
        is StoryImage.Video -> Image(
            // Static thumbnail; the full-screen viewer plays the actual video.
            bitmap = photo.thumbnail,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
        is StoryImage.Url -> Box(modifier) {
            // Breathing placeholder while the photo streams in — never a black gap.
            ShimmerFill(Modifier.matchParentSize())
            AsyncImage(
                model = photo.url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A centered "now playing" card for a story's song: artwork, title/artist, and a
 * live sound-wave equalizer that dances while the story plays (freezes on pause).
 */
@Composable
internal fun StoryMusicCard(music: com.example.syntra.net.StoryMusic, playing: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!music.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = music.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                music.title.ifBlank { "Musik" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (music.artist.isNotBlank()) {
                Text(
                    music.artist,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        SoundWave(playing = playing, modifier = Modifier.size(width = 34.dp, height = 22.dp))
    }
}

/** Text-only music style: a compact pill with a note, "title · artist", and wave. */
@Composable
internal fun StoryMusicText(music: com.example.syntra.net.StoryMusic, playing: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.38f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = buildString {
                append(music.title.ifBlank { "Musik" })
                if (music.artist.isNotBlank()) append(" · ").append(music.artist)
            },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Spacer(Modifier.width(9.dp))
        SoundWave(playing = playing, modifier = Modifier.size(width = 24.dp, height = 14.dp))
    }
}

/** A row of bars that rise and fall like an audio equalizer. Still when [playing] is false. */
@Composable
internal fun SoundWave(playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "wave-phase",
    )
    Canvas(modifier = modifier) {
        val bars = 5
        val gap = size.width / (bars * 2f)
        val barW = gap
        for (i in 0 until bars) {
            val h = if (playing) {
                size.height * (0.25f + 0.75f * (kotlin.math.sin(phase + i * 0.9f) * 0.5f + 0.5f))
            } else {
                size.height * 0.3f
            }
            val x = gap + i * (barW + gap)
            drawLine(
                color = Color.White,
                start = Offset(x, size.height),
                end = Offset(x, size.height - h),
                strokeWidth = barW,
                cap = StrokeCap.Round,
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
        val context = LocalContext.current
        var player by remember { mutableStateOf<MediaPlayer?>(null) }
        // Pausing has to go through the VideoView, not the raw MediaPlayer: the view
        // owns the playback state and would happily restart underneath us.
        var view by remember { mutableStateOf<VideoView?>(null) }
        var durationMs by remember { mutableIntStateOf(0) }
        var ready by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        val finished = remember { mutableStateOf(false) }
        // Download-once: resolve a remote story video to its cached local file (keyed
        // by URL, so the same clip shares ONE cache with the reel/profile viewers).
        // Re-watching a story then costs no egress. A local uri passes straight through.
        var playUri by remember { mutableStateOf<Uri?>(null) }
        LaunchedEffect(uri) {
            val s = uri.toString()
            playUri = if (s.startsWith("http")) Uri.parse(VideoCache.resolve(context, s)) else uri
        }

        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            playUri?.let { resolved ->
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                durationMs = mp.duration
                                player = mp
                                ready = true
                                com.example.syntra.net.MusicPlayer.pauseForExternalAudio()
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
                            setVideoURI(resolved)
                            view = this
                        }
                    },
                    // The uri is fixed for this key, so nothing to re-apply here.
                    update = {},
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

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
            .clip(CircleShape)
            .background(Brush.verticalGradient(SyntraAvatarGradient)),
        contentAlignment = Alignment.Center,
    ) {
        // A bare media id is not something Coil can load — only take real URLs,
        // otherwise show the Syntra empty-profile mark rather than a broken image.
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
            // Syntra's own empty-profile glyph: a soft bust drawn over the brand
            // gradient, clipped to the circle. Deliberately not the stock Material
            // person icon — this is the app's signature placeholder.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val cx = w / 2f
                val glyph = Color.White.copy(alpha = 0.92f)
                // Head.
                drawCircle(color = glyph, radius = w * 0.168f, center = Offset(cx, h * 0.38f))
                // Shoulders — a wide rounded bust; the parent circle clip cuts it into
                // the classic silhouette, but with Syntra's softer, higher proportions.
                val shoulderW = w * 0.62f
                drawRoundRect(
                    color = glyph,
                    topLeft = Offset(cx - shoulderW / 2f, h * 0.6f),
                    size = Size(shoulderW, h * 0.55f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(shoulderW * 0.5f, shoulderW * 0.5f),
                )
            }
        }
    }
}

/** Syntra's signature avatar gradient — the brand two-tone, used for empty profiles. */
private val SyntraAvatarGradient = listOf(Color(0xFF8E7BEA), Color(0xFF5C79F0))

// ---------------------------------------------------------------------------
// Full-screen story viewer (WhatsApp-status style)
// ---------------------------------------------------------------------------

private const val STORY_DURATION_MS = 5000L
private const val MUSIC_STORY_DURATION_MS = 15000L
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

    // Music attached to a story is actually played here (not just shown). One player
    // for the whole viewer; re-pointed as segments change, paused when a finger holds.
    val storyPlayer = remember { MediaPlayer() }
    // True once the current song is prepared, so pause/resume never calls start() on a
    // player that is still resetting/preparing (which throws and kills the audio).
    var musicPrepared by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { runCatching { storyPlayer.stop() }; runCatching { storyPlayer.release() } }
    }

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
            // A song gets more time on screen so it's actually heard, not a 5s blip.
            val total = if (current.music != null) MUSIC_STORY_DURATION_MS else STORY_DURATION_MS
            var elapsed = 0L
            while (elapsed < total) {
                delay(STORY_TICK_MS)
                // Read state directly so pause / active typing freeze it live.
                if (paused || replyText.isNotBlank()) continue
                elapsed += STORY_TICK_MS
                progress.snapTo((elapsed.toFloat() / total).coerceAtMost(1f))
            }
            goNext()
        }
        // Videos are marked watched from onProgress once they truly start playing —
        // a video that never plays is NOT counted as viewed.
    }

    // Point the music player at the current segment's song (if any), and actually
    // play it. Re-runs on every segment/person change so the right track plays.
    LaunchedEffect(personIndex, segment) {
        musicPrepared = false
        runCatching { storyPlayer.reset() }
        val m = person.items.getOrNull(segment)?.music
        if (m != null && m.previewUrl.isNotBlank()) {
            com.example.syntra.net.MusicPlayer.pauseForExternalAudio() // a story song takes over audio
            runCatching {
                storyPlayer.setDataSource(m.previewUrl)
                // MANUAL loop, not isLooping: for a STREAMED preview url the built-in
                // loop seek fails silently on many devices after the first pass, so the
                // song "plays once then goes quiet". Restarting from the completion
                // callback loops reliably for as long as the story is on screen.
                storyPlayer.isLooping = false
                storyPlayer.setOnCompletionListener { mp ->
                    runCatching { mp.seekTo(0); if (!paused) mp.start() }
                }
                storyPlayer.setOnPreparedListener { mp ->
                    musicPrepared = true
                    if (!paused) runCatching { mp.start() }
                }
                storyPlayer.prepareAsync()
            }
        }
    }
    // Holding a finger pauses the story — pause/resume the song with it. Only touch a
    // PREPARED player (start() on a resetting/preparing player throws and would leave
    // the song silent for the rest of the view).
    LaunchedEffect(paused) {
        if (!musicPrepared) return@LaunchedEffect
        runCatching {
            if (paused) {
                if (storyPlayer.isPlaying) storyPlayer.pause()
            } else if (person.items.getOrNull(segment)?.music != null) {
                if (!storyPlayer.isPlaying) storyPlayer.start()
            }
        }
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
                    StoryPhoto(media, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                else -> StoryPhoto(media, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }

            // A story's song shows its widget wherever the author placed it, in the
            // style they chose (card / text / none). "none" plays audio with no UI.
            current.music?.let { m ->
                if (m.mode != "none") {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wPx = constraints.maxWidth.toFloat()
                        val hPx = constraints.maxHeight.toFloat()
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    translationX = (m.posX - 0.5f) * wPx
                                    translationY = (m.posY - 0.5f) * hPx
                                    scaleX = m.scale
                                    scaleY = m.scale
                                },
                            ) {
                                if (m.mode == "text") {
                                    StoryMusicText(music = m, playing = !paused)
                                } else {
                                    StoryMusicCard(music = m, playing = !paused)
                                }
                            }
                        }
                    }
                }
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
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
                        .background(DangerFill, RoundedCornerShape(50))
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
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
                        .background(DangerFill, RoundedCornerShape(50))
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

/**
 * Empty-home invite: shown once loaded when there are no conversations (and,
 * softly noted, no stories from others yet).
 *
 * Pared back from the old version — the heading was 22sp of ExtraBold over a
 * two-line paragraph and a 104dp emblem, which shouted at someone who had simply not
 * started yet. Now it is a small animated add-people mark, one line, and the button.
 * The charisma comes from the aurora behind it rather than from type size.
 */
@Composable
private fun ChatHomeEmpty(noStories: Boolean, onDiscover: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // A slow aurora bloom behind the mark. Two counter-drifting radial washes, so
        // the light moves without ever repeating a recognisable cycle.
        val t = rememberInfiniteTransition(label = "empty-aurora")
        val drift by t.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
            label = "empty-drift",
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(230.dp),
        ) {
            val r = size.minDimension * 0.62f
            listOf(
                Offset(
                    size.width * (0.5f + 0.16f * kotlin.math.cos(drift)),
                    size.height * (0.42f + 0.14f * kotlin.math.sin(drift)),
                ) to NexusAccent.copy(alpha = 0.22f),
                Offset(
                    size.width * (0.5f - 0.14f * kotlin.math.cos(drift * 0.7f)),
                    size.height * (0.52f - 0.12f * kotlin.math.sin(drift * 0.7f)),
                ) to NexusAccentSoft.copy(alpha = 0.16f),
            ).forEach { (center, color) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color, Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                    center = center,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The add-people mark: breathes, with a ring that expands and fades out of
            // it like a signal going looking for someone.
            val pulse by t.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
                label = "empty-pulse",
            )
            val breathe by t.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
                label = "empty-breathe",
            )
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val maxR = size.minDimension / 2f
                    // Expanding ring: fades as it grows, so it reads as emitted light.
                    drawCircle(
                        color = NexusAccent.copy(alpha = 0.30f * (1f - pulse)),
                        radius = maxR * (0.55f + 0.45f * pulse),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(NexusAccent.copy(alpha = 0.26f), Color.Transparent),
                            radius = maxR,
                        ),
                        radius = maxR,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.PersonAddAlt,
                    contentDescription = null,
                    tint = NexusAccentSoft,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            scaleX = breathe
                            scaleY = breathe
                        },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (noStories) {
                    "Ikuti orang untuk melihat story dan mulai mengobrol."
                } else {
                    "Belum ada obrolan. Sapa seseorang untuk memulai."
                },
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDiscover,
                    )
                    .padding(horizontal = 22.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Temukan orang", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * The way into the archive: an icon row pinned at the top of the chat list.
 *
 * Replaces a plain text link that sat at the very BOTTOM — you had to scroll past
 * every conversation to reach the control that hides conversations, and tapping it
 * expanded archived chats in place, where they were indistinguishable from live ones.
 */
@Composable
private fun ArchiveEntryRow(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NexusSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Archive, null,
                tint = NexusTextSecondary, modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = "Diarsipkan",
            color = NexusTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            color = NexusTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.ChevronRight, null,
            tint = NexusTextSecondary, modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The archive, on its own screen.
 *
 * Separate rather than expanded-in-place so archived chats keep the property that
 * makes archiving worth doing: they are somewhere else. Un-archiving is a long-press
 * away, and emptying the archive returns everything at once.
 */
@Composable
private fun ArchivedChatsScreen(
    chats: List<Conversation>,
    onOpen: (Conversation) -> Unit,
    onUnarchive: (Conversation) -> Unit,
    onUnarchiveAll: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Kembali",
                    tint = NexusTextPrimary, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "Diarsipkan",
                color = NexusTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chats.isNotEmpty()) {
                Text(
                    "Keluarkan semua",
                    color = NexusAccentSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onUnarchiveAll,
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        if (chats.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Archive, null,
                    tint = NexusTextSecondary, modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tidak ada obrolan diarsipkan",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }
        Text(
            "Tahan sebuah obrolan untuk mengeluarkannya dari arsip.",
            color = NexusTextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(chats, key = { it.id }) { convo ->
                ConversationRow(
                    convo = convo,
                    selected = false,
                    pinned = false,
                    onClick = { onOpen(convo) },
                    onLongClick = { onUnarchive(convo) },
                    onFirstVisible = {},
                )
            }
        }
    }
}

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

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 780)
@Composable
private fun ChatScreenPreview() {
    SyntraTheme {
        ChatScreen()
    }
}
