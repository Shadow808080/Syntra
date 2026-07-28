package com.example.syntra

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.syntra.net.VideoTrimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * "Edit before send" screen for a chat video, the mirror of [ChatImagePreviewScreen]
 * for photos. A picked clip lands here first — it loops in a preview, the user can
 * TRIM its duration on a filmstrip and add a caption, and only then does [onSend] fire
 * with the (possibly trimmed) bytes. Nothing is uploaded until then.
 *
 * Trimming remuxes without re-encoding ([VideoTrimmer]) so it's near-instant; playback
 * is ExoPlayer over a plain TextureView (the surface Shorts uses), no media3-ui dep.
 */
@Composable
fun ChatVideoPreviewScreen(
    uri: Uri,
    onCancel: () -> Unit,
    /** (bytes, ext, mime, caption) once the user confirms. */
    onSend: (ByteArray, String, String, String) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    var videoW by remember { mutableFloatStateOf(0f) }
    var videoH by remember { mutableFloatStateOf(0f) }

    // Trim state (milliseconds). endMs stays at 0 until the duration is known.
    var durationMs by remember(uri) { mutableFloatStateOf(0f) }
    var startMs by remember(uri) { mutableFloatStateOf(0f) }
    var endMs by remember(uri) { mutableFloatStateOf(0f) }
    var thumbs by remember(uri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var exporting by remember { mutableStateOf(false) }

    val player = remember(uri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
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

    // Load the duration + a small filmstrip of thumbnails off the main thread.
    LaunchedEffect(uri) {
        val dur = withContext(Dispatchers.IO) { VideoTrimmer.durationMs(context, uri) }.toFloat()
        if (dur > 0f) { durationMs = dur; endMs = dur }
        val frames = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, uri)
                val n = 8
                val out = ArrayList<Bitmap>(n)
                for (i in 0 until n) {
                    val t = if (dur > 0f) (dur * i / (n - 1)).toLong() * 1000 else 0L
                    val f = mmr.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                    val h = (120f * f.height / f.width).toInt().coerceIn(1, 400)
                    val scaled = Bitmap.createScaledBitmap(f, 120, h, true)
                    if (scaled !== f) f.recycle()
                    out.add(scaled)
                }
                mmr.release()
                out
            }.getOrNull() ?: emptyList()
        }
        thumbs = frames
    }

    // Keep playback inside the trimmed window: loop back to the in-point when it
    // reaches the out-point (or drifts before the in-point after a drag).
    LaunchedEffect(startMs, endMs) {
        if (endMs <= 0f) return@LaunchedEffect
        while (true) {
            val pos = runCatching { player.currentPosition }.getOrDefault(0L)
            if (pos >= endMs.toLong() || pos + 60 < startMs.toLong()) {
                runCatching { player.seekTo(startMs.toLong()) }
            }
            delay(50)
        }
    }

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

        // The video preview — its own space, fit ("contain").
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

        // Trim bar — only meaningful once the duration is known.
        if (durationMs > 0f) {
            TrimBar(
                thumbs = thumbs,
                durationMs = durationMs,
                startMs = startMs,
                endMs = endMs,
                onStart = { startMs = it },
                onEnd = { endMs = it },
            )
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
                        Text("Tambah keterangan…", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
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
                        enabled = !exporting,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        exporting = true
                        scope.launch {
                            val result = produceBytes(context, uri, durationMs, startMs, endMs)
                            exporting = false
                            if (result != null) {
                                onSend(result.bytes, result.ext, result.mime, caption.trim())
                            } else {
                                Toast.makeText(context, "Tidak bisa menyiapkan video.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (exporting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** The video the composer will send: raw bytes plus how to label them. */
private class VideoBytes(val bytes: ByteArray, val ext: String, val mime: String)

/**
 * Reads the clip to send: trims to [[startMs],[endMs]] when the user narrowed the range
 * (remux → MP4), otherwise the whole file untouched. Falls back to the whole file if the
 * trim can't be produced, so a send never silently fails.
 */
private suspend fun produceBytes(
    context: android.content.Context,
    uri: Uri,
    durationMs: Float,
    startMs: Float,
    endMs: Float,
): VideoBytes? {
    val origMime = context.contentResolver.getType(uri) ?: "video/mp4"
    suspend fun whole(): VideoBytes? = withContext(Dispatchers.IO) {
        val b = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        b?.let { VideoBytes(it, extForMime(origMime), origMime) }
    }
    val trimmed = durationMs > 0f && (startMs > 50f || endMs < durationMs - 50f)
    if (!trimmed) return whole()
    val file = VideoTrimmer.trim(context, uri, startMs.toLong(), endMs.toLong()) ?: return whole()
    val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
    file.delete()
    return bytes?.let { VideoBytes(it, "mp4", "video/mp4") } ?: whole()
}

private fun extForMime(mime: String): String = when {
    mime.contains("webm") -> "webm"
    mime.contains("3gp") || mime.contains("3gpp") -> "3gp"
    mime.contains("quicktime") -> "mov"
    mime.contains("matroska") -> "mkv"
    else -> "mp4"
}

/**
 * Filmstrip with two draggable handles that pick the in/out points. The region outside
 * the selection is dimmed; the selected span is framed in yellow with grippable ends.
 */
@Composable
private fun TrimBar(
    thumbs: List<Bitmap>,
    durationMs: Float,
    startMs: Float,
    endMs: Float,
    onStart: (Float) -> Unit,
    onEnd: (Float) -> Unit,
) {
    val accent = Color(0xFFFFC24B)
    // Never let the selection collapse below ~0.6s — but for a clip shorter than that,
    // shrink the gap to the whole duration so the coerce bounds can't invert (min>max),
    // which would crash on the first drag of a very short video.
    val minGap = 600f.coerceAtMost(durationMs)
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val handleW = 16.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF15151C))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ContentCut, "Potong", tint = accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Potong durasi", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(fmtClock(endMs - startMs), color = accent, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat() },
        ) {
            // Filmstrip background.
            Row(Modifier.fillMaxSize().padding(horizontal = handleW / 2)) {
                if (thumbs.isEmpty()) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF23232D)))
                } else {
                    thumbs.forEach { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }

            // Usable track width sits between the two half-handle insets.
            val insetPx = with(androidx.compose.ui.platform.LocalDensity.current) { (handleW / 2).toPx() }
            val trackW = (barWidthPx - insetPx * 2).coerceAtLeast(1f)
            val startFrac = if (durationMs > 0f) startMs / durationMs else 0f
            val endFrac = if (durationMs > 0f) endMs / durationMs else 1f
            val startX = insetPx + startFrac * trackW
            val endX = insetPx + endFrac * trackW

            // Dim outside the selection + frame the inside.
            Canvas(Modifier.fillMaxSize()) {
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, size = androidx.compose.ui.geometry.Size(startX, size.height))
                drawRect(
                    dim,
                    topLeft = androidx.compose.ui.geometry.Offset(endX, 0f),
                    size = androidx.compose.ui.geometry.Size((size.width - endX).coerceAtLeast(0f), size.height),
                )
                val t = 3.dp.toPx()
                drawRect(
                    accent,
                    topLeft = androidx.compose.ui.geometry.Offset(startX, 0f),
                    size = androidx.compose.ui.geometry.Size((endX - startX).coerceAtLeast(0f), t),
                )
                drawRect(
                    accent,
                    topLeft = androidx.compose.ui.geometry.Offset(startX, size.height - t),
                    size = androidx.compose.ui.geometry.Size((endX - startX).coerceAtLeast(0f), t),
                )
            }

            // Left handle.
            TrimHandle(
                accent = accent,
                width = handleW,
                offsetX = { (startX - insetPx).roundToInt() },
                onDrag = { dx ->
                    if (trackW > 0f && durationMs > 0f) {
                        val next = (startMs + dx / trackW * durationMs).coerceIn(0f, (endMs - minGap).coerceAtLeast(0f))
                        onStart(next)
                    }
                },
            )
            // Right handle.
            TrimHandle(
                accent = accent,
                width = handleW,
                offsetX = { (endX - insetPx).roundToInt() },
                onDrag = { dx ->
                    if (trackW > 0f && durationMs > 0f) {
                        val next = (endMs + dx / trackW * durationMs).coerceIn((startMs + minGap).coerceAtMost(durationMs), durationMs)
                        onEnd(next)
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(fmtClock(startMs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(fmtClock(endMs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun TrimHandle(
    accent: Color,
    width: androidx.compose.ui.unit.Dp,
    offsetX: () -> Int,
    onDrag: (Float) -> Unit,
) {
    // rememberUpdatedState so the gesture (started once, on a stable pointerInput key)
    // always calls back with the LATEST start/end, not the values from first composition.
    val latest = androidx.compose.runtime.rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX(), 0) }
            .width(width)
            .fillMaxHeight()
            .background(accent, RoundedCornerShape(5.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    latest.value(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(18.dp).background(Color(0x99000000), RoundedCornerShape(1.dp)))
    }
}

/** m:ss from milliseconds. */
private fun fmtClock(ms: Float): String {
    val total = (ms / 1000f).roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
