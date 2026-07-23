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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
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
// Report + generic confirm
// ---------------------------------------------------------------------------

@Composable
fun ReportDialog(name: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val reasons = listOf("Spam", "Pelecehan", "Konten tidak pantas", "Penipuan", "Lainnya")
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
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
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
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
                        .background(Color(0xFF3A1620), RoundedCornerShape(50))
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
