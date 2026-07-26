package com.example.syntra

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
            TopIcon(Icons.Filled.RotateRight, "Putar") {
                working = rotate(working, 90f)
            }
            Spacer(Modifier.width(4.dp))
            ViewOnceToggle(on = viewOnce) { viewOnce = !viewOnce }
        }

        // The photo — its own space between the two bars, never covered.
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = working.asImageBitmap(),
                contentDescription = "Pratinjau foto",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
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
                    ) { onSend(working, caption.trim(), viewOnce) },
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
        // Half the old size — a small, unobtrusive marker rather than a big badge.
        Box(
            modifier = Modifier
                .size(13.dp)
                .drawBehind {
                    val d = size.minDimension
                    if (on) drawCircle(color = accent, radius = d / 2f)
                    drawCircle(
                        color = if (on) Color.White else Color.White.copy(alpha = 0.85f),
                        radius = d / 2f - 0.5.dp.toPx(),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "1",
                color = Color.White,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 8.sp,
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
