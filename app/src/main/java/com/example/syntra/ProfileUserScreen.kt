package com.example.syntra

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetUser
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.launch

/**
 * A user's public profile.
 *
 * Needs a username to fetch the full profile (`GET /users/{username}`). When the
 * caller only knows a display name (chats from the list still don't carry the
 * counterpart's username), it shows what it has and says the rest is unavailable.
 */
@Composable
fun ProfileUserScreen(
    username: String?,
    fallbackName: String,
    fallbackGradient: List<Color>,
    onBack: () -> Unit,
    onMessage: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var user by remember(username) { mutableStateOf<NetUser?>(null) }
    var loading by remember(username) { mutableStateOf(username != null && ApiConfig.ENABLED) }
    var followStatus by remember(username) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(username) {
        if (username != null && ApiConfig.ENABLED) {
            runCatching { SyntraClient.getUser(username) }
                .onSuccess { user = it; followStatus = it.followStatus }
            loading = false
        }
    }

    val name = user?.displayName?.ifBlank { user?.username } ?: fallbackName
    val handle = user?.username ?: username
    val avatarUrl = user?.avatarMediaId?.takeIf { it.startsWith("http") }

    fun toggleFollow() {
        val u = username ?: return
        busy = true
        scope.launch {
            val following = followStatus == "accepted" || followStatus == "pending"
            runCatching {
                if (following) SyntraClient.unfollow(u) else SyntraClient.follow(u)
            }.onSuccess {
                // Re-read for the authoritative status.
                runCatching { SyntraClient.getUser(u) }.onSuccess { followStatus = it.followStatus; user = it }
            }.onFailure {
                Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("Profil", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        // Avatar
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(fallbackGradient)),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = name,
            color = NexusTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        if (handle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "@$handle",
                color = NexusTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Follower / following counts
        user?.let { u ->
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CountCell(u.followerCount, "Pengikut")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .size(width = 1.dp, height = 34.dp)
                        .background(NexusStroke),
                )
                CountCell(u.followingCount, "Mengikuti")
            }
        }

        Spacer(Modifier.height(28.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusAccent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        } else if (username != null && user?.isSelf != true) {
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val followLabel = when (followStatus) {
                    "accepted" -> "Mengikuti"
                    "pending" -> "Diminta"
                    else -> "Ikuti"
                }
                val following = followStatus == "accepted" || followStatus == "pending"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .background(
                            brush = if (following) {
                                Brush.horizontalGradient(listOf(NexusSurface, NexusSurface))
                            } else {
                                Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent))
                            },
                            shape = RoundedCornerShape(23.dp),
                        )
                        .then(
                            if (following) Modifier.border(1.dp, NexusStroke, RoundedCornerShape(23.dp))
                            else Modifier,
                        )
                        .clickable(
                            enabled = !busy,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { toggleFollow() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        followLabel,
                        color = if (following) NexusTextPrimary else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (onMessage != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .background(NexusSurface, RoundedCornerShape(23.dp))
                            .border(1.dp, NexusStroke, RoundedCornerShape(23.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onMessage,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.Message, null,
                                tint = NexusTextPrimary, modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Pesan", color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else if (username == null) {
            Text(
                text = "Info profil lengkap belum tersedia dari server untuk chat ini.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )
        }

        // Block, when we know who it is
        if (username != null && user?.isSelf != true) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        scope.launch { runCatching { SyntraClient.blockUser(username) } }
                        BlockStore.block(context, name)
                        Toast.makeText(context, "$name diblokir.", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Block, null, tint = Color(0xFFFF5D5D), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text("Blokir pengguna", color = Color(0xFFFF5D5D), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun CountCell(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = NexusTextSecondary, fontSize = 12.sp)
    }
}
