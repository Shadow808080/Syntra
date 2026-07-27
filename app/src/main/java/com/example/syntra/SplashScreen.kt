package com.example.syntra

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.net.SplashSound
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.delay

/**
 * The launch sequence: the logo assembles itself, one piece at a time.
 *
 * Three parts arrive in order and lock into place — the chat bowl swings in and the
 * S's top is drawn, the shorts triangle drops into the waist and joins it, the rooms
 * bowl sweeps in and completes the letter. Each landing plays one note of an A-major
 * triad, so by the end the chord and the logo finish together.
 *
 * There is no badge and no background shape. The pieces are the whole show — a block
 * sliding around behind them only competed with the thing worth watching.
 *
 * Every piece animates along the SAME geometry the launcher icon uses
 * ([SyntraMark]), so what assembles here is exactly what sits on the home screen.
 *
 * The whole sequence is ~2.1s and [onDone] fires regardless of what the app is doing,
 * so this can never be the reason someone waits.
 */
@Composable
fun SyntraSplash(onDone: () -> Unit) {
    // One progress value per piece: 0 = off-stage, 1 = locked in.
    val chat = remember { Animatable(0f) }
    val shorts = remember { Animatable(0f) }
    val rooms = remember { Animatable(0f) }
    // The stack collapsing into the single mark.
    val merge = remember { Animatable(0f) }
    // The finished mark settling, plus the wordmark.
    val settle = remember { Animatable(0f) }
    // A free-running clock for the aurora. Independent of the assembly so the light
    // keeps moving even while a piece is mid-flight.
    val sky = rememberInfiniteTransition(label = "aurora")
    val drift by sky.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "aurora-drift",
    )

    // A spring with a little overshoot: each piece arrives with weight and snaps home,
    // which is what makes it read as locking rather than sliding.
    val lock = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    // Pieces that slide into place use a plain eased tween — a bouncy spring on every
    // one of them made the sequence feel wobbly rather than assembled.
    val glide = tween<Float>(durationMillis = 520, easing = FastOutSlowInEasing)

    LaunchedEffect(Unit) {
        delay(140)
        // Back to front: waves, then play, then the bubble on top. Each note of the
        // triad lands with its own piece.
        SplashSound.play(SplashSound.Piece.ROOMS)
        rooms.animateTo(1f, glide)

        SplashSound.play(SplashSound.Piece.SHORTS)
        shorts.animateTo(1f, glide)

        SplashSound.play(SplashSound.Piece.CHAT)
        chat.animateTo(1f, lock)

        // Hold the fanned deck for a beat so it reads as three things before it
        // becomes one. Without the pause the merge looks like a glitch.
        delay(260)
        SplashSound.playComplete()
        merge.animateTo(1f, tween(660, easing = FastOutSlowInEasing))
        settle.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(320)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B12)),
        contentAlignment = Alignment.Center,
    ) {
        // Aurora — drifting sheets of blue light behind the mark. It brightens as the
        // pieces land, so the screen itself reacts to the assembly.
        Canvas(Modifier.fillMaxSize()) {
            drawEnergyThreads(drift, 0.25f + 0.75f * ((chat.value + shorts.value + rooms.value) / 3f))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    // The assembled letter breathes outward once, as if it settled
                    // under its own weight.
                    .graphicsLayer {
                        val s = 1f + 0.05f * settle.value
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawAssembly(chat.value, shorts.value, rooms.value, merge.value, settle.value)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Syntra",
                color = Color.White.copy(alpha = settle.value),
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Obrolan · Shorts · Rooms",
                color = NexusTextSecondary.copy(alpha = settle.value * 0.9f),
                fontSize = 12.sp,
            )
        }
    }
}

/** Mixes [c] toward white by [f] — the splash's tints are all "accent, lightened". */
private fun lighten(c: Color, f: Float) = Color(
    red = c.red + (1f - c.red) * f,
    green = c.green + (1f - c.green) * f,
    blue = c.blue + (1f - c.blue) * f,
)

/** Mixes [c] toward black by keeping [f] of it — for the film strip's dark details. */
private fun darken(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f)

/**
 * The three pieces' tints — GETTERS over the theme accent, not fixed blues.
 *
 * The splash was hardcoded to the blue family, so it opened blue over a Forest or
 * Sunset theme and the very first screen contradicted the user's choice. Deriving
 * from [NexusAccent] (Compose state that AppTheme rewrites) keeps the same structure —
 * white lead, two lightened supports — in whatever colour the user picked.
 */
private val markColours: List<Color>
    get() = listOf(
        Color.White,                  // chat — always the brightest piece
        lighten(NexusAccent, 0.72f),  // shorts
        lighten(NexusAccent, 0.52f),  // rooms
    )

/** The bubble's three dots: dark, base, soft — the accent family in miniature. */
private val dotColours: List<Color>
    get() = listOf(darken(NexusAccent, 0.72f), NexusAccent, NexusAccentSoft)

/** The film strip's sprockets and play window — near-black, tinted by the accent. */
private val filmInk: Color
    get() = darken(NexusAccent, 0.24f)

/**
 * Draws the three pieces at their current assembly progress.
 *
 * Each piece does three things at once as its progress runs 0..1: it travels in from
 * its own direction, it un-rotates to upright, and — for the two arcs — it *draws
 * itself* along its true path. The path reveal is what sells the assembly: the stroke
 * grows out of the previous piece's end, so the letter is being built, not stacked.
 */
/**
 * The three products arrive as a fanned deck, then collapse into the one mark that
 * lives on the home screen.
 *
 * WHY A DECK. Earlier versions dropped the three icons into a single cluster, and
 * whatever the spacing they always fought each other — three glyphs of similar weight
 * competing for one centre. Stacking them like cards gives each its own plane, so the
 * eye reads depth instead of collision. Each sits at a slight angle, offset along a
 * diagonal, the way a hand of cards actually falls.
 *
 * WHY IT MERGES. The launcher icon is a single bubble. Ending on that shape explains
 * where it came from — three products folding into one front door — instead of the
 * splash and the icon simply being two different pictures of the same app.
 *
 * Every icon is drawn from its own centre so the fan is even: without recentring, the
 * waves (which sit far left in the shared viewport) would fan around a point outside
 * themselves and the stack would splay.
 */
private fun DrawScope.drawAssembly(
    chat: Float,
    shorts: Float,
    rooms: Float,
    merge: Float,
    settle: Float,
) {
    val u = size.minDimension / 96f
    val mid = Offset(size.width / 2f, size.height / 2f)

    // Each card's resting place in the fan, and where it goes when the deck collapses.
    // As `merge` runs, offset and angle both fall to zero and everything but the
    // bubble fades — the two non-chat cards slide *under* it rather than vanishing.
    fun card(progress: Float, fanX: Float, fanY: Float, angle: Float, block: DrawScope.() -> Unit) {
        val t = progress.coerceIn(0f, 1f)
        if (t <= 0f) return
        val m = merge.coerceIn(0f, 1f)
        val entry = 1f - t
        // Cards fly in along the fan's own diagonal, so arriving looks like being
        // dealt rather than dropped.
        val x = mid.x + (fanX * u) * (1f - m) - 150f * entry
        val y = mid.y + (fanY * u) * (1f - m) - 60f * entry
        translate(left = x - mid.x, top = y - mid.y) {
            rotate(degrees = angle * (1f - m) + 18f * entry, pivot = mid) {
                scale(0.86f + 0.14f * t - 0.10f * m, pivot = mid) {
                    block()
                }
            }
        }
    }

    /** Draws [path] centred on the canvas, whatever its position in the viewport. */
    fun DrawScope.centred(path: Path, cx: Float, cy: Float, block: DrawScope.(Path) -> Unit) {
        translate(left = mid.x - cx * u, top = mid.y - cy * u) { block(path) }
    }

    val fade = 1f - merge.coerceIn(0f, 1f)

    // ROOMS — back of the deck. Rings radiate outward from a core, so each one
    // arrives a beat after the one inside it: sound leaving a point in every
    // direction, rather than a signal aimed somewhere.
    card(rooms, fanX = -19f, fanY = 13f, angle = -13f) {
        centred(Path(), 54f, 54f) {
            drawCircle(
                color = markColours[2].copy(alpha = rooms * fade),
                radius = SyntraMark.RING_CORE_RADIUS * u,
                center = SyntraMark.ringCore * u,
            )
        }
        repeat(3) { i ->
            val k = ((rooms - i * 0.16f) / 0.6f).coerceIn(0f, 1f)
            if (k > 0f) {
                centred(SyntraMark.ring(i, u), 54f, 54f) { p ->
                    scale(0.55f + 0.45f * k, pivot = mid) {
                        drawPath(
                            p,
                            markColours[2].copy(alpha = (1f - i * 0.28f) * k * fade),
                            style = Stroke(SyntraMark.ringWidths[i] * u),
                        )
                    }
                }
            }
        }
    }

    // SHORTS — middle of the deck: a film strip with a play window.
    card(shorts, fanX = 0f, fanY = 0f, angle = 0f) {
        centred(SyntraMark.filmBody(u), 58f, 68f) { p ->
            drawPath(p, markColours[1].copy(alpha = shorts * fade))
        }
        centred(Path(), 58f, 68f) {
            SyntraMark.sprockets.forEach { hole ->
                drawCircle(
                    color = filmInk.copy(alpha = shorts * fade),
                    radius = SyntraMark.SPROCKET * u,
                    center = hole * u,
                )
            }
            if (shorts > 0.7f) {
                val k = ((shorts - 0.7f) / 0.3f).coerceIn(0f, 1f)
                drawPath(
                    SyntraMark.triangle(SyntraMark.filmPlay, u),
                    filmInk.copy(alpha = k * fade),
                )
            }
        }
    }

    // CHAT — front of the deck, and the shape everything resolves into. It alone
    // survives the merge, growing to the size the launcher icon uses.
    card(chat, fanX = 19f, fanY = -13f, angle = 13f) {
        val grow = 1f + 0.34f * merge.coerceIn(0f, 1f)
        scale(grow, pivot = mid) {
            centred(SyntraMark.bubble(u), 54f, 53.5f) { p ->
                drawPath(p, markColours[0].copy(alpha = chat))
            }
            if (chat > 0.72f) {
                val k = ((chat - 0.72f) / 0.28f).coerceIn(0f, 1f)
                centred(Path(), 54f, 53.5f) {
                    SyntraMark.dots.forEachIndexed { i, dot ->
                        drawCircle(
                            color = dotColours[i].copy(alpha = k),
                            radius = SyntraMark.DOT_RADIUS * u * k,
                            center = dot * u,
                        )
                    }
                }
            }
        }
    }
}


/**
 * Threads of energy running left to right behind the mark.
 *
 * Not a background wash — filaments. Each is a sine wave crossing the full width with
 * a bright head travelling along it, and because their wavelengths and speeds are
 * mutually irrational the pattern never visibly repeats. [energy] rises as the logo
 * assembles, so the threads brighten with it rather than looping indifferently.
 *
 * Drawn as short segments rather than one path, so the head can be brighter than the
 * tail — which is what makes each thread read as travelling in a direction.
 */
private fun DrawScope.drawEnergyThreads(phase: Float, energy: Float) {
    val w = size.width
    val h = size.height
    val threads = listOf(
        // Thread tints follow the theme accent too — the filaments were the most
        // visibly blue thing on a non-blue theme.
        Thread(0.34f, 0.055f, 1.6f, 1.00f, NexusAccentSoft, 2.4f),
        Thread(0.47f, 0.085f, 1.1f, 1.45f, Color(0xFFFFFFFF), 1.8f),
        Thread(0.58f, 0.045f, 2.1f, 0.72f, NexusAccent, 3.0f),
        Thread(0.66f, 0.070f, 1.35f, 1.18f, lighten(NexusAccent, 0.55f), 2.0f),
    )
    val steps = 44
    val tau = 2f * Math.PI.toFloat()
    threads.forEach { th ->
        val baseY = h * th.y
        val amp = h * th.amp
        val head = ((phase * th.speed) % tau) / tau
        for (i in 0 until steps) {
            val f0 = i / steps.toFloat()
            val f1 = (i + 1) / steps.toFloat()
            fun yAt(f: Float) = baseY + amp * kotlin.math.sin(f * th.wave * tau + phase * th.speed)
            var d = f0 - head
            if (d < 0f) d += 1f
            val glow = (1f - d).let { it * it * it }
            drawLine(
                color = th.colour.copy(alpha = (0.08f + 0.7f * glow) * energy * 0.6f),
                start = Offset(w * f0, yAt(f0)),
                end = Offset(w * f1, yAt(f1)),
                strokeWidth = th.weight * (0.6f + 0.9f * glow),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** One thread's parameters. */
private data class Thread(
    val y: Float,
    val amp: Float,
    val wave: Float,
    val speed: Float,
    val colour: Color,
    val weight: Float,
)

/** The mark's optical centre within the 108 viewport — not the box centre. */
private val MARK_CENTRE = Offset(56f, 51f)

/**
 * A stroke drawn three times: a wide faint bloom, a mid halo, then the solid line.
 *
 * Compose has no blur on a stroke, so the glow is faked by stacking progressively
 * narrower, brighter passes. It costs three draw calls and is what turns a flat white
 * line into something that looks lit from within.
 */
private fun DrawScope.drawGlowStroke(
    path: Path,
    progress: Float,
    color: Color,
    width: Float,
    settle: Float,
) {
    val measure = PathMeasure().apply { setPath(path, false) }
    val shown = Path()
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), shown, true)
    val bloom = 0.35f + 0.65f * settle
    drawPath(shown, color.copy(alpha = 0.06f * bloom), style = Stroke(width * 2.2f, cap = StrokeCap.Round))
    drawPath(shown, color.copy(alpha = 0.13f * bloom), style = Stroke(width * 1.5f, cap = StrokeCap.Round))
    drawPath(shown, color, style = Stroke(width, cap = StrokeCap.Round))
}

/** Draws only the leading [progress] of [path], so a stroke grows instead of fading. */
private fun DrawScope.drawRevealed(path: Path, progress: Float, color: Color, width: Float) {
    val measure = PathMeasure().apply { setPath(path, false) }
    val shown = Path()
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), shown, true)
    drawPath(path = shown, color = color, style = Stroke(width = width, cap = StrokeCap.Round))
}
