package com.example.syntra

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import java.io.File

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

/** Emoji keyboard that inserts straight into the message field. */
@Composable
fun EmojiPicker(onPick: (String) -> Unit, onBackspace: () -> Unit) {
    var group by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(NexusSurface),
    ) {
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
            .background(Color(0xFF3A1620), RoundedCornerShape(24.dp))
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

@Composable
fun AttachmentSheet(onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(22.dp))
                .padding(vertical = 26.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachOption("Kamera", "📷", NexusAccent, onCamera)
            AttachOption("Galeri", "🖼️", Color(0xFF9733EE), onGallery)
        }
    }
}

@Composable
private fun AttachOption(label: String, emoji: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 26.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = NexusTextPrimary, fontSize = 13.sp)
    }
}
