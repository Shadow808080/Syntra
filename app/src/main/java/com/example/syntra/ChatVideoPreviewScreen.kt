package com.example.syntra

import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * "Edit before send" screen for a chat video, the mirror of [ChatImagePreviewScreen]
 * for photos. A picked clip lands here first — it loops in a preview, the user can add
 * a caption, and only then does [onSend] fire with that caption. Nothing is uploaded
 * until then. Playback is ExoPlayer over a plain TextureView (the same surface Shorts
 * uses), so no media3-ui dependency is pulled in.
 */
@Composable
fun ChatVideoPreviewScreen(
    uri: Uri,
    onCancel: () -> Unit,
    onSend: (String) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    var videoW by remember { mutableFloatStateOf(0f) }
    var videoH by remember { mutableFloatStateOf(0f) }

    val player = remember(uri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(uri) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                videoW = size.width.toFloat()
                videoH = size.height.toFloat()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        com.example.syntra.net.MusicPlayer.pauseForExternalAudio()
        onDispose { runCatching { player.release() } }
    }
    LaunchedEffect(playing) { runCatching { player.playWhenReady = playing } }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        // Header bar: back / cancel.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15151C))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onCancel,
                    ),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Batal", tint = Color.White, modifier = Modifier.size(23.dp)) }
            Spacer(Modifier.weight(1f))
            Text("Pratinjau video", color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(42.dp))
        }

        // The video preview — its own space between the two bars, fit ("contain").
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { playing = !playing },
            contentAlignment = Alignment.Center,
        ) {
            val boxW = maxWidth.value
            val boxH = maxHeight.value
            val sx: Float
            val sy: Float
            if (videoW > 0 && videoH > 0 && boxW > 0f && boxH > 0f) {
                val fit = minOf(boxW / videoW, boxH / videoH)
                sx = videoW * fit / boxW
                sy = videoH * fit / boxH
            } else {
                sx = 1f
                sy = 1f
            }
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                runCatching { player.setVideoSurface(Surface(st)) }
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                runCatching { player.clearVideoSurface() }
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = sx
                        scaleY = sy
                    },
            )
            if (!playing) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, "Putar", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
        }

        // Footer bar: caption field + send button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15151C))
                .imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1C1C24), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (caption.isEmpty()) {
                        Text(
                            "Tambah keterangan…",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 15.sp,
                        )
                    }
                    BasicTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color(0xFF6C8BFF)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF6C8BFF), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSend(caption.trim()) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}
