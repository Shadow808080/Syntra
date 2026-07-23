package com.example.syntra

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetUser
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.launch

/** A body that is just a link into our media bucket (image). */
private fun String.looksLikeImage(): Boolean =
    startsWith("http") && contains("/object/public/media/") &&
        !endsWith(".m4a", true) && !endsWith(".mp3", true) && !endsWith(".mp4", true)

/**
 * WhatsApp-style contact info screen: big avatar, call/video/search actions,
 * shared media, and a settings list.
 *
 * Full details come from `GET /users/{username}`. Chats opened from the list still
 * lack the counterpart's username, so those show what's known and disable the
 * profile-only bits.
 */
@Composable
fun ProfileUserScreen(
    conversation: Conversation,
    onBack: () -> Unit,
    onCall: () -> Unit = {},
    onVideo: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val username = conversation.counterpartUsername

    var user by remember(conversation.id) { mutableStateOf<NetUser?>(null) }
    var followStatus by remember(conversation.id) { mutableStateOf("") }
    val media = remember(conversation.id) { mutableStateListOf<String>() }

    // Local per-conversation toggles.
    var chatLocked by remember(conversation.id) {
        mutableStateOf(ContactPrefs.get(context, conversation.id, "lock"))
    }
    var translate by remember(conversation.id) {
        mutableStateOf(ContactPrefs.get(context, conversation.id, "translate"))
    }
    var muted by remember(conversation.id) {
        mutableStateOf(ContactPrefs.get(context, conversation.id, "mute"))
    }

    LaunchedEffect(conversation.id) {
        if (!ApiConfig.ENABLED) return@LaunchedEffect
        if (username != null) {
            runCatching { SyntraClient.getUser(username) }
                .onSuccess { user = it; followStatus = it.followStatus }
        }
        // Shared media = image links pulled from the conversation history.
        runCatching { SyntraClient.getMessages(conversation.id) }
            .onSuccess { msgs ->
                media.clear()
                media.addAll(msgs.map { it.body }.filter { it.looksLikeImage() }.take(12))
            }
    }

    val name = user?.displayName?.ifBlank { user?.username } ?: conversation.name
    val avatarUrl = user?.avatarMediaId?.takeIf { it.startsWith("http") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack)
                Spacer(Modifier.weight(1f))
                CircleIcon(Icons.Filled.MoreVert, "Menu") {}
            }
        }

        // Avatar + name + handle
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(conversation.gradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = name.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = name,
                    color = NexusTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = username?.let { "@$it" } ?: statusText(conversation),
                    color = NexusTextSecondary,
                    fontSize = 15.sp,
                )
            }
        }

        // Call / Video / Search actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ActionButton(Icons.Filled.Call, "Panggilan", onCall)
                Spacer(Modifier.width(14.dp))
                ActionButton(Icons.Filled.Videocam, "Video", onVideo)
                Spacer(Modifier.width(14.dp))
                ActionButton(Icons.Filled.Search, "Cari", onSearch)
            }
        }

        // Follow (only when we know the user and it isn't me)
        if (username != null && user?.isSelf != true) {
            item {
                val following = followStatus == "accepted" || followStatus == "pending"
                val label = when (followStatus) {
                    "accepted" -> "Mengikuti"
                    "pending" -> "Diminta"
                    else -> "Ikuti"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(46.dp)
                        .background(
                            brush = if (following) Brush.horizontalGradient(listOf(NexusSurface, NexusSurface))
                            else Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)),
                            shape = RoundedCornerShape(23.dp),
                        )
                        .then(if (following) Modifier.border(1.dp, NexusStroke, RoundedCornerShape(23.dp)) else Modifier)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            scope.launch {
                                runCatching {
                                    if (following) SyntraClient.unfollow(username) else SyntraClient.follow(username)
                                    followStatus = SyntraClient.getUser(username).followStatus
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (following) NexusTextPrimary else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Shared media
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Media, tautan, dan dok", color = NexusTextPrimary, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(media.size.toString(), color = NexusTextSecondary, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
            }
            if (media.isEmpty()) {
                Text(
                    "Belum ada media yang dibagikan.",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, bottom = 12.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    items(media) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NexusSurface),
                        )
                    }
                }
            }
            Divider()
        }

        // Settings list
        item {
            NavRow(Icons.Filled.Folder, "Kelola penyimpanan", "Media di perangkat ini") {
                Toast.makeText(context, "Kelola penyimpanan", Toast.LENGTH_SHORT).show()
            }
            ToggleRow(
                Icons.Filled.Notifications, "Notifikasi",
                if (muted) "Dibisukan" else "Aktif",
                checked = !muted,
                onChange = {
                    muted = !it
                    ContactPrefs.set(context, conversation.id, "mute", muted)
                    if (ApiConfig.ENABLED) scope.launch {
                        runCatching { SyntraClient.setConversationMute(conversation.id, if (muted) 480 else null) }
                    }
                },
            )
            NavRow(Icons.Filled.Photo, "Visibilitas media", null) {
                Toast.makeText(context, "Visibilitas media", Toast.LENGTH_SHORT).show()
            }
            NavRow(
                Icons.Filled.Lock, "Enkripsi",
                "Pesan dan panggilan terenkripsi end-to-end.",
            ) {}
            NavRow(Icons.Filled.Timer, "Pesan sementara", "Nonaktif") {
                Toast.makeText(context, "Pesan sementara", Toast.LENGTH_SHORT).show()
            }
            ToggleRow(
                Icons.Filled.Lock, "Kunci obrolan",
                "Kunci dan sembunyikan obrolan ini di perangkat ini.",
                checked = chatLocked,
                onChange = {
                    chatLocked = it
                    ContactPrefs.set(context, conversation.id, "lock", it)
                },
            )
            NavRow(Icons.Filled.Shield, "Privasi obrolan tingkat lanjut", "Nonaktif") {}
            ToggleRow(
                Icons.Filled.Language, "Terjemahkan pesan", null,
                checked = translate,
                onChange = {
                    translate = it
                    ContactPrefs.set(context, conversation.id, "translate", it)
                },
            )
            Divider()
        }

        // Block, when we know who it is
        if (username != null && user?.isSelf != true) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            scope.launch { runCatching { SyntraClient.blockUser(username) } }
                            BlockStore.block(context, name)
                            Toast.makeText(context, "$name diblokir.", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Block, null, tint = Color(0xFFFF5D5D), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(20.dp))
                    Text("Blokir ${name.substringBefore(' ')}", color = Color(0xFFFF5D5D), fontSize = 15.sp)
                }
            }
        }
    }
}

private fun statusText(convo: Conversation): String = when (convo.presence) {
    Presence.ONLINE -> "online"
    Presence.TYPING -> "mengetik…"
    Presence.NONE -> "terakhir dilihat baru-baru ini"
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun CircleIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
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
        Icon(icon, description, tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .width(88.dp)
            .background(NexusSurface, RoundedCornerShape(16.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
    ) {
        Icon(icon, label, tint = NexusAccentSoft, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = NexusTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(8.dp)
            .background(NexusSurface.copy(alpha = 0.5f)),
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = NexusTextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NexusTextPrimary, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = NexusTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = NexusTextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NexusTextPrimary, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = NexusTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NexusAccent,
                uncheckedThumbColor = NexusTextSecondary,
                uncheckedTrackColor = Color(0xFF2A2A32),
                uncheckedBorderColor = NexusStroke,
            ),
        )
    }
}

/** Per-conversation local flags for the contact-info toggles. */
object ContactPrefs {
    private const val PREFS = "syntra_settings"
    private fun key(id: String, flag: String) = "contact_${flag}_$id"

    fun get(context: Context, id: String, flag: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key(id, flag), false)

    fun set(context: Context, id: String, flag: String, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(key(id, flag), value).apply()
    }
}
