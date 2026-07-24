package com.example.syntra

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Shorts / Reels — a chronological, vertically-swiped video feed (docs/api.md).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    var refreshing by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var pendingVideo by remember { mutableStateOf<Uri?>(null) }
    var commentsFor by remember { mutableStateOf<NetReel?>(null) }
    // Reel the owner asked to delete, pending confirmation.
    var pendingDelete by remember { mutableStateOf<NetReel?>(null) }
    // Author whose profile is open (tapped their avatar), null = feed.
    var openProfileUser by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        if (!ApiConfig.ENABLED) { loading = false; return }
        try {
            val list = SyntraClient.getReels()
            reels.clear()
            reels.addAll(list)
        } catch (c: CancellationException) {
            // Switching tabs cancels this load mid-flight — that's normal, not a
            // failure. Re-throw so cancellation propagates; DON'T show a toast or
            // the feed would look "broken" every time you leave and return.
            throw c
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal memuat reels: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(visible) {
        if (visible && !loading) reload()
    }

    // Realtime: like & comment counters change live for everyone watching a reel.
    DisposableEffect(Unit) {
        val listener = object : com.example.syntra.net.SocketListener {
            override fun onReelLike(reelId: String, userId: String, liked: Boolean) {
                val i = reels.indexOfFirst { it.id == reelId }
                if (i < 0) return
                // Ignore my own action (already applied optimistically).
                if (userId == SyntraClient.myUserId) return
                val r = reels[i]
                reels[i] = r.copy(likeCount = (r.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
            }
            override fun onReelComment(reelId: String, userId: String, body: String) {
                val i = reels.indexOfFirst { it.id == reelId }
                if (i < 0) return
                if (userId == SyntraClient.myUserId) return
                val r = reels[i]
                reels[i] = r.copy(commentCount = r.commentCount + 1)
            }
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    // Subscribe to the reel currently on screen so its like/comment events arrive.
    // (reels:all for new/deleted is handled by the feed reload above.)
    LaunchedEffect(reels.size) {
        if (reels.isNotEmpty()) {
            SyntraClient.subscribe(reels.map { "reel:${it.id}" })
        }
    }

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
                // Swipe down on the first reel to reload the feed.
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            reload()
                            refreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    VerticalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        // Keeping neighbours composed meant three MediaPlayers
                        // buffering video at once, which is what made the feed
                        // feel heavy. Only the reel on screen holds a player.
                        beyondViewportPageCount = 0,
                    ) { page ->
                        val reel = reels[page]
                        ReelPage(
                            reel = reel,
                            // Play only the reel in view *and* only while the tab is shown.
                            active = visible && page == pager.currentPage,
                            // Deleting reels lives in Settings › Profil now, not on the
                            // feed — so no delete affordance here.
                            onDelete = null,
                            onLike = { toggleLike(reel) },
                            onComment = { commentsFor = reel },
                            onShare = {
                                Toast.makeText(context, "Bagikan segera hadir.", Toast.LENGTH_SHORT).show()
                            },
                            onOpenProfile = {
                                if (reel.creatorUsername.isNotBlank()) openProfileUser = reel.creatorUsername
                            },
                        )
                    }
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
        ReelCommentsSheet(
            reel = reel,
            onDismiss = { commentsFor = null },
            onPosted = {
                val i = reels.indexOfFirst { it.id == reel.id }
                if (i >= 0) reels[i] = reels[i].copy(commentCount = reels[i].commentCount + 1)
            },
        )
    }

    // Author profile (TikTok-style) opened by tapping an avatar in the feed.
    openProfileUser?.let { uname ->
        ProfileScreen(username = uname, onClose = { openProfileUser = null })
    }

    pendingDelete?.let { reel ->
        DeleteReelDialog(
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    runCatching { SyntraClient.deleteReel(reel.id) }
                        .onSuccess {
                            reels.removeAll { it.id == reel.id }
                            Toast.makeText(context, "Reel dihapus.", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "Gagal menghapus: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
            },
        )
    }
}

@Composable
private fun DeleteReelDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Hapus reel ini?", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Reel akan dihapus dari feed dan tidak bisa dikembalikan.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(22.dp))
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
                        .background(Color(0xFF3A1620), RoundedCornerShape(50))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onConfirm,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Hapus", color = Color(0xFFFF5D5D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
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
    onComment: () -> Unit,
    onShare: () -> Unit,
    /** Non-null only when the signed-in user owns this reel. */
    onDelete: (() -> Unit)? = null,
    onOpenProfile: () -> Unit = {},
) {
    // Tap-to-pause, per reel. Reset when the reel scrolls off so coming back plays.
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) paused = false }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ReelVideo(url = reel.mediaUrl, playing = active && !paused, modifier = Modifier.fillMaxSize())

        // Tap layer toggles pause — but only over the upper video area. The
        // bottom strip (caption, username, action rail) is left out so tapping
        // those never pauses the video.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 200.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { paused = !paused },
        )

        // Paused indicator.
        if (paused && active) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Putar",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

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
                onOpenProfile = onOpenProfile,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, end = 12.dp, bottom = 22.dp),
            )
            ReelActions(
                reel = reel,
                onLike = onLike,
                onComment = onComment,
                onShare = onShare,
                onDelete = onDelete,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.padding(end = 12.dp, bottom = 22.dp),
            )
        }
    }
}

/**
 * Loops the reel's video while [playing]; pauses otherwise (off-screen or tapped).
 *
 * Uses a [TextureView] rather than `VideoView`: a VideoView is backed by a
 * SurfaceView, which owns its own window layer and on many devices draws *on top
 * of* everything — including the neighbouring tab while the pager is mid-swipe.
 * A TextureView composites like an ordinary view, so it stays inside its page.
 */
@Composable
private fun ReelVideo(url: String, playing: Boolean, modifier: Modifier = Modifier) {
    if (url.isBlank()) {
        Box(modifier.background(Color.Black))
        return
    }
    val player = remember(url) { MediaPlayer() }
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    // Intrinsic video size, used to centre-crop instead of stretching.
    var videoW by remember(url) { mutableStateOf(0) }
    var videoH by remember(url) { mutableStateOf(0) }

    DisposableEffect(url) {
        onDispose { runCatching { player.release() } }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        // Fill the page without distorting: scale the stretched surface back to
        // the video's real aspect ratio, cropping the overflow.
        val boxW = maxWidth.value
        val boxH = maxHeight.value
        val scaleX: Float
        val scaleY: Float
        if (videoW > 0 && videoH > 0 && boxW > 0f && boxH > 0f) {
            val cover = maxOf(boxW / videoW, boxH / videoH)
            scaleX = videoW * cover / boxW
            scaleY = videoH * cover / boxH
        } else {
            scaleX = 1f
            scaleY = 1f
        }

        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            runCatching {
                                player.setSurface(Surface(st))
                                player.setDataSource(url)
                                player.isLooping = true
                                player.setOnVideoSizeChangedListener { _, vw, vh ->
                                    videoW = vw
                                    videoH = vh
                                }
                                player.setOnPreparedListener { ready = true }
                                player.setOnErrorListener { _, _, _ -> failed = true; true }
                                player.prepareAsync()
                            }.onFailure { failed = true }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                },
        )

        if (!ready) {
            // A soft breathing placeholder fills the frame while the video buffers,
            // so the screen never shows a black void during load.
            ShimmerFill(Modifier.fillMaxSize())
            if (failed) {
                Text("Video gagal dimuat", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            } else {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
            }
        }
    }

    // Play/pause follows the current page and the tap-to-pause toggle.
    LaunchedEffect(playing, ready) {
        if (!ready) return@LaunchedEffect
        runCatching { if (playing) player.start() else player.pause() }
    }
}

/**
 * Full-screen vertical reel viewer, opened from a profile grid. Shows [reels]
 * as swipeable pages (like the Shorts feed), starting at [startIndex].
 */
@Composable
fun ReelViewer(reels: List<NetReel>, startIndex: Int, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<NetReel>().apply { addAll(reels) } }
    var commentsFor by remember { mutableStateOf<NetReel?>(null) }

    if (items.isEmpty()) { onClose(); return }
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    LaunchedEffect(pager.currentPage) {
        items.getOrNull(pager.currentPage)?.let { r ->
            SyntraClient.fireAndForget { SyntraClient.viewReel(r.id) }
        }
    }
    fun toggleLike(reel: NetReel) {
        val idx = items.indexOfFirst { it.id == reel.id }
        if (idx < 0) return
        val now = !reel.isLiked
        items[idx] = reel.copy(isLiked = now, likeCount = (reel.likeCount + if (now) 1 else -1).coerceAtLeast(0))
        scope.launch { runCatching { SyntraClient.likeReel(reel.id, now) } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, beyondViewportPageCount = 0, modifier = Modifier.fillMaxSize()) { page ->
            val reel = items[page]
            ReelPage(
                reel = reel,
                active = page == pager.currentPage,
                onLike = { toggleLike(reel) },
                onComment = { commentsFor = reel },
                onShare = { Toast.makeText(context, "Bagikan segera hadir.", Toast.LENGTH_SHORT).show() },
            )
        }
        // Back button over the viewer.
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(10.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Kembali",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    commentsFor?.let { reel ->
        ReelCommentsSheet(
            reel = reel,
            onDismiss = { commentsFor = null },
            onPosted = {
                val i = items.indexOfFirst { it.id == reel.id }
                if (i >= 0) items[i] = items[i].copy(commentCount = items[i].commentCount + 1)
            },
        )
    }
}

private val ShortsTeal = Color(0xFF20D5C4)

/**
 * A gentle breathing placeholder shown behind media while it loads, so photos and
 * videos never reveal a black/empty void mid-load. Animates alpha only (no layout),
 * so it's cheap and honours the motion discipline.
 */
@Composable
fun ShimmerFill(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "shimmer-alpha",
    )
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF23232E).copy(alpha = alpha),
                    Color(0xFF141019).copy(alpha = alpha),
                ),
            ),
        ),
    )
}

@Composable
private fun ReelCaption(reel: NetReel, onOpenProfile: () -> Unit = {}, modifier: Modifier = Modifier) {
    val username = reel.creatorUsername.ifBlank { "pengguna" }
    Column(modifier = modifier) {
        Text(
            text = "@$username",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpenProfile,
            ),
        )
        if (reel.caption.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = highlightHashtags(reel.caption),
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Original sound - @$username",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tints `#hashtags` teal like the reference feed. */
private fun highlightHashtags(caption: String) = buildAnnotatedString {
    val tokens = caption.split(" ")
    tokens.forEachIndexed { i, token ->
        if (token.startsWith("#") && token.length > 1) {
            withStyle(SpanStyle(color = ShortsTeal, fontWeight = FontWeight.SemiBold)) { append(token) }
        } else {
            append(token)
        }
        if (i < tokens.lastIndex) append(" ")
    }
}

@Composable
private fun ReelActions(
    reel: NetReel,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Hides the + once you follow this author. Seeded from the server's
    // is_following so already-followed authors show no +, and flips locally on tap.
    var followed by remember(reel.authorId) { mutableStateOf(reel.isFollowing) }
    var showFollowSheet by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Author avatar with a teal follow (+) badge. Tapping the avatar opens
        // the author's profile.
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222228))
                    .border(2.dp, Color.White, CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onOpenProfile,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (reel.creatorAvatarUrl != null) {
                    AsyncImage(
                        model = reel.creatorAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = (reel.creatorUsername.firstOrNull() ?: 'U').uppercase(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Follow (+) — hidden on your own reels AND once you've followed.
            // Tapping it opens a small sheet: Follow, or view the profile.
            if (reel.authorId.isNotBlank() && reel.authorId != SyntraClient.myUserId && !followed) {
                Box(
                    modifier = Modifier
                        .offset(y = 9.dp)
                        .size(20.dp)
                        .background(ShortsTeal, CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showFollowSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, "Ikuti", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        RailItem(
            icon = if (reel.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            tint = if (reel.isLiked) Color(0xFFFF3B5C) else Color.White,
            label = compactCount(reel.likeCount),
            onClick = onLike,
        )
        RailItem(
            icon = Icons.Outlined.ModeComment,
            tint = Color.White,
            label = compactCount(reel.commentCount),
            onClick = onComment,
        )
        RailItem(
            icon = Icons.Filled.Share,
            tint = Color.White,
            label = "Share",
            onClick = onShare,
        )
        // Owner-only: remove my own reel (kept subtle, others never see it).
        if (onDelete != null) {
            RailItem(
                icon = Icons.Filled.Delete,
                tint = Color(0xFFFF5D5D),
                label = "Hapus",
                onClick = onDelete,
            )
        }
        Spacer(Modifier.height(2.dp))
        SpinningMusicDisc(avatarUrl = reel.creatorAvatarUrl)
    }

    if (showFollowSheet) {
        FollowActionSheet(
            username = reel.creatorUsername,
            onDismiss = { showFollowSheet = false },
            onFollow = {
                showFollowSheet = false
                if (reel.creatorUsername.isNotBlank()) {
                    followed = true
                    scope.launch { runCatching { SyntraClient.follow(reel.creatorUsername) } }
                    Toast.makeText(context, "Mengikuti @${reel.creatorUsername}", Toast.LENGTH_SHORT).show()
                }
            },
            onViewProfile = { showFollowSheet = false; onOpenProfile() },
        )
    }
}

/** Small bottom sheet on the reel + badge: follow the author, or open their profile. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FollowActionSheet(
    username: String,
    onDismiss: () -> Unit,
    onFollow: () -> Unit,
    onViewProfile: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NexusSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "@$username",
                color = NexusTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SheetRow(Icons.Filled.Add, "Ikuti", onFollow)
            SheetRow(Icons.Filled.AccountCircle, "Lihat profil", onViewProfile)
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = NexusTextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = NexusTextPrimary, fontSize = 15.sp)
    }
}

@Composable
private fun RailItem(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(34.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The little record that spins at the bottom of the action rail. */
@Composable
private fun SpinningMusicDisc(avatarUrl: String?) {
    val transition = rememberInfiniteTransition(label = "disc")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .graphicsLayer { rotationZ = angle }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF101014))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(22.dp).clip(CircleShape),
            )
        } else {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun ShortsHeader(onPost: () -> Unit) {
    val context = LocalContext.current
    var following by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Left: teal create/upload button.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(38.dp)
                .background(ShortsTeal, RoundedCornerShape(11.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onPost,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, "Unggah", tint = Color(0xFF0A1414), modifier = Modifier.size(26.dp))
        }
        // Center: Following / For You tabs.
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ShortsTab("Following", active = following) {
                following = true
                Toast.makeText(context, "Feed Following segera hadir.", Toast.LENGTH_SHORT).show()
            }
            ShortsTab("For You", active = !following) { following = false }
        }
        // Right: search.
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Cari",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(26.dp),
        )
    }
}

@Composable
private fun ShortsTab(text: String, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
    ) {
        Text(
            text = text,
            color = if (active) Color.White else Color.White.copy(alpha = 0.55f),
            fontSize = if (active) 18.sp else 15.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .background(if (active) ShortsTeal else Color.Transparent, RoundedCornerShape(50)),
        )
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun ReelCommentsSheet(reel: NetReel, onDismiss: () -> Unit, onPosted: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val comments = remember { mutableStateListOf<NetReelComment>() }
    var loading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // My own username for the optimistic comment (so it isn't shown as "kamu").
    val myUsername = ProfileStore.username(context, SessionStore.signedInEmail(context).orEmpty())

    LaunchedEffect(reel.id) {
        runCatching { SyntraClient.getReelComments(reel.id) }
            .onSuccess { comments.clear(); comments.addAll(it) }
        loading = false
    }

    // skipPartiallyExpanded = open at full height straight away, so the input
    // row is visible immediately (no dragging up first).
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF15151C),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp)
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
            // Height follows the screen so the sheet fits small phones too.
            val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
            Box(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
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
                                                    username = myUsername,
                                                    body = body,
                                                ),
                                            )
                                            input = ""
                                            onPosted() // bump the rail's comment count live
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
    // Indonesian style: "88,4K", and a whole thousand shows as "1K".
    n < 1_000_000 -> "%.1f".format(n / 1000f).removeSuffix(".0").replace('.', ',') + "K"
    else -> "%.1f".format(n / 1_000_000f).removeSuffix(".0").replace('.', ',') + "M"
}

@Preview(showBackground = true, backgroundColor = 0xFF090910, widthDp = 360, heightDp = 780)
@Composable
private fun ShortsScreenPreview() {
    SyntraTheme {
        ShortsScreen()
    }
}
