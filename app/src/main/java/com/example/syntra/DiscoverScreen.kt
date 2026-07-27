package com.example.syntra

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.example.syntra.net.BlockStore
import com.example.syntra.net.NetUser
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How many rows appear at once, and how many more each scroll-to-bottom reveals. */
private const val DISCOVER_PAGE = 10

/**
 * Ceiling for the *suggestions* list (empty query). Suggestions are a browse surface —
 * nobody scrolls a thousand strangers — so it stops. A real SEARCH has no ceiling:
 * if you typed a name you are looking for someone specific and cutting the list off
 * could hide them.
 */
private const val DISCOVER_SUGGESTION_MAX = 100

/**
 * Find-people screen. Empty query shows suggestions (popular users) so it's
 * never blank. Tapping a result opens their profile; the follow button follows
 * inline. This is how a fresh account builds a following and starts seeing
 * other people's stories.
 *
 * Rows appear [DISCOVER_PAGE] at a time and extend as you scroll. The paging is done
 * on the client because the search endpoint takes no limit/offset — so this bounds
 * what is COMPOSED, not what is fetched.
 */
@Composable
fun DiscoverScreen(onClose: () -> Unit, onOpenProfile: (String) -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val results = remember { mutableStateListOf<NetUser>() }
    // How many of [results] are currently on screen. Rendering every user the server
    // returned meant building hundreds of rows (each with its own avatar request) for
    // a list nobody scrolls to the end of — slow to appear and wasteful on data.
    var shown by remember { mutableIntStateOf(DISCOVER_PAGE) }
    val listState = rememberLazyListState()

    // Debounced search: re-query 300ms after the last keystroke.
    LaunchedEffect(query) {
        loading = true
        delay(300)
        try {
            val list = SyntraClient.searchUsers(query.trim())
            results.clear()
            // Blocked people are removed from search entirely — being able to look
            // someone up after blocking them defeats the point of blocking.
            val allowed = list.filterNot {
                BlockStore.isBlocked(context, username = it.username, userId = it.id)
            }
            // Suggestions are capped; typed searches are not.
            results.addAll(if (query.isBlank()) allowed.take(DISCOVER_SUGGESTION_MAX) else allowed)
            shown = DISCOVER_PAGE // a new query always starts from the first page
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
        }
        loading = false
    }

    // Reveal the next 10 when the last visible row is near the end of what's rendered.
    LaunchedEffect(listState, results.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }.collect { lastVisible ->
            if (lastVisible >= shown - 3 && shown < results.size) {
                shown = (shown + DISCOVER_PAGE).coerceAtMost(results.size)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(NexusBackground)) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(NexusSurface, RoundedCornerShape(22.dp))
                    .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = NexusTextSecondary, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Cari orang…", color = NexusTextSecondary, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = NexusTextPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(NexusAccentSoft),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (query.isBlank()) {
            Text(
                "Saran untukmu",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
            )
        }

        if (loading && results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tidak ada hasil", color = NexusTextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                val page = results.take(shown)
                items(page, key = { it.id }) { user ->
                    UserRow(
                        user = user,
                        onOpen = { if (user.username.isNotBlank()) onOpenProfile(user.username) },
                        onFollow = { scope.launch { runCatching { SyntraClient.follow(user.username) } } },
                        onUnfollow = { scope.launch { runCatching { SyntraClient.unfollow(user.username) } } },
                    )
                }
                // A quiet footer while the next batch appears, so reaching the bottom
                // never looks like the end of the list when it isn't.
                if (shown < results.size) {
                    item(key = "more") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = NexusAccentSoft,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: NetUser, onOpen: () -> Unit, onFollow: () -> Unit, onUnfollow: () -> Unit) {
    var following by remember(user.id) { mutableStateOf(user.followStatus == "accepted" || user.followStatus == "pending") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen,
            )
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)))),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = user.avatarMediaId
            if (!avatar.isNullOrBlank() && avatar.startsWith("http")) {
                AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Text(
                    (user.displayName.firstOrNull() ?: user.username.firstOrNull() ?: 'U').uppercase(),
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.displayName.ifBlank { user.username },
                color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Text("@${user.username}", color = NexusTextSecondary, fontSize = 12.sp)
        }
        if (!user.isSelf) {
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (following) Color.Transparent else NexusAccent)
                    .border(
                        1.dp,
                        if (following) NexusStroke else Color.Transparent,
                        RoundedCornerShape(17.dp),
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        // Toggle: following → tap unfollows; not following → follows.
                        if (following) { following = false; onUnfollow() }
                        else { following = true; onFollow() }
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (following) "Mengikuti" else "Ikuti",
                    color = if (following) NexusTextSecondary else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
