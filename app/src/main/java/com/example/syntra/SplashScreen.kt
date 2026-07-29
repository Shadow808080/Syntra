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
import androidx.compose.ui.graphics.luminance
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusTextPrimary
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
        // LinearEasing + Restart, and the target is exactly one full turn — every wave
        // is driven by sin/cos of this, so wrapping 2π back to 0 lands on the identical
        // frame. Anything else (an eased curve, or a target that is not a whole period)
        // makes the loop visibly hitch each time it restarts.
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
        label = "aurora-drift",
    )
    // A second, slower clock at a deliberately non-harmonic ratio. With one clock the
    // whole field repeats every cycle and the eye picks the pattern up quickly; two
    // periods that do not divide each other take minutes to visibly repeat, so the
    // light reads as continuous rather than looped.
    val swell by sky.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Restart),
        label = "aurora-swell",
    )
    // The one leaf that flies out of the distance at the mark. Its own clock, running
    // 0 → 1 on a period that shares no factor with the other two, so its pass never
    // syncs up with the drift behind it.
    val heroLeaf by sky.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7300, easing = LinearEasing), RepeatMode.Restart),
        label = "hero-leaf",
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
            // Follows the theme. A fixed near-black meant the very first screen of a
            // light-theme install was dark, then flipped white the moment the app
            // loaded — the one transition guaranteed to look like a fault.
            .background(NexusBackground),
        contentAlignment = Alignment.Center,
    ) {
        // A slow fall of leaves behind the mark, with one tumbling out of the distance
        // to nearly cover it. Brightens as the pieces land, so the screen itself reacts
        // to the assembly.
        Canvas(Modifier.fillMaxSize()) {
            // Energy no longer bottoms out once the assembly finishes: the splash now
            // HOLDS until the app is ready, so the field has to stay alive on its own
            // instead of freezing on the last frame of the build-up. It breathes
            // between 0.55 and 1.0 forever, lifted further while pieces are landing.
            val assembly = (chat.value + shorts.value + rooms.value) / 3f
            val breath = 0.78f + 0.22f * kotlin.math.sin(swell)
            drawLeafDrift(drift, swell, heroLeaf, breath * (0.55f + 0.45f * assembly))
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
                color = NexusTextPrimary.copy(alpha = settle.value),
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/**
 * A leaf's colour for a given depth — 0 is furthest away, 1 is right at the lens.
 *
 * Depth is expressed as VALUE, never hue: distant leaves sink toward the backdrop and
 * near ones come forward, all of them the theme's own colour. That is also what sells
 * the depth — real distance desaturates toward the background, it doesn't change what
 * colour a thing is.
 */
private fun leafTint(depth: Float): Color = if (isLightTheme()) {
    // On a light page a far leaf must get DARKER to recede, not paler — a pale leaf on
    // white is simply invisible, which is how the old filaments disappeared entirely.
    darken(NexusAccent, 0.45f + 0.4f * depth)
} else {
    lighten(NexusAccent, 0.10f + 0.5f * depth)
}

/** True when the active palette is light, so the mark can invert against it. */
private fun isLightTheme(): Boolean = NexusBackground.luminance() > 0.5f

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
        // The lead piece is the highest-CONTRAST colour against the backdrop, not
    // literally white: on a light theme a white bubble on a white page is invisible.
    if (isLightTheme()) darken(NexusAccent, 0.55f) else Color.White,
        if (isLightTheme()) darken(NexusAccent, 0.80f) else lighten(NexusAccent, 0.72f),
        if (isLightTheme()) NexusAccent else lighten(NexusAccent, 0.52f),
    )

/** The bubble's three dots: dark, base, soft — the accent family in miniature. */
/**
 * The bubble's three dots — INVERTED with the theme, because they sit on the bubble,
 * not on the page.
 *
 * On the dark theme the bubble is white, so the dots are the dark accent family. On the
 * light theme the bubble itself became dark (a white bubble on a white page is
 * invisible), which left dark dots on a dark bubble: the mark turned into a mud-blue
 * blob with no dots readable at all.
 */
private val dotColours: List<Color>
    get() = if (isLightTheme()) {
        // The bubble is now a gradient running dark→pale left-to-right, and the dots sit
        // across it in that same order. So they have to run the OTHER way: light on the
        // dark end, dark on the pale end. Keeping all three light made the last one
        // disappear into the tail it was sitting on.
        listOf(Color.White, lighten(NexusAccentSoft, 0.72f), darken(NexusAccent, 0.38f))
    } else {
        listOf(darken(NexusAccent, 0.72f), NexusAccent, NexusAccentSoft)
    }

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
                // A GRADIENT, not a flat fill — deep at the top-left, washing out to
                // near-white at the tail. Flat navy read as a solid blob; the pale tip
                // is what gives the mark its light and keeps it from looking painted on.
                //
                // On the dark theme the bubble is white and needs no shading, so this
                // only applies where the mark had to darken to stay visible.
                if (isLightTheme()) {
                    drawPath(
                        p,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                darken(NexusAccent, 0.42f),
                                NexusAccent,
                                lighten(NexusAccentSoft, 0.72f),
                            ),
                            start = Offset(18f * u, 18f * u),
                            end = Offset(92f * u, 92f * u),
                        ),
                        alpha = chat,
                    )
                } else {
                    drawPath(p, markColours[0].copy(alpha = chat))
                }
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
private fun DrawScope.drawLeafDrift(phase: Float, phase2: Float, hero: Float, energy: Float) {
    val w = size.width
    val h = size.height
    val tau = 2f * Math.PI.toFloat()
    val light = isLightTheme()
    // Light pages need noticeably more ink for the same perceived weight — the thing
    // the old filaments got wrong badly enough to be invisible on a white theme.
    val ink = if (light) 1.5f else 1f

    // --- the drifting stack --------------------------------------------------
    // Fourteen leaves seeded on the golden ratio. Anything modulo-based lines them up
    // into rows the eye finds instantly; φ is the classic fix, and it also means no
    // two leaves share a phase, so the field never pulses as one.
    val count = 14
    for (i in 0 until count) {
        val g = (i * 0.6180339f) % 1f
        val g2 = (i * 0.3819660f) % 1f
        // Depth: mostly far, a few mid. The near plane is reserved for the hero.
        val depth = 0.06f + 0.5f * g2

        // Each leaf falls on its own slow line and wraps. `phase` is one full turn, so
        // the wrap lands on the identical frame and never hitches.
        val fall = ((phase / tau) * (0.10f + 0.16f * depth) + g) % 1f
        // Sideways it sways rather than travels — leaves in still air, not wind.
        val sway = kotlin.math.sin(phase2 * (0.6f + g) + g * tau) * w * (0.05f + 0.07f * depth)
        val x = w * (0.08f + 0.84f * g) + sway
        // Drift downward through 1.25 screens so a leaf is fully gone before it wraps.
        val y = h * (fall * 1.25f - 0.12f)

        // Tumbling: a slow spin plus a wobble, so it turns like a falling leaf rather
        // than rotating like a wheel.
        val spin = phase * (0.35f + 0.5f * g) + g * tau +
            0.55f * kotlin.math.sin(phase2 * 1.3f + g * tau)
        val len = h * (0.030f + 0.075f * depth)
        // Far leaves are faint; the whole field lifts with `energy` as the mark builds.
        val alpha = (0.10f + 0.30f * depth) * energy * ink

        drawLeaf(Offset(x, y), len, spin, leafTint(depth), alpha.coerceIn(0f, 0.85f))
    }

    // --- the one that comes at you -------------------------------------------
    // [hero] runs 0 → 1 forever. It reads as a single leaf tumbling out of the far
    // distance, swelling until it nearly covers the mark, then sweeping past the lens.
    // Almost all of the visual interest in this screen is this one object, which is
    // why the rest of the field is kept deliberately quiet.
    //
    // The depth curve is cubed: distance compresses hard at the far end and rushes at
    // the near end, which is how approach actually looks. A linear ramp reads as a
    // sticker being scaled up.
    val approach = hero * hero * hero
    // Fade in from nothing, hold, then fade out as it passes the lens — so it never
    // pops into or out of existence.
    val heroAlpha = when {
        hero < 0.12f -> hero / 0.12f
        hero > 0.82f -> ((1f - hero) / 0.18f).coerceIn(0f, 1f)
        else -> 1f
    }
    if (heroAlpha > 0.01f) {
        // It arcs across rather than flying straight at the camera — a leaf blown past
        // you, not a projectile aimed at you.
        val hx = w * (0.16f + 0.62f * hero) + w * 0.10f * kotlin.math.sin(hero * tau * 0.75f)
        val hy = h * (0.30f + 0.34f * approach) + h * 0.05f * kotlin.math.sin(hero * tau * 1.4f)
        // Grows from a speck to roughly the width of the 150dp mark.
        val hlen = h * (0.012f + 0.30f * approach)
        // Spins up as it nears — the tumble accelerates with the approach.
        val hspin = hero * tau * 1.6f + approach * 2.4f
        drawLeaf(
            centre = Offset(hx, hy),
            length = hlen,
            angle = hspin,
            colour = leafTint(0.85f),
            alpha = (0.34f * heroAlpha * energy * ink).coerceIn(0f, 0.72f),
            rib = true,
        )
    }
}

/**
 * One leaf: an almond of two mirrored cubics, optionally with a midrib.
 *
 * Kept translucent and filled rather than outlined — a stack of outlines turns into a
 * thicket of lines, while soft filled shapes overlap into depth.
 */
private fun DrawScope.drawLeaf(
    centre: Offset,
    length: Float,
    angle: Float,
    colour: Color,
    alpha: Float,
    rib: Boolean = false,
) {
    if (length <= 0.5f || alpha <= 0.005f) return
    val half = length * 0.5f
    val wide = length * 0.30f
    val path = Path().apply {
        moveTo(0f, -half)
        cubicTo(wide, -half * 0.45f, wide, half * 0.40f, 0f, half)
        cubicTo(-wide, half * 0.40f, -wide, -half * 0.45f, 0f, -half)
        close()
    }
    translate(centre.x, centre.y) {
        rotate(angle * 180f / Math.PI.toFloat(), Offset.Zero) {
            drawPath(path, colour.copy(alpha = alpha))
            if (rib) {
                drawLine(
                    color = colour.copy(alpha = (alpha * 1.5f).coerceAtMost(0.9f)),
                    start = Offset(0f, -half * 0.86f),
                    end = Offset(0f, half * 0.86f),
                    strokeWidth = (length * 0.016f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

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
