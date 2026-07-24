package com.example.syntra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_STORY_VIDEO_MS = 15_000L
private const val FILM_FRAMES = 8

/**
 * CapCut-style story video trimmer. The video plays in the top area; a dedicated
 * dark panel at the bottom holds a thumbnail filmstrip with a draggable 15-second
 * window. Controls are icons, and nothing overlaps the video frame.
 */
@Composable
fun VideoTrimScreen(uri: Uri, onCancel: () -> Unit, onDone: (Uri) -> Unit) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var durationMs by remember { mutableStateOf(0L) }
    var startMs by remember { mutableFloatStateOf(0f) }
    var processing by remember { mutableStateOf(false) }
    val frames = remember { mutableStateListOf<Bitmap>() }
    val player = remember { MediaPlayer() }

    val windowMs = if (durationMs in 1 until MAX_STORY_VIDEO_MS) durationMs else MAX_STORY_VIDEO_MS

    // Read duration + build a filmstrip of evenly-spaced thumbnails.
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(context, uri)
                    val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    withContext(Dispatchers.Main) { durationMs = dur }
                    for (i in 0 until FILM_FRAMES) {
                        val tUs = (dur * 1000L) * i / FILM_FRAMES
                        val f = runCatching { r.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
                        if (f != null) {
                            val small = Bitmap.createScaledBitmap(f, 120, 200, true)
                            withContext(Dispatchers.Main) { frames.add(small) }
                        }
                    }
                }
            }
        }
    }

    // Loop playback within [start, start+window].
    LaunchedEffect(startMs, windowMs, durationMs) {
        if (durationMs <= 0) return@LaunchedEffect
        while (true) {
            runCatching {
                val pos = player.currentPosition
                if (pos < startMs - 150 || pos > startMs + windowMs) player.seekTo(startMs.toInt())
            }
            delay(250)
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        // --- Top bar (icons) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            Text(
                if (durationMs > MAX_STORY_VIDEO_MS) "Maks 15 detik" else "Pratinjau",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        // --- Video preview area ---
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                runCatching {
                                    player.setSurface(Surface(st))
                                    player.setDataSource(ctx, uri)
                                    player.isLooping = false
                                    player.setOnPreparedListener { it.start() }
                                    player.prepareAsync()
                                }
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (durationMs <= 0) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        }

        // --- Trim panel (separate, dark) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15151C))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            var trackW by remember { mutableStateOf(1) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .onSizeChanged { trackW = it.width },
            ) {
                // Filmstrip.
                Row(Modifier.fillMaxSize()) {
                    frames.forEach { f ->
                        Image(
                            bitmap = f.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (frames.isEmpty()) Box(Modifier.fillMaxSize().background(Color(0xFF222230)))
                }

                if (durationMs > 0) {
                    val frac = (windowMs.toFloat() / durationMs).coerceAtMost(1f)
                    val winPx = trackW * frac
                    val leftPx = (trackW * (startMs / durationMs)).coerceIn(0f, trackW - winPx)
                    // Dim the parts outside the window.
                    Box(
                        Modifier.offset { androidx.compose.ui.unit.IntOffset(0, 0) }
                            .width(with(density) { leftPx.toDp() }).fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.55f)),
                    )
                    Box(
                        Modifier.offset { androidx.compose.ui.unit.IntOffset((leftPx + winPx).toInt(), 0) }
                            .width(with(density) { (trackW - leftPx - winPx).coerceAtLeast(0f).toDp() }).fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.55f)),
                    )
                    // The window frame — draggable.
                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(leftPx.toInt(), 0) }
                            .width(with(density) { winPx.toDp() })
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x333B68F5))
                            .pointerInput(durationMs, windowMs) {
                                detectHorizontalDragGestures { change, dx ->
                                    change.consume()
                                    if (durationMs <= windowMs) return@detectHorizontalDragGestures
                                    val dMs = (dx / trackW) * durationMs
                                    startMs = (startMs + dMs).coerceIn(0f, (durationMs - windowMs).toFloat())
                                }
                            },
                    ) {
                        // Left/right handle bars.
                        Box(Modifier.align(Alignment.CenterStart).width(5.dp).fillMaxHeight().background(Color(0xFF3B68F5)))
                        Box(Modifier.align(Alignment.CenterEnd).width(5.dp).fillMaxHeight().background(Color(0xFF3B68F5)))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Bagian terpilih: ${(windowMs / 1000)} dtk",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                // Post (icon).
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(if (processing) Color.White.copy(alpha = 0.4f) else Color.White, RoundedCornerShape(25.dp))
                        .clickable(
                            enabled = !processing && durationMs > 0,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            processing = true
                            scope.launch {
                                val out = withContext(Dispatchers.IO) {
                                    runCatching { trimVideo(context, uri, startMs.toLong(), (startMs + windowMs).toLong()) }.getOrNull()
                                }
                                processing = false
                                if (out != null) onDone(out) else onCancel()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (processing) {
                        CircularProgressIndicator(color = Color(0xFF141726), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, "Posting", tint = Color(0xFF141726), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, cd, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * Cuts [startMs, endMs] into a new mp4 in the cache dir (stream copy, no
 * re-encode). Aligns start to the previous sync frame so the clip is playable.
 */
private fun trimVideo(context: Context, src: Uri, startMs: Long, endMs: Long): Uri {
    val outFile = java.io.File(context.cacheDir, "story_${System.currentTimeMillis()}.mp4")
    val extractor = MediaExtractor()
    extractor.setDataSource(context, src, null)
    val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val indexMap = HashMap<Int, Int>()
    try {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                indexMap[i] = muxer.addTrack(format)
            }
        }
        muxer.start()
        val startUs = startMs * 1000
        val endUs = endMs * 1000
        val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
        val info = android.media.MediaCodec.BufferInfo()
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val firstUs = extractor.sampleTime.coerceAtLeast(0)
        while (true) {
            val sampleTime = extractor.sampleTime
            if (sampleTime < 0 || sampleTime > endUs) break
            info.presentationTimeUs = (sampleTime - firstUs).coerceAtLeast(0)
            info.flags = extractor.sampleFlags
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break
            val outTrack = indexMap[extractor.sampleTrackIndex]
            if (outTrack != null) muxer.writeSampleData(outTrack, buffer, info)
            if (!extractor.advance()) break
        }
    } finally {
        runCatching { muxer.stop() }
        runCatching { muxer.release() }
        runCatching { extractor.release() }
    }
    return Uri.fromFile(outFile)
}
