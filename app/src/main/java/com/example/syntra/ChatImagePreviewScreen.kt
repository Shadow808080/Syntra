package com.example.syntra

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WhatsApp-style "edit before send" screen for a chat photo. A picked or captured
 * image lands here first — the user can rotate, crop (reframe/zoom), add a caption,
 * and mark it "sekali lihat" (view-once) — and only then does [onSend] fire with
 * the final bitmap, caption, and view-once flag. Nothing is uploaded until then.
 */
@Composable
fun ChatImagePreviewScreen(
    source: Bitmap,
    onCancel: () -> Unit,
    onSend: (Bitmap, String, Boolean) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var working by remember { mutableStateOf(source) }
    var caption by remember { mutableStateOf("") }
    var viewOnce by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf(false) }

    // Freehand "coret" (scribble). Strokes live in the display box's coordinate space
    // and are only baked into the bitmap on rotate/crop/send — so undo stays cheap.
    var drawing by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<PenStroke>() }
    val current = remember { mutableStateListOf<Offset>() }
    var penColor by remember { mutableStateOf(PenColors.first()) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val penWidthPx = with(LocalDensity.current) { 5.dp.toPx() }

    // Burn the scribbles into `working` and clear them, so they travel with the image
    // through a later rotate/crop and are permanent on send.
    fun flatten() {
        if (strokes.isEmpty()) return
        working = bakeStrokes(working, strokes.toList(), boxSize.width, boxSize.height)
        strokes.clear()
        current.clear()
    }

    if (cropping) {
        ImageCropScreen(
            source = working,
            aspectRatio = working.width.toFloat() / working.height.toFloat().coerceAtLeast(1f),
            title = "Potong",
            hint = "Geser & cubit untuk membingkai ulang foto.",
            onCancel = { cropping = false },
            onConfirm = { working = it; cropping = false },
        )
        return
    }

    // Header / photo / footer each get their OWN space (the bars have a solid
    // background), so buttons never sit on top of the photo.
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        // Header bar: back · rotate · crop · view-once.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15151C))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Batal", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            DrawToggle(on = drawing) { drawing = !drawing }
            Spacer(Modifier.width(4.dp))
            TopIcon(Icons.Filled.RotateRight, "Putar") {
                flatten()
                working = rotate(working, 90f)
            }
            Spacer(Modifier.width(4.dp))
            TopIcon(Icons.Filled.Crop, "Potong") { flatten(); cropping = true }
            Spacer(Modifier.width(4.dp))
            ViewOnceToggle(on = viewOnce) { viewOnce = !viewOnce }
        }

        // The photo — its own space between the two bars, never covered.
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Image + scribble canvas share one inner box, so drawn points map 1:1 to
            // what's on screen (and, on send, back onto the bitmap).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .onSizeChanged { boxSize = it }
                    .pointerInput(drawing, penColor, penWidthPx) {
                        if (!drawing) return@pointerInput
                        detectDragGestures(
                            onDragStart = { off -> current.clear(); current.add(off) },
                            onDrag = { change, _ -> change.consume(); current.add(change.position) },
                            onDragEnd = {
                                if (current.isNotEmpty()) strokes.add(PenStroke(penColor, penWidthPx, current.toList()))
                                current.clear()
                            },
                            onDragCancel = { current.clear() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = working.asImageBitmap(),
                    contentDescription = "Pratinjau foto",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(Modifier.fillMaxSize()) {
                    strokes.forEach { drawPenStroke(it.points, it.color, it.width) }
                    if (current.isNotEmpty()) drawPenStroke(current.toList(), penColor, penWidthPx)
                }
            }

            // Colour + undo bar, only while drawing.
            if (drawing) {
                ScribbleBar(
                    selected = penColor,
                    onSelect = { penColor = it },
                    canUndo = strokes.isNotEmpty(),
                    onUndo = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
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
                            if (viewOnce) "Tambah keterangan · Sekali lihat" else "Tambah keterangan…",
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
                    ) { flatten(); onSend(working, caption.trim(), viewOnce) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** View-once toggle: a "1" inside a DASHED ring; fills solid when on. */
@Composable
private fun ViewOnceToggle(on: Boolean, onToggle: () -> Unit) {
    val accent = Color(0xFF6C8BFF)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Sized to sit alongside the other header icons — readable, not a tiny speck.
        Box(
            modifier = Modifier
                .size(22.dp)
                .drawBehind {
                    val d = size.minDimension
                    if (on) drawCircle(color = accent, radius = d / 2f)
                    drawCircle(
                        color = if (on) Color.White else Color.White.copy(alpha = 0.85f),
                        radius = d / 2f - 1.dp.toPx(),
                        style = Stroke(
                            width = 1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "1",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 12.sp,
                ),
            )
        }
    }
}

@Composable
private fun TopIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(23.dp)) }
}

private fun rotate(bmp: Bitmap, degrees: Float): Bitmap {
    val m = Matrix().apply { postRotate(degrees) }
    return runCatching { Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) }.getOrDefault(bmp)
}

// ---------------------------------------------------------------------------
// Scribble ("coret") support
// ---------------------------------------------------------------------------

/** Palette offered while drawing. */
private val PenColors = listOf(
    Color(0xFFFF3B30), // red
    Color(0xFFFFCC00), // yellow
    Color(0xFF34C759), // green
    Color(0xFF0A84FF), // blue
    Color.White,
    Color.Black,
)

/** One freehand stroke: a colour, a width (display-box px), and its points. */
private data class PenStroke(val color: Color, val width: Float, val points: List<Offset>)

/** Draws one stroke on the overlay canvas (points are in box-space). */
private fun DrawScope.drawPenStroke(points: List<Offset>, color: Color, width: Float) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color = color, radius = width / 2f, center = points[0])
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Burns the [strokes] (captured in the display box's coordinate space) onto a copy of
 * [src], mapping each point back to bitmap pixels. The image is shown with
 * ContentScale.Fit inside a [boxW]×[boxH] box, so we undo that fit-scale + centering.
 */
private fun bakeStrokes(src: Bitmap, strokes: List<PenStroke>, boxW: Int, boxH: Int): Bitmap {
    if (strokes.isEmpty() || boxW <= 0 || boxH <= 0) return src
    val scale = minOf(boxW.toFloat() / src.width, boxH.toFloat() / src.height)
    if (scale <= 0f) return src
    val offX = (boxW - src.width * scale) / 2f
    val offY = (boxH - src.height * scale) / 2f
    val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
    val canvas = android.graphics.Canvas(out)
    fun mapX(x: Float) = (x - offX) / scale
    fun mapY(y: Float) = (y - offY) / scale
    strokes.forEach { s ->
        if (s.points.isEmpty()) return@forEach
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = s.color.toArgb()
            strokeWidth = s.width / scale
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        if (s.points.size == 1) {
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawCircle(mapX(s.points[0].x), mapY(s.points[0].y), (s.width / scale) / 2f, paint)
        } else {
            paint.style = android.graphics.Paint.Style.STROKE
            val path = android.graphics.Path().apply {
                moveTo(mapX(s.points[0].x), mapY(s.points[0].y))
                for (i in 1 until s.points.size) lineTo(mapX(s.points[i].x), mapY(s.points[i].y))
            }
            canvas.drawPath(path, paint)
        }
    }
    return out
}

/** The brush toggle in the header; tinted accent when drawing is on. */
@Composable
private fun DrawToggle(on: Boolean, onToggle: () -> Unit) {
    val accent = Color(0xFF6C8BFF)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Brush,
            "Coret",
            tint = if (on) accent else Color.White,
            modifier = Modifier.size(23.dp),
        )
    }
}

/** Colour swatches + undo, shown over the photo while drawing. */
@Composable
private fun ScribbleBar(
    selected: Color,
    onSelect: (Color) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color(0xCC15151C), RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PenColors.forEach { c ->
            val isSel = c == selected
            Box(
                modifier = Modifier
                    .size(if (isSel) 26.dp else 22.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (isSel) 2.dp else 1.dp,
                        color = if (isSel) Color.White else Color.White.copy(alpha = 0.35f),
                        shape = CircleShape,
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSelect(c) },
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = canUndo,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onUndo,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                "Batalkan coretan",
                tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
