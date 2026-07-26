package com.example.syntra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.ApiException
import com.example.syntra.net.NetMessage
import com.example.syntra.net.NetPresence
import com.example.syntra.net.SocketListener
import com.example.syntra.net.MessageCache
import com.example.syntra.net.PinStore
import com.example.syntra.net.Translate
import com.example.syntra.net.ViewOnceStore
import com.example.syntra.net.SyntraClient
import com.example.syntra.net.VideoCache
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

// `id` is the server message id once persisted; for a locally-sent message it is
// a client id used as the WebSocket `ref` and swapped for the authoritative id
// when the `ack` arrives. See api.md §9 and app-backend-alignment.md §6.
private data class Message(
    val id: String,
    val text: String,
    val fromMe: Boolean,
    val time: String,
    /** Epoch millis of when the message was sent; drives the per-day date chips. */
    val at: Long = System.currentTimeMillis(),
    /**
     * Media the server attached to this message. Kept apart from [text] so a
     * photo is never shown as a raw URL in the bubble.
     */
    val media: String? = null,
    /** True once deleted-for-everyone — rendered as a greyed "deleted" tombstone. */
    val isDeleted: Boolean = false,
    /** True once the text was edited — shows a small "diedit" marker. */
    val isEdited: Boolean = false,
    /** True for a "sekali lihat" (view-once) photo — opens once, then it's gone. */
    val viewOnce: Boolean = false,
    /** When set, this message is a sticker (large emoji) — rendered big, no bubble. */
    val sticker: String? = null,
    /** Set when this is a reply to a story — a small blurred thumbnail is shown. */
    val storyReplyUrl: String? = null,
    /** Id of the message this one replies to (WhatsApp-style quote), if any. */
    val replyToId: String? = null,
)

/** Marker prefix for a story reply: "STORYREPLY<0x1>url<0x1>text". */
private const val STORY_REPLY_MARKER = "STORYREPLY"
/** Body prefix marking a view-once ("sekali lihat") photo: "VIEWONCE<0x1>caption". */
private const val VIEW_ONCE_MARKER = "VIEWONCE"
/**
 * Reserved "reaction" the recipient writes when they open a view-once photo.
 *
 * There is no server-side view-once state yet, but reactions ARE persisted and
 * broadcast live to the conversation — so this rides that existing channel to tell
 * the sender "your photo has been opened", and still works after a restart because
 * the reaction is stored. It is never shown as a real reaction: both the live event
 * and the initial reactions fetch filter it out.
 */
internal const val VIEW_ONCE_OPENED_MARK = "vo"

/** Body prefix marking a sticker message: "STICKER<0x1>😀". */
private const val STICKER_MARKER = "STICKER"
private val STORY_REPLY_SEP = ''

/** Marks a message that only exists on this device until the server confirms it. */
private const val LOCAL_ID_PREFIX = "local-"

/** Server default message page size — a full page means there may be more to page. */
private const val MESSAGE_PAGE_SIZE = 50

/**
 * The greater of two nullable UUIDv7 ids. Ids sort lexicographically in time
 * order, so plain string comparison picks the newer one; nulls lose.
 */
private fun maxOfNullable(a: String?, b: String?): String? = when {
    a == null -> b
    b == null -> a
    a >= b -> a
    else -> b
}

/** Backend message -> bubble. `fromMe` is derived client-side (alignment doc §6). */
private fun NetMessage.toUi(): Message {
    val attachment = attachments.firstOrNull()
    // Older messages were sent with the media URL as the body; keep rendering
    // those as media instead of printing the link as text.
    val legacyUrl = body.takeIf { it.isMediaUrl() }

    // Story reply: "STORYREPLY<sep>url<sep>text" → thumbnail + text.
    var storyUrl: String? = null
    var displayBody = body
    if (!isDeleted && body.startsWith(STORY_REPLY_MARKER + STORY_REPLY_SEP)) {
        val parts = body.removePrefix(STORY_REPLY_MARKER + STORY_REPLY_SEP)
            .split(STORY_REPLY_SEP, limit = 2)
        if (parts.size == 2) {
            storyUrl = parts[0]
            displayBody = parts[1]
        }
    }

    // View-once photo: "VIEWONCE<sep>caption" → mark as sekali-lihat, strip marker.
    var viewOnce = false
    if (!isDeleted && displayBody.startsWith(VIEW_ONCE_MARKER + STORY_REPLY_SEP)) {
        viewOnce = true
        displayBody = displayBody.removePrefix(VIEW_ONCE_MARKER + STORY_REPLY_SEP)
    }

    // Sticker: "STICKER<sep>😀" → big emoji, no bubble.
    var sticker: String? = null
    if (!isDeleted && displayBody.startsWith(STICKER_MARKER + STORY_REPLY_SEP)) {
        sticker = displayBody.removePrefix(STICKER_MARKER + STORY_REPLY_SEP)
        displayBody = ""
    }

    return Message(
        id = id,
        text = when {
            isDeleted -> "Pesan ini dihapus"
            legacyUrl != null -> ""
            else -> displayBody
        },
        fromMe = senderId == SyntraClient.myUserId,
        time = formatClock(createdAt),
        at = parseEpoch(createdAt),
        media = if (isDeleted) null else attachment ?: legacyUrl,
        isDeleted = isDeleted,
        isEdited = editedAt != null,
        viewOnce = viewOnce,
        sticker = sticker,
        storyReplyUrl = storyUrl,
        replyToId = replyToId,
    )
}

/** Render a RFC3339 UTC timestamp as local HH:mm. */
private fun formatClock(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val local = java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(local)
    }.getOrDefault("")
}

/** RFC3339 UTC → epoch millis; now() when blank/unparseable (optimistic sends). */
private fun parseEpoch(iso: String): Long {
    if (iso.isBlank()) return System.currentTimeMillis()
    return runCatching { java.time.Instant.parse(iso).toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())
}

/** yyy-MM-dd key for grouping messages by calendar day (local time). */
private fun dayKey(at: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale("id")).format(at)

/**
 * Date-chip label in Indonesian: "Hari ini" / "Kemarin", the day name within the
 * last week (Senin, Selasa…), else a full date (12 Juli 2026).
 */
private fun dayLabel(at: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = at }
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    val yesterday = (now.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val id = java.util.Locale("id")
    return when {
        sameDay(now, then) -> "Hari ini"
        sameDay(yesterday, then) -> "Kemarin"
        (now.timeInMillis - at) in 0 until 7L * 24 * 3600_000 ->
            java.text.SimpleDateFormat("EEEE", id).format(at) // Senin, Selasa…
        else -> java.text.SimpleDateFormat("d MMMM yyyy", id).format(at)
    }
}

private fun sampleMessages(convo: Conversation): List<Message> = listOf(
    Message("m1", "Hey! Are you around?", fromMe = false, time = "12:30 PM"),
    Message("m2", "Yeah, just wrapped up a meeting. What's up?", fromMe = true, time = "12:31 PM"),
    Message("m3", "Wanted to sync on the release plan for this week.", fromMe = false, time = "12:33 PM"),
    Message("m4", "Sounds good. I pushed the latest changes this morning.", fromMe = true, time = "12:34 PM"),
    Message("m5", convo.message.removeSuffix("..."), fromMe = false, time = convo.time),
)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    conversation: Conversation,
    onBack: () -> Unit,
    onNewGroup: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember(conversation) {
        if (ApiConfig.ENABLED) mutableStateListOf()
        else sampleMessages(conversation).toMutableStateList()
    }
    // Reactions per message: messageId -> (userId -> emoji). Replacing the value
    // (not mutating in place) is what drives recomposition.
    val reactions = remember(conversation) { mutableStateMapOf<String, Map<String, String>>() }
    // Lazy history: only the most recent page loads first; scrolling to the top
    // fetches an older page (with a skeleton). [oldestId] is the pagination cursor;
    // [hasMore] stops paging once the server returns a short page; [loadingOlder]
    // gates the skeleton + prevents overlapping fetches.
    var oldestId by remember(conversation) { mutableStateOf<String?>(null) }
    var hasMore by remember(conversation) { mutableStateOf(true) }
    var loadingOlder by remember(conversation) { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    // The message being replied to (swipe right on a bubble), null when not replying.
    var replyingTo by remember(conversation) { mutableStateOf<Message?>(null) }
    // The message currently being edited (its text is loaded into the composer), or
    // null. Editing is only offered within 10s of sending — see MessageActionsDialog.
    var editingId by remember(conversation) { mutableStateOf<String?>(null) }
    // Locally pinned message id for this conversation (sematkan pesan) — shown as a
    // banner at the top. On-device only until the backend gains a pin endpoint.
    var pinnedId by remember(conversation) { mutableStateOf(PinStore.get(context, conversation.id)) }
    // Per-message translations the user asked for (message id -> translated text).
    val translations = remember(conversation) { mutableStateMapOf<String, String>() }
    // A picked/captured photo waiting in the edit-before-send screen. Non-null shows
    // that editor; sending clears it.
    var pendingImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // View-once photos already opened on this device (locked as "Dibuka" afterwards).
    val viewOnceOpened = remember(conversation) { mutableStateListOf<String>() }
    // Whether we've told the peer we're typing (so we don't re-send start per key),
    // and a counter bumped on each keystroke to re-arm the stop-debounce.
    var typingActive by remember(conversation) { mutableStateOf(false) }
    var typingPokes by remember(conversation) { mutableIntStateOf(0) }
    // Send typing.stop ~2.5s after the last keystroke.
    LaunchedEffect(typingPokes) {
        if (typingPokes == 0 || !typingActive) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        if (typingActive) {
            typingActive = false
            if (ApiConfig.ENABLED) SyntraClient.typingStop(conversation.id)
        }
    }
    var peerTyping by remember(conversation) { mutableStateOf(false) }
    // Safety auto-clear: if a "stopped" event is ever missed, drop the indicator a
    // few seconds after the last typing signal so it can't stick on forever.
    var lastTypingAt by remember(conversation) { mutableStateOf(0L) }
    LaunchedEffect(peerTyping, lastTypingAt) {
        if (peerTyping) {
            kotlinx.coroutines.delay(6000)
            if (System.currentTimeMillis() - lastTypingAt >= 6000) peerTyping = false
        }
    }
    // Live online state for the header; seeded from the list, then kept current
    // by presence.update so it changes without leaving the chat.
    var peerOnline by remember(conversation) {
        mutableStateOf(conversation.presence == Presence.ONLINE)
    }
    // Profile photo for the header. Seeded from the list row, then confirmed
    // against the server so it is right even when the list hadn't resolved it yet
    // — and so a photo the peer just changed shows up on entering the chat.
    var peerAvatar by remember(conversation) { mutableStateOf(conversation.avatarUrl) }

    LaunchedEffect(conversation.id) {
        if (!ApiConfig.ENABLED) return@LaunchedEffect
        val username = conversation.counterpartUsername
        if (username.isNullOrBlank()) return@LaunchedEffect
        runCatching { SyntraClient.getUser(username) }.getOrNull()
            ?.avatarMediaId
            ?.takeIf { it.startsWith("http") }
            ?.let { peerAvatar = it }
    }
    var counterpartLastReadId by remember(conversation) {
        mutableStateOf(conversation.counterpartLastReadId)
    }
    // Highest message id the peer's device has acknowledged receiving (✓✓ grey).
    // Moves forward only — never regresses. Seeded from the PERSISTED delivered
    // mark (and read, since read implies delivered) and advanced ONLY by real
    // delivered/read receipts from the peer's device. It deliberately does NOT
    // trust the online indicator: presence can be hidden or stale, so guessing
    // "online ⇒ delivered" produced wrong ticks in both directions. Ticks now mean
    // exactly what they say — ✓ sent, ✓✓ grey the peer's device really got it,
    // ✓✓ blue the peer really read it.
    var deliveredUpToId by remember(conversation) {
        val read = conversation.counterpartLastReadId
        val delivered = conversation.counterpartLastDeliveredId
        mutableStateOf(maxOfNullable(delivered, read))
    }
    var confirmClear by remember(conversation) { mutableStateOf(false) }
    var pendingMessage by remember(conversation) { mutableStateOf<Message?>(null) }
    var fullscreenImage by remember(conversation) { mutableStateOf<String?>(null) }
    // Overflow-menu actions.
    var showReport by remember(conversation) { mutableStateOf(false) }
    var confirmBlock by remember(conversation) { mutableStateOf(false) }
    var showChatTheme by remember(conversation) { mutableStateOf(false) }
    var showWallpaper by remember(conversation) { mutableStateOf(false) }
    // Per-conversation chat background: a built-in URL, a local content:// uri, or null.
    var wallpaper by remember(conversation) { mutableStateOf(ChatWallpaperStore.get(context, conversation.id)) }
    var showProfile by remember(conversation) { mutableStateOf(false) }
    var chatTheme by remember(conversation) { mutableStateOf(ChatThemeStore.get(context, conversation.id)) }

    fun startCall(video: Boolean) {
        if (!ApiConfig.ENABLED) {
            Toast.makeText(context, "Server belum aktif.", Toast.LENGTH_SHORT).show()
            return
        }
        if (CallController.isBusy) {
            Toast.makeText(context, "Masih ada panggilan berlangsung.", Toast.LENGTH_SHORT).show()
            return
        }
        showProfile = false
        // The call lives at the app root (CallHost) so it can float and survive
        // navigating away — we just hand it the peer and hang up here.
        CallController.startOutgoing(
            conversationId = conversation.id,
            peerName = conversation.name,
            peerId = conversation.counterpartId.orEmpty(),
            video = video,
        )
    }
    val listState = rememberLazyListState()

    // Composer extras: emoji panel, attachments, voice notes.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    var showEmoji by remember { mutableStateOf(false) }
    var showGifSheet by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    val recorder = remember { VoiceRecorder(context) }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            recordSeconds++
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) Toast.makeText(context, "Izin mikrofon ditolak.", Toast.LENGTH_SHORT).show()
    }

    // First landing: jump straight to the first unread message (or the very last
    // when all are read), with no visible scroll from the top. Each message is one
    // lazy item now (the date chip renders inside it), so message i == lazy index i.
    var landed by remember(conversation) { mutableStateOf(false) }
    LaunchedEffect(messages.isNotEmpty()) {
        if (messages.isNotEmpty() && !landed) {
            val firstUnread = (messages.size - conversation.unread).coerceIn(0, messages.lastIndex)
            val target = if (conversation.unread > 0) firstUnread else messages.lastIndex
            listState.scrollToItem(target)
            landed = true
        }
    }

    // After landing, keep the newest message in view. Always scroll for my own
    // sent messages; for incoming ones, only when I'm already near the bottom so
    // it doesn't yank me while reading history.
    LaunchedEffect(messages.size) {
        if (!landed || messages.isEmpty()) return@LaunchedEffect
        val lastIndex = messages.lastIndex
        val mineIsNewest = messages.lastOrNull()?.fromMe == true
        val visibleLast = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (mineIsNewest || visibleLast >= lastIndex - 2) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    // --- Backend: history, realtime, read receipts -------------------------
    if (ApiConfig.ENABLED) {
        LaunchedEffect(conversation.id) {
            // 1) Instant paint from the on-device cache — no spinner, no download —
            //    so reopening a chat you've already read shows immediately.
            val cached = MessageCache.load(context, conversation.id) // oldest-first
            if (cached.isNotEmpty()) {
                messages.clear()
                messages.addAll(cached.map { it.toUi() })
                oldestId = cached.firstOrNull()?.id
            }
            runCatching {
                SyntraClient.subscribe(listOf("conversation:${conversation.id}"))
                // Ask for the peer's live presence so the checkmarks (1 tick offline,
                // 2 ticks online) are correct from the moment the chat opens.
                conversation.counterpartId?.let { SyntraClient.presenceQuery(listOf(it)) }
                // 2) Sync the newest page from the server (newest-first → render oldest
                //    -first). This is the "today" batch; older days load on scroll-up.
                val page = SyntraClient.getMessages(conversation.id) // newest-first
                val history = page.reversed()
                if (history.isNotEmpty()) {
                    MessageCache.merge(context, conversation.id, history)
                    // Rebuild from the merged cache so cached + fresh reconcile cleanly
                    // (keeps older cached messages that the newest page didn't include).
                    val full = MessageCache.load(context, conversation.id)
                    messages.clear()
                    messages.addAll(full.map { it.toUi() })
                    oldestId = full.firstOrNull()?.id
                    history.lastOrNull()?.let { SyntraClient.messageRead(conversation.id, it.id) }
                }
                // A short first page means there's nothing older to fetch.
                if (page.size < MESSAGE_PAGE_SIZE) hasMore = false
                // Load existing reactions for the visible messages in one call.
                runCatching { SyntraClient.getReactions(conversation.id, history.map { it.id }) }
                    .getOrNull()?.let { loaded ->
                        // Pull the view-once "opened" marks out of the reaction data:
                        // they drive the "sudah dibuka" state (so it survives a restart)
                        // and must never render as an actual reaction.
                        val real = HashMap<String, Map<String, String>>()
                        loaded.forEach { (msgId, byUser) ->
                            byUser.forEach { (uid, emoji) ->
                                if (emoji == VIEW_ONCE_OPENED_MARK && uid != SyntraClient.myUserId) {
                                    ViewOnceStore.markPeerOpened(context, msgId)
                                }
                            }
                            val cleaned = byUser.filterValues { it != VIEW_ONCE_OPENED_MARK }
                            if (cleaned.isNotEmpty()) real[msgId] = cleaned
                        }
                        reactions.clear(); reactions.putAll(real)
                    }
            }.onFailure {
                // Offline / error is fine when we already showed the cache.
                if (cached.isEmpty()) {
                    Toast.makeText(context, "Gagal memuat pesan: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Scroll-to-top → load the previous day/page of history, with a skeleton.
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { first ->
                    if (first <= 1 && hasMore && !loadingOlder && oldestId != null && messages.isNotEmpty()) {
                        loadingOlder = true
                        val cursor = oldestId
                        runCatching { SyntraClient.getMessages(conversation.id, before = cursor) }
                            .onSuccess { older ->
                                if (older.isEmpty() || older.size < MESSAGE_PAGE_SIZE) hasMore = false
                                if (older.isNotEmpty()) {
                                    MessageCache.merge(context, conversation.id, older)
                                    val ordered = older.reversed().map { it.toUi() } // oldest-first
                                    val existing = messages.map { it.id }.toSet()
                                    val toAdd = ordered.filter { it.id !in existing }
                                    messages.addAll(0, toAdd)
                                    oldestId = older.minByOrNull { it.id }?.id ?: oldestId
                                    // Hide the skeleton FIRST so index math is clean, then
                                    // anchor to the previously-first message (now at lazy
                                    // index toAdd.size) so the view doesn't jump.
                                    loadingOlder = false
                                    if (toAdd.isNotEmpty()) {
                                        runCatching { listState.scrollToItem(toAdd.size) }
                                    }
                                } else {
                                    loadingOlder = false
                                }
                            }
                            .onFailure { loadingOlder = false }
                    }
                }
        }
        // Tell the notification layer which chat is open, so it won't notify for
        // the very conversation the user is reading (but still notifies for others).
        DisposableEffect(conversation.id) {
            com.example.syntra.net.AppForeground.openConversationId = conversation.id
            onDispose {
                if (com.example.syntra.net.AppForeground.openConversationId == conversation.id) {
                    com.example.syntra.net.AppForeground.openConversationId = null
                }
            }
        }
        DisposableEffect(conversation.id) {
            val listener = object : SocketListener {
                override fun onMessageNew(message: NetMessage) {
                    if (message.conversationId != conversation.id) return
                    // The backend deliberately delivers our own message twice: once as
                    // `ack`, once as this broadcast. Drop it if we already have that id.
                    if (messages.any { it.id == message.id }) return
                    // …and the broadcast can beat the ack, in which case the optimistic
                    // copy still carries its client id. Reconcile instead of appending,
                    // otherwise the sender sees the message twice.
                    if (message.senderId == SyntraClient.myUserId) {
                        // Reconcile against the optimistic copy. Match on the *display*
                        // text: toUi() strips the markers (VIEWONCE / STICKER /
                        // STORYREPLY), so a view-once photo or sticker finally matches
                        // its own broadcast. Without this the marker made every such
                        // send fail to match, get appended a second time, and then
                        // collide with the ack into a duplicate LazyColumn key — a hard
                        // crash ("Key … was already used") the moment you sent one.
                        val incoming = message.toUi()
                        val pending = messages.indexOfFirst {
                            it.id.startsWith(LOCAL_ID_PREFIX) && it.text == incoming.text &&
                                (it.media == null) == (incoming.media == null)
                        }
                        if (pending >= 0) {
                            // Keep the optimistic row's already-decoded LOCAL file rather
                            // than the broadcast's remote URL — otherwise the same swap makes
                            // a GIF/sticker flash black and jump size on reconcile. Adopt the
                            // server id/time via [incoming]; the bucket URL loads on next open.
                            val localMedia = messages[pending].media
                            messages[pending] = if (!localMedia.isNullOrBlank() && !localMedia.startsWith("http")) {
                                incoming.copy(media = localMedia)
                            } else {
                                incoming
                            }
                            return
                        }
                    }
                    // A freshly broadcast message is never a deletion — the delete
                    // tombstone only ever comes from the message.deleted event or from
                    // loaded history, so guard against a stray is_deleted here.
                    messages.add(message.copy(isDeleted = false).toUi())
                    peerTyping = false
                    // Honour the privacy switch: no read receipt when it is off.
                    if (SettingsStore.getBool(context, SettingsStore.READ_RECEIPTS, true)) {
                        SyntraClient.messageRead(conversation.id, message.id)
                    }
                }

                override fun onTyping(conversationId: String, userId: String, typing: Boolean) {
                    if (conversationId == conversation.id && userId != SyntraClient.myUserId) {
                        peerTyping = typing
                        if (typing) lastTypingAt = System.currentTimeMillis()
                    }
                }

                override fun onReadReceipt(conversationId: String, userId: String, messageId: String) {
                    // Only the PEER reading flips my ✓✓ to blue. My own read receipts
                    // (synced across my devices) must NOT touch this — that was the bug
                    // making sent messages instantly blue.
                    if (conversationId != conversation.id) return
                    if (userId == SyntraClient.myUserId) return
                    if (counterpartLastReadId == null || messageId > counterpartLastReadId!!) {
                        counterpartLastReadId = messageId
                    }
                    // Read implies delivered — keep the delivered mark at least this far.
                    if (deliveredUpToId == null || messageId > deliveredUpToId!!) {
                        deliveredUpToId = messageId
                    }
                }

                override fun onDeliveredReceipt(conversationId: String, userId: String, messageId: String) {
                    // The peer's device got my message (✓✓ grey). Ignore my own echoes,
                    // and only ever advance the high-water mark so ticks never regress.
                    if (conversationId != conversation.id) return
                    if (userId == SyntraClient.myUserId) return
                    if (deliveredUpToId == null || messageId > deliveredUpToId!!) {
                        deliveredUpToId = messageId
                    }
                }

                override fun onPresence(presence: NetPresence) {
                    // Online/offline in the header updates itself.
                    if (presence.userId == conversation.counterpartId) {
                        peerOnline = presence.online
                    }
                }

                override fun onMessageDeleted(conversationId: String, messageId: String) {
                    if (conversationId != conversation.id) return
                    val i = messages.indexOfFirst { it.id == messageId }
                    if (i >= 0) {
                        messages[i] = messages[i].copy(
                            text = "Pesan ini dihapus",
                            media = null,
                            isDeleted = true,
                        )
                    }
                    reactions.remove(messageId)
                    // Reflect the deletion in the cache so it doesn't come back on reopen.
                    MessageCache.remove(context, conversation.id, messageId)
                }

                override fun onMessageUpdated(conversationId: String, messageId: String, body: String) {
                    if (conversationId != conversation.id) return
                    val i = messages.indexOfFirst { it.id == messageId }
                    if (i >= 0 && !messages[i].isDeleted) {
                        messages[i] = messages[i].copy(text = body, isEdited = true)
                        // A translation of the old text is now stale — drop it.
                        translations.remove(messageId)
                    }
                }

                override fun onMessageReaction(
                    conversationId: String,
                    messageId: String,
                    userId: String,
                    emoji: String,
                ) {
                    if (conversationId != conversation.id) return
                    // Not a real reaction: the peer opened a view-once photo I sent.
                    // Flip my bubble to "sudah dibuka" and keep it out of the reaction row.
                    if (emoji == VIEW_ONCE_OPENED_MARK) {
                        if (userId != SyntraClient.myUserId) ViewOnceStore.markPeerOpened(context, messageId)
                        return
                    }
                    val current = reactions[messageId].orEmpty()
                    reactions[messageId] =
                        if (emoji.isBlank()) current - userId else current + (userId to emoji)
                }

                override fun onUserUpdated(userId: String, displayName: String, avatarUrl: String?) {
                    // The person I'm chatting with changed their photo — reflect it in
                    // the header without leaving the chat.
                    if (userId == conversation.counterpartId && !avatarUrl.isNullOrBlank()) {
                        peerAvatar = avatarUrl
                    }
                }

                override fun onConversationUpdated(conversationId: String) {
                    // Group renamed / avatar changed while I'm reading it: reload the
                    // messages so anything that changed with it is current too.
                    if (conversationId != conversation.id) return
                    scope.launch {
                        runCatching { SyntraClient.getMessages(conversation.id) }.onSuccess { list ->
                            messages.clear()
                            messages.addAll(list.reversed().map { it.toUi() })
                        }
                    }
                }

                override fun onAck(ref: String?, data: Any?) {
                    // Swap the optimistic message for the authoritative one (api.md §9).
                    val obj = data as? JSONObject ?: return
                    val serverId = obj.optString("id", "")
                    if (ref == null || serverId.isBlank()) return
                    val idx = messages.indexOfFirst { it.id == ref }
                    if (idx < 0) return
                    // The broadcast may already have replaced it; then this ref is stale
                    // and keeping both rows would duplicate the message.
                    if (messages.any { it.id == serverId }) {
                        messages.removeAt(idx)
                        return
                    }
                    messages[idx] = messages[idx].copy(
                        id = serverId,
                        time = formatClock(obj.optString("created_at", "")),
                    )
                }

                override fun onReconnect() {
                    // Pub/Sub is at-most-once: re-sync this conversation on reconnect.
                    scope.launch {
                        runCatching {
                            SyntraClient.subscribe(listOf("conversation:${conversation.id}"))
                            val history = SyntraClient.getMessages(conversation.id).reversed()
                            messages.clear()
                            messages.addAll(history.map { it.toUi() })
                        }
                    }
                }
            }
            SyntraClient.addListener(listener)
            onDispose {
                SyntraClient.removeListener(listener)
                SyntraClient.typingStop(conversation.id)
            }
        }
    }

    /**
     * Sends media by uploading it and putting the resulting public URL in the
     * message body. The backend rejects `media_id` on messages (`unknown field`),
     * so a link is the only way to deliver a photo or voice note today; the bubble
     * renderer turns it back into a picture or a player.
     */
    fun sendMedia(
        kind: String,
        ext: String,
        mime: String,
        bytes: ByteArray,
        durationMs: Long = 0,
        caption: String = "",
        viewOnce: Boolean = false,
    ) {
        if (!ApiConfig.ENABLED) {
            Toast.makeText(context, "Backend belum dikonfigurasi.", Toast.LENGTH_SHORT).show()
            return
        }
        // Reject oversized media before spending the upload (docs/api.md limits).
        val maxBytes = when (kind) {
            "image" -> 10L * 1024 * 1024
            "video" -> 100L * 1024 * 1024
            "audio" -> 20L * 1024 * 1024
            else -> 16L * 1024 * 1024
        }
        if (bytes.size > maxBytes) {
            Toast.makeText(
                context,
                "Berkas terlalu besar (maks ${maxBytes / (1024 * 1024)} MB).",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        // Body carries the caption; a view-once photo is prefixed with a marker so
        // the peer's app knows to show it as "sekali lihat".
        val body = if (viewOnce) VIEW_ONCE_MARKER + STORY_REPLY_SEP + caption else caption
        val ref = "$LOCAL_ID_PREFIX${System.currentTimeMillis()}"
        uploading = true
        scope.launch {
            // Show the photo/GIF IMMEDIATELY with a "mengirim" clock (the local- id
            // drives DeliveryState.SENDING), rendered from a local copy on disk — before
            // the slow network upload. Once the upload lands, the bubble swaps to the
            // bucket URL, which then caches through Coil exactly like any other photo.
            val localPath = withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(context.cacheDir, "outgoing-$ref.$ext").apply { writeBytes(bytes) }.absolutePath
                }.getOrNull()
            }
            messages.add(Message(ref, caption, fromMe = true, time = "now", media = localPath, viewOnce = viewOnce))
            runCatching {
                val (mediaId, url) = SyntraClient.uploadMediaFull(kind, ext, mime, bytes, durationMs = durationMs)
                // The message itself carries the media *id*, so the body stays empty
                // instead of leaking a URL into the conversation.
                val sent = SyntraClient.sendMessageRest(conversation.id, body, listOf(mediaId))
                val i = messages.indexOfFirst { it.id == ref }
                if (i >= 0) {
                    // The broadcast may have raced ahead and already inserted this id;
                    // replacing would then leave two rows with the same key (a crash).
                    // Drop the optimistic row and let the broadcast copy stand.
                    if (messages.any { it.id == sent.id }) {
                        messages.removeAt(i)
                    } else {
                        val authoritative = sent.toUi()
                        // Keep showing the already-decoded LOCAL file for this session
                        // instead of swapping in the just-uploaded bucket URL. Swapping the
                        // model forces Coil to re-fetch over the network: the bubble flashes
                        // black and — for a GIF/sticker, whose height comes from the image —
                        // briefly collapses to the wrong size until the download lands, so it
                        // looked misplaced until a refresh. The authoritative id/time still
                        // come from the server; the bucket URL is picked up on the next open.
                        messages[i] = authoritative.copy(
                            media = localPath ?: authoritative.media ?: url.ifBlank { null },
                        )
                    }
                }
            }.onFailure {
                // Drop the optimistic bubble so it doesn't hang with a clock forever.
                val i = messages.indexOfFirst { it.id == ref }
                if (i >= 0) messages.removeAt(i)
                Toast.makeText(context, "Gagal mengirim: ${it.message}", Toast.LENGTH_LONG).show()
            }
            uploading = false
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (recorder.start()) {
            recording = true
            recordSeconds = 0
        } else {
            Toast.makeText(context, "Tidak bisa merekam.", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        val result = recorder.stop()
        if (result == null) {
            Toast.makeText(context, "Tahan untuk merekam pesan suara.", Toast.LENGTH_SHORT).show()
            return
        }
        val (file, millis) = result
        sendMedia("voice_note", "m4a", "audio/mp4", file.readBytes(), millis)
        file.delete()
    }

    fun cancelRecording() {
        recording = false
        recorder.cancel()
    }

    // Goes through the permission gate; capturing without CAMERA granted crashes.
    // The captured photo lands in the edit-before-send screen rather than sending.
    val camera = rememberCameraCapture { bitmap -> pendingImage = bitmap }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            // Decode to a bitmap and hand off to the edit screen; nothing is sent
            // until the user confirms there.
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bmp != null) pendingImage = bmp
            else Toast.makeText(context, "Tidak bisa membuka gambar.", Toast.LENGTH_SHORT).show()
        }
    }

    // Wallpaper from the gallery. The read grant is persisted so the background
    // still renders after a restart — without it the uri goes dead and the chat
    // would silently fall back to plain.
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            wallpaper = uri.toString()
            ChatWallpaperStore.set(context, conversation.id, uri.toString())
        }
    }

    fun sendSticker(emoji: String) {
        val ref = "$LOCAL_ID_PREFIX${System.currentTimeMillis()}"
        messages.add(Message(ref, "", fromMe = true, time = "now", sticker = emoji))
        if (ApiConfig.ENABLED) {
            SyntraClient.messageSend(conversation.id, STICKER_MARKER + STORY_REPLY_SEP + emoji, ref)
        }
    }

    fun sendGif(url: String) {
        if (!ApiConfig.ENABLED) return
        // Download the GIF bytes from Tenor, then push through the media pipeline so
        // it lands in our bucket and renders (animated) like any other chat photo.
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { java.net.URL(url).openStream().use { it.readBytes() } }.getOrNull()
            }
            if (bytes != null) sendMedia("image", "gif", "image/gif", bytes)
            else Toast.makeText(context, "Gagal memuat GIF.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Send a GIF the user picked from the phone's gallery (a content:// uri). */
    fun sendGifFromUri(uri: android.net.Uri) {
        if (!ApiConfig.ENABLED) return
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes != null) sendMedia("image", "gif", "image/gif", bytes)
            else Toast.makeText(context, "Gagal memuat GIF.", Toast.LENGTH_SHORT).show()
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return

        // Edit mode: PATCH the existing message instead of sending a new one.
        val editId = editingId
        if (editId != null) {
            val i = messages.indexOfFirst { it.id == editId }
            if (i >= 0) messages[i] = messages[i].copy(text = text, isEdited = true)
            input = ""
            editingId = null
            typingActive = false
            if (ApiConfig.ENABLED) {
                SyntraClient.typingStop(conversation.id)
                scope.launch {
                    runCatching { SyntraClient.editMessage(editId, text) }
                        .onFailure {
                            val why = if ((it as? ApiException)?.code == "not_found") {
                                "Server belum mendukung edit pesan."
                            } else {
                                it.message ?: "Gagal mengedit pesan."
                            }
                            Toast.makeText(context, why, Toast.LENGTH_LONG).show()
                        }
                }
            }
            return
        }

        val replyId = replyingTo?.id
        // Optimistic message: a client id now, replaced by the server id on ack.
        val ref = "$LOCAL_ID_PREFIX${System.currentTimeMillis()}"
        messages.add(Message(ref, text, fromMe = true, time = "now", replyToId = replyId))
        input = ""
        replyingTo = null
        typingActive = false
        if (ApiConfig.ENABLED) {
            SyntraClient.typingStop(conversation.id)
            if (replyId != null) {
                // Replies go over REST so the reply_to_id is carried; swap the
                // optimistic bubble for the authoritative one on success.
                scope.launch {
                    runCatching { SyntraClient.sendMessageRest(conversation.id, text, replyToId = replyId) }
                        .onSuccess { sent ->
                            val i = messages.indexOfFirst { it.id == ref }
                            if (i >= 0) {
                                // Guard against the broadcast beating this ack: dropping
                                // the optimistic row avoids a duplicate LazyColumn key.
                                if (messages.any { it.id == sent.id }) messages.removeAt(i)
                                else messages[i] = sent.toUi()
                            }
                        }
                }
            } else {
                SyntraClient.messageSend(conversation.id, text, ref)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            // Chat wallpaper, painted BEHIND everything in this screen (built-in URL or
            // a local uri — Coil loads either). A scrim on top keeps message bubbles
            // and text readable over a busy or bright picture.
            .then(
                wallpaper?.let { model ->
                    Modifier
                        .paint(
                            painter = coil.compose.rememberAsyncImagePainter(model),
                            contentScale = ContentScale.Crop,
                        )
                        .background(Color.Black.copy(alpha = 0.28f))
                } ?: Modifier,
            )
            // This screen is drawn as a full-screen overlay ON TOP of the chat list.
            // A bare background does NOT consume touches in Compose, so a tap on any
            // empty gap here would fall through to the list behind and open a DIFFERENT
            // chat ("kepencet halaman lain"). Swallow stray taps so nothing leaks
            // through; child buttons/rows still get theirs first.
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        DetailTopBar(
            convo = conversation,
            peerTyping = peerTyping,
            peerOnline = peerOnline,
            peerAvatar = peerAvatar,
            onBack = onBack,
            onLongPressAvatar = { confirmClear = true },
            onOpenProfile = { showProfile = true },
            onVoiceCall = { startCall(video = false) },
            onVideoCall = { startCall(video = true) },
            onMenuAction = { action ->
                when (action) {
                    "Laporkan" -> showReport = true
                    "Blokir" -> confirmBlock = true
                    "Bersihkan obrolan" -> confirmClear = true
                    "Grup Baru" -> onNewGroup()
                    "Tema obrolan" -> showChatTheme = true
                    "Latar obrolan" -> showWallpaper = true
                }
            },
        )

        // Pinned-message banner (sematkan pesan). Tap to jump to it, X to unpin.
        pinnedId?.let { pid ->
            val pinned = messages.firstOrNull { it.id == pid }
            if (pinned == null) {
                // The pinned message is gone (deleted/cleared) — drop the stale pin.
                PinStore.clear(context, conversation.id)
                pinnedId = null
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusSurface)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            val i = messages.indexOfFirst { it.id == pid }
                            if (i >= 0) scope.launch {
                                listState.animateScrollToItem((i + if (loadingOlder) 1 else 0).coerceAtLeast(0))
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(NexusAccentSoft),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.PushPin, null, tint = NexusAccentSoft, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pesan tersemat", color = NexusAccentSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when {
                                pinned.media != null && pinned.media.isAudioUrl() -> "🎤 Pesan suara"
                                pinned.media != null -> "📷 Foto"
                                else -> pinned.text
                            },
                            color = NexusTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Lepas sematan",
                        tint = NexusTextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { PinStore.clear(context, conversation.id); pinnedId = null },
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Skeleton while an older page loads (scroll-to-top). Sits above the
            // history so it reads as "loading earlier messages".
            if (loadingOlder) {
                item(key = "skeleton") { MessagesSkeleton() }
            }
            // Messages grouped by calendar day, each group preceded by a date chip
            // ("Hari ini" / "Kemarin" / "Senin" / "12 Juli 2026"). The chip is keyed by
            // the day so a new day inserts a fresh header as history scrolls in.
            itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                val prev = messages.getOrNull(index - 1)
                if (prev == null || dayKey(prev.at) != dayKey(msg.at)) {
                    DateChip(dayLabel(msg.at))
                }
                MessageBubble(
                    msg = msg,
                    reactions = aggregateReactions(reactions[msg.id]),
                    outgoingColor = chatTheme.bubble,
                    onLongPress = { pendingMessage = msg },
                    onImageClick = { fullscreenImage = it },
                    onReply = { replyingTo = msg },
                    // Spent from THIS side's point of view: for a received photo that's
                    // my own open; for one I sent it's the recipient having opened it
                    // (learned live). So the sender is never locked out of their own photo.
                    viewOnceOpened = msg.viewOnce && ViewOnceStore.isSpent(context, msg.id, msg.fromMe),
                    onOpenViewOnce = {
                        if (!msg.fromMe) {
                            // Recipient side: consume the single view, and tell the sender
                            // so their bubble flips to "sudah dibuka" too.
                            if (!ViewOnceStore.isOpened(context, msg.id)) {
                                ViewOnceStore.markOpened(context, msg.id)
                                SyntraClient.fireAndForget {
                                    SyntraClient.reactToMessage(msg.id, VIEW_ONCE_OPENED_MARK)
                                }
                            }
                        }
                        msg.media?.let { fullscreenImage = it }
                    },
                    translation = translations[msg.id],
                    onHideTranslation = { translations.remove(msg.id) },
                    quoted = msg.replyToId?.let { rid -> messages.firstOrNull { it.id == rid } },
                    state = when {
                        msg.id.startsWith(LOCAL_ID_PREFIX) -> DeliveryState.SENDING
                        // READ (blue) is separate and driven ONLY by the peer's read
                        // mark — never by online status. UUIDv7 sorts by time, so a
                        // plain id comparison answers "have they read this yet?".
                        counterpartLastReadId != null && msg.id <= counterpartLastReadId!! ->
                            DeliveryState.READ
                        // DELIVERED (✓✓ grey) = it reached the peer's device. True when
                        // the peer is ONLINE right now (they're connected + subscribed,
                        // so the message reached them) OR a stored delivered receipt
                        // already covers it. This is the "peer active but hasn't read =
                        // two grey ticks" behaviour.
                        peerOnline || (deliveredUpToId != null && msg.id <= deliveredUpToId!!) ->
                            DeliveryState.DELIVERED
                        // Otherwise it only reached the server: a single ✓.
                        else -> DeliveryState.SENT
                    },
                )
            }
        }

            // Jump-to-bottom button — appears once you scroll up away from the
            // newest message. Tapping it animates back to the last message.
            val showJump by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    messages.isNotEmpty() && last < messages.size - 1
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showJump,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(NexusSurfaceElevated, CircleShape)
                        .border(1.dp, NexusStroke, CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { scope.launch { listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0)) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Ke pesan terbaru",
                        tint = NexusTextPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        if (recording) {
            RecordingBar(seconds = recordSeconds, onCancel = { cancelRecording() })
        }

        // Edit banner above the composer — shows we're editing an existing message.
        if (editingId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = NexusAccentSoft,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Edit pesan", color = NexusAccentSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tekan kirim untuk menyimpan perubahan",
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Batal edit",
                    tint = NexusTextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { editingId = null; input = "" },
                )
            }
        }

        // Reply banner above the composer — shows which message you're replying to.
        replyingTo?.let { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(NexusAccentSoft),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (q.fromMe) "Membalas dirimu" else "Membalas ${conversation.name}",
                        color = NexusAccentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            q.media != null && q.media.isAudioUrl() -> "🎤 Pesan suara"
                            q.media != null -> "📷 Foto"
                            else -> q.text
                        },
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Batal balas",
                    tint = NexusTextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { replyingTo = null },
                )
            }
        }

        MessageInputBar(
            value = input,
            emojiOpen = showEmoji,
            focusRequester = fieldFocus,
            onToggleEmoji = {
                showEmoji = !showEmoji
                if (showEmoji) {
                    // Opening emoji: drop the soft keyboard AND release field focus.
                    // Clearing focus is what makes the reverse work — a later tap on
                    // the field is then a real focus change that retracts this panel.
                    keyboard?.hide()
                    focusManager.clearFocus()
                } else {
                    // Back to keyboard: refocus the field and raise it.
                    fieldFocus.requestFocus()
                    keyboard?.show()
                }
            },
            onFieldFocused = {
                // Box tapped: keyboard takes over, emoji panel closes, icon → emoji.
                showEmoji = false
                keyboard?.show()
            },
            onAttach = { showAttach = true },
            onStartRecording = { startRecording() },
            onStopRecording = { stopRecording() },
            onValueChange = { text ->
                val wasBlank = input.isBlank()
                input = text
                if (ApiConfig.ENABLED) {
                    when {
                        text.isBlank() -> {
                            typingActive = false
                            SyntraClient.typingStop(conversation.id)
                        }
                        else -> {
                            // Send typing.start only on the FIRST keystroke of a burst
                            // (not once per letter — that spammed the socket). A debounce
                            // job then fires typing.stop ~2.5s after you stop typing.
                            if (wasBlank || !typingActive) {
                                typingActive = true
                                SyntraClient.typingStart(conversation.id)
                            }
                            typingPokes++
                        }
                    }
                }
            },
            onSend = { send() },
        )

        if (showEmoji) {
            EmojiPicker(
                onPick = { input += it },
                onBackspace = { input = input.dropLast(1) },
                onSticker = { emoji ->
                    sendSticker(emoji)
                    showEmoji = false
                },
                onOpenGif = {
                    showEmoji = false
                    showGifSheet = true
                },
            )
        }
    }

    // Draggable GIF bottom-sheet (search / generate / from gallery). Keyboard-safe.
    if (showGifSheet) {
        GifPickerSheet(
            onGif = { url -> sendGif(url) },
            onGifDevice = { uri -> sendGifFromUri(uri) },
            onDismiss = { showGifSheet = false },
        )
    }

    if (showAttach) {
        AttachmentSheet(
            onCamera = { showAttach = false; camera.launch() },
            onGallery = {
                showAttach = false
                gallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onDismiss = { showAttach = false },
        )
    }

    pendingMessage?.let { msg ->
        val mine = SyntraClient.myUserId
        MessageActionsDialog(
            msg = msg,
            // Edit is offered only for my own text message, within 10s of sending.
            canEdit = msg.fromMe && !msg.isDeleted && msg.media == null &&
                (System.currentTimeMillis() - msg.at) <= 10_000L,
            isPinned = pinnedId == msg.id,
            isTranslated = translations.containsKey(msg.id),
            onEdit = {
                pendingMessage = null
                editingId = msg.id
                replyingTo = null
                input = msg.text
                showEmoji = false
                fieldFocus.requestFocus()
                keyboard?.show()
            },
            onCopy = {
                pendingMessage = null
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("pesan", msg.text))
                Toast.makeText(context, "Pesan disalin.", Toast.LENGTH_SHORT).show()
            },
            onPin = {
                pendingMessage = null
                if (pinnedId == msg.id) {
                    PinStore.clear(context, conversation.id); pinnedId = null
                } else {
                    PinStore.set(context, conversation.id, msg.id); pinnedId = msg.id
                }
            },
            onTranslate = {
                pendingMessage = null
                if (translations.containsKey(msg.id)) {
                    translations.remove(msg.id)
                } else {
                    scope.launch {
                        val result = Translate.translate(msg.text)
                        if (result != null && result != msg.text) {
                            translations[msg.id] = result
                        } else {
                            Toast.makeText(context, "Tidak bisa menerjemahkan.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            myReaction = mine?.let { reactions[msg.id]?.get(it) },
            onReact = { emoji ->
                pendingMessage = null
                if (ApiConfig.ENABLED && mine != null) {
                    // Tapping the emoji I already picked clears it (toggle).
                    val base = reactions[msg.id].orEmpty()
                    val next = if (base[mine] == emoji) "" else emoji
                    reactions[msg.id] = if (next.isBlank()) base - mine else base + (mine to next)
                    scope.launch { runCatching { SyntraClient.reactToMessage(msg.id, next) } }
                }
            },
            onDismiss = { pendingMessage = null },
            onDeleteForMe = {
                pendingMessage = null
                messages.remove(msg)
            },
            onDeleteForEveryone = {
                pendingMessage = null
                scope.launch {
                    runCatching { SyntraClient.deleteMessage(msg.id) }
                        .onSuccess {
                            // Keep the tombstone (matches what the peer sees), don't drop it.
                            val i = messages.indexOfFirst { it.id == msg.id }
                            if (i >= 0) {
                                messages[i] = messages[i].copy(
                                    text = "Pesan ini dihapus",
                                    media = null,
                                    isDeleted = true,
                                )
                            }
                            reactions.remove(msg.id)
                            MessageCache.remove(context, conversation.id, msg.id)
                        }
                        .onFailure {
                            val why = if ((it as? ApiException)?.code == "not_found") {
                                "Server belum mendukung hapus untuk semua orang."
                            } else {
                                it.message ?: "Gagal menghapus."
                            }
                            Toast.makeText(context, why, Toast.LENGTH_LONG).show()
                        }
                }
            },
        )
    }

    if (confirmClear) {
        ClearChatDialog(
            name = conversation.name,
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                // Local only: the backend has no delete endpoint yet, so this clears
                // the view on this device and says so plainly.
                messages.clear()
                Toast.makeText(
                    context,
                    "Pesan dihapus di perangkat ini saja.",
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }

    if (showReport) {
        ReportDialog(
            name = conversation.name,
            onDismiss = { showReport = false },
            onSubmit = { reason ->
                showReport = false
                val target = conversation.counterpartId
                if (ApiConfig.ENABLED && target != null) {
                    scope.launch {
                        runCatching { SyntraClient.reportUser(target, reason) }
                            .onSuccess {
                                Toast.makeText(context, "Laporan terkirim. Terima kasih.", Toast.LENGTH_LONG).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "Gagal melapor: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Laporan dicatat, tapi identitas pengguna belum tersedia dari server.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    if (confirmBlock) {
        ConfirmActionDialog(
            title = "Blokir ${conversation.name}?",
            message = "Kamu tidak akan menerima pesan darinya lagi. Percakapan ini " +
                "disembunyikan dari daftar.",
            confirmText = "Blokir",
            onDismiss = { confirmBlock = false },
            onConfirm = {
                confirmBlock = false
                val username = conversation.counterpartUsername
                if (ApiConfig.ENABLED && !username.isNullOrBlank()) {
                    scope.launch { runCatching { SyntraClient.blockUser(username) } }
                }
                // Hide locally regardless, so the block takes effect immediately.
                BlockStore.block(context, conversation.name)
                Toast.makeText(context, "${conversation.name} diblokir.", Toast.LENGTH_SHORT).show()
                onBack()
            },
        )
    }

    if (showChatTheme) {
        ChatThemeDialog(
            current = chatTheme,
            onDismiss = { showChatTheme = false },
            onPick = {
                chatTheme = it
                ChatThemeStore.set(context, conversation.id, it)
                showChatTheme = false
            },
        )
    }

    if (showWallpaper) {
        ChatWallpaperDialog(
            current = wallpaper,
            onDismiss = { showWallpaper = false },
            onPick = {
                wallpaper = it
                ChatWallpaperStore.set(context, conversation.id, it)
                showWallpaper = false
            },
            onPickLocal = {
                showWallpaper = false
                wallpaperPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
    }

    // Fullscreen photo viewer — tap a chat photo to see it at screen size.
    fullscreenImage?.let { url ->
        androidx.activity.compose.BackHandler { fullscreenImage = null }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { fullscreenImage = null },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = "Foto layar penuh",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Edit-before-send screen for a picked/captured photo. Sending compresses the
    // (possibly edited) bitmap and routes through sendMedia with the caption + flag.
    pendingImage?.let { bmp ->
        ChatImagePreviewScreen(
            source = bmp,
            onCancel = { pendingImage = null },
            onSend = { finalBmp, caption, viewOnce ->
                pendingImage = null
                val out = java.io.ByteArrayOutputStream()
                finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                sendMedia("image", "jpg", "image/jpeg", out.toByteArray(), caption = caption, viewOnce = viewOnce)
            },
        )
    }

    if (showProfile) {
        // TikTok-style profile for the person you're chatting with (their shorts,
        // follower counts, etc.). Falls back to the counterpart id when no username.
        val handle = conversation.counterpartUsername
            ?: conversation.counterpartId
        if (handle != null) {
            ProfileScreen(username = handle, onClose = { showProfile = false })
        } else {
            showProfile = false
        }
    }

    // The call itself is rendered by CallHost at the app root, not here — so it
    // keeps running (as a floating window) even after you leave this chat.
}

/** Long-press a bubble. "For everyone" only makes sense for messages I sent. */
private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@Composable
private fun MessageActionsDialog(
    msg: Message,
    myReaction: String?,
    canEdit: Boolean,
    isPinned: Boolean,
    isTranslated: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onTranslate: () -> Unit,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp),
        ) {
            // Quick-reaction row — hidden for a message that's already deleted.
            if (!msg.isDeleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        val picked = emoji == myReaction
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (picked) NexusAccent.copy(alpha = 0.25f) else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { onReact(emoji) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = msg.text,
                color = NexusTextSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(14.dp))
            if (canEdit) {
                MessageAction("Edit pesan", NexusTextPrimary, onEdit)
            }
            if (!msg.isDeleted && msg.text.isNotBlank()) {
                MessageAction("Salin", NexusTextPrimary, onCopy)
                MessageAction(if (isTranslated) "Sembunyikan terjemahan" else "Terjemahkan", NexusTextPrimary, onTranslate)
            }
            if (!msg.isDeleted) {
                MessageAction(if (isPinned) "Batalkan semat" else "Sematkan pesan", NexusTextPrimary, onPin)
            }
            if (msg.fromMe && !msg.isDeleted) {
                MessageAction("Hapus untuk semua orang", Color(0xFFFF5D5D), onDeleteForEveryone)
            }
            MessageAction("Hapus untuk saya", NexusTextPrimary, onDeleteForMe)
            MessageAction("Batal", NexusTextSecondary, onDismiss)
        }
    }
}

@Composable
private fun MessageAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = tint,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
    )
}

@Composable
private fun ClearChatDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Hapus semua pesan?", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Seluruh percakapan dengan $name akan dihapus dari perangkat ini. " +
                    "Salinan di perangkat lawan bicara tidak ikut terhapus.",
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
// Top bar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailTopBar(
    convo: Conversation,
    peerTyping: Boolean = false,
    peerOnline: Boolean = false,
    peerAvatar: String? = null,
    onBack: () -> Unit = {},
    onLongPressAvatar: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onVoiceCall: () -> Unit = {},
    onVideoCall: () -> Unit = {},
    onMenuAction: (String) -> Unit = {},
) {
    val status = when {
        peerTyping -> "typing…"
        peerOnline || convo.presence == Presence.ONLINE -> "online"
        convo.presence == Presence.TYPING -> "typing…"
        else -> "last seen recently"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusSurface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonBox(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali ke daftar chat",
                tint = NexusTextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier.combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                // Tap the photo → open the profile; long-press → clear chat.
                onClick = onOpenProfile,
                onLongClick = onLongPressAvatar,
            ),
        ) {
            GradientAvatar(
                gradient = convo.gradient,
                initial = convo.name.first().toString(),
                size = 36.dp,
                photoUrl = peerAvatar ?: convo.avatarUrl,
            )
        }
        Spacer(Modifier.width(14.dp))
        // Name/status take all remaining space and truncate with an ellipsis so a
        // long name becomes e.g. "reza ramadhan start" -> "reza ramadhan…".
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onOpenProfile,
                )
                .padding(end = 6.dp),
        ) {
            Text(
                text = convo.name,
                color = NexusTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                color = if (convo.presence == Presence.NONE) NexusTextSecondary else NexusAccentSoft,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButtonBox(onClick = onVideoCall) {
            Icon(Icons.Filled.Videocam, "Video call", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
        }
        IconButtonBox(onClick = onVoiceCall) {
            Icon(Icons.Filled.Call, "Call", tint = NexusTextPrimary, modifier = Modifier.size(20.dp))
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButtonBox(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, "More", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf(
                    "Laporkan" to false,
                    "Blokir" to true,
                    "Bersihkan obrolan" to false,
                    "Grup Baru" to false,
                    "Tema obrolan" to false,
                    "Latar obrolan" to false,
                ).forEach { (label, danger) ->
                    DropdownMenuItem(
                        text = {
                            Text(label, color = if (danger) Color(0xFFFF5D5D) else NexusTextPrimary)
                        },
                        onClick = {
                            menuOpen = false
                            onMenuAction(label)
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Message list pieces
// ---------------------------------------------------------------------------

/**
 * Placeholder shown while an older page of messages is fetched. A few shimmering
 * bubbles — alternating sides — so scrolling up reads as "loading earlier chat"
 * instead of a dead gap.
 */
@Composable
private fun MessagesSkeleton() {
    val transition = rememberInfiniteTransition(label = "msgSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "shimmer",
    )
    // width fractions + side per fake bubble (deterministic, so it doesn't jump).
    val rows = listOf(0.55f to false, 0.42f to true, 0.68f to false, 0.5f to true)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { (frac, mine) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .height(38.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NexusSurface.copy(alpha = alpha)),
                )
            }
        }
    }
}

/** A "sekali lihat" photo placeholder — tap to open once, then locked as "Dibuka". */
@Composable
private fun ViewOnceBubble(opened: Boolean, textColor: Color, onOpen: () -> Unit) {
    // Once opened (by either side) there's no icon at all — just an italic
    // "sudah dibuka", like a spent message.
    if (opened) {
        Text(
            text = "sudah dibuka",
            color = textColor.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
        )
        return
    }
    // Unopened: a small SOLID ring with the "1" centred, and "Foto" beside it.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .border(1.dp, textColor.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "1",
                color = textColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                // No font padding, so the digit sits dead-centre in the small ring.
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 9.sp,
                ),
            )
        }
        Spacer(Modifier.width(7.dp))
        Text("Foto", color = textColor, fontSize = 14.sp)
    }
}

@Composable
private fun DateChip(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = NexusTextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .background(NexusSurface, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: Message,
    state: DeliveryState,
    outgoingColor: Color,
    onLongPress: () -> Unit,
    onImageClick: (String) -> Unit = {},
    onReply: () -> Unit = {},
    quoted: Message? = null,
    reactions: Map<String, Int> = emptyMap(),
    translation: String? = null,
    onHideTranslation: () -> Unit = {},
    viewOnceOpened: Boolean = false,
    onOpenViewOnce: () -> Unit = {},
) {
    // Stickers float free — no bubble background behind a big emoji.
    val isSticker = msg.sticker != null
    // A photo/GIF with no caption floats free too: no coloured bubble behind the
    // image, just the rounded media. A caption, a reply-quote or a story-reply keeps
    // the bubble so the accompanying text stays readable.
    val isPureMedia = !isSticker && msg.media != null && !msg.media.isAudioUrl() &&
        !msg.viewOnce && msg.text.isBlank() && quoted == null && msg.storyReplyUrl == null
    val bubbleColor = when {
        isSticker || isPureMedia -> Color.Transparent
        msg.fromMe -> outgoingColor
        else -> NexusSurfaceElevated
    }
    val textColor = if (msg.fromMe) Color.White else NexusTextPrimary
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (msg.fromMe) 16.dp else 4.dp,
        bottomEnd = if (msg.fromMe) 4.dp else 16.dp,
    )

    // Pop the bubble in when it first appears, so sending feels immediate.
    val appear = remember { Animatable(0.85f) }
    LaunchedEffect(msg.id) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    // Swipe-right-to-reply: drag the row, snap back, and fire onReply past a threshold.
    val swipeX = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(swipeX.value.toInt(), 0) }
                .pointerInput(msg.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeX.value > 120f) onReply()
                            swipeScope.launch { swipeX.animateTo(0f) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        // Only allow dragging to the right, capped so it feels elastic.
                        swipeScope.launch { swipeX.snapTo((swipeX.value + dragAmount).coerceIn(0f, 160f)) }
                    }
                },
            verticalAlignment = Alignment.Bottom,
        ) {
            if (msg.fromMe) Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = appear.value
                        scaleY = appear.value
                        alpha = appear.value
                        transformOrigin = TransformOrigin(if (msg.fromMe) 1f else 0f, 1f)
                    }
                    .widthIn(max = 280.dp)
                    .background(bubbleColor, shape)
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                    .padding(
                        horizontal = if (isPureMedia) 0.dp else 10.dp,
                        vertical = if (isPureMedia) 0.dp else 6.dp,
                    ),
            ) {
                // Quoted message (WhatsApp-style): a small placeholder of the message
                // this one replies to, shown above the body.
                quoted?.let { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(start = 6.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(NexusAccentSoft),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (q.fromMe) "Kamu" else "Membalas",
                                color = NexusAccentSoft,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when {
                                    q.media != null && q.media.isAudioUrl() -> "🎤 Pesan suara"
                                    q.media != null -> "📷 Foto"
                                    else -> q.text
                                },
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // Story reply: a small blurred thumbnail of the story, above the text,
                // so it's clear which story this replies to (bubble-height sized).
                msg.storyReplyUrl?.let { url ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            // Tap the reply preview to open the story again (full screen).
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onImageClick(url) },
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Story dibalas",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 30.dp, height = 42.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .blur(3.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Membalas story",
                            color = textColor.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
                // Attachments come back as ready URLs; a caption may sit under them.
                val media = msg.media
                when {
                    msg.sticker != null -> Text(
                        text = msg.sticker,
                        fontSize = 68.sp,
                        lineHeight = 74.sp,
                    )
                    media != null && media.isAudioUrl() -> AudioBubble(media, textColor)
                    media != null && msg.viewOnce -> ViewOnceBubble(
                        opened = viewOnceOpened,
                        textColor = textColor,
                        onOpen = onOpenViewOnce,
                    )
                    media != null && media.substringBefore('?').endsWith(".gif", ignoreCase = true) ->
                        // GIF: show the WHOLE thing (contain), sized to its own aspect
                        // ratio and capped to the bubble — no crop, no background box.
                        AsyncImage(
                            model = media,
                            contentDescription = "GIF",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .widthIn(max = 220.dp)
                                .heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onImageClick(media) },
                        )
                    media != null -> Box(
                        modifier = Modifier
                            .size(width = 220.dp, height = 260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onImageClick(media) },
                    ) {
                        // Shimmer behind the photo so the bubble never shows an empty
                        // grey box while it loads.
                        com.example.syntra.ShimmerFill(Modifier.matchParentSize())
                        SubcomposeAsyncImage(
                            model = media,
                            contentDescription = "Foto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    else -> Text(
                        text = msg.text,
                        color = if (msg.isDeleted) textColor.copy(alpha = 0.6f) else textColor,
                        fontStyle = if (msg.isDeleted) FontStyle.Italic else FontStyle.Normal,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    )
                }
                if (media != null && msg.text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = msg.text, color = textColor, fontSize = 15.sp, lineHeight = 20.sp)
                }
                // Translation, shown under the original text. Tap to hide it again.
                translation?.let { tr ->
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onHideTranslation,
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "Terjemahan",
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(text = tr, color = textColor, fontSize = 15.sp, lineHeight = 20.sp)
                    }
                }
                // Time + delivery ticks INSIDE the bubble, bottom-right — WhatsApp style.
                // Colour adapts: muted-white on the accent bubble, grey on the surface one.
                val metaColor = if (msg.fromMe) Color.White.copy(alpha = 0.72f) else NexusTextSecondary
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (msg.isEdited && !msg.isDeleted) {
                        Text(text = "diedit", color = metaColor, fontSize = 10.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(text = msg.time, color = metaColor, fontSize = 10.sp)
                    if (msg.fromMe) {
                        Spacer(Modifier.width(4.dp))
                        DeliveryTicks(state, metaColor)
                    }
                }
            }
            if (!msg.fromMe) Spacer(Modifier.weight(1f))
        }
        // Reaction chips sit just under the bubble, on the same side.
        if (reactions.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (msg.fromMe) Arrangement.End else Arrangement.Start,
            ) {
                reactions.forEach { (emoji, count) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(NexusSurfaceElevated, RoundedCornerShape(50))
                            .border(1.dp, NexusStroke, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(emoji, fontSize = 12.sp)
                        if (count > 1) {
                            Spacer(Modifier.width(3.dp))
                            Text(count.toString(), color = NexusTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Collapses `userId -> emoji` into `emoji -> count` for display. */
private fun aggregateReactions(users: Map<String, String>?): Map<String, Int> {
    if (users.isNullOrEmpty()) return emptyMap()
    return users.values.groupingBy { it }.eachCount()
}

/** A body that is nothing but a link to our own media bucket. */
private fun String.isMediaUrl(): Boolean =
    startsWith("http") && !contains(' ') && contains("/object/public/media/")

private fun String.isAudioUrl(): Boolean =
    endsWith(".m4a", true) || endsWith(".mp3", true) || endsWith(".aac", true)

/**
 * Only ONE voice note plays at a time across the whole chat. When a bubble starts,
 * it claims the bus; every other bubble watches the bus and pauses itself. This is
 * what stops two clips overlapping into noise.
 */
object VoiceBus {
    var active by mutableStateOf<Any?>(null)

    /**
     * Stop whatever voice note is playing. Clearing the active token makes every
     * bubble's "someone else took the bus" effect pause itself — used when the app
     * goes to the background so a voice note doesn't keep playing.
     */
    fun pauseActive() { active = null }
}

/** Voice note bubble with a play/pause button. */
@Composable
private fun AudioBubble(url: String, tint: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Unique identity for this bubble instance, used to arbitrate the single-play bus.
    val token = remember { Any() }
    var playing by remember(url) { mutableStateOf(false) }
    // Buffering the (remote) clip for the first time. Drives the spinner so the
    // button never looks frozen while the audio loads.
    var loading by remember(url) { mutableStateOf(false) }
    var prepared by remember(url) { mutableStateOf(false) }
    // Playback progress 0..1 and total length, so the waveform can be washed with
    // an "aura" up to where playback currently is (and show elapsed / total time).
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var durationMs by remember(url) { mutableIntStateOf(0) }
    val player = remember(url) { android.media.MediaPlayer() }

    // Another voice note took over the bus — pause this one so they never overlap.
    LaunchedEffect(VoiceBus.active) {
        if (VoiceBus.active !== token && playing) {
            runCatching { player.pause() }
            playing = false
        }
    }

    // While playing, sample the position ~15×/sec into a smooth progress signal.
    LaunchedEffect(playing, durationMs) {
        if (!playing || durationMs <= 0) return@LaunchedEffect
        while (true) {
            progress = (runCatching { player.currentPosition }.getOrDefault(0)
                .toFloat() / durationMs).coerceIn(0f, 1f)
            kotlinx.coroutines.delay(60)
        }
    }

    DisposableEffect(url) {
        onDispose {
            if (VoiceBus.active === token) VoiceBus.active = null
            runCatching { player.release() }
        }
    }

    fun toggle() {
        runCatching {
            when {
                loading -> Unit // still buffering — ignore extra taps
                playing -> { player.pause(); playing = false }
                prepared -> { com.example.syntra.net.MusicPlayer.pauseForExternalAudio(); VoiceBus.active = token; player.start(); playing = true }
                else -> {
                    // First play: prepare OFF the main thread (prepareAsync) so the UI
                    // never blocks; show a spinner until the audio is actually ready.
                    VoiceBus.active = token
                    loading = true
                    // Cache-once: resolve to the local cached file (download the first
                    // time), then play from disk. Keyed by URL, so the same voice note
                    // shares ONE cache everywhere and replays cost no egress. Streams
                    // straight from the URL if caching fails.
                    scope.launch {
                        val src = if (url.startsWith("http")) VideoCache.resolve(context, url) else url
                        runCatching {
                            player.setAudioAttributes(
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build(),
                            )
                            player.setDataSource(context, android.net.Uri.parse(src))
                            player.setOnPreparedListener { mp ->
                                loading = false
                                prepared = true
                                durationMs = runCatching { mp.duration }.getOrDefault(0)
                                com.example.syntra.net.MusicPlayer.pauseForExternalAudio()
                                mp.start()
                                playing = true
                            }
                            player.setOnCompletionListener { mp ->
                                playing = false
                                progress = 0f
                                runCatching { mp.seekTo(0) }
                            }
                            player.setOnErrorListener { mp, _, _ ->
                                loading = false; playing = false; prepared = false
                                // Reset so a later tap can start a clean prepare instead
                                // of hitting IllegalStateException on the dead player.
                                runCatching { mp.reset() }
                                Toast.makeText(context, "Tidak bisa memutar suara.", Toast.LENGTH_SHORT).show()
                                true
                            }
                            player.prepareAsync()
                        }.onFailure {
                            loading = false
                            Toast.makeText(context, "Tidak bisa memutar suara.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }.onFailure {
            loading = false
            Toast.makeText(context, "Tidak bisa memutar suara.", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(190.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.18f), androidx.compose.foundation.shape.CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = tint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Jeda" else "Putar",
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            // Waveform washed with an accent "aura" that flows with playback. Two
            // things make it SMOOTH (not the old jerky per-bar jump): (1) the raw
            // progress is interpolated by a short linear tween, and (2) the aura fills
            // each bar with a CONTINUOUS alpha, so the leading bar fades in gradually
            // instead of snapping on. Heights are a fixed pattern (no stored amplitude).
            val heights = remember { listOf(7, 13, 9, 17, 11, 20, 13, 22, 12, 18, 10, 16, 9, 14, 8, 15, 11, 19) }
            val auraBrush = Brush.verticalGradient(listOf(NexusAccentSoft, NexusAccent))
            val animP by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 90, easing = androidx.compose.animation.core.LinearEasing),
                label = "voice-progress",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            ) {
                heights.forEachIndexed { i, h ->
                    // 0..1 fill for this bar: fully lit behind the line, partial on the
                    // leading bar, dark ahead — a soft moving edge instead of a step.
                    val fill = (animP * heights.size - i).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = h.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    ) {
                        Box(Modifier.matchParentSize().background(tint.copy(alpha = 0.4f)))
                        if (fill > 0f) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .graphicsLayer { alpha = fill }
                                    .background(auraBrush),
                            )
                        }
                    }
                }
            }
            // Elapsed / total time under the wave.
            if (durationMs > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatMillis((progress * durationMs).toInt())} / ${formatMillis(durationMs)}",
                    color = tint.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** mm:ss from milliseconds, for the voice-note timer. */
private fun formatMillis(ms: Int): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * Where a sent message got to:
 *  - SENDING   : still local, no server ack yet (clock)
 *  - SENT      : stored on the server, not yet on the peer's device (1 tick)
 *  - DELIVERED : the peer's device confirmed receipt via message.delivered (2 grey ticks)
 *  - READ      : the peer opened the chat and read it (2 blue ticks)
 */
private enum class DeliveryState { SENDING, SENT, DELIVERED, READ }

@Composable
private fun DeliveryTicks(state: DeliveryState, base: Color) {
    when (state) {
        DeliveryState.SENDING -> Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = "Mengirim",
            tint = base.copy(alpha = 0.6f),
            modifier = Modifier.size(11.dp),
        )
        DeliveryState.SENT -> Icon(
            imageVector = Icons.Filled.Done,
            contentDescription = "Terkirim",
            tint = base.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp),
        )
        DeliveryState.DELIVERED -> Icon(
            imageVector = Icons.Filled.DoneAll,
            contentDescription = "Sampai",
            tint = base.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp),
        )
        DeliveryState.READ -> Icon(
            imageVector = Icons.Filled.DoneAll,
            contentDescription = "Dibaca",
            tint = Color(0xFF7FE3FF),
            modifier = Modifier.size(13.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Input bar
// ---------------------------------------------------------------------------

@Composable
private fun MessageInputBar(
    value: String,
    emojiOpen: Boolean,
    focusRequester: FocusRequester,
    onToggleEmoji: () -> Unit,
    onFieldFocused: () -> Unit,
    onAttach: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusBackground)
            // Insets are mutually exclusive: opening emoji hides the keyboard, so we
            // only ever need one. When the emoji panel is up it owns the nav-bar gap;
            // otherwise one combined ime∪navbar inset avoids the double-lift bug.
            .then(
                if (emojiOpen) Modifier
                else Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Text pill
        Row(
            modifier = Modifier
                .weight(1f)
                .background(NexusSurface, RoundedCornerShape(26.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                // Toggles to a keyboard glyph while the emoji panel is up.
                imageVector = if (emojiOpen) Icons.Outlined.Keyboard else Icons.Outlined.EmojiEmotions,
                contentDescription = if (emojiOpen) "Keyboard" else "Emoji",
                tint = if (emojiOpen) NexusAccentSoft else NexusTextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onToggleEmoji,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Message",
                        color = NexusTextSecondary,
                        fontSize = 15.sp,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(NexusAccentSoft),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusEvent {
                            // Tapping the input box: switch to the system keyboard and
                            // flip the icon back to emoji. Handled by a dedicated
                            // callback (not the toggle) so it never re-opens the panel.
                            if (it.isFocused && emojiOpen) onFieldFocused()
                        },
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Kamera atau galeri",
                tint = NexusTextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onAttach,
                    ),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Send / mic action — grows when there is something to send, and gives a
        // quick squeeze on tap so the press registers visually.
        val hasText = value.isNotBlank()
        val scope = rememberCoroutineScope()
        val press = remember { Animatable(1f) }
        val grow by animateFloatAsState(if (hasText) 1f else 0.9f, label = "sendGrow")
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    val s = press.value * grow
                    scaleX = s
                    scaleY = s
                }
                .background(if (hasText) NexusAccent else Color(0xFF2A2A32), RoundedCornerShape(50))
                .then(
                    if (hasText) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            scope.launch {
                                press.animateTo(0.82f, tween(70))
                                press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                            onSend()
                        }
                    } else {
                        // Hold to record, release to send — the WhatsApp gesture.
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onStartRecording()
                                    tryAwaitRelease()
                                    onStopRecording()
                                },
                            )
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (hasText) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                contentDescription = if (hasText) "Send" else "Voice",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Small helper: borderless icon button
// ---------------------------------------------------------------------------

@Composable
private fun IconButtonBox(
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
