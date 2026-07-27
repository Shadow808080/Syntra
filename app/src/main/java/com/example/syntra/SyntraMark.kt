package com.example.syntra

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * The Syntra mark, as geometry — one definition shared by the launcher icon and the
 * splash animation, so the two can never drift apart.
 *
 * THE MARK IS THREE PRODUCT ICONS, not a letter. A speech bubble, a play button and a
 * set of voice waves, interlocked. Earlier versions drew an S and hung the products
 * off it as decoration; this is the other way round. The letterforms are still in
 * there — an S in the sweep from bubble down through the waves, an A in the peak where
 * bubble meets play — but they are what the edges leave behind, not what is drawn.
 *
 * All coordinates are the launcher icon's 108-unit viewport.
 */
object SyntraMark {

    const val VIEWPORT = 108f

    /** A slight lean, so the cluster reads as an object rather than a diagram. */
    const val TILT = -6f

    /** The mark's optical centre — not the box centre. */
    val centre = Offset(52f, 48f)

    // ---- CHAT: the bubble ----------------------------------------------------

    /**
     * The bubble, as ONE closed path — body and hooked tail in a single outline.
     *
     * Identical to the launcher icon's shape, so the thing that assembles on the
     * splash is the thing that ends up on the home screen. The tail hooks back under
     * the body rather than stopping at a point, which is what leaves an S in the
     * silhouette's left edge without drawing one.
     *
     * One path, not two overlapping contours: an earlier version kept the tail
     * separate and its overlap with the body cancelled under nonzero winding, punching
     * a hole straight through the bubble.
     */
    fun bubble(scale: Float): Path = Path().apply {
        fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
        moveTo(p(38f, 22f).x, p(38f, 22f).y)
        lineTo(p(70f, 22f).x, p(70f, 22f).y)
        cubicTo(p(79.4f, 22f).x, p(79.4f, 22f).y, p(87f, 29.6f).x, p(87f, 29.6f).y, p(87f, 39f).x, p(87f, 39f).y)
        lineTo(p(87f, 55f).x, p(87f, 55f).y)
        cubicTo(p(87f, 64.4f).x, p(87f, 64.4f).y, p(79.4f, 72f).x, p(79.4f, 72f).y, p(70f, 72f).x, p(70f, 72f).y)
        lineTo(p(57f, 72f).x, p(57f, 72f).y)
        cubicTo(p(50f, 72f).x, p(50f, 72f).y, p(44f, 76f).x, p(44f, 76f).y, p(40f, 82f).x, p(40f, 82f).y)
        cubicTo(p(37.6f, 85.6f).x, p(37.6f, 85.6f).y, p(32f, 84.4f).x, p(32f, 84.4f).y, p(32.8f, 79.6f).x, p(32.8f, 79.6f).y)
        cubicTo(p(33.6f, 75f).x, p(33.6f, 75f).y, p(35.4f, 72.4f).x, p(35.4f, 72.4f).y, p(38f, 70.6f).x, p(38f, 70.6f).y)
        cubicTo(p(28.4f, 68.4f).x, p(28.4f, 68.4f).y, p(21f, 60.4f).x, p(21f, 60.4f).y, p(21f, 50f).x, p(21f, 50f).y)
        lineTo(p(21f, 39f).x, p(21f, 39f).y)
        cubicTo(p(21f, 29.6f).x, p(21f, 29.6f).y, p(28.6f, 22f).x, p(28.6f, 22f).y, p(38f, 22f).x, p(38f, 22f).y)
        close()
    }

    /** The three dots. Without them the silhouette is a blob, not a message. */
    val dots = listOf(
        Offset(40.5f, 45f),
        Offset(54f, 45f),
        Offset(67.5f, 45f),
    )
    val dotColours = listOf(0xFF1B49C9, 0xFF2E6BF0, 0xFF4D9BFF)
    const val DOT_RADIUS = 5.2f

    // ---- SHORTS: the play button ---------------------------------------------

    /**
     * The film strip — reels.
     *
     * A camcorder says "record a video"; a film strip says "short film", which is what
     * a reel actually is. It is also the shape people already read on Reels and TikTok
     * surfaces, so it needs no learning.
     */
    fun filmBody(scale: Float): Path = Path().apply {
        val r = 7f * scale
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 34f * scale, top = 52f * scale,
                right = 82f * scale, bottom = 84f * scale,
                radiusX = r, radiusY = r,
            ),
        )
    }

    /** The sprocket holes down both edges — what makes it read as film. */
    val sprockets: List<Offset> = buildList {
        listOf(58.6f, 68f, 77.4f).forEach { y ->
            add(Offset(40.4f, y))
            add(Offset(75.6f, y))
        }
    }
    const val SPROCKET = 3.1f

    /** The play triangle in the strip's middle window. */
    val filmPlay = listOf(
        Offset(52f, 60.4f),
        Offset(66f, 68f),
        Offset(52f, 75.6f),
    )

    // ---- ROOMS: the voice waves ----------------------------------------------

    /**
     * Ring [index] of the voice waves — a FULL circle, not a one-sided arc.
     *
     * The old version drew arcs opening to the right, which read as a wifi symbol
     * pointed somewhere. A room's audio does not have a direction: it fills the
     * space. Concentric rings say that, and they stay legible however the mark is
     * rotated, which a directional arc never does.
     */
    fun ring(index: Int, scale: Float): Path {
        val r = when (index) {
            0 -> 8f
            1 -> 15f
            else -> 22f
        }
        return Path().apply {
            addOval(
                Rect(center = Offset(54f, 54f) * scale, radius = r * scale),
            )
        }
    }

    /** The solid core the rings radiate from — the voice itself. */
    val ringCore = Offset(54f, 54f)
    const val RING_CORE_RADIUS = 3.6f

    val ringWidths = listOf(3.8f, 3.2f, 2.6f)

    fun triangle(points: List<Offset>, scale: Float): Path = Path().apply {
        moveTo(points[0].x * scale, points[0].y * scale)
        lineTo(points[1].x * scale, points[1].y * scale)
        lineTo(points[2].x * scale, points[2].y * scale)
        close()
    }
}
