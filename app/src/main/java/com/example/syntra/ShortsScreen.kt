package com.example.syntra

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun ShortsScreen(
    modifier: Modifier = Modifier,
    selectedTab: NexusTab = NexusTab.SHORTS,
    onTabSelected: (NexusTab) -> Unit = {},
) {
    val context = LocalContext.current
    // Shorts have no backend yet (`/api/v1/reels|shorts|videos` → not found), so a
    // picked video cannot be published. Say that instead of failing silently.
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            Toast.makeText(
                context,
                "Video terpilih, tapi server belum punya endpoint Shorts untuk menerbitkannya.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        // Video surface (placeholder standing in for the playing short)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NexusBackground),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ShortsHeader(onPost = {
                pickVideo.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            })
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                ShortsCaption(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp, end = 12.dp, bottom = 20.dp),
                )
                ShortsActions(
                    modifier = Modifier.padding(end = 16.dp, bottom = 20.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun ShortsHeader(onPost: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(SyntraHeaderPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyntraTitle()
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = NexusTextPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        // Post a new short.
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    Brush.linearGradient(listOf(NexusAccentSoft, NexusAccent)),
                    CircleShape,
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onPost,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Posting short",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Caption (author + description + audio)
// ---------------------------------------------------------------------------

@Composable
private fun ShortsCaption(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "@quantum_flow",
                color = NexusTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .background(
                        Color(0xFF3B68F5).copy(alpha = 0.18f),
                        RoundedCornerShape(50),
                    )
                    .border(1.dp, NexusAccentSoft.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Follow",
                    color = NexusAccentSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Exploring the new neural engine architecture in Syntra v4.2. The...",
            color = NexusTextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = NexusAccentSoft,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Original Audio – Syntra Soundscape",
                color = NexusTextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Right-side action rail
// ---------------------------------------------------------------------------

@Composable
private fun ShortsActions(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        ActionButton(Icons.Filled.Favorite, "24.5k")
        ActionButton(Icons.Outlined.ModeComment, "842")
        ActionButton(Icons.Filled.Share, "Share")
        // Audio thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1B2A6B), Color(0xFF3B68F5))),
                    RoundedCornerShape(14.dp),
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = NexusTextPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = NexusTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// (the avatar helper was replaced by the post button in the header)

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF090910, widthDp = 360, heightDp = 780)
@Composable
private fun ShortsScreenPreview() {
    SyntraTheme {
        ShortsScreen()
    }
}
