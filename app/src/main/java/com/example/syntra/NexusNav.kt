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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.syntra.ui.theme.NexusTextSecondary

enum class NexusTab { CHAT, MUSIC, SHORTS, ROOMS, CALLS }

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
                // Slim, TikTok-style bar: tighter vertical padding keeps it compact.
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Each tab takes an equal 1/5 slice so they span edge to edge — no
            // empty gutters on the sides. Music sits next to Chat, which pushes
            // Shorts to the visual centre.
            NavItem(NexusTab.CHAT, Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble, "Chat", selected, onSelect, Modifier.weight(1f))
            NavItem(NexusTab.MUSIC, Icons.Outlined.MusicNote, Icons.Filled.MusicNote, "Musik", selected, onSelect, Modifier.weight(1f))
            NavItem(NexusTab.SHORTS, Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle, "Shorts", selected, onSelect, Modifier.weight(1f))
            NavItem(NexusTab.ROOMS, Icons.Outlined.Mic, Icons.Filled.Mic, "Rooms", selected, onSelect, Modifier.weight(1f))
            NavItem(NexusTab.CALLS, Icons.Outlined.Call, Icons.Filled.Call, "Calls", selected, onSelect, Modifier.weight(1f))
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
) {
    val isSelected = tab == selected

    // Colour and the pill width animate on select — transform/opacity-class only,
    // no layout thrash, no bounce. Instant enough to feel snappy (180ms).
    val color by animateColorAsState(
        targetValue = if (isSelected) NexusAccentSoft else NexusTextSecondary,
        animationSpec = tween(180),
        label = "nav-color",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (isSelected) 52.dp else 34.dp,
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
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
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
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
