package com.example.syntra

import android.graphics.SurfaceTexture
import android.content.Context
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.example.syntra.net.SocketListener
import com.example.syntra.net.SyntraClient
import com.example.syntra.net.ReelCache
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // Signals the host to hide the bottom bar while the full-screen add-reels flow
    // (trim + details) is up, so it doesn't sit on top of that screen.
    onOverlayChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Auto-advance to the next reel when a clip finishes (toggle in Settings). Re-read
    // whenever the tab comes back so a change in Settings takes effect immediately.
    var autoScroll by remember { mutableStateOf(SettingsStore.getBool(context, SettingsStore.AUTO_SCROLL_REELS, true)) }
    LaunchedEffect(visible) { if (visible) autoScroll = SettingsStore.getBool(context, SettingsStore.AUTO_SCROLL_REELS, true) }
    // Long-press on a reel opens this playback-settings sheet (moved out of the
    // app-wide Settings so it lives right where you watch).
    var showReelSettings by remember { mutableStateOf(false) }
    // Tell the notifier we're on Shorts, so a "comment reply" toast is suppressed
    // here (it shows live) but still fires on every other screen.
    DisposableEffect(visible) {
        com.example.syntra.net.AppForeground.inShorts = visible
        onDispose { com.example.syntra.net.AppForeground.inShorts = false }
    }
    // Seed from the in-memory feed cache so RE-ENTERING Shorts is instant — the list
    // and scroll position survive the tab being disposed (the home stays light with
    // beyondViewportPageCount=1, but Shorts no longer "reloads from scratch"). Videos
    // themselves already come from VideoCache on disk; this just avoids the empty-list
    // spinner + refetch every time you come back.
    val reels = remember { mutableStateListOf<NetReel>().also { it.addAll(ShortsFeedCache.reels) } }
    var loading by remember { mutableStateOf(ShortsFeedCache.reels.isEmpty()) }
    // Persist the feed to the cache whenever it changes, so the next entry is seeded.
    LaunchedEffect(Unit) {
        snapshotFlow { reels.toList() }.collect { ShortsFeedCache.reels = it }
    }
    var refreshing by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    // Raw picked video (awaiting trim), then the trimmed clip (awaiting caption).
    var pendingVideo by remember { mutableStateOf<Uri?>(null) }
    var trimmedVideo by remember { mutableStateOf<Uri?>(null) }
    var commentsFor by remember { mutableStateOf<NetReel?>(null) }
    // Reel the owner asked to delete, pending confirmation.
    var pendingDelete by remember { mutableStateOf<NetReel?>(null) }
    // Author whose profile is open (tapped their avatar), null = feed.
    var openProfileUser by remember { mutableStateOf<String?>(null) }
    // A reel opened full-screen from a notification deep-link (comment reply etc.).
    var deepLinkReel by remember { mutableStateOf<NetReel?>(null) }

    // Notification tap → open that reel full-screen. Fetch FIRST, clear the request
    // AFTER: clearing it before the fetch changed this effect's key and cancelled the
    // getReel call mid-flight, so the reel never opened and you were left on the feed.
    LaunchedEffect(ReelNavRequest.reelId) {
        val rid = ReelNavRequest.reelId ?: return@LaunchedEffect
        val reel = runCatching { SyntraClient.getReel(rid) }
            .onFailure { android.util.Log.w("Shorts", "deep-link getReel failed", it) }
            .getOrNull()
        ReelNavRequest.reelId = null
        if (reel != null) deepLinkReel = reel
        else Toast.makeText(context, "Postingan tidak bisa dibuka.", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(deepLinkReel) { onOverlayChange(deepLinkReel != null) }

    // Upload progress card (top of the Shorts feed). Shown while a reel uploads.
    var uploadCardVisible by remember { mutableStateOf(false) }
    var uploadThumb by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var uploadStartMs by remember { mutableStateOf(0L) }
    var uploadEtaMs by remember { mutableStateOf(6000L) }

    // Following tab: show ONLY reels from people the user actually follows (the
    // feed already carries is_following per reel). Own reels are excluded so it
    // reads like TikTok's Following, not a mix with your own uploads.
    var showFollowing by remember { mutableStateOf(false) }
    val displayReels by remember {
        derivedStateOf {
            if (showFollowing) {
                reels.filter { it.isFollowing && it.authorId != SyntraClient.myUserId }
            } else {
                reels.toList()
            }
        }
    }
    val pager = rememberPagerState(pageCount = { displayReels.size })
    // Flipping the tab (or posting a new short) should land you at the top, not
    // stranded mid-feed on a page that no longer exists after the list changed.
    LaunchedEffect(showFollowing) { runCatching { pager.scrollToPage(0) } }

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

    // Quietly pull the freshest feed and MERGE in anything new (e.g. a short a
    // followed user just posted), without a spinner and without disturbing the
    // reel you're currently watching. New items are prepended; deletions drop out.
    suspend fun syncFeed() {
        if (!ApiConfig.ENABLED) return
        val fresh = runCatching { SyntraClient.getReels() }.getOrNull() ?: return
        val freshIds = fresh.map { it.id }.toSet()
        val currentIds = reels.map { it.id }.toSet()
        if (freshIds == currentIds) return // nothing changed
        // Keep the currently playing reel anchored: rebuild the list from `fresh`
        // (authoritative order, newest first) but only actually swap when membership
        // differs, so counters updated live aren't clobbered.
        val currentPageId = displayReels.getOrNull(pager.currentPage)?.id
        reels.clear()
        reels.addAll(fresh)
        // Re-anchor to the reel we were on, if it still exists.
        if (currentPageId != null) {
            val idx = reels.indexOfFirst { it.id == currentPageId }
            if (idx >= 0) runCatching { pager.scrollToPage(idx) }
        }
    }

    // Only do a blocking (spinner) load when we have nothing cached; otherwise show
    // the cached feed instantly and let the quiet syncFeed() below merge fresh items.
    LaunchedEffect(Unit) { if (reels.isEmpty()) reload() else loading = false }

    // Returning to the tab RESUMES the last video you were watching — a quiet merge
    // that keeps your scroll position, instead of a full reload that would reset the
    // pager to the top. New shorts still arrive live via the reels:all listener.
    LaunchedEffect(visible) {
        if (visible && !loading) syncFeed()
    }

    // Realtime: like & comment counters, plus new/deleted reels, update live.
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
            override fun onReelNew(reelId: String, authorId: String) {
                // Someone (e.g. a followed creator) posted — pull it into the feed
                // live so it appears without a manual refresh. Skip my own (already
                // shown via the post flow's reload).
                if (authorId == SyntraClient.myUserId) return
                if (reels.any { it.id == reelId }) return
                scope.launch { syncFeed() }
            }
            override fun onReelDeleted(reelId: String) {
                reels.removeAll { it.id == reelId }
            }
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    // Subscribe to the global reels feed (reel.new / reel.deleted) so new shorts
    // arrive live, plus the reel on screen for its like/comment events.
    LaunchedEffect(Unit) { SyntraClient.subscribe(listOf("reels:all")) }
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

    // Hide the host's bottom bar for the whole add-reels flow (trim + details).
    // Hide the bottom bar + lock tab-swipe while the add-reels flow OR a profile is
    // open, so the profile is truly full-screen (not covered by the nav bar).
    LaunchedEffect(pendingVideo != null, openProfileUser) {
        onOverlayChange(pendingVideo != null || openProfileUser != null)
    }

    // The add-reels flow is its OWN full screen: trim → details. While it is up we
    // do NOT compose the feed at all — the reel video surface leaves the
    // composition entirely, so it can never bleed over or fight the preview.
    val rawVideo = pendingVideo
    if (rawVideo != null) {
        val trimmed = trimmedVideo
        if (trimmed == null) {
            VideoTrimScreen(
                uri = rawVideo,
                onCancel = { pendingVideo = null },
                onDone = { trimmedVideo = it },
                maxMs = 60_000L,
            )
        } else {
            // Step 2: title / description / tags / audience / agreement.
            ReelDetailsScreen(
                onBack = { trimmedVideo = null }, // back returns to the trimmer
                onPost = { caption, visibility, commentsEnabled ->
                    // Return to the feed immediately; the upload runs in the
                    // background behind a small fixed card at the top of Shorts, so
                    // the user can keep scrolling while it publishes.
                    val src = trimmed
                    pendingVideo = null
                    trimmedVideo = null
                    posting = true
                    uploadCardVisible = true
                    uploadThumb = null
                    uploadStartMs = System.currentTimeMillis()
                    uploadEtaMs = 6000L
                    scope.launch {
                        runCatching {
                            val bytes = context.contentResolver.openInputStream(src)?.use { it.readBytes() }
                                ?: error("Tidak bisa membaca video")
                            // Shape the progress bar from a size-based ETA + grab a
                            // thumbnail for the card (both best-effort).
                            uploadEtaMs = estimateUploadEtaMs(bytes.size)
                            uploadThumb = withContext(Dispatchers.IO) { reelThumbnail(context, src) }
                            val mime = context.contentResolver.getType(src) ?: "video/mp4"
                            val ext = mime.substringAfterLast('/', "mp4")
                            val mediaId = SyntraClient.uploadMedia("video", ext, mime, bytes)
                            SyntraClient.postReel(mediaId, caption, visibility, commentsEnabled)
                        }.onSuccess {
                            reload()
                            showFollowing = false
                            runCatching { pager.scrollToPage(0) }
                        }.onFailure {
                            Toast.makeText(context, "Gagal menerbitkan: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                        posting = false
                    }
                },
            )
        }
        return
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

            // Following tab can be empty even when the global feed isn't.
            displayReels.isEmpty() -> FollowingEmpty()

            else -> {
                // Auto-hide the bottom bar in the feed: moving to a later reel
                // (scrolling down) hides it for an immersive view; going back up shows
                // it. Reset to shown when leaving the tab.
                var prevPage by remember { mutableStateOf(0) }
                LaunchedEffect(pager.currentPage) {
                    val p = pager.currentPage
                    if (p > prevPage) BottomBarVisibility.visible = false
                    else if (p < prevPage) BottomBarVisibility.visible = true
                    prevPage = p
                }
                DisposableEffect(Unit) { onDispose { BottomBarVisibility.visible = true } }
                // Count a view whenever a reel settles on screen.
                LaunchedEffect(pager.currentPage, displayReels.size) {
                    displayReels.getOrNull(pager.currentPage)?.let { r ->
                        SyntraClient.fireAndForget { SyntraClient.viewReel(r.id) }
                    }
                    // Warm the next reel so it's already on disk (and free to
                    // replay) by the time it scrolls into view.
                    displayReels.getOrNull(pager.currentPage + 1)?.mediaUrl?.let {
                        ReelCache.prefetch(context, it)
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
                        val reel = displayReels.getOrNull(page) ?: return@VerticalPager
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
                            autoScroll = autoScroll,
                            onVideoEnded = {
                                // Auto-advance only when there's a next reel; the last
                                // one just stops (its own player already replayed once).
                                if (autoScroll && page < displayReels.lastIndex) {
                                    scope.launch { pager.animateScrollToPage(page + 1) }
                                }
                            },
                            onLongPress = { showReelSettings = true },
                        )
                    }
                }
            }
        }

        // Header floats over the feed.
        ShortsHeader(
            following = showFollowing,
            onSelectFollowing = { showFollowing = it },
            onPost = {
                if (posting) return@ShortsHeader
                pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
        )

        // Fixed upload card at the very top. Stays while the reel uploads, then
        // fills the bar and disappears. Only ever visible on this (Shorts) screen.
        LaunchedEffect(posting) {
            if (!posting && uploadCardVisible) { delay(900); uploadCardVisible = false }
        }
        if (uploadCardVisible) {
            UploadReelCard(
                thumb = uploadThumb,
                startMs = uploadStartMs,
                etaMs = uploadEtaMs,
                uploading = posting,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // "Back to top" — appears once you've scrolled past the first reel, jumps
        // straight back to the top video. Sits just under the header on the left.
        if (displayReels.isNotEmpty() && pager.currentPage > 0 && !uploadCardVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, top = 60.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { scope.launch { pager.animateScrollToPage(0) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Ke video teratas",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Author profile (TikTok-style), opened by tapping an avatar or @name. Kept
        // INSIDE the root box as the last child so it reliably draws on top of the
        // feed (as a sibling it wasn't rendering full-screen).
        openProfileUser?.let { uname ->
            ProfileScreen(username = uname, onClose = { openProfileUser = null })
        }

        // A reel opened from a notification — full-screen viewer, with comments a tap
        // away, so tapping "budi membalas komentar kamu" lands right on the post.
        deepLinkReel?.let { reel ->
            ReelViewer(
                reels = listOf(reel),
                startIndex = 0,
                onClose = { deepLinkReel = null },
                openCommentsOnStart = true,
            )
        }
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

    if (showReelSettings) {
        ReelSettingsSheet(
            autoScroll = autoScroll,
            onAutoScrollChange = { on ->
                autoScroll = on
                SettingsStore.setBool(context, SettingsStore.AUTO_SCROLL_REELS, on)
            },
            onDismiss = { showReelSettings = false },
        )
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

// Shared once, not re-allocated per reel/recomposition: a coordinate-less vertical
// gradient adapts to whatever size it's drawn at, so one instance fits every page.
private val ReelBottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))

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
    /** When true, the clip plays once and [onVideoEnded] advances to the next reel. */
    autoScroll: Boolean = false,
    onVideoEnded: () -> Unit = {},
    /** Long-press on the video opens the playback settings (auto-scroll). */
    onLongPress: () -> Unit = {},
) {
    // Tap-to-pause, per reel. Reset when the reel scrolls off so coming back plays.
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) paused = false }

    // Scrubber state, per reel. `seekReq` is bumped to a fresh Int on every drag so
    // the same target twice still triggers a seek; `scrubbing` freezes the bar (and
    // pauses playback) while the finger is down.
    var posMs by remember(reel.id) { mutableIntStateOf(0) }
    var durMs by remember(reel.id) { mutableIntStateOf(0) }
    var seekReq by remember(reel.id) { mutableStateOf<Int?>(null) }
    var scrubbing by remember(reel.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ReelVideo(
            url = reel.mediaUrl,
            playing = active && !paused && !scrubbing,
            modifier = Modifier.fillMaxSize(),
            loop = !autoScroll,
            seekToMs = seekReq,
            onDuration = { durMs = it },
            onPosition = { if (!scrubbing) posMs = it },
            onEnded = onVideoEnded,
        )

        // Tap layer toggles pause — but only over the upper video area. The
        // bottom strip (caption, username, action rail) is left out so tapping
        // those never pauses the video. Long-press opens the playback settings.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 200.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { paused = !paused },
                        onLongPress = { onLongPress() },
                    )
                },
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
                .background(ReelBottomScrim),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                ReelCaption(
                    reel = reel,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp, end = 12.dp, bottom = 14.dp),
                )
                ReelActions(
                    reel = reel,
                    onLike = onLike,
                    onComment = onComment,
                    onShare = onShare,
                    onDelete = onDelete,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.padding(end = 12.dp, bottom = 14.dp),
                )
            }
            // Scrubber pill — drag to jump anywhere in the clip. Sits at the very
            // bottom, full width, and swells while dragging (modern short-video feel).
            ReelScrubber(
                // Deferred read: posMs updates every frame but only the scrubber cares.
                positionMs = { posMs },
                durationMs = durMs,
                onScrubStart = { scrubbing = true },
                onScrub = { ms -> posMs = ms; seekReq = ms },
                // After releasing, stay PAUSED on the chosen frame (don't auto-resume
                // and don't restart) — tap the video to continue from there.
                onScrubEnd = { scrubbing = false; paused = true },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            )
        }
    }
}

/**
 * Playback-settings sheet for Shorts, opened by long-pressing a reel. Currently
 * hosts the "auto-scroll" toggle that used to live in the app-wide Settings —
 * it belongs here, right where you're watching.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReelSettingsSheet(
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF15151C),
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        ) {
            Text(
                "Pengaturan pemutaran",
                color = NexusTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onAutoScrollChange(!autoScroll) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(ShortsTeal.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription = null,
                        tint = ShortsTeal,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Geser otomatis",
                        color = NexusTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Lanjut ke video berikutnya setelah selesai menonton",
                        color = NexusTextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.Switch(
                    checked = autoScroll,
                    onCheckedChange = { onAutoScrollChange(it) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ShortsTeal,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF3A3A44),
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

/**
 * Draggable progress pill for a reel — jump anywhere in the clip by sliding it.
 *
 * Idle it's a thin, unobtrusive line; touch it and the whole bar swells with a
 * round thumb and a time readout, the way modern short-video apps do it. Tapping
 * anywhere on the track also seeks there. Reports seeks up via [onScrub]; the
 * caller pauses playback between [onScrubStart] and [onScrubEnd].
 */
@Composable
private fun ReelScrubber(
    // A provider, not a plain Int: the playback position ticks many times a second.
    // Reading it *inside* this composable (positionMs()) keeps that per-frame state
    // read here, so only the scrubber recomposes — the parent ReelPage (and its
    // video, caption, action rail, Modifier chains) no longer rebuild every tick.
    positionMs: () -> Int,
    durationMs: Int,
    onScrubStart: () -> Unit,
    onScrub: (Int) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0) {
        // No known duration yet (still preparing) — reserve nothing.
        Spacer(modifier.fillMaxWidth().height(10.dp))
        return
    }
    val density = LocalDensity.current
    var trackW by remember { mutableStateOf(1) }
    // While the finger is down we drive the bar from the drag, not playback.
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val dragging = dragFrac != null
    val frac = (dragFrac ?: (positionMs().toFloat() / durationMs)).coerceIn(0f, 1f)

    val barHeight by animateDpAsState(if (dragging) 7.dp else 3.dp, label = "scrubH")
    val thumb by animateDpAsState(if (dragging) 16.dp else 0.dp, label = "scrubThumb")

    fun seekTo(x: Float) {
        val f = (x / trackW.toFloat()).coerceIn(0f, 1f)
        dragFrac = f
        onScrub((f * durationMs).toInt())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Time readout, only while scrubbing.
        if (dragging) {
            Text(
                text = "${formatClock((frac * durationMs).toInt())} / ${formatClock(durationMs)}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp) // generous touch target
                .onSizeChanged { trackW = it.width.coerceAtLeast(1) }
                .pointerInput(durationMs) {
                    detectTapGestures { offset -> onScrubStart(); seekTo(offset.x); onScrubEnd(); dragFrac = null }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> onScrubStart(); seekTo(offset.x) },
                        onDragEnd = { onScrubEnd(); dragFrac = null },
                        onDragCancel = { onScrubEnd(); dragFrac = null },
                    ) { change, _ -> change.consume(); seekTo(change.position.x) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // Track (dim) + filled portion (white), both fully-rounded pills.
            Box(
                Modifier.fillMaxWidth().height(barHeight)
                    .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.32f)),
            )
            Box(
                Modifier.fillMaxWidth(frac).height(barHeight)
                    .clip(RoundedCornerShape(50)).background(Color.White),
            )
            // Thumb — appears only while dragging.
            if (thumb > 0.dp) {
                Box(
                    Modifier
                        .offset { IntOffset((frac * trackW - with(density) { thumb.toPx() } / 2f).roundToInt(), 0) }
                        .size(thumb)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

/** mm:ss for the scrubber readout. */
private fun formatClock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Loops the reel's video while [playing]; pauses otherwise (off-screen or tapped).
 *
 * Uses a [TextureView] rather than `VideoView`: a VideoView is backed by a
 * SurfaceView, which owns its own window layer and on many devices draws *on top
 * of* everything — including the neighbouring tab while the pager is mid-swipe.
 * A TextureView composites like an ordinary view, so it stays inside its page.
 *
 * Playback is ExoPlayer (Media3) reading through [ReelCache]'s CacheDataSource, so
 * a clip is downloaded once and replays from disk.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ReelVideo(
    url: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
    /** Loop forever (true) vs play once then fire [onEnded] (used for auto-scroll). */
    loop: Boolean = true,
    /** When this changes to a non-null value, seek there (ms). Drives the scrubber. */
    seekToMs: Int? = null,
    /** Reports total duration (ms) once the video is prepared; 0 until then. */
    onDuration: (Int) -> Unit = {},
    /** Reports the current playback position (ms) a few times a second. */
    onPosition: (Int) -> Unit = {},
    /** Fired once the clip reaches its end (only when [loop] is false). */
    onEnded: () -> Unit = {},
) {
    if (url.isBlank()) {
        Box(modifier.background(Color.Black))
        return
    }
    val context = LocalContext.current
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    // Intrinsic video size, used to centre-crop instead of stretching.
    var videoW by remember(url) { mutableStateOf(0) }
    var videoH by remember(url) { mutableStateOf(0) }
    // Latest callbacks/flags captured so the player's listener always sees current
    // values without the player being re-created.
    val loopLatest by rememberUpdatedState(loop)
    val onEndedLatest by rememberUpdatedState(onEnded)
    val onDurationLatest by rememberUpdatedState(onDuration)

    // One ExoPlayer per url, playing THROUGH the shared cache: the first view streams
    // and fills the cache in the SAME pass (download-once, no separate stream+prefetch
    // double fetch), and replays read from disk. Surface swaps on scroll are handled by
    // ExoPlayer itself, so the old MediaPlayer black-frame re-attach dance is gone.
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    com.example.syntra.net.ReelCache.dataSourceFactory(context),
                ),
            )
            .build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                repeatMode = if (loopLatest) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE
                } else {
                    androidx.media3.common.Player.REPEAT_MODE_OFF
                }
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                        videoW = size.width
                        videoH = size.height
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            androidx.media3.common.Player.STATE_READY -> {
                                ready = true
                                onDurationLatest(duration.coerceAtLeast(0L).toInt())
                            }
                            // Only meaningful when NOT looping — the "finished" signal the
                            // feed uses to auto-advance to the next reel.
                            androidx.media3.common.Player.STATE_ENDED ->
                                if (!loopLatest) onEndedLatest()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        failed = true
                    }
                })
                prepare()
            }
    }

    // Keep looping in sync if the setting flips while a reel is on screen.
    LaunchedEffect(loop) {
        player.repeatMode = if (loop) {
            androidx.media3.common.Player.REPEAT_MODE_ONE
        } else {
            androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }

    // Seek when the scrubber asks. ExoPlayer's default seek parameters are exact, so
    // dragging shows a precise frame while paused instead of jumping to a keyframe.
    LaunchedEffect(seekToMs) {
        val ms = seekToMs ?: return@LaunchedEffect
        if (ready) runCatching { player.seekTo(ms.toLong()) }
    }

    // Feed the scrubber: sample the real playback position while it plays.
    LaunchedEffect(ready, playing) {
        if (!ready) return@LaunchedEffect
        while (true) {
            if (playing) onPosition(runCatching { player.currentPosition.toInt() }.getOrDefault(0))
            delay(200)
        }
    }

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
                            player.setVideoSurface(Surface(st))
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            player.clearVideoSurface()
                            return true
                        }
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

    // Play/pause follows the current page and the tap-to-pause toggle. ExoPlayer
    // honours playWhenReady even before it's buffered, so no `ready` gate is needed —
    // it starts the instant it can.
    LaunchedEffect(playing) {
        if (playing) com.example.syntra.net.MusicPlayer.pauseForExternalAudio() // don't talk over music
        runCatching { player.playWhenReady = playing }
    }
}

/**
 * Full-screen vertical reel viewer, opened from a profile grid. Shows [reels]
 * as swipeable pages (like the Shorts feed), starting at [startIndex].
 */
@Composable
fun ReelViewer(
    reels: List<NetReel>,
    startIndex: Int,
    onClose: () -> Unit,
    /** Open the comments sheet immediately — used when arriving from a reply notif. */
    openCommentsOnStart: Boolean = false,
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<NetReel>().apply { addAll(reels) } }
    var commentsFor by remember { mutableStateOf<NetReel?>(null) }
    val autoScroll = remember { SettingsStore.getBool(context, SettingsStore.AUTO_SCROLL_REELS, true) }

    if (items.isEmpty()) { onClose(); return }
    // Land with comments open so the reply that triggered the notification is right
    // there, not one tap away.
    LaunchedEffect(Unit) {
        if (openCommentsOnStart) items.getOrNull(startIndex.coerceIn(0, items.lastIndex))?.let { commentsFor = it }
    }
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    LaunchedEffect(pager.currentPage) {
        items.getOrNull(pager.currentPage)?.let { r ->
            SyntraClient.fireAndForget { SyntraClient.viewReel(r.id) }
        }
        items.getOrNull(pager.currentPage + 1)?.mediaUrl?.let {
            ReelCache.prefetch(context, it)
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
                autoScroll = autoScroll,
                onVideoEnded = {
                    if (autoScroll && page < items.lastIndex) {
                        scope.launch { pager.animateScrollToPage(page + 1) }
                    }
                },
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

/**
 * In-memory feed cache so returning to the Shorts tab is instant. The home is kept
 * light by NOT keeping Shorts composed off-screen (MainTabs beyondViewportPageCount=1),
 * so this holds the last feed + is re-seeded on the next entry. Cleared on sign-out.
 */
object ShortsFeedCache {
    var reels: List<NetReel> = emptyList()
    fun clear() { reels = emptyList() }
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
    // My own reel reads "Postingan Anda" with a badge instead of @username, so it's
    // obvious at a glance which clips in the feed are mine.
    val isMine = reel.authorId.isNotBlank() && reel.authorId == SyntraClient.myUserId
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpenProfile,
            ),
        ) {
            Text(
                text = if (isMine) "Postingan Anda" else "@$username",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            if (isMine) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AccountCircle, null,
                            tint = Color.White, modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("Anda", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (reel.caption.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            // Collapsed to 2 lines with a "selengkapnya" affordance; tap the caption
            // to expand and again to collapse. Reset when the reel changes.
            var expanded by remember(reel.id) { mutableStateOf(false) }
            var overflows by remember(reel.id) { mutableStateOf(false) }
            Text(
                text = highlightHashtags(reel.caption),
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (overflows || expanded) expanded = !expanded },
            )
            if (overflows || expanded) {
                Text(
                    text = if (expanded) "Sembunyikan" else "Selengkapnya",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { expanded = !expanded },
                )
            }
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Author avatar — tap opens their TikTok-style profile. A small "+" (no
        // coloured background, just a hairline chip) sits under it to follow.
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222228))
                    .border(1.5.dp, Color.White, CircleShape)
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Follow (+) — hidden on your own reels AND once you've followed. No fill
            // colour, just a plain white "+" so it reads clean over any video.
            if (reel.authorId.isNotBlank() && reel.authorId != SyntraClient.myUserId && !followed) {
                Box(
                    modifier = Modifier
                        .offset(y = 8.dp)
                        .size(18.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showFollowSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, "Ikuti", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
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
            label = "Bagikan",
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
                .size(27.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
            .size(38.dp)
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
                modifier = Modifier.size(18.dp).clip(CircleShape),
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
private fun ShortsHeader(
    following: Boolean,
    onSelectFollowing: (Boolean) -> Unit,
    onPost: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Left: white round create/upload button.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(38.dp)
                .background(Color.White, CircleShape)
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
            ShortsTab("Following", active = following) { onSelectFollowing(true) }
            ShortsTab("For You", active = !following) { onSelectFollowing(false) }
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

/**
 * Fixed card at the very top of Shorts while a reel uploads. Full width, short,
 * with a progress bar shaped by an ETA (no numbers), a label, and a small
 * thumbnail at the far end. Tap to expand for a little more detail.
 */
@Composable
private fun UploadReelCard(
    thumb: androidx.compose.ui.graphics.ImageBitmap?,
    startMs: Long,
    etaMs: Long,
    uploading: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Drive a time-based fraction toward ~0.92 over the ETA, then snap to full when
    // the upload finishes — an ETA-shaped bar without ever showing a number.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uploading, startMs) {
        while (uploading) { now = System.currentTimeMillis(); delay(80) }
    }
    val target = if (!uploading) 1f else {
        val elapsed = (now - startMs).coerceAtLeast(0L).toFloat()
        (elapsed / etaMs.coerceAtLeast(1L).toFloat()).coerceIn(0.04f, 0.92f)
    }
    val frac by animateFloatAsState(targetValue = target, animationSpec = tween(320), label = "upload-bar")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF215151C))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = if (expanded) 14.dp else 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!uploading) "Video terunggah" else "Menunggu upload video..",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (expanded) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Reel kamu sedang diproses dan akan segera tayang.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Small thumbnail at the far end of the card.
            Box(
                modifier = Modifier
                    .size(if (expanded) 52.dp else 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222230)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(if (expanded) 12.dp else 9.dp))
        // ETA-shaped progress bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(ShortsTeal, NexusAccentSoft))),
            )
        }
    }
}

/** Rough upload ETA from the payload size — assumes ~300 KB/s, clamped sensibly. */
private fun estimateUploadEtaMs(sizeBytes: Int): Long {
    val bytesPerMs = 300.0 * 1024 / 1000 // ~307 B/ms
    return (sizeBytes / bytesPerMs).toLong().coerceIn(2500L, 40_000L)
}

/** A single frame from the video, for the upload card thumbnail. Best-effort. */
private fun reelThumbnail(context: Context, uri: Uri): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        android.media.MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            r.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.asImageBitmap()
        }
    }.getOrNull()

/** Shown on the Following tab when you don't follow anyone with shorts yet. */
@Composable
private fun FollowingEmpty() {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.People, null, tint = NexusTextSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("Belum ada video", color = NexusTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ikuti kreator dan short mereka muncul di sini. Sementara itu, buka For You.",
            color = NexusTextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    // Filter/sort of the list. Newest-first by default, like most comment UIs.
    var newestFirst by remember { mutableStateOf(true) }
    // Who may delete a comment: its author (mine), OR the reel owner (any comment on
    // their post). Matched by user id — the old username match failed when the local
    // username was blank/differently-cased, so my own comment couldn't be deleted.
    // The backend enforces the same rule.
    val myId = SyntraClient.myUserId
    val iOwnReel = reel.authorId.isNotBlank() && reel.authorId == myId
    var pendingDelete by remember { mutableStateOf<NetReelComment?>(null) }
    // The comment being replied to (null = a normal top-level comment).
    var replyingTo by remember { mutableStateOf<NetReelComment?>(null) }
    val focusRequester = remember { FocusRequester() }

    suspend fun refresh() {
        runCatching { SyntraClient.getReelComments(reel.id) }
            .onSuccess { comments.clear(); comments.addAll(it) }
        loading = false
    }

    LaunchedEffect(reel.id) { refresh() }

    // Realtime: the feed already subscribes to reel:<id>, so a comment from anyone
    // else on this reel arrives here — re-pull the list so it shows up live with
    // full author info (name, avatar, time). My own posts are handled optimistically
    // + refreshed on send, so skip the echo of my own event to avoid a double fetch.
    DisposableEffect(reel.id) {
        val listener = object : SocketListener {
            override fun onReelComment(reelId: String, userId: String, body: String) {
                if (reelId != reel.id || userId == SyntraClient.myUserId) return
                scope.launch { refresh() }
            }
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    // Group into top-level comments (sorted by the filter), each immediately
    // followed by its replies (always chronological). Emitted as a flat list of
    // (comment, isReply) so the LazyColumn can render replies indented under parents.
    val display by remember {
        derivedStateOf {
            val replies = comments.filter { it.parentId != null }.groupBy { it.parentId }
            val tops = comments.filter { it.parentId == null }
            val sortedTops = if (newestFirst) tops.sortedByDescending { it.createdAt }
            else tops.sortedBy { it.createdAt }
            buildList {
                for (t in sortedTops) {
                    add(t to false)
                    replies[t.id]?.sortedBy { it.createdAt }?.forEach { add(it to true) }
                }
            }
        }
    }

    fun send() {
        if (sending) return
        val body = input.trim()
        if (body.isEmpty()) return
        // 1-level threading: a reply always attaches to the top-level ancestor, so
        // replying to a reply still lands in the same thread (not a deeper level).
        // replyTo keeps the EXACT comment answered (even a reply inside the thread)
        // so it can be quoted inside the new reply.
        val target = replyingTo
        val parent = target?.let { it.parentId ?: it.id }
        val replyTo = target?.id
        sending = true
        input = ""
        replyingTo = null
        scope.launch {
            runCatching { SyntraClient.postReelComment(reel.id, body, parent, replyTo) }
                .onSuccess {
                    onPosted() // bump the rail's comment count live
                    refresh()  // pull the server copy (correct name/time/id/parent)
                }
                .onFailure {
                    input = body // restore so the text isn't lost
                    Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            sending = false
        }
    }

    // skipPartiallyExpanded = open at full height straight away, so the input
    // row is visible immediately (no dragging up first).
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF15151C),
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A bounded height + a weighted list (below) is what fixes the keyboard
                // bug: when the IME opens, imePadding shrinks the CONTENT area, the
                // comment list gives up the space, and the fixed-height input row rides
                // just above the keyboard instead of being squeezed/resized.
                .height((LocalConfiguration.current.screenHeightDp * 0.72f).dp)
                .padding(bottom = 10.dp)
                .imePadding(),
        ) {
            // Header: title + count, then filter chips.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Komentar",
                    color = NexusTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                if (comments.isNotEmpty()) {
                    Text(
                        compactCount(comments.size),
                        color = NexusTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CommentFilterChip("Terbaru", active = newestFirst) { newestFirst = true }
                CommentFilterChip("Terlama", active = !newestFirst) { newestFirst = false }
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            // Weighted: the list takes all the space left between the header and the
            // input row, and yields it back to the keyboard when the IME opens.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp)
                    }
                    comments.isEmpty() -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.ChatBubbleOutline, null,
                            tint = NexusTextSecondary, modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Belum ada komentar", color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Jadilah yang pertama berkomentar", color = NexusTextSecondary, fontSize = 12.sp)
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(display, key = { it.first.id }) { (c, isReply) ->
                            CommentRow(
                                c = c,
                                isReply = isReply,
                                canDelete = iOwnReel || (c.authorId.isNotBlank() && c.authorId == myId),
                                isMine = c.authorId.isNotBlank() && c.authorId == myId,
                                onLongPress = { pendingDelete = c },
                                onReply = { replyingTo = c; focusRequester.requestFocus() },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // "Replying to …" banner — a quoted preview of the exact comment being
            // answered (accent bar + @name + a snippet of its text), so it's clear
            // which message you're replying to, even a specific reply inside a thread.
            replyingTo?.let { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .padding(start = 10.dp)
                            .width(3.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(NexusAccentSoft),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 7.dp),
                    ) {
                        Text(
                            "Membalas @${r.username.ifBlank { r.displayName }.ifBlank { "pengguna" }}",
                            color = NexusAccentSoft,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (r.body.isNotBlank()) {
                            Text(
                                r.body,
                                color = NexusTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Close, "Batal balas",
                        tint = NexusTextSecondary,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(18.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { replyingTo = null },
                    )
                }
            }
            // Input row.
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
                        Text(
                            if (replyingTo != null) "Tulis balasan…" else "Tambahkan komentar…",
                            color = NexusTextSecondary, fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(NexusAccentSoft),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
                            ) { send() },
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

    // Confirm before deleting a comment.
    pendingDelete?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus komentar?", fontWeight = FontWeight.Bold) },
            text = { Text("Komentar ini akan dihapus permanen.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching { SyntraClient.deleteReelComment(reel.id, c.id) }
                            .onSuccess {
                                // Deleting a top-level comment removes its replies too
                                // (the server cascades); drop the comment AND anything
                                // whose parent is it, so the UI matches immediately.
                                val hadReplies = comments.any { it.parentId == c.id }
                                comments.removeAll { it.id == c.id || it.parentId == c.id }
                                Toast.makeText(
                                    context,
                                    if (hadReplies) "Komentar & balasannya dihapus" else "Komentar dihapus",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure { Toast.makeText(context, "Gagal hapus: ${it.message}", Toast.LENGTH_SHORT).show() }
                    }
                }) { Text("Hapus", color = Color(0xFFFF5D5D), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) {
                    Text("Batal", color = NexusTextSecondary)
                }
            },
            containerColor = Color(0xFF1E1E27),
        )
    }
}

/**
 * One comment: avatar, name · time, body, then a "Balas" (reply) action. Replies
 * ([isReply]) are indented and use a smaller avatar so a thread reads as a thread.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    c: NetReelComment,
    isReply: Boolean = false,
    canDelete: Boolean = false,
    isMine: Boolean = false,
    onLongPress: () -> Unit = {},
    onReply: () -> Unit = {},
) {
    val name = if (isMine) "Komentar Anda" else c.displayName.ifBlank { c.username }.ifBlank { "pengguna" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Long-press my own comment to delete it.
            .then(
                if (canDelete) {
                    Modifier.combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                } else {
                    Modifier
                },
            )
            .padding(start = if (isReply) 26.dp else 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (isReply) {
            // Thread connector: an L-shaped line from the parent's avatar column down
            // to this reply, so it's clear which comment a reply belongs to.
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(30.dp),
            ) {
                // Vertical stroke.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 1.dp)
                        .width(1.5.dp)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.16f)),
                )
                // Horizontal elbow into the avatar.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(y = 15.dp)
                        .padding(start = 1.dp)
                        .width(12.dp)
                        .height(1.5.dp)
                        .background(Color.White.copy(alpha = 0.16f)),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        CommentAvatar(url = c.avatarUrl, name = name, size = if (isReply) 30.dp else 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    // My own comment reads in the accent colour, like the "you" chip.
                    color = if (isMine) NexusAccentSoft else NexusTextPrimary,
                    fontSize = if (isReply) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isMine) {
                    // A small pill badge, same idea as "Anda" on your own Shorts.
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(NexusAccent.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .border(1.dp, NexusAccent.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text("Anda", color = NexusAccentSoft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                val rel = relativeCommentTime(c.createdAt)
                if (rel.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text("· $rel", color = NexusTextSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
            // A faded quote of the EXACT comment this reply answers — same shape as a
            // reply, just a subtle background and lowered opacity — so it's clear which
            // message is being replied to (a specific reply inside the thread, too).
            if (c.replyToId != null && (c.replyToUsername.isNotBlank() || c.replyToBody.isNotBlank())) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .alpha(0.72f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .width(2.5.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(NexusAccentSoft),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.padding(top = 5.dp, bottom = 5.dp, end = 10.dp)) {
                        if (c.replyToUsername.isNotBlank()) {
                            Text(
                                "@${c.replyToUsername}",
                                color = NexusAccentSoft,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (c.replyToBody.isNotBlank()) {
                            Text(
                                c.replyToBody,
                                color = NexusTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(c.body, color = NexusTextPrimary.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                "Balas",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onReply,
                    ),
            )
        }
    }
}

/** Round avatar with a colour-from-name fallback when there's no photo. */
@Composable
private fun CommentAvatar(url: String?, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(commentGradient(name.ifBlank { "?" }))),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CommentFilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) NexusAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (active) NexusAccent.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(50))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (active) NexusTextPrimary else NexusTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

private val commentGradients = listOf(
    listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFEE5A6F), Color(0xFFF29263)),
    listOf(Color(0xFFDA22FF), Color(0xFF9733EE)),
)

private fun commentGradient(key: String): List<Color> =
    commentGradients[(key.hashCode() and Int.MAX_VALUE) % commentGradients.size]

/** ISO-8601 timestamp → short relative label (Indonesian). Empty if unparseable. */
private fun relativeCommentTime(iso: String): String {
    if (iso.isBlank()) return ""
    val millis = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    val m = (System.currentTimeMillis() - millis) / 60_000
    return when {
        m < 1 -> "baru saja"
        m < 60 -> "${m}m"
        m < 1440 -> "${m / 60}j"
        m < 10080 -> "${m / 1440}h"
        else -> "${m / 10080}mg"
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
