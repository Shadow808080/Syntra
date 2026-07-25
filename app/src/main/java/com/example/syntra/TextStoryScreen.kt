package com.example.syntra

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Gradient backgrounds a text story can cycle through. */
private val StoryGradients = listOf(
    listOf(Color(0xFF7C4DFF), Color(0xFF3B68F5)),
    listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFF232526), Color(0xFF414345)),
    listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
    listOf(Color(0xFF2193B0), Color(0xFF6DD5ED)),
)

/**
 * Full-screen text-story composer. Type a message on a coloured background, tap
 * the palette to cycle colours, then Post — the text is rendered onto a
 * 1080×1920 bitmap and handed back via [onDone], reusing the normal image-story
 * upload path (no backend change needed).
 */
@Composable
fun TextStoryScreen(onClose: () -> Unit, onDone: (Bitmap) -> Unit) {
    BackHandler(onBack = onClose)
    var text by remember { mutableStateOf("") }
    var gradientIndex by remember { mutableIntStateOf(0) }
    val gradient = StoryGradients[gradientIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient)),
    ) {
        // Top bar: close + palette.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClose,
                    ),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { gradientIndex = (gradientIndex + 1) % StoryGradients.size },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Palette, "Ganti warna", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        // Centered editable text.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (text.isEmpty()) {
                Text(
                    "Ketik status…",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 280) text = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Post button.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 26.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            val enabled = text.isNotBlank()
            Row(
                modifier = Modifier
                    .background(
                        if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(28.dp),
                    )
                    .clickable(
                        enabled = enabled,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onDone(renderTextStory(text.trim(), gradient)) }
                    .padding(horizontal = 30.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Posting status",
                    color = if (enabled) Color(0xFF141726) else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Preview + editor for a photo story:
 *  - an OVERLAY TEXT you can type and DRAG anywhere on the photo, and
 *  - a separate CAPTION shown as a bar at the very bottom.
 * On post, both are baked onto the photo at their on-screen positions.
 */
@Composable
fun PhotoStoryPreview(
    photo: Bitmap,
    onCancel: () -> Unit,
    onDone: (Bitmap, com.example.syntra.net.StoryMusic?) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var overlayText by remember { mutableStateOf("") }
    // A song attached to this photo story (via the music tool), or null.
    var music by remember { mutableStateOf<com.example.syntra.net.StoryMusic?>(null) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    // Overlay position as a fraction of the photo frame (0..1), starts centred.
    var posX by remember { mutableStateOf(0.5f) }
    var posY by remember { mutableStateOf(0.42f) }
    var rotation by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var boxW by remember { mutableStateOf(1) }
    var boxH by remember { mutableStateOf(1) }
    var editingOverlay by remember { mutableStateOf(false) }
    // Crop toggle: false = original shape, true = centre square (1:1). Reversible.
    var cropSquare by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // The bitmap actually shown/baked: a centred square crop, or the original.
    val shown = remember(photo, cropSquare) { if (cropSquare) centreSquare(photo) else photo }
    // When the text tool is turned on, focus the field so the keyboard opens and
    // you can actually type (this was the "gabisa" bug — no focus, no keyboard).
    androidx.compose.runtime.LaunchedEffect(editingOverlay) {
        if (editingOverlay) {
            kotlinx.coroutines.delay(100)
            runCatching { textFocus.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding(),
    ) {
        // Top bar: back + crop toggle + add-text tool (all icon-only).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onCancel,
                    ),
            )
            Spacer(Modifier.weight(1f))
            // Crop toggle — square vs original. Highlighted while cropped.
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (cropSquare) Color.White else Color.White.copy(alpha = 0.18f),
                        CircleShape,
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { cropSquare = !cropSquare },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (cropSquare) Icons.Filled.Crop else Icons.Filled.CropSquare,
                    contentDescription = if (cropSquare) "Kembalikan bentuk asli" else "Potong jadi kotak",
                    tint = if (cropSquare) Color(0xFF141726) else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // Add-text tool (icon only).
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { editingOverlay = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.TextFields,
                    contentDescription = "Tambah teks",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // Music tool — attach a song to this photo story (highlighted once chosen).
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (music != null) Color.White else Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { showMusicPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = "Tambah musik",
                    tint = if (music != null) Color(0xFF141726) else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        // Chosen-song chip, so it's clear a track is attached.
        music?.let { m ->
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${m.title} · ${m.artist}",
                    color = Color.White, fontSize = 12.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        }
        if (showMusicPicker) {
            MusicPickerSheet(
                onDismiss = { showMusicPicker = false },
                onPick = { music = it; showMusicPicker = false },
            )
        }

        // Image area: fills the space between the bars. The photo frame is 1:1 when
        // cropped, otherwise fits the original. The overlay text lives INSIDE this
        // frame so it never spills onto the caption panel below.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .then(if (cropSquare) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.fillMaxSize())
                    .clip(RoundedCornerShape(if (cropSquare) 8.dp else 0.dp))
                    .onSizeChanged { boxW = it.width; boxH = it.height },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = "Pratinjau foto",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                // Overlay text: drag to move, TWO-finger pinch to scale, twist to ROTATE.
                if (overlayText.isNotEmpty() || editingOverlay) {
                    val transformState = androidx.compose.foundation.gestures.rememberTransformableState { zoom, pan, rotate ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                        rotation += rotate
                        posX = (posX + pan.x / boxW).coerceIn(0.05f, 0.95f)
                        posY = (posY + pan.y / boxH).coerceIn(0.05f, 0.95f)
                    }
                    Box(
                        modifier = Modifier
                            .offset {
                                androidx.compose.ui.unit.IntOffset(
                                    (posX * boxW).toInt() - 400,
                                    (posY * boxH).toInt(),
                                )
                            }
                            .width(with(density) { 800.toDp() })
                            .graphicsLayer {
                                rotationZ = rotation
                                scaleX = scale
                                scaleY = scale
                            }
                            // Drag to move + pinch/rotate. Enabled only while NOT typing so a
                            // tap can place the cursor and open the keyboard.
                            .then(
                                if (!editingOverlay) {
                                    Modifier
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                posX = (posX + drag.x / boxW).coerceIn(0.05f, 0.95f)
                                                posY = (posY + drag.y / boxH).coerceIn(0.05f, 0.95f)
                                            }
                                        }
                                        .transformable(transformState)
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicTextField(
                            value = overlayText,
                            onValueChange = { if (it.length <= 120) overlayText = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { editingOverlay = false }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(textFocus),
                        )
                    }
                }
            }
        }

        // Dedicated bottom panel: caption on its own surface, never over the image.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141726))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (caption.isEmpty()) {
                        Text("Tambahkan caption…", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = caption,
                        onValueChange = { if (it.length <= 200) caption = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Post (icon only — a send arrow).
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.White, CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onDone(bakePhoto(shown, overlayText.trim(), posX, posY, rotation, scale, caption.trim()), music) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Posting",
                        tint = Color(0xFF141726),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** Centre-crops [src] to a square (1:1). The largest square that fits, centred. */
private fun centreSquare(src: Bitmap): Bitmap {
    val side = minOf(src.width, src.height)
    if (src.width == side && src.height == side) return src
    val left = (src.width - side) / 2
    val top = (src.height - side) / 2
    return Bitmap.createBitmap(src, left, top, side, side)
}

/**
 * Composites onto the photo: the draggable [overlay] at fractional position
 * ([px], [py]) and the [caption] as a bar at the bottom. Blank parts are skipped.
 */
private fun bakePhoto(src: Bitmap, overlay: String, px: Float, py: Float, rot: Float, scale: Float, caption: String): Bitmap {
    if (overlay.isEmpty() && caption.isEmpty()) return src
    val bitmap = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bitmap)

    if (overlay.isNotEmpty()) {
        val tp = TextPaint().apply {
            color = android.graphics.Color.WHITE
            textSize = bitmap.width * 0.075f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(10f, 0f, 3f, android.graphics.Color.argb(190, 0, 0, 0))
        }
        val tw = (bitmap.width * 0.84f).toInt()
        val layout = StaticLayout.Builder.obtain(overlay, 0, overlay.length, tp, tw)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .build()
        // Rotate + scale around the dragged centre point, matching the preview.
        val cx = px * bitmap.width
        val cy = py * bitmap.height
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rot)
        canvas.scale(scale, scale)
        canvas.translate(-tw / 2f, -layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    if (caption.isNotEmpty()) {
        val cp = TextPaint().apply {
            color = android.graphics.Color.WHITE
            textSize = bitmap.width * 0.045f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val tw = (bitmap.width * 0.9f).toInt()
        val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, cp, tw)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .build()
        // Dark scrim behind the caption for legibility.
        val scrim = Paint().apply { color = android.graphics.Color.argb(130, 0, 0, 0) }
        val top = bitmap.height - layout.height - bitmap.height * 0.06f
        canvas.drawRect(0f, top - 24f, bitmap.width.toFloat(), bitmap.height.toFloat(), scrim)
        canvas.save()
        canvas.translate((bitmap.width - tw) / 2f, top)
        layout.draw(canvas)
        canvas.restore()
    }
    return bitmap
}

/** Draws the text on a gradient 1080×1920 bitmap so it can be uploaded as an image. */
private fun renderTextStory(text: String, gradient: List<Color>): Bitmap {
    val w = 1080
    val h = 1920
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Background gradient (top → bottom).
    val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            gradient.first().toArgb(), gradient.last().toArgb(),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    // Text, centered, wrapped to 80% width.
    val textPaint = TextPaint().apply {
        color = android.graphics.Color.WHITE
        textSize = if (text.length > 120) 56f else if (text.length > 40) 72f else 96f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    val textWidth = (w * 0.8f).toInt()
    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, textPaint, textWidth)
        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
        .build()

    canvas.save()
    canvas.translate((w - textWidth) / 2f, (h - layout.height) / 2f)
    layout.draw(canvas)
    canvas.restore()

    return bitmap
}
