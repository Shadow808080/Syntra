package com.example.syntra

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.net.GifClient
import com.example.syntra.net.GifItem
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.syntra.net.StickerStore

// ---------------------------------------------------------------------------
// Emoji picker
// ---------------------------------------------------------------------------

private data class EmojiGroup(val title: String, val emojis: List<String>)

private val emojiGroups = listOf(
    EmojiGroup(
        "Sering dipakai",
        "😀 😂 🥹 😍 😘 🤔 😴 😭 😡 👍 🙏 🔥 ❤️ 🎉 💯 👀".split(" "),
    ),
    EmojiGroup(
        "Wajah",
        ("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 🥲 😋 😛 😜 🤪 " +
            "😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 " +
            "🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 " +
            "😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬").split(" "),
    ),
    EmojiGroup(
        "Gestur",
        ("👍 👎 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👋 🤚 🖐️ ✋ 🖖 👏 🙌 🫶 " +
            "👐 🤲 🤝 🙏 💪 🦾 ✍️ 💅").split(" "),
    ),
    EmojiGroup(
        "Hati & simbol",
        ("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 " +
            "🔥 ⭐ 🌟 ✨ ⚡ ☀️ 🌈 ☁️ ❄️ 🎉 🎊 🎈 🎁 🏆 🥇").split(" "),
    ),
    EmojiGroup(
        "Makanan & aktivitas",
        ("🍎 🍌 🍇 🍓 🍑 🍍 🥭 🍕 🍔 🍟 🌭 🍿 🧁 🍰 🍫 🍩 🍪 ☕ 🍵 🧋 🍺 🍻 🥂 " +
            "⚽ 🏀 🏈 🎾 🎮 🎧 🎤 🎬 📷 ✈️ 🚗 🏝️ 🌙").split(" "),
    ),
)

/** Big expressive emoji offered as one-tap stickers (rendered large, no bubble). */
private val stickerSet = (
    "🥳 😂 😍 🥰 😎 🤩 😭 😤 🤯 🥶 🤗 🙌 👏 🔥 💯 ❤️ 💔 👍 👎 🙏 🎉 💪 🤝 👀 🫶 🤌 " +
        "🤙 ✌️ 🤞 🫡 🥹 😴 🤤 🤠 🥲 😇 🤪 😜 😝 🫢 🙈 🙉 🙊 💀 👻 🤖 🎁"
    ).split(" ")

/**
 * Emoji keyboard + sticker picker. The Emoji tab inserts into the message field; the
 * Stiker tab sends a big-emoji sticker via [onSticker] OR one of the user's own image
 * stickers via [onImageSticker], and lets them add new ones from the gallery.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPicker(
    onPick: (String) -> Unit,
    onBackspace: () -> Unit,
    onSticker: (String) -> Unit = {},
    /** A saved image/GIF sticker the user tapped — sent through the media pipeline. */
    onImageSticker: (StickerStore.Sticker) -> Unit = {},
    // Tapping the GIF tab opens the draggable GIF bottom-sheet (rendered by the host),
    // instead of a cramped inline panel — so it can be pulled up and its search field
    // rides above the keyboard.
    onOpenGif: () -> Unit = {},
) {
    // 0 = emoji (insert into field), 1 = stickers. GIF is a separate bottom-sheet.
    var mode by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The user's own sticker tray (device-local), observed live.
    //
    // STILLS only. A saved GIF belongs to the GIF sheet, which is where you go looking
    // for GIFs — showing the same item in both places made "favorit" mean two different
    // trays at once, and left the Stiker grid animating half its cells.
    val myStickers = StickerStore.stickers(context).filterNot { it.animated }
    var removeTarget by remember { mutableStateOf<StickerStore.Sticker?>(null) }
    // Add-a-sticker: pick an image or GIF from the gallery, copy it into the tray.
    val addSticker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) scope.launch { StickerStore.add(context, uri) } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusSurface)
            // The panel sits where the keyboard was, so it owns the nav-bar gap.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(300.dp),
    ) {
        // Emoji / Stiker / GIF tab switch.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerTab("Emoji", active = mode == 0) { mode = 0 }
            PickerTab("Stiker", active = mode == 1) { mode = 1 }
            PickerTab("GIF", active = false) { onOpenGif() }
        }

        if (mode == 1) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // Add your own sticker (image or GIF from the gallery).
                item {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(NexusSurfaceElevated)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                addSticker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, "Tambah stiker", tint = NexusAccent, modifier = Modifier.size(26.dp))
                    }
                }
                // The user's own image / GIF stickers. Tap to send, long-press to remove.
                items(myStickers, key = { it.id }) { s ->
                    AsyncImage(
                        model = s.path,
                        contentDescription = "Stiker",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onImageSticker(s) },
                                onLongClick = { removeTarget = s },
                            ),
                    )
                }
                // Built-in big-emoji stickers.
                items(stickerSet) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 40.sp,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onSticker(emoji) }
                            .padding(10.dp),
                    )
                }
            }
            // Confirm before removing one of the user's stickers.
            removeTarget?.let { s ->
                Dialog(onDismissRequest = { removeTarget = null }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NexusSurfaceElevated, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                    ) {
                        Text("Hapus stiker ini?", color = NexusTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Stiker akan dihapus dari koleksimu di perangkat ini.",
                            color = NexusTextSecondary, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                "Batal",
                                color = NexusTextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { removeTarget = null }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Hapus",
                                color = DangerFill,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { StickerStore.remove(context, s.id); removeTarget = null }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emojiGroups.forEachIndexed { i, g ->
                val active = i == group
                Text(
                    text = g.emojis.first(),
                    fontSize = 18.sp,
                    modifier = Modifier
                        .background(
                            if (active) NexusAccent.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { group = i }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "⌫",
                color = NexusTextSecondary,
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBackspace,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Text(
            text = emojiGroups[group].title,
            color = NexusTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            items(emojiGroups[group].emojis) { emoji ->
                Text(
                    text = emoji,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onPick(emoji) }
                        .padding(6.dp),
                )
            }
        }
    }
}

/**
 * GIF picker as a DRAGGABLE bottom-sheet. Pulls up to full height, and its
 * search/generate field rides above the soft keyboard (imePadding), so typing is
 * never covered. Three sources, so a GIF can always be sent:
 *  - "Dari galeri" — pick a .gif already on the phone (keyless, always works).
 *  - "Cari GIF" — search/trending from GIPHY (needs the API key in [GifClient]).
 *  - "Buat teks" — GENERATE an animated-text GIF from a typed phrase (GIPHY Animate).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPickerSheet(
    onGif: (String) -> Unit,
    onGifDevice: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    // false = search existing GIFs, true = generate an animated-text GIF from [query].
    var genMode by remember { mutableStateOf(false) }
    val gifs = remember { mutableStateListOf<GifItem>() }
    var loading by remember { mutableStateOf(GifClient.configured) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // Animate the sheet down THEN remove it. Just flipping the host flag while the
    // sheet is at rest leaves it on screen (a Material3 quirk) — this is why it didn't
    // disappear after sending. Run the send first, then close.
    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    val deviceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) { onGifDevice(uri); dismiss() } }

    LaunchedEffect(query, genMode) {
        if (!GifClient.configured) { loading = false; return@LaunchedEffect }
        loading = true
        delay(300)
        val results = when {
            genMode -> GifClient.animate(query)
            query.isBlank() -> GifClient.featured()
            else -> GifClient.search(query)
        }
        gifs.clear(); gifs.addAll(results.distinctBy { it.id })
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NexusSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                // The keyboard pushes the whole sheet content up so the field + grid
                // stay visible instead of being covered.
                .imePadding(),
        ) {
            // Header: device button + search/generate toggle + field.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusSurfaceElevated)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { deviceLauncher.launch("image/gif") }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Image, null, tint = NexusAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("GIF dari galeri HP", color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            // Your saved animated stickers, first.
            //
            // "Tambahkan ke stiker favorit" on someone's GIF put it in the Stiker tray
            // and nowhere else — so the obvious place to go looking for it, the GIF
            // sheet you send GIFs from, never showed it. Stills stay out: this row is
            // specifically the GIFs, and they already have the Stiker tab.
            val savedGifs = StickerStore.stickers(ctx).filter { it.animated }
            if (savedGifs.isNotEmpty()) {
                Text(
                    "Favorit kamu",
                    color = NexusTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp),
                )
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    // items(count) is a MEMBER of LazyListScope, so it needs no import
                    // — importing the list `items` extension would collide with the
                    // grid one already used by the sticker tab in this same file.
                    items(savedGifs.size, key = { savedGifs[it].id }) { i ->
                        val s = savedGifs[i]
                        AsyncImage(
                            model = java.io.File(s.path),
                            contentDescription = "GIF favorit",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NexusSurfaceElevated)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onGifDevice(Uri.fromFile(java.io.File(s.path))); dismiss() },
                        )
                    }
                }
            }

            if (!GifClient.configured) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Pencarian GIF online belum aktif.\nTempel API key gratis GIPHY di GifClient.kt.",
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickerTab("Cari GIF", active = !genMode) { genMode = false }
                PickerTab("Buat teks", active = genMode) { genMode = true }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .background(NexusSurfaceElevated, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(
                        if (genMode) "Tulis teks untuk dibuat GIF…" else "Cari GIF…",
                        color = NexusTextSecondary,
                        fontSize = 14.sp,
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(NexusAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when {
                loading && gifs.isEmpty() ->
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = NexusAccent, strokeWidth = 2.dp)
                    }
                genMode && query.isBlank() && gifs.isEmpty() ->
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Tulis teks apa saja, lalu pilih gaya GIF yang muncul.",
                            color = NexusTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(gifs, key = { it.id }) { gif ->
                        coil.compose.AsyncImage(
                            model = gif.previewUrl,
                            contentDescription = "GIF",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NexusSurfaceElevated)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onGif(gif.sendUrl); dismiss() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTab(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) NexusTextPrimary else NexusTextSecondary,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .background(
                if (active) NexusAccent.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

// ---------------------------------------------------------------------------
// Voice notes
// ---------------------------------------------------------------------------

/**
 * Records a voice note to a file in the cache directory.
 *
 * Kept deliberately small: start on press, stop on release. AAC in an MP4
 * container is what every Android version can both record and play back.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /**
     * Current mic loudness as 0f..1f, for a live waveform. MediaRecorder reports a
     * raw amplitude up to 32767; we normalise on a gentle curve so quiet speech still
     * moves the bars. Returns 0 when not recording.
     */
    val amplitude: Float
        get() = runCatching {
            val raw = recorder?.maxAmplitude ?: return 0f
            (raw / 12000f).coerceIn(0f, 1f)
        }.getOrDefault(0f)

    fun start(): Boolean = runCatching {
        val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(64_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        output = file
        startedAt = System.currentTimeMillis()
        true
    }.getOrElse {
        cleanup()
        false
    }

    /** Returns the recording and its length, or null if it was too short to send. */
    fun stop(): Pair<File, Long>? {
        val rec = recorder ?: return null
        val file = output
        val elapsed = System.currentTimeMillis() - startedAt
        runCatching { rec.stop() }
        cleanup()
        // Under a second is almost always an accidental tap.
        if (file == null || elapsed < 1000) {
            file?.delete()
            return null
        }
        return file to elapsed
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        cleanup()
        output?.delete()
        output = null
    }

    private fun cleanup() {
        runCatching { recorder?.release() }
        recorder = null
    }
}

/** Banner shown while the mic is held down. */
@Composable
fun RecordingBar(seconds: Int, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(DangerFill, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color(0xFFFF5D5D), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Merekam  %d:%02d".format(seconds / 60, seconds % 60),
            color = NexusTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Batal",
            color = Color(0xFFFF5D5D),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onCancel,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Attachment sheet
// ---------------------------------------------------------------------------

/**
 * The "kirim media" chooser behind the camera button.
 *
 * Deliberately plain: two full-width rows, a flat monochrome icon tile each, a label
 * and a line saying what it does. No gradient discs — those read as a toy next to the
 * rest of the chat, and a two-item menu doesn't need decoration to be understood.
 */
@Composable
fun AttachmentSheet(onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(20.dp))
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "Kirim media",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            )
            AttachOption(
                label = "Kamera",
                detail = "Ambil foto sekarang",
                icon = Icons.Filled.PhotoCamera,
                onClick = onCamera,
            )
            AttachOption(
                label = "Galeri",
                detail = "Pilih dari penyimpanan",
                icon = Icons.Filled.Image,
                onClick = onGallery,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AttachOption(
    label: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NexusSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = NexusTextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(detail, color = NexusTextSecondary, fontSize = 12.sp)
        }
    }
}
