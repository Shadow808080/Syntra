package com.example.syntra

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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

/** Brand gradient shared by the wordmark and the logo hero on the auth screen. */
private val BrandGradient = Brush.horizontalGradient(listOf(Color(0xFFB79CFF), Color(0xFF6E8BFF)))

/** The app title, identical on every screen — a soft two-tone brand wordmark. */
@Composable
fun SyntraTitle(modifier: Modifier = Modifier) {
    Text(
        text = "Syntra",
        style = TextStyle(brush = BrandGradient),
        fontSize = 23.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier,
    )
}

@Composable
fun NexusBottomBar(
    selected: NexusTab,
    onSelect: (NexusTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(NexusSurface)) {
        // Hairline that lifts the bar off the content above it — depth without a shadow.
        Box(Modifier.fillMaxWidth().height(1.dp).background(NexusStroke))
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
            // The centre tab stands out: a filled brand-gradient button with a white
            // glyph, always accented so it reads as the app's signature action.
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
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
