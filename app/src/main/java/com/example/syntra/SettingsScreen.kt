package com.example.syntra

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.BlockStore
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.AppTheme
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.launch

/**
 * Local preferences. The backend exposes no profile or settings endpoints yet
 * (no `GET /users/me`, no `PATCH`), so everything here lives on the device and
 * the screen says so where it matters.
 */
object SettingsStore {
    private const val PREFS = "syntra_settings"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBool(context: Context, key: String, default: Boolean) =
        prefs(context).getBoolean(key, default)

    fun setBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    const val NOTIF_MESSAGES = "notif_messages"
    const val NOTIF_ROOMS = "notif_rooms"
    const val NOTIF_SOUND = "notif_sound"
    const val READ_RECEIPTS = "read_receipts"
    const val SHOW_PRESENCE = "show_presence"
    const val AUTO_PLAY_VIDEO = "autoplay_video"
    const val LOUD_SPEAKER = "loud_speaker"
    const val AUTO_SCROLL_REELS = "autoscroll_reels"

    // Auto-download per media kind inside chats. When a kind is off, the bubble shows
    // a tap-to-download placeholder instead of fetching straight away — the user
    // decides what spends their data.
    const val AUTO_DL_PHOTO = "auto_dl_photo"
    const val AUTO_DL_VIDEO = "auto_dl_video"
    const val AUTO_DL_STICKER = "auto_dl_sticker"
    const val AUTO_DL_GIF = "auto_dl_gif"
    const val AUTO_DL_VOICE = "auto_dl_voice"
}

private enum class SettingsPage { PROFILE, SECURITY, APP_LOCK, BLOCKED, THEME, STORAGE, AUTO_DOWNLOAD, PRIVACY_POLICY, TERMS }

/** Top-level settings categories; each opens a sub-page holding its own menus. */
private enum class SettingsCategory(val title: String) {
    AKUN("Akun"),
    PRIVASI("Privasi"),
    NOTIFIKASI("Notifikasi"),
    MEDIA("Media & tampilan"),
    TENTANG("Tentang"),
}

@Composable
fun SettingsScreen(onClose: () -> Unit, onSignedOut: () -> Unit) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<SettingsPage?>(null) }
    var openCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var showMyProfile by remember { mutableStateOf(false) }

    // Tapping the profile card opens the full TikTok-style profile page.
    if (showMyProfile) {
        ProfileScreen(username = null, onClose = { showMyProfile = false })
        return
    }

    // Leaf sub-screens take over the whole surface when open. Checked FIRST so a page
    // opened from inside a category (e.g. Profil under Akun) shows, and backing out of
    // it returns to that category page (which is still open underneath).
    open?.let { page ->
        when (page) {
            SettingsPage.PROFILE -> ProfileSettingsScreen { open = null }
            SettingsPage.SECURITY -> SecurityScreen(
                onClose = { open = null },
                onSignedOut = { open = null; onSignedOut() },
            )
            SettingsPage.APP_LOCK -> AppLockSettingsScreen { open = null }
            SettingsPage.BLOCKED -> BlockedContactsScreen { open = null }
            SettingsPage.THEME -> ThemeScreen { open = null }
            SettingsPage.STORAGE -> StorageScreen { open = null }
            SettingsPage.AUTO_DOWNLOAD -> AutoDownloadScreen { open = null }
            SettingsPage.PRIVACY_POLICY -> PrivacyPolicyScreen { open = null }
            SettingsPage.TERMS -> TermsScreen { open = null }
        }
        return
    }

    // A category page (Akun / Privasi / …) holds the actual menus for that group.
    openCategory?.let { category ->
        SettingsCategoryScreen(
            category = category,
            onClose = { openCategory = null },
            onOpenPage = { open = it },
        )
        return
    }

    val email = SessionStore.signedInEmail(context).orEmpty()
    val username = ProfileStore.displayName(context, email)

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
            Text("Pengaturan", color = NexusTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                ProfileCard(
                    username = username,
                    email = email,
                    avatarUrl = ProfileStore.avatarUrl(context),
                    onClick = { showMyProfile = true },
                )
            }

            // Top level = one card of categories. Each opens its own page holding the
            // menus that belong to it (Akun → Profil, Keamanan, Kunci aplikasi, dst.).
            item {
                SettingsGroup {
                    NavRow(Icons.Filled.Person, "Akun", "Profil, keamanan, kunci aplikasi") {
                        openCategory = SettingsCategory.AKUN
                    }
                    Divider()
                    NavRow(Icons.Filled.Visibility, "Privasi", "Status dibaca, status aktif, blokir") {
                        openCategory = SettingsCategory.PRIVASI
                    }
                    Divider()
                    NavRow(Icons.Filled.Notifications, "Notifikasi", "Pesan, voice room, suara") {
                        openCategory = SettingsCategory.NOTIFIKASI
                    }
                    Divider()
                    NavRow(Icons.Filled.DarkMode, "Media & tampilan", "Video, tema, unduh, penyimpanan") {
                        openCategory = SettingsCategory.MEDIA
                    }
                    Divider()
                    NavRow(Icons.Filled.Info, "Tentang", "Info aplikasi, kebijakan, ketentuan") {
                        openCategory = SettingsCategory.TENTANG
                    }
                }
            }

            item {
                // Plain text, nothing else. It used to be a full-width red-filled slab
                // with an icon — the loudest element on a screen of quiet rows, for the
                // one action nobody comes to Settings to perform. The colour alone is
                // enough to mark it as the destructive one.
                Text(
                    "Keluar akun",
                    color = Color(0xFFFF5D5D),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { confirmSignOut = true }
                        .padding(horizontal = 16.dp, vertical = 22.dp),
                )
            }

        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Keluar dari akun?",
            message = "Kamu perlu masuk lagi untuk membuka Syntra. Pesan dan story " +
                "tetap tersimpan di server.",
            confirmText = "Keluar",
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                scope.launch {
                    if (ApiConfig.ENABLED) runCatching { SyntraClient.logoutRemote() }
                    SessionStore.signOut(context)
                    onSignedOut()
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Category page — the menus that belong to one settings category
// ---------------------------------------------------------------------------

@Composable
private fun SettingsCategoryScreen(
    category: SettingsCategory,
    onClose: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }

    // Toggle state for whichever category needs it (read once from device prefs).
    var notifMessages by remember { mutableStateOf(SettingsStore.getBool(context, SettingsStore.NOTIF_MESSAGES, true)) }
    var notifRooms by remember { mutableStateOf(SettingsStore.getBool(context, SettingsStore.NOTIF_ROOMS, true)) }
    var notifSound by remember { mutableStateOf(SettingsStore.getBool(context, SettingsStore.NOTIF_SOUND, true)) }
    var readReceipts by remember { mutableStateOf(SettingsStore.getBool(context, SettingsStore.READ_RECEIPTS, true)) }
    var showPresence by remember { mutableStateOf(SettingsStore.getBool(context, SettingsStore.SHOW_PRESENCE, true)) }
    var autoPlay by remember { mutableStateOf(SettingsStore.getBool(context, SettingsStore.AUTO_PLAY_VIDEO, true)) }
    var loudSpeaker by remember { mutableStateOf(SettingsStore.getBool(context, SettingsStore.LOUD_SPEAKER, true)) }

    fun save(key: String, value: Boolean) = SettingsStore.setBool(context, key, value)

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
            Text(category.title, color = NexusTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
            item {
                SettingsGroup {
                    when (category) {
                        SettingsCategory.AKUN -> {
                            NavRow(Icons.Filled.Person, "Profil", "Ubah nama tampilan") {
                                onOpenPage(SettingsPage.PROFILE)
                            }
                            Divider()
                            NavRow(Icons.Filled.Lock, "Keamanan", "Sesi aktif") {
                                onOpenPage(SettingsPage.SECURITY)
                            }
                            Divider()
                            NavRow(
                                Icons.Filled.Fingerprint,
                                "Kunci aplikasi",
                                if (com.example.syntra.net.AppLockStore.isEnabled(context)) "Aktif — PIN & sidik jari" else "Nonaktif",
                            ) { onOpenPage(SettingsPage.APP_LOCK) }
                        }

                        SettingsCategory.PRIVASI -> {
                            ToggleRow(
                                icon = Icons.Filled.Visibility,
                                title = "Status dibaca",
                                subtitle = "Orang lain melihat centang biru saat kamu membaca",
                                checked = readReceipts,
                                onChange = { readReceipts = it; save(SettingsStore.READ_RECEIPTS, it) },
                            )
                            Divider()
                            ToggleRow(
                                icon = Icons.Filled.Person,
                                title = "Status aktif",
                                subtitle = "Tampilkan titik hijau & \"terakhir dilihat\" saat kamu aktif",
                                checked = showPresence,
                                onChange = { on ->
                                    showPresence = on
                                    save(SettingsStore.SHOW_PRESENCE, on)
                                    // Enforced server-side: PATCH presence_visible, then reconnect
                                    // so the backend re-reads it and stops/starts broadcasting my
                                    // online status from the next connection.
                                    if (ApiConfig.ENABLED) scope.launch {
                                        runCatching { SyntraClient.updateProfile(presenceVisible = on) }
                                        runCatching { SyntraClient.reconnect() }
                                    }
                                },
                            )
                            Divider()
                            NavRow(
                                Icons.Filled.Block,
                                "Kontak diblokir",
                                "${BlockStore.all(context).size} kontak",
                            ) { onOpenPage(SettingsPage.BLOCKED) }
                        }

                        SettingsCategory.NOTIFIKASI -> {
                            // Re-homed into this category page after the settings
                            // restructure. Shown only while the exemption is missing —
                            // a row that can only say "already done" is noise.
                            if (com.example.syntra.net.ChatConnectionService.batteryRestricted(context)) {
                                NavRow(
                                    Icons.Filled.BatteryAlert,
                                    "Jalankan di latar belakang",
                                    "Agar pesan dan panggilan tetap masuk saat layar mati",
                                ) {
                                    com.example.syntra.net.ChatConnectionService
                                        .requestBatteryExemption(context)
                                }
                                Divider()
                            }
                            ToggleRow(
                                icon = Icons.Filled.Notifications,
                                title = "Pesan",
                                subtitle = "Notifikasi untuk chat pribadi dan grup",
                                checked = notifMessages,
                                onChange = { notifMessages = it; save(SettingsStore.NOTIF_MESSAGES, it) },
                            )
                            Divider()
                            ToggleRow(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                title = "Voice room",
                                subtitle = "Beri tahu saat room yang kamu ikuti dimulai",
                                checked = notifRooms,
                                onChange = { notifRooms = it; save(SettingsStore.NOTIF_ROOMS, it) },
                            )
                            Divider()
                            ToggleRow(
                                icon = Icons.Filled.Notifications,
                                title = "Suara & getar",
                                subtitle = null,
                                checked = notifSound,
                                onChange = { notifSound = it; save(SettingsStore.NOTIF_SOUND, it) },
                            )
                        }

                        SettingsCategory.MEDIA -> {
                            ToggleRow(
                                icon = Icons.Filled.DataUsage,
                                title = "Putar video otomatis",
                                subtitle = "Story video langsung diputar saat dibuka",
                                checked = autoPlay,
                                onChange = { autoPlay = it; save(SettingsStore.AUTO_PLAY_VIDEO, it) },
                            )
                            Divider()
                            ToggleRow(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                title = "Pengeras suara di room",
                                subtitle = "Audio keluar lewat speaker, bukan earpiece",
                                checked = loudSpeaker,
                                onChange = { loudSpeaker = it; save(SettingsStore.LOUD_SPEAKER, it) },
                            )
                            Divider()
                            NavRow(Icons.Filled.DarkMode, "Tema", AppTheme.current.label) {
                                onOpenPage(SettingsPage.THEME)
                            }
                            Divider()
                            NavRow(
                                Icons.Filled.CloudDownload,
                                "Unduh otomatis",
                                "Pilih media yang diunduh sendiri di obrolan",
                            ) { onOpenPage(SettingsPage.AUTO_DOWNLOAD) }
                            Divider()
                            NavRow(Icons.Filled.Storage, "Penyimpanan", "Kelola cache media") {
                                onOpenPage(SettingsPage.STORAGE)
                            }
                        }

                        SettingsCategory.TENTANG -> {
                            NavRow(Icons.Filled.Info, "Tentang Syntra", "Versi 1.0") { showAbout = true }
                            Divider()
                            NavRow(Icons.Filled.Policy, "Kebijakan privasi", "Data apa yang kami simpan") {
                                onOpenPage(SettingsPage.PRIVACY_POLICY)
                            }
                            Divider()
                            NavRow(Icons.Filled.Gavel, "Ketentuan layanan", "Aturan memakai Syntra") {
                                onOpenPage(SettingsPage.TERMS)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAbout) {
        ConfirmDialog(
            title = "Syntra",
            message = "Versi 1.0\n\nAplikasi chat, story, dan voice room.\n" +
                "Terhubung ke backend Syntra (REST + WebSocket) dan LiveKit untuk suara.",
            confirmText = "Tutup",
            dismissText = null,
            onDismiss = { showAbout = false },
            onConfirm = { showAbout = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun ProfileCard(username: String, email: String, avatarUrl: String?, onClick: () -> Unit = {}) {
    // Centred, WhatsApp-style: big avatar on top, name and email centred beneath it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF3B68F5))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Foto profil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = username.first().uppercase(),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = username.replaceFirstChar { it.uppercase() },
            color = NexusTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = email.ifBlank { "Belum masuk" },
            color = NexusTextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    // Tampilan datar: tanpa kotak, tanpa bingkai. Pemisah antar-baris sudah cukup
    // menandai batas tiap item.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp)
            .height(1.dp)
            .background(NexusStroke.copy(alpha = 0.6f)),
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = NexusAccentSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NexusTextPrimary, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = NexusTextSecondary, fontSize = 12.sp)
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = NexusAccentSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
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
                // Theme surface, not a fixed dark grey: on the light theme an off
                // switch was a near-black pill on a white page.
                uncheckedTrackColor = NexusSurfaceElevated,
                uncheckedBorderColor = NexusStroke,
            ),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String? = "Batal",
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
            Text(title, color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = NexusTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (dismissText != null) {
                    Text(
                        text = dismissText,
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
                }
                Box(
                    modifier = Modifier
                        .background(
                            // The destructive tint was a dark maroon that only works
                            // on a dark page. A translucent red reads correctly on both
                            // because it takes the colour underneath it.
                            if (dismissText == null) NexusAccent else DangerFill,
                            RoundedCornerShape(50),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onConfirm,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = confirmText,
                        color = if (dismissText == null) Color.White else Color(0xFFFF5D5D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
