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

private const val MAX_STORY_VIDEO_MS = 30_000L
private const val FILM_FRAMES = 8

/**
 * CapCut-style video trimmer. The video plays in the top area; a dedicated dark
 * panel at the bottom holds a thumbnail filmstrip with a window whose BOTH edges
 * drag, so the clip length is free up to [maxMs] (30s stories, 60s Shorts).
 * Controls are icons, and nothing overlaps the video frame.
 */
/** Shortest clip the trimmer will let you keep. */
private const val MIN_TRIM_MS = 1_000L

@Composable
fun VideoTrimScreen(
    uri: Uri,
    onCancel: () -> Unit,
    onDone: (Uri) -> Unit,
    // Longest clip the caller allows: 30s for stories, 60s for Shorts.
    maxMs: Long = MAX_STORY_VIDEO_MS,
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var durationMs by remember { mutableStateOf(0L) }
    // The selected window as [startMs, endMs]. BOTH edges drag, so the length is
    // free anywhere between MIN_TRIM_MS and maxMs — that's how you get "50 detik".
    var startMs by remember { mutableFloatStateOf(0f) }
    var endMs by remember { mutableFloatStateOf(0f) }
    var processing by remember { mutableStateOf(false) }
    // True once the player is prepared and playing — drives the preview spinner.
    var prepared by remember(uri) { mutableStateOf(false) }
    val frames = remember { mutableStateListOf<Bitmap>() }
    val player = remember { MediaPlayer() }

    // Once we know the duration, default the window to the whole clip, capped at maxMs.
    LaunchedEffect(durationMs) {
        if (durationMs > 0) {
            startMs = 0f
            endMs = minOf(durationMs.toFloat(), maxMs.toFloat())
        }
    }
    val selectedMs = (endMs - startMs).toLong().coerceAtLeast(0L)

    // Read duration + build a filmstrip of evenly-spaced thumbnails.
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                // release() in finally, not `.use {}` — AutoCloseable on
                // MediaMetadataRetriever is API 29+, and minSdk is 26.
                val r = MediaMetadataRetriever()
                try {
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
                } finally {
                    r.release()
                }
            }
        }
    }

    // Prepare the player ONCE, independent of the surface. Setting the data source
    // inside the surface callback meant a relayout (the filmstrip loading resizes
    // the view) recreated the TextureView and called setDataSource again, throwing
    // — so the surface never attached and you got sound with a black frame.
    LaunchedEffect(uri) {
        runCatching {
            player.setDataSource(context, uri)
            player.isLooping = false
            player.setOnPreparedListener { mp ->
                prepared = true
                runCatching { mp.start() }
            }
            player.prepareAsync()
        }
    }

    // Loop playback within [start, end] so the preview matches the selection.
    LaunchedEffect(startMs, endMs, durationMs) {
        if (durationMs <= 0) return@LaunchedEffect
        while (true) {
            runCatching {
                val pos = player.currentPosition
                if (pos < startMs - 150 || pos > endMs) player.seekTo(startMs.toInt())
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
                if (durationMs > maxMs) "Maks ${maxMs / 1000} detik" else "Pratinjau",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        // --- Video preview area ---
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            // ONLY attach the surface here — never touch the data source.
                            // Safe to (re)attach whenever a texture appears, including
                            // after the player has already prepared.
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                runCatching { player.setSurface(Surface(st)) }
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                            // Keep the texture so the surface stays valid across relayouts.
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = false
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!prepared) {
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
                    val leftPx = (trackW * (startMs / durationMs)).coerceIn(0f, trackW.toFloat())
                    val rightPx = (trackW * (endMs / durationMs)).coerceIn(0f, trackW.toFloat())
                    val winPx = (rightPx - leftPx).coerceAtLeast(0f)
                    // Dim the parts outside the window (left slab + right slab).
                    Box(
                        Modifier.width(with(density) { leftPx.toDp() }).fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.55f)),
                    )
                    Box(
                        Modifier.offset { androidx.compose.ui.unit.IntOffset(rightPx.toInt(), 0) }
                            .width(with(density) { (trackW - rightPx).coerceAtLeast(0f).toDp() }).fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.55f)),
                    )
                    // The window frame — drag the MIDDLE to slide the whole selection,
                    // keeping its length; drag either HANDLE to change the length.
                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(leftPx.toInt(), 0) }
                            .width(with(density) { winPx.toDp() })
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x333B68F5))
                            .pointerInput(durationMs, trackW) {
                                detectHorizontalDragGestures { change, dx ->
                                    change.consume()
                                    val len = endMs - startMs
                                    val dMs = (dx / trackW) * durationMs
                                    val ns = (startMs + dMs).coerceIn(0f, durationMs - len)
                                    startMs = ns
                                    endMs = ns + len
                                }
                            },
                    ) {
                        // LEFT handle — moves the start edge (shrinks/grows from left).
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(20.dp)
                                .fillMaxHeight()
                                .pointerInput(durationMs, trackW) {
                                    detectHorizontalDragGestures { change, dx ->
                                        change.consume()
                                        val dMs = (dx / trackW) * durationMs
                                        startMs = (startMs + dMs).coerceIn(
                                            maxOf(0f, endMs - maxMs.toFloat()),
                                            endMs - MIN_TRIM_MS,
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.width(5.dp).fillMaxHeight().background(Color(0xFF3B68F5)))
                        }
                        // RIGHT handle — moves the end edge.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(20.dp)
                                .fillMaxHeight()
                                .pointerInput(durationMs, trackW) {
                                    detectHorizontalDragGestures { change, dx ->
                                        change.consume()
                                        val dMs = (dx / trackW) * durationMs
                                        endMs = (endMs + dMs).coerceIn(
                                            startMs + MIN_TRIM_MS,
                                            minOf(durationMs.toFloat(), startMs + maxMs.toFloat()),
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.width(5.dp).fillMaxHeight().background(Color(0xFF3B68F5)))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Bagian terpilih: ${selectedMs / 1000} dtk",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                // Post (icon).
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(if (processing) Color.White.copy(alpha = 0.4f) else Color.White, RoundedCornerShape(25.dp))
                        .clickable(
                            enabled = !processing && selectedMs >= MIN_TRIM_MS,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            processing = true
                            scope.launch {
                                val out = withContext(Dispatchers.IO) {
                                    runCatching { trimVideo(context, uri, startMs.toLong(), endMs.toLong()) }.getOrNull()
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
    val outFile = java.io.File(context.cacheDir, "trim_${System.currentTimeMillis()}.mp4")
    val extractor = MediaExtractor()
    extractor.setDataSource(context, src, null)
    val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val indexMap = HashMap<Int, Int>()
    // Big enough for one compressed sample. Grow it to the track's declared max so
    // a high-bitrate frame never overflows the buffer (which would truncate the
    // output — the "it didn't really cut" symptom).
    var bufferSize = 1 shl 20
    try {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                indexMap[i] = muxer.addTrack(format)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
        }

        // Keep the video upright: carry the source rotation onto the output.
        runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, src)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()?.let { muxer.setOrientationHint(it) }
            } finally {
                r.release()
            }
        }

        muxer.start()
        val startUs = startMs * 1000
        val endUs = endMs * 1000
        val buffer = java.nio.ByteBuffer.allocate(bufferSize)
        val info = android.media.MediaCodec.BufferInfo()
        // Align to the previous sync frame so decoding starts cleanly, then rebase
        // timestamps to zero so the clip begins at 0:00.
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val firstUs = extractor.sampleTime.coerceAtLeast(0)
        while (true) {
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break
            val sampleTime = extractor.sampleTime
            if (sampleTime > endUs) break
            info.presentationTimeUs = (sampleTime - firstUs).coerceAtLeast(0)
            info.offset = 0
            // MediaExtractor.SAMPLE_FLAG_* are NOT MediaCodec.BUFFER_FLAG_* — passing
            // them raw can e.g. read SAMPLE_FLAG_PARTIAL_FRAME (4) as the muxer's
            // BUFFER_FLAG_END_OF_STREAM (4) and truncate the clip. Translate instead.
            info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
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
