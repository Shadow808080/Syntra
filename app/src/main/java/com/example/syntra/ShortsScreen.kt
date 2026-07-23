package com.example.syntra

import android.net.Uri
import android.media.MediaPlayer
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetReel
import com.example.syntra.net.NetReelComment
import com.example.syntra.net.SyntraClient
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Shorts / Reels — a chronological, vertically-swiped video feed (docs/api.md).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    modifier: Modifier = Modifier,
    selectedTab: NexusTab = NexusTab.SHORTS,
    onTabSelected: (NexusTab) -> Unit = {},
    // False when the Shorts tab is off-screen (swiped away / call on top); the
    // current reel must pause so its audio doesn't keep playing in the background.
    visible: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reels = remember { mutableStateListOf<NetReel>() }
    var loading by remember { mutableStateOf(true) }
    var posting by remember { mutableStateOf(false) }
    var pendingVideo by remember { mutableStateOf<Uri?>(null) }
    var commentsFor by remember { mutableStateOf<NetReel?>(null) }

    suspend fun reload() {
        if (!ApiConfig.ENABLED) { loading = false; return }
        runCatching { SyntraClient.getReels() }
            .onSuccess { list -> reels.clear(); reels.addAll(list) }
            .onFailure { Toast.makeText(context, "Gagal memuat reels: ${it.message}", Toast.LENGTH_SHORT).show() }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pendingVideo = uri }

    fun toggleLike(reel: NetReel) {
        val idx = reels.indexOfFirst { it.id == reel.id }
        if (idx < 0) return
        val now = !reel.isLiked
        reels[idx] = reel.copy(isLiked = now, likeCount = (reel.likeCount + if (now) 1 else -1).coerceAtLeast(0))
        scope.launch { runCatching { SyntraClient.likeReel(reel.id, now) } }
    }

    fun toggleSave(reel: NetReel) {
        val idx = reels.indexOfFirst { it.id == reel.id }
        if (idx < 0) return
        val now = !reel.isSaved
        reels[idx] = reel.copy(isSaved = now)
        scope.launch { runCatching { SyntraClient.saveReel(reel.id, now) } }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.5.dp)
            }

            reels.isEmpty() -> EmptyReels()

            else -> {
                val pager = rememberPagerState(pageCount = { reels.size })
                // Count a view whenever a reel settles on screen.
                LaunchedEffect(pager.currentPage, reels.size) {
                    reels.getOrNull(pager.currentPage)?.let { r ->
                        SyntraClient.fireAndForget { SyntraClient.viewReel(r.id) }
                    }
                }
                VerticalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val reel = reels[page]
                    ReelPage(
                        reel = reel,
                        // Play only the reel in view *and* only while the tab is shown.
                        active = visible && page == pager.currentPage,
                        onLike = { toggleLike(reel) },
                        onSave = { toggleSave(reel) },
                        onComment = { commentsFor = reel },
                        onShare = {
                            Toast.makeText(context, "Bagikan segera hadir.", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }

        // Header floats over the feed.
        ShortsHeader(onPost = {
            if (posting) return@ShortsHeader
            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        })

        if (posting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Mengunggah…", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }

    // Caption + confirm before publishing the picked video.
    pendingVideo?.let { uri ->
        PostReelDialog(
            onDismiss = { pendingVideo = null },
            onPost = { caption ->
                pendingVideo = null
                posting = true
                scope.launch {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Tidak bisa membaca video")
                        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                        val ext = mime.substringAfterLast('/', "mp4")
                        val mediaId = SyntraClient.uploadMedia("video", ext, mime, bytes)
                        SyntraClient.postReel(mediaId, caption)
                    }.onSuccess {
                        Toast.makeText(context, "Reel diterbitkan.", Toast.LENGTH_SHORT).show()
                        reload()
                    }.onFailure {
                        Toast.makeText(context, "Gagal menerbitkan: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                    posting = false
                }
            },
        )
    }

    commentsFor?.let { reel ->
        ReelCommentsSheet(reel = reel, onDismiss = { commentsFor = null })
    }
}

// ---------------------------------------------------------------------------
// One reel page
// ---------------------------------------------------------------------------

@Composable
private fun ReelPage(
    reel: NetReel,
    active: Boolean,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ReelVideo(url = reel.mediaUrl, active = active, modifier = Modifier.fillMaxSize())

        // Bottom gradient so caption/rail stay legible over bright video.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalAlignment = Alignment.Bottom,
        ) {
            ReelCaption(
                reel = reel,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, end = 12.dp, bottom = 22.dp),
            )
            ReelActions(
                reel = reel,
                onLike = onLike,
                onSave = onSave,
                onComment = onComment,
                onShare = onShare,
                modifier = Modifier.padding(end = 14.dp, bottom = 22.dp),
            )
        }
    }
}

/** Loops the reel's video while its page is the active one; pauses otherwise. */
@Composable
private fun ReelVideo(url: String, active: Boolean, modifier: Modifier = Modifier) {
    if (url.isBlank()) {
        Box(modifier.background(Color.Black))
        return
    }
    var view by remember { mutableStateOf<VideoView?>(null) }
    var ready by remember { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setOnPreparedListener { mp: MediaPlayer ->
                        mp.isLooping = true
                        ready = true
                        if (active) start()
                    }
                    setVideoURI(Uri.parse(url))
                    view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.stopPlayback() },
        )
        if (!ready) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
        }
    }

    // Play/pause follows which page is showing.
    LaunchedEffect(active, ready) {
        val v = view ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        runCatching { if (active) v.start() else v.pause() }
    }
}

@Composable
private fun ReelCaption(reel: NetReel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (reel.creatorAvatarUrl != null) {
                AsyncImage(
                    model = reel.creatorAvatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = "@" + reel.creatorUsername.ifBlank { "pengguna" },
                color = NexusTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (reel.caption.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = reel.caption,
                color = NexusTextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, null, tint = NexusAccentSoft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Original Audio", color = NexusTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReelActions(
    reel: NetReel,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ReelActionButton(
            icon = if (reel.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            tint = if (reel.isLiked) Color(0xFFFF3B5C) else NexusTextPrimary,
            label = compactCount(reel.likeCount),
            onClick = onLike,
        )
        ReelActionButton(
            icon = Icons.Outlined.ModeComment,
            tint = NexusTextPrimary,
            label = compactCount(reel.commentCount),
            onClick = onComment,
        )
        ReelActionButton(
            icon = if (reel.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            tint = if (reel.isSaved) NexusAccentSoft else NexusTextPrimary,
            label = "Simpan",
            onClick = onSave,
        )
        ReelActionButton(
            icon = Icons.Filled.Share,
            tint = NexusTextPrimary,
            label = "Bagikan",
            onClick = onShare,
        )
    }
}

@Composable
private fun ReelActionButton(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = NexusTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Brush.linearGradient(listOf(NexusAccentSoft, NexusAccent)), CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onPost,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, "Posting short", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyReels() {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = NexusTextSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("Belum ada reels", color = NexusTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Jadilah yang pertama — ketuk + di atas untuk mengunggah video.",
            color = NexusTextSecondary,
            fontSize = 13.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Post dialog
// ---------------------------------------------------------------------------

@Composable
private fun PostReelDialog(onDismiss: () -> Unit, onPost: (String) -> Unit) {
    var caption by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Reel baru", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (caption.isEmpty()) {
                    Text("Tambahkan keterangan…", color = NexusTextSecondary, fontSize = 14.sp)
                }
                BasicTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(NexusAccentSoft),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Batal",
                    color = NexusTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(listOf(NexusAccentSoft, NexusAccent)),
                            RoundedCornerShape(50),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onPost(caption.trim()) },
                        )
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text("Terbitkan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Comments
// ---------------------------------------------------------------------------

@Composable
private fun ReelCommentsSheet(reel: NetReel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val comments = remember { mutableStateListOf<NetReelComment>() }
    var loading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(reel.id) {
        runCatching { SyntraClient.getReelComments(reel.id) }
            .onSuccess { comments.clear(); comments.addAll(it) }
        loading = false
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15151C), RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp)
                .imePadding(),
        ) {
            Text(
                "Komentar",
                color = NexusTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp)
                    }
                    comments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada komentar", color = NexusTextSecondary, fontSize = 13.sp)
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(comments) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Column {
                                    Text(
                                        "@" + c.username.ifBlank { "pengguna" },
                                        color = NexusAccentSoft,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(c.body, color = NexusTextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (input.isEmpty()) {
                        Text("Tambahkan komentar…", color = NexusTextSecondary, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(NexusAccentSoft),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (input.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(NexusAccent, CircleShape)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                if (sending) return@clickable
                                val body = input.trim()
                                sending = true
                                scope.launch {
                                    runCatching { SyntraClient.postReelComment(reel.id, body) }
                                        .onSuccess {
                                            comments.add(
                                                NetReelComment(
                                                    id = "local-${System.currentTimeMillis()}",
                                                    username = "kamu",
                                                    body = body,
                                                ),
                                            )
                                            input = ""
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    sending = false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send, "Kirim",
                            tint = Color.White, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun compactCount(n: Int): String = when {
    n <= 0 -> "0"
    n < 1000 -> n.toString()
    n < 1_000_000 -> "%.1fk".format(n / 1000f)
    else -> "%.1fM".format(n / 1_000_000f)
}

@Preview(showBackground = true, backgroundColor = 0xFF090910, widthDp = 360, heightDp = 780)
@Composable
private fun ShortsScreenPreview() {
    SyntraTheme {
        ShortsScreen()
    }
}
