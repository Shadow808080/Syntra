package com.example.syntra

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.roundToInt

/** Frame geometry, filled in once the crop box is laid out; read by the Save action. */
private class CropGeom {
    var frameW = 0f
    var frameH = 0f
    var baseScale = 1f
}

/**
 * A pan-and-zoom crop editor. The bright rectangle is a fixed frame of [aspectRatio]
 * (width / height); the photo behind it can be dragged and pinch-zoomed so that
 * whatever ends up inside the frame is exactly what gets kept. Everything outside is
 * dimmed, so it's obvious which part will be cut.
 *
 * Reused wherever a fixed-ratio crop is needed — profile background (wide) and, later,
 * a Shorts cover frame (tall). [onConfirm] receives the cropped bitmap.
 */
@Composable
fun ImageCropScreen(
    source: Bitmap,
    aspectRatio: Float,
    title: String = "Atur posisi",
    hint: String = "Geser & cubit untuk mengatur. Yang di dalam kotak akan terlihat.",
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val bw = source.width.toFloat()
    val bh = source.height.toFloat()
    val image = remember(source) { source.asImageBitmap() }

    // User transform: zoom (>= 1) and pan offset in pixels, both clamped inside the box
    // so the frame is always fully covered by the photo (guaranteeing a valid crop).
    var zoom by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    val geom = remember { CropGeom() }

    fun performCrop() {
        val eff = geom.baseScale * zoom
        if (eff <= 0f || geom.frameW <= 0f) { onConfirm(source); return }
        val dispW = bw * eff
        val dispH = bh * eff
        val cropW = geom.frameW / eff
        val cropH = geom.frameH / eff
        val cropLeft = (((dispW - geom.frameW) / 2f) - offX) / eff
        val cropTop = (((dispH - geom.frameH) / 2f) - offY) / eff
        val l = cropLeft.roundToInt().coerceIn(0, (source.width - 1))
        val t = cropTop.roundToInt().coerceIn(0, (source.height - 1))
        val w = cropW.roundToInt().coerceIn(1, source.width - l)
        val h = cropH.roundToInt().coerceIn(1, source.height - t)
        val cropped = runCatching { Bitmap.createBitmap(source, l, t, w, h) }.getOrNull()
        onConfirm(cropped ?: source)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        // Top bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Batal",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onCancel,
                ),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "Simpan",
                color = Color(0xFF6C8BFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { performCrop() },
                ),
            )
        }

        BoxWithConstraints(
            // clipToBounds is essential: a Compose Canvas does NOT clip its drawing to
            // its bounds by default, so a tall photo would paint up over the header
            // (covering "Simpan"). Clipping keeps the photo inside the crop area.
            modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()

            // Fit a rectangle of aspectRatio inside the available area (with margin).
            val avW = boxW * 0.92f
            val avH = boxH * 0.92f
            val frameW: Float
            val frameH: Float
            if (avW / aspectRatio <= avH) {
                frameW = avW
                frameH = avW / aspectRatio
            } else {
                frameH = avH
                frameW = avH * aspectRatio
            }

            // Base scale so the photo covers the frame at zoom = 1.
            val baseScale = maxOf(frameW / bw, frameH / bh)
            geom.frameW = frameW
            geom.frameH = frameH
            geom.baseScale = baseScale

            val eff = baseScale * zoom
            val dispW = bw * eff
            val dispH = bh * eff

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Clamp inside the gesture (not during composition) so the frame
                    // always stays covered by the photo — a valid, in-bounds crop.
                    .pointerInput(bw, bh, frameW, frameH, baseScale) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                            val e = baseScale * newZoom
                            val maxX = ((bw * e - frameW) / 2f).coerceAtLeast(0f)
                            val maxY = ((bh * e - frameH) / 2f).coerceAtLeast(0f)
                            zoom = newZoom
                            offX = (offX + pan.x).coerceIn(-maxX, maxX)
                            offY = (offY + pan.y).coerceIn(-maxY, maxY)
                        }
                    },
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val left = cx - dispW / 2f + offX
                val top = cy - dispH / 2f + offY

                // The photo, panned + zoomed.
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(source.width, source.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt()),
                )

                // Dim everything outside the frame with four rectangles.
                val fLeft = cx - frameW / 2f
                val fTop = cy - frameH / 2f
                val dim = Color.Black.copy(alpha = 0.6f)
                drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, fTop))
                drawRect(dim, topLeft = Offset(0f, fTop + frameH), size = Size(size.width, size.height - (fTop + frameH)))
                drawRect(dim, topLeft = Offset(0f, fTop), size = Size(fLeft, frameH))
                drawRect(dim, topLeft = Offset(fLeft + frameW, fTop), size = Size(size.width - (fLeft + frameW), frameH))

                // Frame border + rule-of-thirds guides.
                drawRect(
                    color = Color.White,
                    topLeft = Offset(fLeft, fTop),
                    size = Size(frameW, frameH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
                val gridColor = Color.White.copy(alpha = 0.35f)
                for (i in 1..2) {
                    val gx = fLeft + frameW * i / 3f
                    drawLine(gridColor, Offset(gx, fTop), Offset(gx, fTop + frameH), strokeWidth = 1f)
                    val gy = fTop + frameH * i / 3f
                    drawLine(gridColor, Offset(fLeft, gy), Offset(fLeft + frameW, gy), strokeWidth = 1f)
                }
            }
        }

        // Hint.
        Text(
            text = hint,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 32.dp, vertical = 18.dp),
        )
    }
}
