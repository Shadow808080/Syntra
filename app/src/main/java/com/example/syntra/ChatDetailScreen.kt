package com.example.syntra

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.ApiException
import com.example.syntra.net.NetMessage
import com.example.syntra.net.NetPresence
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
)

/** Marks a message that only exists on this device until the server confirms it. */
private const val LOCAL_ID_PREFIX = "local-"

/** Backend message -> bubble. `fromMe` is derived client-side (alignment doc §6). */
private fun NetMessage.toUi() = Message(
    id = id,
    text = if (isDeleted) "Pesan ini dihapus" else body,
    fromMe = senderId == SyntraClient.myUserId,
    time = formatClock(createdAt),
)

/** Render a RFC3339 UTC timestamp as local HH:mm. */
private fun formatClock(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val local = java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(local)
    }.getOrDefault("")
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
    var input by remember { mutableStateOf("") }
    var peerTyping by remember { mutableStateOf(false) }
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
    var confirmClear by remember(conversation) { mutableStateOf(false) }
    var pendingMessage by remember(conversation) { mutableStateOf<Message?>(null) }
    // Overflow-menu actions.
    var showReport by remember(conversation) { mutableStateOf(false) }
    var confirmBlock by remember(conversation) { mutableStateOf(false) }
    var showChatTheme by remember(conversation) { mutableStateOf(false) }
    var showProfile by remember(conversation) { mutableStateOf(false) }
    // When non-null, a full-screen call (true = video) is on top of the chat.
    var activeCallVideo by remember(conversation) { mutableStateOf<Boolean?>(null) }
    var chatTheme by remember(conversation) { mutableStateOf(ChatThemeStore.get(context, conversation.id)) }

    fun startCall(video: Boolean) {
        if (!ApiConfig.ENABLED) {
            Toast.makeText(context, "Server belum aktif.", Toast.LENGTH_SHORT).show()
            return
        }
        showProfile = false
        activeCallVideo = video
    }
    val listState = rememberLazyListState()

    // Composer extras: emoji panel, attachments, voice notes.
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    var showEmoji by remember { mutableStateOf(false) }
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
    // when all are read), with no visible scroll from the top. Item 0 is the date
    // chip, so message i sits at list index i+1.
    var landed by remember(conversation) { mutableStateOf(false) }
    LaunchedEffect(messages.isNotEmpty()) {
        if (messages.isNotEmpty() && !landed) {
            val firstUnread = (messages.size - conversation.unread).coerceIn(0, messages.lastIndex)
            val target = if (conversation.unread > 0) firstUnread + 1 else messages.size
            listState.scrollToItem(target)
            landed = true
        }
    }

    // After landing, keep the newest message in view — but only when the user is
    // already near the bottom, so it doesn't yank them while reading history.
    LaunchedEffect(messages.size) {
        if (!landed || messages.isEmpty()) return@LaunchedEffect
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (last >= messages.size - 2) listState.animateScrollToItem(messages.size)
    }

    // --- Backend: history, realtime, read receipts -------------------------
    if (ApiConfig.ENABLED) {
        LaunchedEffect(conversation.id) {
            runCatching {
                SyntraClient.subscribe(listOf("conversation:${conversation.id}"))
                // History comes back newest-first; the UI renders oldest-first.
                val history = SyntraClient.getMessages(conversation.id).reversed()
                messages.clear()
                messages.addAll(history.map { it.toUi() })
                history.lastOrNull()?.let { SyntraClient.messageRead(conversation.id, it.id) }
            }.onFailure {
                Toast.makeText(context, "Gagal memuat pesan: ${it.message}", Toast.LENGTH_SHORT).show()
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
                        val pending = messages.indexOfFirst {
                            it.id.startsWith(LOCAL_ID_PREFIX) && it.text == message.body
                        }
                        if (pending >= 0) {
                            messages[pending] = message.toUi()
                            return
                        }
                    }
                    messages.add(message.toUi())
                    peerTyping = false
                    // Honour the privacy switch: no read receipt when it is off.
                    if (SettingsStore.getBool(context, SettingsStore.READ_RECEIPTS, true)) {
                        SyntraClient.messageRead(conversation.id, message.id)
                    }
                }

                override fun onTyping(conversationId: String, userId: String, typing: Boolean) {
                    if (conversationId == conversation.id && userId != SyntraClient.myUserId) {
                        peerTyping = typing
                    }
                }

                override fun onReadReceipt(conversationId: String, messageId: String) {
                    // The peer read up to here: flip our ✓✓ to blue live, no refresh.
                    if (conversationId != conversation.id) return
                    if (counterpartLastReadId == null || messageId > counterpartLastReadId!!) {
                        counterpartLastReadId = messageId
                    }
                }

                override fun onPresence(presence: NetPresence) {
                    // Online/offline in the header updates itself.
                    if (presence.userId == conversation.counterpartId) {
                        peerOnline = presence.online
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
    fun sendMedia(kind: String, ext: String, mime: String, bytes: ByteArray, durationMs: Long = 0) {
        if (!ApiConfig.ENABLED) {
            Toast.makeText(context, "Backend belum dikonfigurasi.", Toast.LENGTH_SHORT).show()
            return
        }
        uploading = true
        scope.launch {
            runCatching {
                SyntraClient.uploadMediaFull(kind, ext, mime, bytes, durationMs = durationMs)
            }.onSuccess { (_, url) ->
                if (url.isBlank()) {
                    Toast.makeText(context, "Server tidak mengembalikan URL media.", Toast.LENGTH_LONG).show()
                } else {
                    val ref = "$LOCAL_ID_PREFIX${System.currentTimeMillis()}"
                    messages.add(Message(ref, url, fromMe = true, time = "now"))
                    SyntraClient.messageSend(conversation.id, url, ref)
                }
            }.onFailure {
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

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
            sendMedia("image", "jpg", "image/jpeg", out.toByteArray())
        }
    }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) sendMedia("image", "jpg", "image/jpeg", bytes)
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        // Optimistic message: a client id now, replaced by the server id on ack.
        val ref = "$LOCAL_ID_PREFIX${System.currentTimeMillis()}"
        messages.add(Message(ref, text, fromMe = true, time = "now"))
        input = ""
        if (ApiConfig.ENABLED) {
            SyntraClient.typingStop(conversation.id)
            SyntraClient.messageSend(conversation.id, text, ref)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
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
                }
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { DateChip("Today") }
            items(messages) { msg ->
                MessageBubble(
                    msg = msg,
                    outgoingColor = chatTheme.bubble,
                    onLongPress = { pendingMessage = msg },
                    state = when {
                        msg.id.startsWith(LOCAL_ID_PREFIX) -> DeliveryState.SENDING
                        // UUIDv7 sorts by time, so a plain comparison answers
                        // "did they read this yet?" without any receipt table.
                        counterpartLastReadId != null && msg.id <= counterpartLastReadId!! ->
                            DeliveryState.READ
                        else -> DeliveryState.SENT
                    },
                )
            }
        }

        if (recording) {
            RecordingBar(seconds = recordSeconds, onCancel = { cancelRecording() })
        }

        MessageInputBar(
            value = input,
            emojiOpen = showEmoji,
            focusRequester = fieldFocus,
            onToggleEmoji = {
                showEmoji = !showEmoji
                if (showEmoji) {
                    // Opening emoji: drop the soft keyboard so both don't fight.
                    keyboard?.hide()
                } else {
                    // Back to keyboard: refocus the field and raise it.
                    fieldFocus.requestFocus()
                    keyboard?.show()
                }
            },
            onAttach = { showAttach = true },
            onStartRecording = { startRecording() },
            onStopRecording = { stopRecording() },
            onValueChange = { text ->
                input = text
                if (ApiConfig.ENABLED) {
                    if (text.isBlank()) SyntraClient.typingStop(conversation.id)
                    else SyntraClient.typingStart(conversation.id)
                }
            },
            onSend = { send() },
        )

        if (showEmoji) {
            EmojiPicker(
                onPick = { input += it },
                onBackspace = { input = input.dropLast(1) },
            )
        }
    }

    if (showAttach) {
        AttachmentSheet(
            onCamera = { showAttach = false; camera.launch(null) },
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
        MessageActionsDialog(
            msg = msg,
            onDismiss = { pendingMessage = null },
            onDeleteForMe = {
                pendingMessage = null
                messages.remove(msg)
            },
            onDeleteForEveryone = {
                pendingMessage = null
                scope.launch {
                    runCatching { SyntraClient.deleteMessage(msg.id) }
                        .onSuccess { messages.remove(msg) }
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

    if (showProfile) {
        ProfileUserScreen(
            conversation = conversation,
            onBack = { showProfile = false },
            onCall = { startCall(video = false) },
            onVideo = { startCall(video = true) },
            onSearch = { showProfile = false }, // back to the conversation
        )
    }

    // Full-screen call overlay (voice or video), on top of everything else.
    activeCallVideo?.let { video ->
        CallScreen(
            peerName = conversation.name,
            conversationId = conversation.id,
            video = video,
            peerId = conversation.counterpartId.orEmpty(),
            onClose = { activeCallVideo = null },
        )
    }
}

/** Long-press a bubble. "For everyone" only makes sense for messages I sent. */
@Composable
private fun MessageActionsDialog(
    msg: Message,
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
            Text(
                text = msg.text,
                color = NexusTextSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(14.dp))
            if (msg.fromMe) {
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
) {
    val bubbleColor = if (msg.fromMe) outgoingColor else NexusSurfaceElevated
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromMe) Arrangement.End else Arrangement.Start,
    ) {
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // A message body that is just an uploaded media URL is rendered as the
            // media itself; the backend has no media message type to rely on.
            when {
                msg.text.isMediaUrl() && msg.text.isAudioUrl() -> AudioBubble(msg.text, textColor)
                msg.text.isMediaUrl() -> AsyncImage(
                    model = msg.text,
                    contentDescription = "Foto",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 220.dp, height = 260.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                else -> Text(
                    text = msg.text,
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp),
            ) {
                Text(
                    text = msg.time,
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                )
                if (msg.fromMe) {
                    Spacer(Modifier.width(4.dp))
                    DeliveryTicks(state, textColor)
                }
            }
        }
    }
}

/** A body that is nothing but a link to our own media bucket. */
private fun String.isMediaUrl(): Boolean =
    startsWith("http") && !contains(' ') && contains("/object/public/media/")

private fun String.isAudioUrl(): Boolean =
    endsWith(".m4a", true) || endsWith(".mp3", true) || endsWith(".aac", true)

/** Voice note bubble with a play/pause button. */
@Composable
private fun AudioBubble(url: String, tint: Color) {
    val context = LocalContext.current
    var playing by remember(url) { mutableStateOf(false) }
    val player = remember(url) { android.media.MediaPlayer() }

    DisposableEffect(url) {
        onDispose { runCatching { player.release() } }
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
                ) {
                    runCatching {
                        if (playing) {
                            player.pause()
                            playing = false
                        } else {
                            if (!player.isPlaying && player.currentPosition == 0) {
                                player.setDataSource(context, android.net.Uri.parse(url))
                                player.prepare()
                                player.setOnCompletionListener { playing = false }
                            }
                            player.start()
                            playing = true
                        }
                    }.onFailure {
                        Toast.makeText(context, "Tidak bisa memutar suara.", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Jeda" else "Putar",
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        // Static waveform: a real one needs amplitude data the server does not keep.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(8, 16, 11, 20, 14, 22, 10, 17, 12, 19, 9, 15).forEach { h ->
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = h.dp)
                        .background(tint.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/** Where a sent message got to: still local, on the server, or read by the peer. */
private enum class DeliveryState { SENDING, SENT, READ }

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
                            // Tapping the field to type should retract the emoji panel.
                            if (it.isFocused && emojiOpen) onToggleEmoji()
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
