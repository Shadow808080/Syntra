package com.example.syntra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.launch

/** Which list a [PeopleListScreen] is showing. */
enum class PeopleList { FOLLOWERS, FOLLOWING, REQUESTS }

/**
 * Followers, following, and pending follow requests — one screen, three sources.
 *
 * [PeopleList.REQUESTS] is the one that actually fixes something rather than adding
 * a view. A private account produced `pending` follows that nothing in the app could
 * ever resolve: the requester never became a follower, so they never saw a story, and
 * neither side had any way to find out why. The approve/reject endpoints have existed
 * the whole time with nothing calling them.
 */
@Composable
fun PeopleListScreen(
    kind: PeopleList,
    username: String? = null,
    onClose: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val people = remember { mutableStateListOf<NetUser>() }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Rows mid-decision, so a double tap can't fire approve twice.
    val deciding = remember { mutableStateListOf<String>() }

    suspend fun load() {
        if (!ApiConfig.ENABLED) { loading = false; return }
        runCatching {
            when (kind) {
                PeopleList.FOLLOWERS ->
                    if (username != null) SyntraClient.getFollowersOf(username) else SyntraClient.getFollowers()
                PeopleList.FOLLOWING -> SyntraClient.getFollowing()
                PeopleList.REQUESTS -> SyntraClient.getFollowRequests()
            }
        }
            .onSuccess { fresh -> people.clear(); people.addAll(fresh); failed = false }
            .onFailure { failed = people.isEmpty() }
        loading = false
    }

    LaunchedEffect(kind, username) { load() }
    androidx.activity.compose.BackHandler(onBack = onClose)

    fun decide(u: NetUser, approve: Boolean) {
        if (u.username in deciding) return
        deciding.add(u.username)
        scope.launch {
            runCatching {
                if (approve) SyntraClient.approveFollow(u.username)
                else SyntraClient.rejectFollow(u.username)
            }
                .onSuccess { people.removeAll { it.username == u.username } }
            deciding.remove(u.username)
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
                .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Kembali",
                    tint = NexusTextPrimary, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                when (kind) {
                    PeopleList.FOLLOWERS -> "Pengikut"
                    PeopleList.FOLLOWING -> "Mengikuti"
                    PeopleList.REQUESTS -> "Permintaan mengikuti"
                },
                color = NexusTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }

                failed -> PeopleEmpty("Gagal memuat.\nTarik ke bawah untuk mencoba lagi.")

                people.isEmpty() -> PeopleEmpty(
                    when (kind) {
                        PeopleList.FOLLOWERS -> "Belum ada pengikut."
                        PeopleList.FOLLOWING -> "Belum mengikuti siapa pun."
                        PeopleList.REQUESTS -> "Tidak ada permintaan menunggu."
                    },
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(people, key = { it.id }) { u ->
                        PersonRow(
                            user = u,
                            showDecision = kind == PeopleList.REQUESTS,
                            busy = u.username in deciding,
                            onApprove = { decide(u, true) },
                            onReject = { decide(u, false) },
                            onClick = { if (u.username.isNotBlank()) onOpenProfile(u.username) },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PeopleEmpty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = NexusTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PersonRow(
    user: NetUser,
    showDecision: Boolean,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val name = user.displayName.ifBlank { user.username }
        if (user.avatarMediaId != null && user.avatarMediaId.startsWith("http")) {
            AsyncImage(
                model = user.avatarMediaId,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(NexusSurfaceElevated),
            )
        } else {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(NexusSurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    color = NexusTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = NexusTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.username.isNotBlank()) {
                Text("@${user.username}", color = NexusTextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (showDecision) {
            Spacer(Modifier.width(10.dp))
            if (busy) {
                CircularProgressIndicator(
                    color = NexusAccentSoft,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                // Approve is the filled one. Reject is quiet on purpose: turning
                // someone down should not be the loudest thing on the row.
                Text(
                    "Terima",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(NexusAccent, RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onApprove,
                        )
                        .padding(horizontal = 15.dp, vertical = 7.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tolak",
                    color = NexusTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .border(1.dp, NexusStroke, RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onReject,
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
