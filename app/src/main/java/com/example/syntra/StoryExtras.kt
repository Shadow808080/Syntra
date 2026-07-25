package com.example.syntra

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.syntra.net.MusicClient
import com.example.syntra.net.MusicTrack
import com.example.syntra.net.StoryMusic
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Music picker — a bottom sheet that searches the free catalogue (Deezer, via
// MusicClient) and returns the chosen track as a StoryMusic to stick on a story.
// Shared by the "Musik" story action and the "add music" chip on a photo story.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPickerSheet(onDismiss: () -> Unit, onPick: (StoryMusic) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<MusicTrack>() }
    var loading by remember { mutableStateOf(false) }

    // Seed with the trending chart; then live-search as the user types (debounced).
    LaunchedEffect(query) {
        if (query.isBlank()) {
            loading = true
            runCatching { MusicClient.browse() }.getOrNull()?.let {
                results.clear(); results.addAll(it.trending)
            }
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(350)
        runCatching { MusicClient.search(query) }.getOrNull()?.let {
            results.clear(); results.addAll(it.tracks)
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NexusSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                "Pilih musik",
                color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
            // Search field.
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Cari lagu…", color = NexusTextSecondary, fontSize = 14.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(NexusAccentSoft),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (loading && results.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(results, key = { it.id }) { t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    onPick(
                                        StoryMusic(
                                            title = t.title,
                                            artist = t.artist,
                                            previewUrl = t.previewUrl,
                                            artworkUrl = t.artworkUrl,
                                        ),
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(NexusBackground),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!t.artworkUrl.isNullOrBlank()) {
                                    AsyncImage(t.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
                                } else {
                                    Icon(Icons.Filled.MusicNote, null, tint = NexusTextSecondary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, color = NexusTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Filled.PlayArrow, null, tint = NexusAccentSoft, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bitmaps: collage from several photos, and a "music story" background card.
// Both produce a single portrait bitmap posted through the normal photo path.
// ---------------------------------------------------------------------------

/** Story canvas size — 9:16 portrait, comfortable resolution. */
private const val STORY_W = 1080
private const val STORY_H = 1920

/**
 * Composes [photos] (2–4) into one portrait collage bitmap: two side-by-side, or a
 * 2×2 grid. Each cell is centre-cropped to fill, with a thin gap between them.
 */
fun buildCollage(photos: List<Bitmap>): Bitmap {
    val out = Bitmap.createBitmap(STORY_W, STORY_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK)
    val gap = 10f
    val cells: List<RectF> = when (photos.size.coerceIn(1, 4)) {
        1 -> listOf(RectF(0f, 0f, STORY_W.toFloat(), STORY_H.toFloat()))
        2 -> listOf(
            RectF(0f, 0f, STORY_W.toFloat(), STORY_H / 2f - gap / 2),
            RectF(0f, STORY_H / 2f + gap / 2, STORY_W.toFloat(), STORY_H.toFloat()),
        )
        3 -> listOf(
            RectF(0f, 0f, STORY_W.toFloat(), STORY_H / 2f - gap / 2),
            RectF(0f, STORY_H / 2f + gap / 2, STORY_W / 2f - gap / 2, STORY_H.toFloat()),
            RectF(STORY_W / 2f + gap / 2, STORY_H / 2f + gap / 2, STORY_W.toFloat(), STORY_H.toFloat()),
        )
        else -> listOf(
            RectF(0f, 0f, STORY_W / 2f - gap / 2, STORY_H / 2f - gap / 2),
            RectF(STORY_W / 2f + gap / 2, 0f, STORY_W.toFloat(), STORY_H / 2f - gap / 2),
            RectF(0f, STORY_H / 2f + gap / 2, STORY_W / 2f - gap / 2, STORY_H.toFloat()),
            RectF(STORY_W / 2f + gap / 2, STORY_H / 2f + gap / 2, STORY_W.toFloat(), STORY_H.toFloat()),
        )
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    photos.take(cells.size).forEachIndexed { i, bmp ->
        drawCentreCrop(canvas, bmp, cells[i], paint)
    }
    return out
}

/** Draws [bmp] into [dst] as centre-crop (fill, keep aspect, clip overflow). */
private fun drawCentreCrop(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint) {
    val scale = maxOf(dst.width() / bmp.width, dst.height() / bmp.height)
    val w = bmp.width * scale
    val h = bmp.height * scale
    val srcW = (dst.width() / scale).toInt().coerceIn(1, bmp.width)
    val srcH = (dst.height() / scale).toInt().coerceIn(1, bmp.height)
    val srcX = ((bmp.width - srcW) / 2).coerceAtLeast(0)
    val srcY = ((bmp.height - srcH) / 2).coerceAtLeast(0)
    canvas.drawBitmap(bmp, Rect(srcX, srcY, srcX + srcW, srcY + srcH), dst, paint)
    // silence unused w/h (kept for readability of the crop maths)
    if (w < 0 || h < 0) Unit
}

/**
 * A pure "music story" background: a vibrant gradient with the album art, song
 * title and artist — used when the user posts a song with no photo of their own.
 */
fun buildMusicStory(music: StoryMusic, artwork: Bitmap?): Bitmap {
    val out = Bitmap.createBitmap(STORY_W, STORY_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    // Gradient background, colour derived from the title so it varies per song.
    val palette = listOf(
        intArrayOf(0xFF6C5CE7.toInt(), 0xFF3B68F5.toInt()),
        intArrayOf(0xFF11998E.toInt(), 0xFF38EF7D.toInt()),
        intArrayOf(0xFFEE5A6F.toInt(), 0xFFF29263.toInt()),
        intArrayOf(0xFFDA22FF.toInt(), 0xFF9733EE.toInt()),
    )
    val g = palette[(music.title.hashCode() and Int.MAX_VALUE) % palette.size]
    val bg = Paint().apply {
        shader = LinearGradient(0f, 0f, STORY_W.toFloat(), STORY_H.toFloat(), g[0], g[1], Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, STORY_W.toFloat(), STORY_H.toFloat(), bg)

    // Album art card, centred.
    val artSize = 640f
    val left = (STORY_W - artSize) / 2f
    val top = STORY_H / 2f - artSize - 40f
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (artwork != null) {
        val dst = RectF(left, top, left + artSize, top + artSize)
        drawCentreCrop(canvas, artwork, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    } else {
        cardPaint.color = 0x33FFFFFF
        canvas.drawRoundRect(RectF(left, top, left + artSize, top + artSize), 28f, 28f, cardPaint)
    }

    // Title + artist below the art.
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 68f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val artist = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }
    val cx = STORY_W / 2f
    canvas.drawText(ellipsize(music.title, 22), cx, top + artSize + 110f, title)
    canvas.drawText(ellipsize(music.artist, 26), cx, top + artSize + 180f, artist)
    return out
}

private fun ellipsize(s: String, max: Int): String =
    if (s.length <= max) s else s.take(max - 1) + "…"
