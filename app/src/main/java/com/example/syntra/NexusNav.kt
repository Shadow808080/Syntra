package com.example.syntra

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import com.example.syntra.ui.theme.NexusBackground
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary

enum class NexusTab { CHAT, MUSIC, SHORTS, ROOMS, CALLS }

/**
 * Whether the bottom navigation is currently shown. Scrolling content down hides
 * it (more room to read/watch); scrolling up brings it back. Screens toggle this
 * via [hideBottomBarOnScroll]; the host (MainActivity) reads it. Reset to true on
 * tab change so a tab never opens with the bar hidden.
 */
object BottomBarVisibility {
    var visible by androidx.compose.runtime.mutableStateOf(true)
}

/**
 * A nested-scroll connection that hides the bottom bar when the user scrolls the
 * content down and reveals it when they scroll up. A small threshold avoids the
 * bar flickering on tiny movements.
 */
@Composable
fun rememberHideBottomBarOnScroll(): androidx.compose.ui.input.nestedscroll.NestedScrollConnection =
    androidx.compose.runtime.remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                val dy = available.y
                if (dy < -4f) BottomBarVisibility.visible = false   // finger up → content scrolls down
                else if (dy > 4f) BottomBarVisibility.visible = true // finger down → content scrolls up
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

/**
 * Padding every top bar uses, so the app title lands on the same spot no matter
 * which tab you are on. Top is deliberately larger than bottom: it lifts the row
 * clear of the status bar without opening a gap above the content below it.
 */
val SyntraHeaderPadding = PaddingValues(
    start = 20.dp,
    end = 20.dp,
    top = 26.dp,
    bottom = 14.dp,
)

/**
 * Brand gradient shared by the wordmark and the featured Shorts tab.
 *
 * A GETTER, not a val. [NexusAccent] and [NexusAccentSoft] are Compose state that
 * AppTheme rewrites when the user picks a theme; a `val` would capture whatever the
 * colours happened to be at class-init and then never change, which is exactly why
 * the wordmark and the Shorts pill stayed blue no matter what theme was selected.
 * Reading the state on each access also makes anything that draws with it recompose.
 */
private val BrandGradient: Brush
    get() = Brush.horizontalGradient(listOf(NexusAccent, NexusAccentSoft, Color.White))

/**
 * The featured tab's fill: a diagonal run from the light accent through the base into
 * a deepened edge, so the pill has a light source instead of a single tint.
 */
private val FeaturedGradient: Brush
    get() = Brush.linearGradient(
        listOf(
            NexusAccentSoft,
            NexusAccent,
            Color(
                red = NexusAccent.red * 0.72f,
                green = NexusAccent.green * 0.72f,
                blue = NexusAccent.blue * 0.82f,
            ),
        ),
    )

/**
 * The glass: a bright sheen across the top half, a faint dark pool at the bottom.
 * The hard hairline border is gone — an outline flattens glass back into a button;
 * depth has to come from light, not from an edge.
 */
private val FeaturedSheen: Brush
    get() = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.38f),
        0.45f to Color.White.copy(alpha = 0.10f),
        0.5f to Color.Transparent,
        0.82f to Color.Black.copy(alpha = 0.04f),
        1.0f to Color.Black.copy(alpha = 0.14f),
    )

/** The app title, identical on every screen: wordmark with the mark perched on it. */
@Composable
fun SyntraTitle(modifier: Modifier = Modifier) {
    // The bubble PERCHES on the wordmark — tucked against the top-right of the "a",
    // tilted outward like a sticker — rather than sitting inline like a list icon.
    // Inline made it read as "icon, label"; perched it reads as one logotype.
    Box(modifier = modifier) {
        Text(
            text = "Syntra",
            style = TextStyle(brush = BrandGradient),
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Canvas(
            Modifier
                .align(Alignment.TopEnd)
                // Nudged up and past the last glyph so it overlaps the corner.
                .offset(x = 15.dp, y = (-7).dp)
                .size(17.dp)
                .graphicsLayer { rotationZ = 14f },
        ) {
            val u = size.minDimension / 66f
            translate(left = -21f * u, top = -22f * u) {
                // Same accent-to-white run as the wordmark, so the mark is the end of
                // the gradient rather than a separate solid shape stuck on it.
                drawPath(
                    SyntraMark.bubble(u),
                    brush = Brush.linearGradient(
                        colors = listOf(NexusAccentSoft, Color.White),
                        start = Offset(21f * u, 22f * u),
                        end = Offset(87f * u, 85f * u),
                    ),
                )
                SyntraMark.dots.forEach { dot ->
                    drawCircle(
                        color = NexusAccent,
                        radius = SyntraMark.DOT_RADIUS * u,
                        center = dot * u,
                    )
                }
            }
        }
    }
}

@Composable
fun NexusBottomBar(
    selected: NexusTab,
    onSelect: (NexusTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(NexusSurface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                // Slim, TikTok-style bar: minimal vertical padding keeps it compact.
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Each tab takes an equal 1/5 slice so they span edge to edge — no
            // empty gutters on the sides. Music sits next to Chat, which pushes
            // Shorts to the visual centre.
            NavItem(NexusTab.CHAT, Icons.Outlined.Forum, Icons.Rounded.Forum, "Chat", selected, onSelect, Modifier.weight(1f))
            NavItem(NexusTab.MUSIC, Icons.Outlined.Headphones, Icons.Rounded.Headphones, "Musik", selected, onSelect, Modifier.weight(1f))
            NavItem(NexusTab.SHORTS, Icons.Outlined.SmartDisplay, Icons.Rounded.SmartDisplay, "Shorts", selected, onSelect, Modifier.weight(1f), featured = true)
            NavItem(NexusTab.ROOMS, Icons.Outlined.Podcasts, Icons.Rounded.Podcasts, "Rooms", selected, onSelect, Modifier.weight(1f))
            NavItem(NexusTab.CALLS, Icons.Outlined.Call, Icons.Rounded.Call, "Calls", selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    tab: NexusTab,
    iconIdle: ImageVector,
    iconActive: ImageVector,
    label: String,
    selected: NexusTab,
    onSelect: (NexusTab) -> Unit,
    modifier: Modifier = Modifier,
    /** The centre tab (Shorts): rendered as a distinctive gradient button. */
    featured: Boolean = false,
) {
    val isSelected = tab == selected

    // Colour and the pill width animate on select — transform/opacity-class only,
    // no layout thrash, no bounce. Instant enough to feel snappy (180ms).
    val color by animateColorAsState(
        // Idle is a near-white (only lightly dimmed) so icons/labels read bright,
        // still a step below the accent-coloured selected tab. Theme-aware.
        targetValue = if (isSelected) NexusAccentSoft else NexusTextPrimary.copy(alpha = 0.9f),
        animationSpec = tween(180),
        label = "nav-color",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (isSelected) 48.dp else 34.dp,
        animationSpec = tween(220),
        label = "nav-pill",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onSelect(tab) }
            .padding(horizontal = 6.dp),
    ) {
        if (featured) {
            // The centre tab is the app's signature control, so it gets real depth
            // rather than a flat fill: a diagonal three-stop gradient, a translucent
            // highlight across the top half, and a hairline rim. That combination is
            // what reads as "glass" — a single-stop background looked printed on.
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(FeaturedGradient),
                contentAlignment = Alignment.Center,
            ) {
                // Top-half sheen. Sits above the gradient and below the glyph, which
                // is what makes the surface look lit from above rather than tinted.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(FeaturedSheen),
                )
                Icon(
                    imageVector = iconActive,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(23.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (isSelected) NexusAccent.copy(alpha = 0.16f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSelected) iconActive else iconIdle,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            color = color,
            fontSize = 9.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            // Strip the font's built-in vertical padding so the label sits right under
            // the icon (tighter gap) and the whole bar is shorter.
            style = TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeight = 10.sp,
            ),
        )
    }
}
