package com.example.syntra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.MediaAutoDownload
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.AppTheme
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Shared chrome
// ---------------------------------------------------------------------------

@Composable
fun SettingsSubScreen(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
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
            Text(title, color = NexusTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

/**
 * Auto-download settings: which media kinds fetch themselves inside a chat.
 *
 * A kind that is OFF is not downloaded when the message arrives — the bubble shows a
 * tap-to-download placeholder instead, so the user (not the app) decides what spends
 * their data. Everything defaults to ON, which is the behaviour people expect.
 */
@Composable
fun AutoDownloadScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var photo by remember { mutableStateOf(MediaAutoDownload.photo(context)) }
    var video by remember { mutableStateOf(MediaAutoDownload.video(context)) }
    var sticker by remember { mutableStateOf(MediaAutoDownload.sticker(context)) }
    var gif by remember { mutableStateOf(MediaAutoDownload.gif(context)) }
    var voice by remember { mutableStateOf(MediaAutoDownload.voice(context)) }

    SettingsSubScreen("Unduh otomatis", onClose) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Note(
                "Media yang dimatikan tidak diunduh sendiri. Di obrolan ia muncul " +
                    "sebagai tombol — ketuk untuk mengunduhnya saat kamu mau.",
            )
            Card {
                AutoDownloadRow("Foto", photo) {
                    photo = it; SettingsStore.setBool(context, SettingsStore.AUTO_DL_PHOTO, it)
                }
                AutoDownloadRow("Video", video) {
                    video = it; SettingsStore.setBool(context, SettingsStore.AUTO_DL_VIDEO, it)
                }
                AutoDownloadRow("Stiker", sticker) {
                    sticker = it; SettingsStore.setBool(context, SettingsStore.AUTO_DL_STICKER, it)
                }
                AutoDownloadRow("GIF", gif) {
                    gif = it; SettingsStore.setBool(context, SettingsStore.AUTO_DL_GIF, it)
                }
                AutoDownloadRow("Pesan suara", voice) {
                    voice = it; SettingsStore.setBool(context, SettingsStore.AUTO_DL_VOICE, it)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AutoDownloadRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NexusTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NexusAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF3A3A44),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(NexusSurface, RoundedCornerShape(18.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary.copy(alpha = 0.85f),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(
                brush = if (enabled) {
                    Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF2A2A32), Color(0xFF2A2A32)))
                },
                shape = RoundedCornerShape(25.dp),
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else NexusTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------------------
// Profil
// ---------------------------------------------------------------------------

@Composable
fun ProfileSettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val email = SessionStore.signedInEmail(context).orEmpty()

    // Originals — the baseline we compare against to know if anything is dirty.
    val origName = remember { ProfileStore.displayName(context, email) }
    val origUsername = remember { ProfileStore.username(context, email) }
    val origAvatarUrl = remember { ProfileStore.avatarUrl(context) }

    var displayName by remember { mutableStateOf(origName) }
    var username by remember { mutableStateOf(origUsername) }
    // A newly-picked photo is HELD locally until Save — nothing hits the server
    // until the user commits. preview shows it immediately.
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showBackConfirm by remember { mutableStateOf(false) }

    // Pull the real username from the server once, so the field isn't just the
    // email prefix on first open.
    LaunchedEffect(Unit) {
        if (ApiConfig.ENABLED) runCatching { SyntraClient.getMyProfile() }.getOrNull()?.let { me ->
            if (me.username.isNotBlank() && username == origUsername) username = me.username
        }
    }

    val dirty = displayName.trim() != origName ||
        username.trim() != origUsername ||
        pendingBytes != null

    fun holdPhoto(bytes: ByteArray) {
        pendingBytes = bytes
        pendingPreview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

    fun save() {
        val name = displayName.trim()
        val uname = username.trim()
        if (name.isBlank()) {
            Toast.makeText(context, "Nama tidak boleh kosong.", Toast.LENGTH_SHORT).show()
            return
        }
        // Persist locally first so the rest of the app updates instantly.
        ProfileStore.setDisplayName(context, name)
        ProfileStore.setUsername(context, uname)
        if (!ApiConfig.ENABLED) { onClose(); return }
        saving = true
        val previousMediaId = ProfileStore.avatarMediaId(context)
        scope.launch {
            runCatching {
                var mediaId: String? = null
                var newUrl: String? = null
                // 1) upload the held photo (if any) → media id
                pendingBytes?.let { bytes ->
                    val (mid, _) = SyntraClient.uploadMediaFull("image", "jpg", "image/jpeg", bytes, 512, 512)
                    mediaId = mid
                }
                // 2) commit everything in one PATCH
                val me = SyntraClient.updateProfile(
                    displayName = name,
                    avatarMediaId = mediaId,
                    username = uname.ifBlank { null },
                )
                if (mediaId != null) newUrl = me.avatarMediaId
                mediaId to newUrl
            }.onSuccess { (mediaId, newUrl) ->
                if (mediaId != null && !newUrl.isNullOrBlank()) {
                    ProfileStore.setAvatar(context, newUrl, mediaId)
                    // clean up the old photo from storage (silent if it 404s)
                    if (!previousMediaId.isNullOrBlank() && previousMediaId != mediaId) {
                        runCatching { SyntraClient.deleteMedia(previousMediaId) }
                    }
                }
                saving = false
                Toast.makeText(context, "Profil diperbarui.", Toast.LENGTH_SHORT).show()
                onClose() // auto-return to Settings
            }.onFailure {
                saving = false
                Toast.makeText(context, "Gagal menyimpan: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Camera / gallery just HOLD the photo — no upload here.
    val camera = rememberCameraCapture { bitmap ->
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        holdPhoto(out.toByteArray())
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes != null) holdPhoto(bytes)
        }
    }

    // Back = confirm if there are unsaved changes.
    val onBack = { if (dirty) showBackConfirm = true else onClose() }

    SettingsSubScreen("Profil", onBack) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF3B68F5))))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { showPicker = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        val preview = pendingPreview
                        val url = origAvatarUrl
                        when {
                            preview != null -> Image(
                                bitmap = preview,
                                contentDescription = "Foto profil",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            url != null -> AsyncImage(
                                model = url,
                                contentDescription = "Foto profil",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> Text(
                                text = displayName.firstOrNull()?.uppercase() ?: "S",
                                color = Color.White,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(34.dp)
                            .background(NexusAccent, CircleShape)
                            .border(3.dp, NexusBackground, CircleShape)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { showPicker = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, "Ganti foto", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }
            Card {
                ProfileField("Nama tampilan", displayName, "Nama kamu") { displayName = it }
                Spacer(Modifier.height(16.dp))
                ProfileField("Nama pengguna", username, "username") { v ->
                    username = v.filterNot { it.isWhitespace() }.lowercase()
                }
                Spacer(Modifier.height(16.dp))
                Text("Email", color = NexusTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(email.ifBlank { "—" }, color = NexusTextPrimary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                PrimaryAction(
                    text = if (saving) "Menyimpan…" else "Simpan",
                    enabled = dirty && displayName.isNotBlank() && !saving,
                ) { save() }
            }
            Note("Foto, nama & username disimpan di akunmu dan terlihat oleh pengguna lain.")
        }
    }

    if (showPicker) {
        AttachmentSheet(
            onCamera = { showPicker = false; camera.launch() },
            onGallery = {
                showPicker = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showBackConfirm) {
        ConfirmDiscardDialog(
            onKeepEditing = { showBackConfirm = false },
            onDiscard = { showBackConfirm = false; onClose() },
            onSave = { showBackConfirm = false; save() },
        )
    }
}

/** Labeled text field used across the profile editor. */
@Composable
private fun ProfileField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Text(label, color = NexusTextSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, NexusStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = NexusTextSecondary, fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(NexusAccentSoft),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Asked when leaving the profile editor with unsaved changes. */
@Composable
private fun ConfirmDiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onKeepEditing,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(36.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NexusSurface)
                .border(1.dp, NexusStroke, RoundedCornerShape(20.dp))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(22.dp),
        ) {
            Text("Simpan perubahan?", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Kamu punya perubahan yang belum disimpan.", color = NexusTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f).height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDiscard),
                    contentAlignment = Alignment.Center,
                ) { Text("Buang", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    modifier = Modifier
                        .weight(1f).height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(NexusAccent)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onSave),
                    contentAlignment = Alignment.Center,
                ) { Text("Simpan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/**
 * Local profile overrides, until the backend offers a profile endpoint.
 *
 * The avatar is stored here because there is no `PATCH /users/me` to attach it to
 * the account. That also means the old photo can't be removed from storage from the
 * app (no `DELETE /media/{id}` either) — we keep the previous media id so it can be
 * cleaned up the moment the backend adds that endpoint.
 */
object ProfileStore {
    private const val PREFS = "syntra_settings"
    private const val KEY_NAME = "display_name"
    private const val KEY_USERNAME = "username"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_AVATAR_MEDIA = "avatar_media_id"
    private const val KEY_COVER_URL = "cover_url"
    private const val KEY_COVER_MEDIA = "cover_media_id"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun displayName(context: Context, fallbackEmail: String): String =
        prefs(context).getString(KEY_NAME, null)
            ?: fallbackEmail.substringBefore('@').replaceFirstChar { it.uppercase() }

    fun setDisplayName(context: Context, value: String) {
        prefs(context).edit().putString(KEY_NAME, value).apply()
    }

    fun username(context: Context, fallbackEmail: String): String =
        prefs(context).getString(KEY_USERNAME, null) ?: fallbackEmail.substringBefore('@')

    fun setUsername(context: Context, value: String) {
        prefs(context).edit().putString(KEY_USERNAME, value).apply()
    }

    fun avatarUrl(context: Context): String? = prefs(context).getString(KEY_AVATAR_URL, null)

    fun avatarMediaId(context: Context): String? = prefs(context).getString(KEY_AVATAR_MEDIA, null)

    fun setAvatar(context: Context, url: String, mediaId: String) {
        prefs(context).edit()
            .putString(KEY_AVATAR_URL, url)
            .putString(KEY_AVATAR_MEDIA, mediaId)
            .apply()
    }

    fun coverUrl(context: Context): String? = prefs(context).getString(KEY_COVER_URL, null)

    fun coverMediaId(context: Context): String? = prefs(context).getString(KEY_COVER_MEDIA, null)

    fun setCover(context: Context, url: String, mediaId: String) {
        prefs(context).edit()
            .putString(KEY_COVER_URL, url)
            .putString(KEY_COVER_MEDIA, mediaId)
            .apply()
    }
}

// ---------------------------------------------------------------------------
// Kode QR
// ---------------------------------------------------------------------------

/** Encodes [content] as a QR bitmap. Runs off the main thread. */
private suspend fun qrBitmap(content: String, size: Int, fg: Int, bg: Int): Bitmap? =
    withContext(Dispatchers.Default) {
        runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) fg else bg)
                }
            }
            bmp
        }.getOrNull()
    }

@Composable
fun QrCodeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val email = SessionStore.signedInEmail(context).orEmpty()
    val username = email.substringBefore('@').ifBlank { "pengguna" }
    val link = "syntra://u/$username"

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(link) {
        bitmap = qrBitmap(link, 640, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
    }

    SettingsSubScreen("Kode QR saya", onClose) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                if (bmp == null) {
                    CircularProgressIndicator(color = NexusAccent, strokeWidth = 3.dp)
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Kode QR $username",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "@$username",
                color = NexusTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Bagikan kode ini agar orang lain bisa menemukan dan memulai " +
                    "percakapan denganmu.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text(link, color = NexusAccentSoft, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Keamanan
// ---------------------------------------------------------------------------

@Composable
fun SecurityScreen(onClose: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val email = SessionStore.signedInEmail(context).orEmpty()

    SettingsSubScreen("Keamanan", onClose) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = NexusAccentSoft, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Sesi ini", color = NexusTextPrimary, fontSize = 15.sp)
                        Text(
                            text = email.ifBlank { "—" },
                            color = NexusTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Token akses berlaku 1 jam dan diperbarui otomatis selama kamu " +
                        "tetap masuk.",
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                PrimaryAction("Keluar dari perangkat ini") {
                    scope.launch {
                        if (ApiConfig.ENABLED) runCatching { SyntraClient.logoutRemote() }
                        SessionStore.signOut(context)
                        onSignedOut()
                    }
                }
            }
            Note(
                "Ganti kata sandi dan daftar sesi aktif belum tersedia — server belum " +
                    "punya endpoint-nya. Keluar akun mencabut token di perangkat ini.",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Kontak diblokir
// ---------------------------------------------------------------------------

@Composable
fun BlockedContactsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val blocked = remember { mutableStateOf(BlockStore.all(context).toList()) }

    SettingsSubScreen("Kontak diblokir", onClose) {
        if (blocked.value.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 60.dp, start = 32.dp, end = 32.dp),
            ) {
                Icon(
                    Icons.Filled.PersonOff, null,
                    tint = NexusTextSecondary, modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Belum ada kontak yang diblokir.",
                    color = NexusTextPrimary,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pemblokiran diterapkan di perangkat ini: chat mereka " +
                        "disembunyikan. Server belum punya endpoint blokir, jadi mereka " +
                        "masih bisa mengirim pesan.",
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(blocked.value) { username ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GradientAvatar(
                            listOf(Color(0xFF485563), Color(0xFF29323C)),
                            username.first().toString(),
                            42.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "@$username",
                            color = NexusTextPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Buka blokir",
                            color = NexusAccentSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                BlockStore.unblock(context, username)
                                blocked.value = BlockStore.all(context).toList()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Device-local block list. */
object BlockStore {
    private const val PREFS = "syntra_settings"
    private const val KEY = "blocked_usernames"

    fun all(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()

    fun block(context: Context, username: String) = save(context, all(context) + username)

    fun unblock(context: Context, username: String) = save(context, all(context) - username)

    private fun save(context: Context, value: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, value).apply()
    }
}

// ---------------------------------------------------------------------------
// Tema
// ---------------------------------------------------------------------------

@Composable
fun ThemeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    SettingsSubScreen("Tema", onClose) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(AppTheme.Choice.entries.toList()) { choice ->
                val palette = AppTheme.paletteOf(choice)
                val selected = AppTheme.current == choice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(NexusSurface, RoundedCornerShape(18.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) NexusAccent else NexusStroke,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { AppTheme.select(context, choice) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Live preview of the palette
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(palette.background, RoundedCornerShape(14.dp))
                            .border(1.dp, palette.stroke, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 26.dp, height = 6.dp)
                                    .background(palette.accent, RoundedCornerShape(3.dp)),
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 6.dp)
                                    .background(palette.textSecondary, RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            choice.label,
                            color = NexusTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(choice.description, color = NexusTextSecondary, fontSize = 12.sp)
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier.size(24.dp).background(NexusAccent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check, null,
                                tint = Color.White, modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Penyimpanan
// ---------------------------------------------------------------------------

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun dirSize(dir: java.io.File?): Long {
    if (dir == null || !dir.exists()) return 0
    return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}

@Composable
fun StorageScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheSize by remember { mutableStateOf<Long?>(null) }
    var videoSize by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var clearingVideo by remember { mutableStateOf(false) }

    suspend fun measure() {
        withContext(Dispatchers.IO) {
            // Downloaded videos live in app data (filesDir) — reels in ReelCache
            // (ExoPlayer), other media in VideoCache — so they're shown and cleared on
            // their own below, separate from the throwaway "cache" figure.
            val videoBytes = com.example.syntra.net.ReelCache.sizeBytes(context) +
                com.example.syntra.net.VideoCache.sizeBytes(context)
            val cacheBytes = dirSize(context.cacheDir) + dirSize(context.externalCacheDir)
            videoSize = videoBytes
            cacheSize = cacheBytes
        }
    }

    LaunchedEffect(Unit) { measure() }

    SettingsSubScreen("Penyimpanan", onClose) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Card {
                Text("Cache media", color = NexusTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cacheSize?.let { formatBytes(it) } ?: "Menghitung…",
                    color = NexusTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Foto, thumbnail, dan berkas sementara. Bisa diambil ulang " +
                        "dari server — aman dihapus kapan saja.",
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                PrimaryAction(
                    text = if (clearing) "Membersihkan…" else "Bersihkan cache",
                    enabled = !clearing && (cacheSize ?: 0) > 0,
                ) {
                    clearing = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { context.cacheDir?.deleteRecursively() }
                            runCatching { context.externalCacheDir?.deleteRecursively() }
                        }
                        measure()
                        clearing = false
                        Toast.makeText(context, "Cache dibersihkan.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Downloaded videos — stored in app data so they're never re-downloaded.
            // Shown & cleared separately from cache so the user manages them without a
            // "Clear data" that would also sign them out.
            Card {
                Text("Video tersimpan", color = NexusTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = videoSize?.let { formatBytes(it) } ?: "Menghitung…",
                    color = NexusTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Video yang sudah kamu tonton disimpan agar TIDAK diunduh " +
                        "ulang saat diputar lagi. Kosongkan untuk melepas ruang; video " +
                        "akan diunduh sekali lagi bila ditonton kembali.",
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                PrimaryAction(
                    text = if (clearingVideo) "Mengosongkan…" else "Kosongkan video",
                    enabled = !clearingVideo && (videoSize ?: 0) > 0,
                ) {
                    clearingVideo = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            com.example.syntra.net.ReelCache.clear(context)
                            com.example.syntra.net.VideoCache.clear(context)
                        }
                        measure()
                        clearingVideo = false
                        Toast.makeText(context, "Video tersimpan dikosongkan.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.DeleteSweep, null,
                    tint = NexusTextSecondary, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Pesan dan story tetap tersimpan di server — membersihkan " +
                        "cache tidak menghapusnya.",
                    color = NexusTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
