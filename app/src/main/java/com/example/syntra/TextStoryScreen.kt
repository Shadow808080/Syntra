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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
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
 * Preview + light editor for a photo story. Shows the picked photo full-screen
 * with a back arrow to cancel, an optional caption you can type over it, and a
 * Post button. On post the caption is baked onto the photo.
 */
@Composable
fun PhotoStoryPreview(photo: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    BackHandler(onBack = onCancel)
    var caption by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.foundation.Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = "Pratinjau foto",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // Back arrow (cancel).
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Kembali",
            tint = Color.White,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(14.dp)
                .size(26.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onCancel,
                ),
        )

        // Caption overlay (typed near the bottom, drawn onto the photo on post).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (caption.isEmpty()) {
                Text(
                    "Ketuk untuk menambah teks…",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            BasicTextField(
                value = caption,
                onValueChange = { if (it.length <= 140) caption = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
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
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(28.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onDone(bakeCaption(photo, caption.trim())) }
                    .padding(horizontal = 26.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Posting", color = Color(0xFF141726), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Composites the caption near the bottom of the photo (no-op when blank). */
private fun bakeCaption(src: Bitmap, caption: String): Bitmap {
    if (caption.isEmpty()) return src
    val bitmap = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bitmap)
    val tp = TextPaint().apply {
        color = android.graphics.Color.WHITE
        textSize = bitmap.width * 0.07f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(8f, 0f, 2f, android.graphics.Color.argb(180, 0, 0, 0))
    }
    val tw = (bitmap.width * 0.86f).toInt()
    val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, tp, tw)
        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
        .build()
    canvas.save()
    canvas.translate((bitmap.width - tw) / 2f, bitmap.height * 0.72f)
    layout.draw(canvas)
    canvas.restore()
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
