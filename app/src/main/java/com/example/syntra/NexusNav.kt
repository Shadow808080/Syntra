package com.example.syntra

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary

enum class NexusTab { CHAT, SHORTS, ROOMS, CALLS }

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

/** The app title, identical on every screen. */
@Composable
fun SyntraTitle(modifier: Modifier = Modifier) {
    Text(
        text = "Syntra",
        color = NexusTextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
fun NexusBottomBar(
    selected: NexusTab,
    onSelect: (NexusTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NexusBackground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(NexusTab.CHAT, Icons.Outlined.ChatBubble, "Chat", selected, onSelect)
        NavItem(NexusTab.SHORTS, Icons.Filled.PlayArrow, "Shorts", selected, onSelect)
        NavItem(NexusTab.ROOMS, Icons.Outlined.Mic, "Rooms", selected, onSelect)
        NavItem(NexusTab.CALLS, Icons.Outlined.Call, "Calls", selected, onSelect)
    }
}

@Composable
private fun NavItem(
    tab: NexusTab,
    icon: ImageVector,
    label: String,
    selected: NexusTab,
    onSelect: (NexusTab) -> Unit,
) {
    val isSelected = tab == selected
    val color = if (isSelected) NexusAccentSoft else NexusTextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onSelect(tab) }
            .padding(horizontal = 12.dp),
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(NexusAccent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
            }
        } else {
            Icon(icon, label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
