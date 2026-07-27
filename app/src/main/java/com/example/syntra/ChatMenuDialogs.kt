package com.example.syntra

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary

// ---------------------------------------------------------------------------
// Chat theme — a per-conversation outgoing-bubble colour, stored on the device.
// ---------------------------------------------------------------------------

data class ChatTheme(val name: String, val bubble: Color)

val chatThemes = listOf(
    ChatTheme("Biru", Color(0xFF3B68F5)),
    ChatTheme("Ungu", Color(0xFF7C4DFF)),
    ChatTheme("Hijau", Color(0xFF1FA971)),
    ChatTheme("Merah muda", Color(0xFFE9548C)),
    ChatTheme("Oranye", Color(0xFFF2663C)),
    ChatTheme("Teal", Color(0xFF00A8CC)),
)

object ChatThemeStore {
    private const val PREFS = "syntra_settings"
    private fun key(id: String) = "chat_theme_$id"

    fun get(context: Context, conversationId: String): ChatTheme {
        val idx = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(key(conversationId), 0)
        return chatThemes.getOrElse(idx) { chatThemes.first() }
    }

    fun set(context: Context, conversationId: String, theme: ChatTheme) {
        val idx = chatThemes.indexOf(theme).coerceAtLeast(0)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(key(conversationId), idx).apply()
    }
}

@Composable
fun ChatThemeDialog(current: ChatTheme, onDismiss: () -> Unit, onPick: (ChatTheme) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Tema obrolan", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Warna gelembung pesan yang kamu kirim, khusus obrolan ini.",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(18.dp))
            chatThemes.chunked(3).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowThemes.forEach { theme ->
                        val selected = theme.bubble == current.bubble
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onPick(theme) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(theme.bubble)
                                    .then(
                                        if (selected) {
                                            Modifier.border(3.dp, Color.White, RoundedCornerShape(50))
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Done, null,
                                        tint = Color.White, modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(theme.name, color = NexusTextSecondary, fontSize = 11.sp)
                        }
                    }
                    repeat(3 - rowThemes.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat wallpaper — a per-conversation background, stored on the device.
//
// Two sources: the app's own set (hosted in our public bucket, identical for every
// user) and anything the user picks from their gallery (a content:// uri we hold a
// persisted read grant for). Both are just "a model Coil can load", so the renderer
// doesn't care which one it got.
// ---------------------------------------------------------------------------

/** One built-in wallpaper: a display name and the public image URL. */
data class ChatWallpaper(val name: String, val url: String)

private const val WALLPAPER_BASE =
    "https://tqcfmueshhpuqjuqgafi.supabase.co/storage/v1/object/public/media/wallpapers"

/**
 * The wallpapers Syntra ships to everyone. Drawn for this app (not stock imagery),
 * deliberately low-contrast so message bubbles stay readable on top.
 */
val chatWallpapers = listOf(
    ChatWallpaper("Doodle", "$WALLPAPER_BASE/syntra_doodles.png"),
    ChatWallpaper("Aurora", "$WALLPAPER_BASE/syntra_aurora.png"),
    ChatWallpaper("Bubble", "$WALLPAPER_BASE/syntra_bubbles.png"),
    ChatWallpaper("Bintang", "$WALLPAPER_BASE/syntra_stars.png"),
    ChatWallpaper("Ombak", "$WALLPAPER_BASE/syntra_waves.png"),
    ChatWallpaper("Blossom", "$WALLPAPER_BASE/syntra_blossom.png"),
)

object ChatWallpaperStore {
    private const val PREFS = "syntra_settings"
    private fun key(id: String) = "chat_wallpaper_$id"

    /** The chosen wallpaper (built-in URL or a local uri), or null for none. */
    fun get(context: Context, conversationId: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(conversationId), null)
            ?.takeIf { it.isNotBlank() }

    fun set(context: Context, conversationId: String, model: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (model.isNullOrBlank()) remove(key(conversationId)) else putString(key(conversationId), model) }
            .apply()
    }
}

/**
 * Wallpaper picker: "none", the app's own set, and a gallery entry. [onPickLocal]
 * opens the device picker (handled by the caller, which owns the launcher).
 */
@Composable
fun ChatWallpaperDialog(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    onPickLocal: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Latar obrolan", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pilih dari koleksi Syntra, atau ambil gambar dari galerimu. Berlaku untuk obrolan ini.",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(18.dp))

            // Your own photo comes FIRST and looks nothing like the rest — a wide card
            // with an accent wash and its own explanation. Buried at the end as a plain
            // grey square it was indistinguishable from a wallpaper nobody had loaded
            // yet, so people never found it.
            GalleryWallpaperCard(
                // A gallery pick is the only wallpaper that isn't one of our URLs, so
                // "not http" is exactly "this chat is using your own photo".
                selected = current != null && !current.startsWith("http"),
                onClick = onPickLocal,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Koleksi Syntra",
                color = NexusTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )

            // Tiles: "no wallpaper" then the built-in set — all the same square shape,
            // so the grid reads as one uniform set of choices.
            val tiles: List<ChatWallpaper?> = listOf(null) + chatWallpapers
            tiles.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { wp ->
                        val model = wp?.url
                        val selected = current == model
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onPick(model) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NexusSurface)
                                    .then(
                                        if (selected) Modifier.border(2.5.dp, Color.White, RoundedCornerShape(12.dp))
                                        else Modifier,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (wp != null) {
                                    coil.compose.AsyncImage(
                                        model = wp.url,
                                        contentDescription = wp.name,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    Text("Tanpa\nlatar", color = NexusTextSecondary, fontSize = 11.sp)
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Done, null,
                                        tint = Color.White, modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(wp?.name ?: "Polos", color = NexusTextSecondary, fontSize = 11.sp)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * The "use my own photo" entry of the wallpaper picker.
 *
 * A wide card rather than a grid square, with an accent gradient and a subtitle — it
 * is the one option that does something different (opens the system picker), so it is
 * allowed to look different.
 */
@Composable
private fun GalleryWallpaperCard(selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(NexusAccent.copy(alpha = 0.30f), NexusAccent.copy(alpha = 0.10f)),
                ),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Color.White else NexusAccent.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PhotoLibrary, null,
                tint = Color.White, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text("Dari galeri", color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("Pakai fotomu sendiri sebagai latar", color = NexusTextSecondary, fontSize = 11.sp)
        }
        Icon(
            if (selected) Icons.Filled.Done else Icons.Filled.Add, null,
            tint = if (selected) Color.White else NexusTextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Report + generic confirm
// ---------------------------------------------------------------------------

@Composable
fun ReportDialog(name: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val reasons = listOf("Spam", "Pelecehan", "Konten tidak pantas", "Penipuan", "Lainnya")
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
                .padding(vertical = 20.dp),
        ) {
            Text(
                "Laporkan $name",
                color = NexusTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Text(
                "Pilih alasan laporan.",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            reasons.forEach { reason ->
                Text(
                    text = reason,
                    color = NexusTextPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSubmit(reason) }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                )
            }
            Text(
                text = "Batal",
                color = NexusTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
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
                    Text(confirmText, color = Color(0xFFFF5D5D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
