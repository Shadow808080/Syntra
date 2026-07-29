package com.example.syntra

import android.content.Context
import android.net.Uri
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
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
import com.example.syntra.net.UploadCenter
import com.example.syntra.net.BlockMask
import com.example.syntra.net.BlockStore
import com.example.syntra.net.NotInterestedStore
import com.example.syntra.net.PipController
import com.example.syntra.net.ReelCache
import com.example.syntra.net.ReelDownloader
import com.example.syntra.net.ShortsFeedCache
import com.example.syntra.net.Translate
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.DangerFill
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import com.example.syntra.ui.theme.SyntraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Shorts / Reels — a chronological, vertically-swiped video feed (docs/api.md).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
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
    // Long-press on a reel opens the playback-settings sheet (moved out of the
    // app-wide Settings so it lives right where you watch). Non-null = which reel.
    var reelSettingsFor by remember { mutableStateOf<NetReel?>(null) }
    // Playback speed for the feed (applies to the current and following reels).
    var playbackSpeed by remember { mutableStateOf(1f) }
    // Reel awaiting a report (a reason dialog is shown), and the translated-caption
    // dialog (null = closed, "" = still translating).
    var reportFor by remember { mutableStateOf<NetReel?>(null) }
    var translation by remember { mutableStateOf<String?>(null) }
    // Tell the notifier we're on Shorts, so a "comment reply" toast is suppressed
    // here (it shows live) but still fires on every other screen.
    DisposableEffect(visible) {
        com.example.syntra.net.AppForeground.inShorts = visible
        onDispose { com.example.syntra.net.AppForeground.inShorts = false }
    }
    // Leaving Shorts entirely gives the pooled players back. Switching tabs does NOT
    // come through here — the pages release their players to the pool on their own and
    // an idle player holds no codec, so returning to the feed stays instant.
    DisposableEffect(Unit) {
        onDispose {
            com.example.syntra.net.ReelPlayerPool.releaseAll()
            // Don't leave full-screen armed for the next visit: coming back to a feed
            // with no header and no bottom bar reads as a broken screen, not a mode.
            com.example.syntra.net.CleanScreen.on = false
        }
    }
    // Seed from the feed cache so entering Shorts is instant — from memory on a tab
    // switch, and from DISK on a cold start, which is the case that used to mean a
    // spinner and a full round-trip before the first video existed. Videos themselves
    // already come off disk via ReelCache; this is about the list.
    val reels = remember {
        ShortsFeedCache.warm(context)
        mutableStateListOf<NetReel>().also { it.addAll(ShortsFeedCache.reels) }
    }
    var loading by remember { mutableStateOf(reels.isEmpty()) }
    // Persist whenever the feed changes, so the next launch is seeded. Debounced: the
    // list churns during a sync and there's no reason to rewrite the file each time.
    LaunchedEffect(Unit) {
        snapshotFlow { reels.toList() }
            .debounce(500)
            .collect { ShortsFeedCache.persist(context, it) }
    }
    var refreshing by remember { mutableStateOf(false) }
    // Owned by UploadCenter now — a screen must not decide whether an upload is running.
    val posting = UploadCenter.reelBusy
    // Raw picked video (awaiting trim), then the trimmed clip (awaiting caption).
    var pendingVideo by remember { mutableStateOf<Uri?>(null) }
    var trimmedVideo by remember { mutableStateOf<Uri?>(null) }
    var commentsFor by remember { mutableStateOf<NetReel?>(null) }
    // "Layar penuh": every piece of chrome off, just the clip. One flag drives it, so
    // the button on the feed and "Layar bersih" in the long-press sheet are the same
    // mode rather than two lookalikes that can disagree.
    val fullscreen = com.example.syntra.net.CleanScreen.on
    // Auto-advance is suspended while the comment sheet is up: the video the comments
    // belong to must not slide away underneath them mid-read. The reel loops instead
    // (ReelVideo keeps repeatMode in sync live), so playback never just stops dead.
    val autoScrollActive = autoScroll && commentsFor == null
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
    // Mirrored from UploadCenter rather than held locally, so the card is still there
    // (and still counting) when the user swipes away and comes back mid-upload.
    val activeUpload = UploadCenter.reel
    var uploadCardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(activeUpload) { if (activeUpload != null) uploadCardVisible = true }
    var uploadThumb by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val uploadStartMs = activeUpload?.startedAt ?: 0L
    val uploadEtaMs = activeUpload?.etaMs ?: 6000L

    // Following tab: show ONLY reels from people the user actually follows (the
    // feed already carries is_following per reel). Own reels are excluded so it
    // reads like TikTok's Following, not a mix with your own uploads.
    var showFollowing by remember { mutableStateOf(false) }
    val displayReels by remember {
        derivedStateOf {
            // distinctBy is the last line of defence: the pager keys pages by reel id,
            // and a single repeated id from the feed endpoint (paging overlap) crashes
            // the whole screen with "Key … was already used". Never trust the list.
            // Blocked creators are dropped here so their reels never reach the pager.
            // "Not interested" reels are hidden the same way (device-local).
            val base = reels.filterNot {
                BlockMask.hidden(context, it.creatorUsername, it.authorId) ||
                    NotInterestedStore.isHidden(context, it.id)
            }
            if (showFollowing) {
                base.filter { it.isFollowing && it.authorId != SyntraClient.myUserId }
                    .distinctBy { it.id }
            } else {
                base.distinctBy { it.id }
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
            reels.addAll(list.distinctBy { it.id })
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

    // A reel finished uploading (possibly while this screen was away). Refresh so the
    // new post is actually there when the user comes back.
    LaunchedEffect(UploadCenter.reelCompleted) {
        if (UploadCenter.reelCompleted > 0) {
            reload()
            showFollowing = false
            runCatching { pager.scrollToPage(0) }
        }
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
        reels.addAll(fresh.distinctBy { it.id })
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

    // Bookmark a reel. Optimistic like the like-toggle: the icon flips instantly and
    // the server call follows; saved reels show up in the profile's "saved" grid.
    fun toggleSave(reel: NetReel) {
        val idx = reels.indexOfFirst { it.id == reel.id }
        if (idx < 0) return
        val now = !reel.isSaved
        reels[idx] = reel.copy(isSaved = now)
        scope.launch {
            runCatching { SyntraClient.saveReel(reel.id, now) }
                .onFailure {
                    // Put it back if the server refused, so the icon never lies.
                    val i = reels.indexOfFirst { r -> r.id == reel.id }
                    if (i >= 0) reels[i] = reels[i].copy(isSaved = !now)
                }
        }
        Toast.makeText(context, if (now) "Disimpan" else "Dihapus dari simpanan", Toast.LENGTH_SHORT).show()
    }

    // "Tidak tertarik": remember the id and drop it from the feed straight away, so
    // it vanishes now and never returns (even after a refresh re-fetches the list).
    fun notInterested(reel: NetReel) {
        NotInterestedStore.mark(context, reel.id)
        reels.removeAll { it.id == reel.id }
        Toast.makeText(context, "Tidak akan ditampilkan lagi", Toast.LENGTH_SHORT).show()
    }

    // Download the reel's video to the phone's gallery (Movies/Syntra).
    fun downloadReel(reel: NetReel) {
        Toast.makeText(context, "Mengunduh video…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val ok = ReelDownloader.saveVideo(context, reel.mediaUrl, "syntra-${reel.id}.mp4")
            Toast.makeText(
                context,
                if (ok) "Tersimpan di galeri (Movies/Syntra)" else "Gagal mengunduh video",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Translate the reel's caption to Indonesian. Full spoken-word subtitles would
    // need speech recognition we don't have; the caption is what we can translate.
    fun translateReel(reel: NetReel) {
        val text = reel.caption.trim()
        if (text.isBlank()) {
            Toast.makeText(context, "Video ini tidak punya keterangan untuk diterjemahkan.", Toast.LENGTH_SHORT).show()
            return
        }
        translation = "" // opens the dialog in its loading state
        scope.launch {
            val t = Translate.translate(text, "id")
            translation = t?.ifBlank { null } ?: "Gagal menerjemahkan. Coba lagi."
        }
    }

    // Hide the host's bottom bar for the whole add-reels flow (trim + details).
    // Hide the bottom bar + lock tab-swipe while the add-reels flow OR a profile is
    // open, so the profile is truly full-screen (not covered by the nav bar).
    LaunchedEffect(pendingVideo != null, openProfileUser) {
        onOverlayChange(pendingVideo != null || openProfileUser != null)
    }

    // Is the feed the thing the user is actually looking at? A profile or a deep-linked
    // reel is drawn OVER the still-composed pager, so without this the reel underneath
    // kept playing — audible behind someone's profile — and, worse, kept its decoder
    // while the overlay's own player wanted one. Cheap phones have very few decoders to
    // go around, so nothing below the top surface is allowed to hold one.
    val feedOnTop = visible && openProfileUser == null && deepLinkReel == null

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
                    // Runs in UploadCenter, NOT in this screen's scope. Swiping to
                    // another tab disposes this composable, which used to cancel the
                    // upload mid-file with no error shown — the card simply disappeared
                    // and the reel never arrived.
                    UploadCenter.startReel(label = "Menerbitkan reel", etaMs = 6000L) {
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(src)?.use { it.readBytes() }
                        } ?: error("Tidak bisa membaca video")
                        UploadCenter.updateReel { it.copy(etaMs = estimateUploadEtaMs(bytes.size)) }
                        val thumb = withContext(Dispatchers.IO) { reelThumbnail(context, src) }
                        UploadCenter.updateReel { it.copy(thumb = thumb?.toString()) }
                        val mime = context.contentResolver.getType(src) ?: "video/mp4"
                        val ext = mime.substringAfterLast('/', "mp4")
                        val mediaId = SyntraClient.uploadMedia("video", ext, mime, bytes)
                        SyntraClient.postReel(mediaId, caption, visibility, commentsEnabled)
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
                    // In full-screen the bar stays down regardless of direction —
                    // otherwise scrolling back up would pop it into a mode whose whole
                    // point is that nothing but the video is on screen.
                    if (!com.example.syntra.net.CleanScreen.on) {
                        if (p > prevPage) BottomBarVisibility.visible = false
                        else if (p < prevPage) BottomBarVisibility.visible = true
                    }
                    prevPage = p
                }
                // Entering full-screen hides the bar; leaving restores it.
                LaunchedEffect(fullscreen) {
                    if (fullscreen) BottomBarVisibility.visible = false else BottomBarVisibility.visible = true
                }
                DisposableEffect(Unit) { onDispose { BottomBarVisibility.visible = true } }
                // Count a view whenever a reel settles on screen.
                LaunchedEffect(pager.currentPage, displayReels.size) {
                    displayReels.getOrNull(pager.currentPage)?.let { r ->
                        SyntraClient.fireAndForget { SyntraClient.viewReel(r.id) }
                    }
                    // Warm the next two reels so they're already on disk (and free to
                    // replay) by the time they scroll into view. Two, not one: a fast
                    // scroller outruns a single-page lookahead, and the reel after next
                    // is the one that would otherwise buffer from scratch.
                    for (ahead in 1..2) {
                        displayReels.getOrNull(pager.currentPage + ahead)?.mediaUrl?.let {
                            ReelCache.prefetch(context, it)
                        }
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
                        flingBehavior = rememberReelFling(pager),
                        // The neighbour IS composed now, so its caption, avatar and
                        // action rail are already measured when you swipe — that
                        // layout pass was a visible hitch on a slow phone. What used
                        // to make this expensive (a second video decoding alongside
                        // the first) is handled by `prewarm` instead: only the current
                        // page and the NEXT one hold a decoder, and only the current
                        // one actually plays.
                        beyondViewportPageCount = 1,
                    ) { page ->
                        val reel = displayReels.getOrNull(page) ?: return@VerticalPager
                        ReelPage(
                            reel = reel,
                            // Play only the reel in view *and* only while the feed is
                            // the top-most thing on screen.
                            active = feedOnTop && page == pager.currentPage,
                            // Prepare the one you're about to swipe onto, so it is
                            // already showing its first frame when it arrives.
                            prewarm = feedOnTop && page in pager.currentPage..(pager.currentPage + 1),
                            // Deleting reels lives in Settings › Profil now, not on the
                            // feed — so no delete affordance here.
                            onDelete = null,
                            onLike = { toggleLike(reel) },
                            onComment = { commentsFor = reel },
                            onSave = { toggleSave(reel) },
                            onShare = { shareReel(context, reel) },
                            onOpenProfile = {
                                if (reel.creatorUsername.isNotBlank()) openProfileUser = reel.creatorUsername
                            },
                            autoScroll = autoScrollActive,
                            speed = playbackSpeed,
                            onVideoEnded = {
                                // Auto-advance only when there's a next reel; the last
                                // one just stops (its own player already replayed once).
                                // Re-checked here as well as in `autoScrollActive`: the
                                // sheet can open between the clip ending and this firing.
                                if (autoScrollActive && page < displayReels.lastIndex) {
                                    scope.launch { pager.animateScrollToPage(page + 1) }
                                }
                            },
                            onLongPress = { reelSettingsFor = reel },
                        )
                    }
                }
            }
        }

        // Header floats over the feed — gone entirely in full-screen.
        if (!fullscreen) {
            ShortsHeader(
                following = showFollowing,
                onSelectFollowing = { showFollowing = it },
                onPost = {
                    if (posting) return@ShortsHeader
                    pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
            )
        }

        // Fixed upload card at the very top. Stays while the reel uploads, then
        // fills the bar and disappears. Only ever visible on this (Shorts) screen.
        val uploadFailed = activeUpload?.failed
        LaunchedEffect(posting, uploadFailed) {
            // A failed card must NOT auto-hide — it is the only place the reason is
            // shown, and dismissing it is what frees the upload slot.
            if (!posting && uploadFailed == null && uploadCardVisible) {
                delay(900); uploadCardVisible = false
            }
        }
        if (uploadCardVisible && !fullscreen) {
            UploadReelCard(
                thumb = uploadThumb,
                startMs = uploadStartMs,
                etaMs = uploadEtaMs,
                uploading = posting && uploadFailed == null,
                failed = uploadFailed,
                onDismiss = { UploadCenter.clearReel(); uploadCardVisible = false },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // "Ke atas" — appears once you've scrolled past the first reel and jumps back
        // to the top video.
        //
        // It used to be a bare circle pinned to the top-LEFT, which read as a stray
        // back arrow floating over the video and sat off-balance against the centred
        // tabs. It is now a labelled pill directly under those tabs — centred, so it
        // belongs to the feed rather than hovering beside it — and it slides in
        // instead of appearing out of nowhere mid-scroll.
        AnimatedVisibility(
            visible = displayReels.isNotEmpty() && pager.currentPage > 0 &&
                !uploadCardVisible && !fullscreen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 62.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
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
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        // Full-screen toggle. The SAME button in both states — it swaps its icon (and
        // label) for the exit affordance once the chrome is gone, so there is always
        // exactly one visible way back out. Sits opposite the "Ke atas" pill.
        if (displayReels.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 16.dp, top = 60.dp)
                    .size(40.dp)
                    // Dimmer in full-screen: it must stay findable without becoming
                    // the thing you look at instead of the video.
                    .background(
                        Color.Black.copy(alpha = if (fullscreen) 0.32f else 0.45f),
                        CircleShape,
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = if (fullscreen) 0.14f else 0.18f),
                        CircleShape,
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { com.example.syntra.net.CleanScreen.on = !fullscreen },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (fullscreen) "Keluar layar penuh" else "Layar penuh",
                    tint = Color.White.copy(alpha = if (fullscreen) 0.75f else 1f),
                    modifier = Modifier.size(23.dp),
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
            // Close the sheet first — the profile is full-screen, and leaving the sheet
            // stacked under it means backing out lands you on comments you'd finished.
            onOpenUser = { uname ->
                commentsFor = null
                if (uname.isNotBlank()) openProfileUser = uname
            },
        )
    }

    reelSettingsFor?.let { reel ->
        ReelSettingsSheet(
            autoScroll = autoScroll,
            onAutoScrollChange = { on ->
                autoScroll = on
                SettingsStore.setBool(context, SettingsStore.AUTO_SCROLL_REELS, on)
            },
            speed = playbackSpeed,
            onSpeedChange = { playbackSpeed = it },
            onCleanScreen = { reelSettingsFor = null; com.example.syntra.net.CleanScreen.on = true },
            onDownload = { reelSettingsFor = null; downloadReel(reel) },
            onPip = { reelSettingsFor = null; PipController.request() },
            onTranslate = { reelSettingsFor = null; translateReel(reel) },
            onNotInterested = { reelSettingsFor = null; notInterested(reel) },
            onReport = { reelSettingsFor = null; reportFor = reel },
            onDismiss = { reelSettingsFor = null },
        )
    }

    reportFor?.let { reel ->
        ReportReelDialog(
            onDismiss = { reportFor = null },
            onSubmit = { reason ->
                reportFor = null
                scope.launch {
                    runCatching { SyntraClient.reportReel(reel.id, reason) }
                        .onSuccess { Toast.makeText(context, "Laporan terkirim. Terima kasih.", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Gagal mengirim laporan: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            },
        )
    }

    translation?.let { result ->
        TranslationDialog(text = result, onDismiss = { translation = null })
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
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
                        .background(DangerFill, RoundedCornerShape(50))
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
    /** Hold a prepared decoder — see [ReelVideo]'s `prewarm`. */
    prewarm: Boolean = active,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSave: () -> Unit = {},
    onShare: () -> Unit,
    /** Non-null only when the signed-in user owns this reel. */
    onDelete: (() -> Unit)? = null,
    onOpenProfile: () -> Unit = {},
    /** When true, the clip plays once and [onVideoEnded] advances to the next reel. */
    autoScroll: Boolean = false,
    /** Playback speed multiplier (0.5–2×), driven by the settings sheet. */
    speed: Float = 1f,
    onVideoEnded: () -> Unit = {},
    /** Long-press on the video opens the playback settings (auto-scroll). */
    onLongPress: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // Tap-to-pause, per reel. Reset when the reel scrolls off so coming back plays.
    var paused by remember { mutableStateOf(false) }
    // Scrolling to another reel resets pause. "Layar bersih" (clean screen) is NOT
    // reset here — once on, it stays on across every reel until a tap brings it back.
    LaunchedEffect(active) { if (!active) paused = false }

    // Pinch-to-zoom is GONE, deliberately. It only ever worked because the video was
    // drawn through the app's own GPU canvas, and that is exactly what was costing
    // 17–23 ms of GPU time per frame here (86% janky frames on the RMX2180). The feed
    // now hands frames straight to the system compositor, which cannot be scaled by a
    // graphicsLayer — so leaving the gesture in would have left a control that quietly
    // did nothing. See the note in [ReelVideo].
    // While in the floating PiP window, strip everything but the video.
    val inPip = PipController.inPip
    // "Layar bersih": same idea, on demand — hide all chrome for a clean view.
    val cleanScreen = com.example.syntra.net.CleanScreen.on

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
            prewarm = prewarm,
            speed = speed,
            modifier = Modifier.fillMaxSize(),
            loop = !autoScroll,
            seekToMs = seekReq,
            onDuration = { durMs = it },
            onPosition = { if (!scrubbing) posMs = it },
            onEnded = onVideoEnded,
        )

        // Tap layer over the upper video area only. The bottom strip (caption,
        // username, action rail) is left out so tapping those never pauses the video.
        //   • single tap → play/pause
        //   • double tap → like (never un-likes) + a heart bursts where you tapped
        //   • long press → playback settings
        // Read the latest like state/callback through updated-state so the gesture
        // block (keyed on Unit, never rebuilt) never acts on a stale reel.
        val likedNow by rememberUpdatedState(reel.isLiked)
        val onLikeLatest by rememberUpdatedState(onLike)
        var burstAt by remember { mutableStateOf<Offset?>(null) }
        var burstKey by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 200.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Always play/pause — including in full-screen. Leaving that mode
                        // is the toggle button's job now; when a stray tap could also do
                        // it, half the taps meant to pause dropped you out of full-screen
                        // instead.
                        onTap = { paused = !paused },
                        onLongPress = { onLongPress() },
                        onDoubleTap = { offset ->
                            // Always show the heart; only send a like when it isn't
                            // liked yet, so a double-tap can never toggle a like off.
                            if (!likedNow) onLikeLatest()
                            burstAt = offset
                            burstKey++
                        },
                    )
                },
        ) {
            burstAt?.let { pos ->
                // A shower of little hearts flies out, with the big heart popping on top.
                HeartFlurry(pos = pos, triggerKey = burstKey)
                LikeBurst(pos = pos, triggerKey = burstKey)
            }
        }

        // Paused indicator. Shown in full-screen too: it only exists while you have
        // deliberately paused, so it is feedback for an action rather than chrome, and
        // pausing with no acknowledgement at all reads as the tap not registering.
        if (paused && active && !inPip) {
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

        // Everything below is chrome over the video — hidden in the PiP window and in
        // "layar bersih" so the player shows just the clip.
        if (!inPip && !cleanScreen) {
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
                        onSave = onSave,
                        onShare = onShare,
                        onDelete = onDelete,
                        onOpenProfile = onOpenProfile,
                        active = active,
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
}

/**
 * The heart that blooms where you double-tapped a reel, then fades away — the
 * familiar "double-tap to like" flourish. Re-runs its animation whenever
 * [triggerKey] changes, so a second tap restarts the burst even at the same spot.
 */
@Composable
private fun LikeBurst(pos: Offset, triggerKey: Int) {
    val density = LocalDensity.current
    val heart = 116.dp
    val scale = remember { androidx.compose.animation.core.Animatable(0f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val rot = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(triggerKey) {
        // Tilt each pop a touch, alternating sides, so repeat taps feel lively.
        rot.snapTo(if (triggerKey % 2 == 0) -10f else 10f)
        alpha.snapTo(1f)
        scale.snapTo(0.2f)
        // Pop in, hold briefly, then drift up a little as it fades out.
        scale.animateTo(1f, tween(230, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        delay(320)
        launch { scale.animateTo(1.22f, tween(260)) }
        alpha.animateTo(0f, tween(260))
    }
    val sizePx = with(density) { heart.toPx() }
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = Color(0xFFFF3040),
        modifier = Modifier
            .offset { IntOffset((pos.x - sizePx / 2f).roundToInt(), (pos.y - sizePx / 2f).roundToInt()) }
            .size(heart)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = rot.value
            },
    )
}

/** One little heart in the double-tap shower: a direction, distance, size and tint. */
private data class HeartParticle(
    val angle: Float,
    val distancePx: Float,
    val sizeDp: androidx.compose.ui.unit.Dp,
    val rotation: Float,
    val startDelay: Float,
    val tint: Color,
)

/**
 * A shower of small hearts that fan outward (mostly upward) from the double-tap point
 * and fade as they fly — TikTok-style "tap-tap" flurry. Re-emits whenever [triggerKey]
 * changes, so every double-tap sends a fresh burst.
 */
@Composable
private fun HeartFlurry(pos: Offset, triggerKey: Int) {
    if (triggerKey == 0) return
    val density = LocalDensity.current
    val tints = listOf(Color(0xFFFF3040), Color(0xFFFF5C8A), Color(0xFFFF2D55), Color(0xFFFF7AA8))
    // Fresh randomised particles each tap; stable across recompositions within a burst.
    val particles = remember(triggerKey) {
        List(14) {
            // Spread in a fan pointing up: -90° is straight up, ±70° to the sides.
            val deg = -90f + (Random.nextFloat() * 140f - 70f)
            HeartParticle(
                angle = Math.toRadians(deg.toDouble()).toFloat(),
                distancePx = with(density) { (70 + Random.nextInt(140)).dp.toPx() },
                sizeDp = (14 + Random.nextInt(26)).dp,
                rotation = (Random.nextInt(80) - 40).toFloat(),
                startDelay = Random.nextFloat() * 0.22f,
                tint = tints[Random.nextInt(tints.size)],
            )
        }
    }
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(triggerKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1000, easing = LinearEasing))
    }
    particles.forEach { p ->
        val local = ((progress.value - p.startDelay) / (1f - p.startDelay)).coerceIn(0f, 1f)
        if (local <= 0f) return@forEach
        // Ease-out distance so they shoot fast then settle.
        val eased = 1f - (1f - local) * (1f - local)
        val dist = p.distancePx * eased
        val x = pos.x + cos(p.angle) * dist
        val y = pos.y + sin(p.angle) * dist
        val a = if (local < 0.15f) local / 0.15f else 1f - (local - 0.15f) / 0.85f
        val scale = (0.4f + local * 0.8f).coerceAtMost(1.15f)
        val sizePx = with(density) { p.sizeDp.toPx() }
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = p.tint,
            modifier = Modifier
                .offset { IntOffset((x - sizePx / 2f).roundToInt(), (y - sizePx / 2f).roundToInt()) }
                .size(p.sizeDp)
                .graphicsLayer {
                    this.alpha = a.coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                    rotationZ = p.rotation
                },
        )
    }
}

/**
 * Playback & actions sheet for Shorts, opened by long-pressing a reel. Hosts the
 * playback controls (speed, auto-scroll, picture-in-picture) plus per-reel actions
 * (download, translate, not-interested, report) — all right where you're watching.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReelSettingsSheet(
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onCleanScreen: () -> Unit,
    onDownload: () -> Unit,
    onPip: () -> Unit,
    onTranslate: () -> Unit,
    onNotInterested: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NexusSurface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        ) {
            Text(
                "Pengaturan",
                color = NexusTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            // Speed — a row of pills; the active one is highlighted.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(ShortsTeal.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Speed, null, tint = ShortsTeal, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("Kecepatan", color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0.5f, 0.75f, 1f, 1.5f, 2f).forEach { s ->
                    val selected = kotlin.math.abs(s - speed) < 0.01f
                    val label = if (s == s.toInt().toFloat()) "${s.toInt()}×" else "${s}×"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) ShortsTeal else NexusSurfaceElevated)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onSpeedChange(s) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.Black else NexusTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            // Auto-scroll toggle.
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
                    Icon(Icons.Filled.SwapVert, null, tint = ShortsTeal, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Geser otomatis", color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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

            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
            Spacer(Modifier.height(6.dp))

            ReelActionRow(
                icon = Icons.Filled.Fullscreen,
                title = "Layar bersih",
                subtitle = "Sembunyikan tampilan, tampilkan video saja",
                onClick = onCleanScreen,
            )
            ReelActionRow(
                icon = Icons.Filled.PictureInPictureAlt,
                title = "Gambar-dalam-gambar",
                subtitle = "Putar dalam jendela mengambang",
                onClick = onPip,
            )
            ReelActionRow(
                icon = Icons.Filled.Download,
                title = "Unduh",
                subtitle = "Simpan video ke galeri",
                onClick = onDownload,
            )
            ReelActionRow(
                icon = Icons.Filled.Translate,
                title = "Subtitel & terjemahan",
                subtitle = "Terjemahkan keterangan ke bahasa Indonesia",
                onClick = onTranslate,
            )
            ReelActionRow(
                icon = Icons.Filled.VisibilityOff,
                title = "Tidak tertarik",
                subtitle = "Sembunyikan video ini dari beranda",
                onClick = onNotInterested,
            )
            ReelActionRow(
                icon = Icons.Filled.Flag,
                title = "Laporkan",
                subtitle = "Beri tahu kami jika ada masalah",
                tint = Color(0xFFFF6B6B),
                onClick = onReport,
            )
        }
    }
}

/** One tappable action row in the reel settings sheet (icon + title + subtitle). */
@Composable
private fun ReelActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = ShortsTeal,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NexusTextSecondary, fontSize = 12.sp)
        }
    }
}

/**
 * Report reasons for a reel. Picking one submits immediately — no free-text step,
 * which is both simpler and how most short-video apps do a first-pass report.
 */
@Composable
internal fun ReportReelDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val reasons = listOf(
        "Spam atau menyesatkan",
        "Konten seksual",
        "Kekerasan atau berbahaya",
        "Ujaran kebencian",
        "Pelecehan atau perundungan",
        "Lainnya",
    )
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp),
        ) {
            Text(
                "Laporkan video",
                color = NexusTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Pilih alasan pelaporan.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(10.dp))
            reasons.forEach { reason ->
                Text(
                    reason,
                    color = NexusTextPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSubmit(reason) }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                )
            }
            Text(
                "Batal",
                color = NexusTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
    }
}

/** Shows the translated caption ([text] == "" while still translating). */
@Composable
private fun TranslationDialog(text: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Terjemahan", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (text.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Menerjemahkan…", color = NexusTextSecondary, fontSize = 14.sp)
                }
            } else {
                Text(text, color = NexusTextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Tutup",
                color = NexusAccentSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
                    .padding(8.dp),
            )
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
 * Fling tuned for a full-screen reel feed, shared by the main feed and the profile
 * viewer so both swipe identically.
 *
 * Two departures from the default make the scroll feel smoother:
 *  - a lower `snapPositionalThreshold` (0.3 vs 0.5), so a short lazy flick still
 *    carries to the next reel instead of springing back — the stiff "won't let go"
 *    feeling people read as jank; and
 *  - a critically-damped (no-bounce) medium-stiffness settle, so the page glides
 *    into place quickly without the springy overshoot the default snap can show.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberReelFling(pager: PagerState) =
    PagerDefaults.flingBehavior(
        state = pager,
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        snapPositionalThreshold = 0.3f,
    )

/**
 * Loops the reel's video while [playing]; pauses otherwise (off-screen or tapped).
 *
 * Renders into a `SurfaceView`, which gets its own hardware-composited layer instead
 * of being drawn through the app's GPU canvas every frame.
 *
 * This file used a `TextureView` for a long time, on the reasoning that a SurfaceView
 * owns a window layer and can draw over its neighbours mid-swipe. That trade was
 * measured on the target device and is not worth it: at 86% janky frames the GPU was
 * spending 17 ms (median) to 23 ms (p90) per frame against a 16.7 ms budget, almost
 * all of it uploading and compositing a full-screen video texture. Since Android 10
 * the compositor keeps a SurfaceView's position in step with its view, so the old
 * mid-swipe artefact is largely historical.
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
    /**
     * Hold a decoder for this page. True for the reel on screen AND the next one, so
     * the next swipe finds a player that is already prepared and showing its first
     * frame. False releases the player back to the pool — a page three swipes away
     * keeps its layout but costs nothing.
     */
    prewarm: Boolean = true,
    /** Loop forever (true) vs play once then fire [onEnded] (used for auto-scroll). */
    loop: Boolean = true,
    /** Playback speed multiplier applied to the player (1× = normal). */
    speed: Float = 1f,
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

    // The TextureView's surface, held in state so it can be handed to whichever player
    // this page currently owns — and, unlike before, actually RELEASED when the view
    // goes away. Every `Surface(st)` that was never released leaked a graphics buffer;
    // a few dozen swipes of that is exactly how a cheap phone runs out of them.
    var surface by remember { mutableStateOf<Surface?>(null) }

    // The player is borrowed from [ReelPlayerPool] rather than built here, and only
    // while this page is on screen or immediately next ([prewarm]). Pages further away
    // hold no decoder at all — which is what lets the neighbour stay composed (so its
    // caption/buttons are already laid out) without three videos fighting for codecs.
    var player by remember(url) { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }

    DisposableEffect(url, prewarm) {
        if (!prewarm) return@DisposableEffect onDispose { }
        val p = com.example.syntra.net.ReelPlayerPool.acquire(context)
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                videoW = size.width
                videoH = size.height
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    androidx.media3.common.Player.STATE_READY -> {
                        ready = true
                        onDurationLatest(p.duration.coerceAtLeast(0L).toInt())
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
        }
        p.addListener(listener)
        p.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
        p.repeatMode = if (loopLatest) {
            androidx.media3.common.Player.REPEAT_MODE_ONE
        } else {
            androidx.media3.common.Player.REPEAT_MODE_OFF
        }
        // Prepared but NOT started. ExoPlayer renders the first frame onto the surface
        // as soon as it has one, so by the time this page is swiped to, the picture is
        // already sitting there — no shimmer, no black flash, just play.
        p.prepare()
        player = p
        onDispose {
            p.removeListener(listener)
            player = null
            ready = false
            com.example.syntra.net.ReelPlayerPool.recycle(p)
        }
    }

    // Attach the surface to whichever player is current — either can arrive first.
    LaunchedEffect(player, surface) {
        val p = player ?: return@LaunchedEffect
        runCatching { p.setVideoSurface(surface) }
    }

    // Keep looping in sync if the setting flips while a reel is on screen.
    LaunchedEffect(loop, player) {
        player?.repeatMode = if (loop) {
            androidx.media3.common.Player.REPEAT_MODE_ONE
        } else {
            androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }

    // Apply the chosen playback speed (pitch left at 1 so audio stays natural-ish).
    LaunchedEffect(speed, player) {
        runCatching { player?.setPlaybackSpeed(speed) }
    }

    // Seek when the scrubber asks. ExoPlayer's default seek parameters are exact, so
    // dragging shows a precise frame while paused instead of jumping to a keyframe.
    LaunchedEffect(seekToMs) {
        val ms = seekToMs ?: return@LaunchedEffect
        if (ready) runCatching { player?.seekTo(ms.toLong()) }
    }

    // Feed the scrubber: sample the real playback position while it plays. Gated on
    // `playing` so a paused or off-screen page isn't waking up five times a second
    // to report a position that hasn't moved.
    LaunchedEffect(ready, playing, player) {
        val p = player ?: return@LaunchedEffect
        if (!ready || !playing) return@LaunchedEffect
        while (true) {
            onPosition(runCatching { p.currentPosition.toInt() }.getOrDefault(0))
            delay(200)
        }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        // Aspect-fit by SIZING the view, not by scaling it.
        //
        // This used to stretch the surface across the whole page and then squash it
        // back with a graphicsLayer. Two costs came with that: a full-screen offscreen
        // render target every frame, and — because only a TextureView's contents follow
        // such a transform — it forced the heavier of Android's two video views.
        //
        // Measured on the RMX2180 before this change: 86% janky frames, with the GPU
        // alone taking 17 ms at the median and 23 ms at p90 against a 16.7 ms budget.
        // GPU-bound, not CPU-bound: a full-screen video texture was being uploaded and
        // composited by the app every single frame.
        //
        // A SurfaceView instead hands the frames to the system compositor, which puts
        // them on their own hardware layer and never routes them through our canvas.
        val boxW = maxWidth
        val boxH = maxHeight
        val fitted = if (videoW > 0 && videoH > 0) {
            val fit = minOf(boxW.value / videoW, boxH.value / videoH)
            androidx.compose.ui.unit.DpSize((videoW * fit).dp, (videoH * fit).dp)
        } else {
            androidx.compose.ui.unit.DpSize(boxW, boxH)
        }

        AndroidView(
            factory = { ctx ->
                android.view.SurfaceView(ctx).apply {
                    holder.addCallback(object : android.view.SurfaceHolder.Callback {
                        override fun surfaceCreated(h: android.view.SurfaceHolder) {
                            surface = h.surface
                        }

                        override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, ht: Int) {
                            surface = h.surface
                        }

                        override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
                            // Detach from the player BEFORE the system tears the surface
                            // down; the other order hands ExoPlayer a dead surface.
                            // The holder owns this one, so we must NOT release it here.
                            surface = null
                        }
                    })
                }
            },
            modifier = Modifier.size(fitted.width, fitted.height),
        )

        if (!ready && !prewarm) {
            // An off-screen page that holds no decoder: plain black. Running a
            // breathing shimmer here would keep an animation clock ticking for a page
            // nobody is looking at.
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else if (!ready) {
            // A soft breathing placeholder fills the frame while the video buffers,
            // so the screen never shows a black void during load. The spinner is held
            // back briefly: with a prewarmed player most reels are ready well inside
            // that window, and flashing a spinner for 200 ms reads as jank, not speed.
            ShimmerFill(Modifier.fillMaxSize())
            var showSpinner by remember(url) { mutableStateOf(false) }
            LaunchedEffect(url) { delay(350); showSpinner = true }
            when {
                failed -> Text("Video gagal dimuat", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                showSpinner -> CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.7f),
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    // Play/pause follows the current page and the tap-to-pause toggle. ExoPlayer
    // honours playWhenReady even before it's buffered, so no `ready` gate is needed —
    // it starts the instant it can.
    LaunchedEffect(playing, player) {
        if (playing) com.example.syntra.net.MusicPlayer.pauseForExternalAudio() // don't talk over music
        runCatching { player?.playWhenReady = playing }
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
    // Profile opened by tapping an @mention in a comment (null = viewer only).
    var openProfileUser by remember { mutableStateOf<String?>(null) }

    fun toggleLike(reel: NetReel) {
        val idx = items.indexOfFirst { it.id == reel.id }
        if (idx < 0) return
        val now = !reel.isLiked
        items[idx] = reel.copy(isLiked = now, likeCount = (reel.likeCount + if (now) 1 else -1).coerceAtLeast(0))
        scope.launch { runCatching { SyntraClient.likeReel(reel.id, now) } }
    }
    fun toggleSave(reel: NetReel) {
        val idx = items.indexOfFirst { it.id == reel.id }
        if (idx < 0) return
        val now = !reel.isSaved
        items[idx] = reel.copy(isSaved = now)
        scope.launch {
            runCatching { SyntraClient.saveReel(reel.id, now) }
                .onFailure {
                    val i = items.indexOfFirst { r -> r.id == reel.id }
                    if (i >= 0) items[i] = items[i].copy(isSaved = !now)
                }
        }
        Toast.makeText(context, if (now) "Disimpan" else "Dihapus dari simpanan", Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Same prewarm shape as the main feed — see the pager there for the reasoning.
        VerticalPager(
            state = pager,
            beyondViewportPageCount = 1,
            flingBehavior = rememberReelFling(pager),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val reel = items[page]
            ReelPage(
                reel = reel,
                active = page == pager.currentPage,
                prewarm = page in pager.currentPage..(pager.currentPage + 1),
                onLike = { toggleLike(reel) },
                onComment = { commentsFor = reel },
                onSave = { toggleSave(reel) },
                onShare = { shareReel(context, reel) },
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
            onOpenUser = { uname ->
                commentsFor = null
                if (uname.isNotBlank()) openProfileUser = uname
            },
        )
    }

    // A mention tapped inside the viewer opens that profile over it.
    openProfileUser?.let { uname ->
        ProfileScreen(username = uname, onClose = { openProfileUser = null })
    }
}

/**
 * In-memory feed cache so returning to the Shorts tab is instant. The home is kept
 * light by NOT keeping Shorts composed off-screen (MainTabs beyondViewportPageCount=1),
 * so this holds the last feed + is re-seeded on the next entry. Cleared on sign-out.
 */
private val ShortsTeal = Color(0xFF20D5C4)

/**
 * A gentle breathing placeholder shown behind media while it loads, so photos and
 * videos never reveal a black/empty void mid-load. Animates alpha only (no layout),
 * so it's cheap and honours the motion discipline.
 */
/**
 * A loading placeholder whose highlight sweeps across exactly ONCE, then settles
 * into a flat block.
 *
 * The looping kind is the reflex, but a shimmer that never stops stops meaning
 * "loading" — it becomes wallpaper, and on a cheap phone it keeps an animation clock
 * running for as long as the row is composed. One pass announces the wait; the flat
 * block that remains is enough to say the space is still reserved.
 */
@Composable
private fun OneShotSkeleton(modifier: Modifier = Modifier) {
    val sweep = remember { Animatable(-0.6f) }
    LaunchedEffect(Unit) { sweep.animateTo(1.6f, tween(1150, easing = LinearEasing)) }
    androidx.compose.foundation.Canvas(modifier) {
        drawRect(Color.White.copy(alpha = 0.07f))
        val w = size.width
        val x = sweep.value * w
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.14f),
                    Color.Transparent,
                ),
                start = Offset(x - w * 0.34f, 0f),
                end = Offset(x + w * 0.34f, 0f),
            ),
        )
    }
}

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
                    NexusSurfaceElevated.copy(alpha = alpha),
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
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(15.dp))
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

/**
 * Hands a short to Android's share sheet.
 *
 * This was a toast that said "segera hadir" on a button that has always looked
 * functional. There is no public web page for a reel yet, so what gets shared is the
 * creator, the caption, and the direct media link — which does open and play. That is
 * a real share; a promise is not.
 */
private fun shareReel(context: Context, reel: NetReel) {
    val who = reel.creatorUsername.ifBlank { reel.creatorName }.ifBlank { "seseorang" }
    val text = buildString {
        append("Tonton short dari @").append(who).append(" di Syntra")
        if (reel.caption.isNotBlank()) append("\n\n").append(reel.caption.take(200))
        if (reel.mediaUrl.isNotBlank()) append("\n\n").append(reel.mediaUrl)
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(android.content.Intent.createChooser(send, "Bagikan short"))
    }.onFailure {
        Toast.makeText(context, "Tidak ada aplikasi untuk berbagi.", Toast.LENGTH_SHORT).show()
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

/**
 * Marks up body text: `@username` becomes a tappable link in the theme accent,
 * `#hashtag` keeps the feed's teal.
 *
 * The mention is what the tag button writes, and until now it was plain grey text —
 * indistinguishable from the rest of the sentence, and dead. It carries a real
 * notification to a real account, so it should look like it points somewhere and
 * actually go there.
 *
 * Trailing punctuation is stripped from the username but kept in the visible text, so
 * "cek @reza, mantap" links to `reza` and still reads with its comma.
 */
internal fun mentionedText(
    text: String,
    mentionColour: Color,
    onOpenUser: (String) -> Unit,
) = buildAnnotatedString {
    // Scanned with a regex over the WHOLE string rather than split on " ".
    //
    // Splitting on a literal space was wrong twice over. Comment bodies come from the
    // server and are not guaranteed single-line, so "@reza\nkeren" produced the handle
    // "reza\nkeren" — a link to an account that cannot exist, with the rest of the line
    // swallowed into it — while "keren\n@reza" was not detected as a mention at all.
    // And rejoining the tokens with " " quietly collapsed any run of repeated spaces
    // in someone's comment.
    //
    // The character class is also what makes trailing punctuation fall away on its own:
    // "@reza," stops at the comma because a comma cannot be part of a handle. Only "."
    // needs trimming afterwards, since it is legal inside a username but not at the end
    // of a sentence.
    val token = Regex("[@#][A-Za-z0-9_.]+")
    var cursor = 0
    for (m in token.findAll(text)) {
        if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
        cursor = m.range.last + 1

        val word = m.value
        val handle = word.substring(1).trimEnd('.')
        if (word[0] == '@' && handle.isNotEmpty()) {
            withLink(
                LinkAnnotation.Clickable(
                    tag = "mention:$handle",
                    styles = TextLinkStyles(
                        style = SpanStyle(color = mentionColour, fontWeight = FontWeight.SemiBold),
                    ),
                ) { onOpenUser(handle) },
            ) { append(word) }
        } else if (word[0] == '#' && handle.isNotEmpty()) {
            withStyle(SpanStyle(color = ShortsTeal, fontWeight = FontWeight.SemiBold)) { append(word) }
        } else {
            append(word)
        }
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
private fun ReelActions(
    reel: NetReel,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSave: () -> Unit = {},
    onShare: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onOpenProfile: () -> Unit = {},
    /** False for a page held ready off-screen — stops its record spinning. */
    active: Boolean = true,
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
                    .background(NexusStroke)
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
                    Icon(Icons.Rounded.Add, "Ikuti", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        RailItem(
            icon = if (reel.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            tint = if (reel.isLiked) Color(0xFFFF3B5C) else Color.White,
            label = compactCount(reel.likeCount),
            onClick = onLike,
        )
        RailItem(
            icon = Icons.Rounded.ChatBubbleOutline,
            tint = Color.White,
            label = compactCount(reel.commentCount),
            onClick = onComment,
        )
        // Save (bookmark) — fills in once saved; the reel then shows up in the
        // profile's "disimpan" grid.
        RailItem(
            icon = if (reel.isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            tint = if (reel.isSaved) Color(0xFFFFD166) else Color.White,
            label = "Simpan",
            onClick = onSave,
        )
        RailItem(
            icon = Icons.AutoMirrored.Rounded.Reply,
            tint = Color.White,
            label = "Bagikan",
            onClick = onShare,
        )
        // Owner-only: remove my own reel (kept subtle, others never see it).
        if (onDelete != null) {
            RailItem(
                icon = Icons.Rounded.DeleteOutline,
                tint = Color(0xFFFF5D5D),
                label = "Hapus",
                onClick = onDelete,
            )
        }
        SpinningMusicDisc(avatarUrl = reel.creatorAvatarUrl, spinning = active)
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
            SheetRow(Icons.Rounded.Add, "Ikuti", onFollow)
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
                .size(24.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            // Drop the font's built-in vertical padding so the count sits snug under
            // the icon instead of floating away from it.
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeight = 12.sp,
            ),
        )
    }
}

/**
 * The little record that spins at the bottom of the action rail.
 *
 * Only spins while [spinning]. The pager keeps the neighbouring page composed so its
 * layout is ready before you swipe onto it, which meant TWO of these were driving a
 * frame every 16 ms forever — including one for a page nobody was looking at. An
 * animation running off-screen still forces the whole frame to be produced.
 */
@Composable
private fun SpinningMusicDisc(avatarUrl: String?, spinning: Boolean = true) {
    val angle = if (spinning) {
        val transition = rememberInfiniteTransition(label = "disc")
        val a by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        )
        a
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer { rotationZ = angle }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF3A3A3A), NexusSurface)))
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
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
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
            Icon(Icons.Rounded.Add, "Unggah", tint = Color(0xFF0A1414), modifier = Modifier.size(26.dp))
        }
        // Centre: the two feeds. Indonesian, because every other label in the app is
        // ("Untuk Kamu" / "Mengikuti") — an English pair here read as a leftover from
        // a different product.
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ShortsTab("Mengikuti", active = following) { onSelectFollowing(true) }
            ShortsTab("Untuk Kamu", active = !following) { onSelectFollowing(false) }
        }
        // Right: search.
        Icon(
            imageVector = Icons.Rounded.Search,
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
    // Everything animates rather than snaps. The old tab jumped 15sp -> 18sp on
    // selection, which shoved its neighbour sideways on every tap — the row visibly
    // reflowed each time you switched feed.
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 1f else 0.6f,
        animationSpec = tween(220),
        label = "tab-alpha",
    )
    val underline by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(220),
        label = "tab-underline",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            // ONE size for both states — weight and opacity carry the selection, so
            // the label never changes width and the row never reflows.
            color = Color.White.copy(alpha = alpha),
            fontSize = 16.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            // A soft shadow so white text stays readable over a bright video frame.
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.55f),
                    offset = Offset(0f, 1f),
                    blurRadius = 6f,
                ),
            ),
        )
        Spacer(Modifier.height(5.dp))
        // The underline grows out of the centre instead of blinking on.
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = underline
                    this.alpha = underline
                }
                .width(20.dp)
                .height(2.5.dp)
                .background(ShortsTeal, RoundedCornerShape(50)),
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
        Icon(Icons.Rounded.MusicNote, null, tint = NexusTextSecondary, modifier = Modifier.size(40.dp))
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
    /** Non-null when the upload failed — shows the reason and a way to dismiss it. */
    failed: String? = null,
    onDismiss: () -> Unit = {},
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
                    .background(NexusStroke),
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
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(if (expanded) 12.dp else 9.dp))
        if (failed != null) {
            // A failed upload has to be BOTH visible and dismissible.
            //
            // `failed` was written by UploadCenter and read by nothing, and clearReel()
            // had no callers at all — so a reel that failed on a flaky network left the
            // slot occupied forever. `startReel` returns early while busy, which meant
            // every later upload was silently dropped while this card sat at ~100%
            // claiming to still be working.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    failed,
                    color = Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Tutup",
                    color = NexusAccentSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
                )
            }
        } else {
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
}

/** Rough upload ETA from the payload size — assumes ~300 KB/s, clamped sensibly. */
private fun estimateUploadEtaMs(sizeBytes: Int): Long {
    val bytesPerMs = 300.0 * 1024 / 1000 // ~307 B/ms
    return (sizeBytes / bytesPerMs).toLong().coerceIn(2500L, 40_000L)
}

/** A single frame from the video, for the upload card thumbnail. Best-effort. */
private fun reelThumbnail(context: Context, uri: Uri): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        // NOT `.use {}` — MediaMetadataRetriever only implements AutoCloseable on API
        // 29+, and minSdk is 26; release() in a finally works on every version.
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            r.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.asImageBitmap()
        } finally {
            r.release()
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
                .background(NexusSurfaceElevated, RoundedCornerShape(22.dp))
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
private fun ReelCommentsSheet(
    reel: NetReel,
    onDismiss: () -> Unit,
    onPosted: () -> Unit = {},
    /** Tapping an `@username` in a comment opens that person's profile. */
    onOpenUser: (String) -> Unit = {},
) {
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
    // Long-press menu on my own comment (edit / delete).
    var actionsFor by remember { mutableStateOf<NetReelComment?>(null) }
    // The comment being edited (null = writing a new one). Reuses the same composer
    // rather than a second text box — one input, two modes, like the reply flow.
    var editing by remember { mutableStateOf<NetReelComment?>(null) }
    // The comment being replied to (null = a normal top-level comment).
    var replyingTo by remember { mutableStateOf<NetReelComment?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // A GIF chosen to ride along with the next comment (null = text-only comment).
    // Two sources, so both are held: a content:// uri when it came from the phone's
    // gallery, an https url when it came from GIPHY. Only ever one at a time.
    var pendingGifUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingGifUrl by remember { mutableStateOf<String?>(null) }
    val hasGif = pendingGifUri != null || pendingGifUrl != null
    var showGifPicker by remember { mutableStateOf(false) }
    // Tag picker: pick a person to @mention so they're pinged to watch this reel.
    var showTagPicker by remember { mutableStateOf(false) }

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

    // Suka / batal suka sebuah komentar. Optimistis: perbarui baris seketika, lalu
    // kirim ke server; kalau gagal, kembalikan ke keadaan semula supaya angka tak
    // berbohong. Diketik ulang penuh (copy) karena NetReelComment immutable.
    fun toggleCommentLike(c: NetReelComment) {
        val idx = comments.indexOfFirst { it.id == c.id }
        if (idx < 0) return
        val liked = !comments[idx].likedByMe
        comments[idx] = comments[idx].copy(
            likedByMe = liked,
            likeCount = (comments[idx].likeCount + if (liked) 1 else -1).coerceAtLeast(0),
        )
        scope.launch {
            runCatching { SyntraClient.likeReelComment(reel.id, c.id, liked) }
                .onFailure {
                    val j = comments.indexOfFirst { it.id == c.id }
                    if (j >= 0) comments[j] = comments[j].copy(
                        likedByMe = !liked,
                        likeCount = (comments[j].likeCount + if (liked) -1 else 1).coerceAtLeast(0),
                    )
                }
        }
    }

    /** Saves an edit in place: the row updates at once and rolls back if refused. */
    fun saveEdit() {
        val target = editing ?: return
        val body = input.trim()
        if (body.isEmpty() && target.mediaUrl == null) return
        editing = null
        input = ""
        focusManager.clearFocus(force = true)
        keyboard?.hide()

        val before = target.body
        val i = comments.indexOfFirst { it.id == target.id }
        // Stamp editedAt locally so the "diedit" marker appears immediately; the
        // refresh below replaces it with the server's own timestamp.
        if (i >= 0) comments[i] = comments[i].copy(body = body, editedAt = "now")
        scope.launch {
            runCatching { SyntraClient.updateReelComment(reel.id, target.id, body) }
                .onSuccess { refresh() }
                .onFailure { e ->
                    val j = comments.indexOfFirst { it.id == target.id }
                    if (j >= 0) comments[j] = comments[j].copy(body = before, editedAt = target.editedAt)
                    Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun send() {
        if (editing != null) { saveEdit(); return }
        if (sending) return
        val body = input.trim()
        val gifUri = pendingGifUri
        val gifUrl = pendingGifUrl
        // A comment needs at least text OR a GIF.
        if (body.isEmpty() && gifUri == null && gifUrl == null) return
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
        pendingGifUri = null
        pendingGifUrl = null

        // Show the comment IMMEDIATELY, marked pending. Uploading a GIF and waiting for
        // the round-trip takes long enough that posting used to look like it had done
        // nothing at all: the box emptied and the list sat unchanged until the refresh
        // landed. The local uri/url goes straight into mediaUrl so the GIF is on screen
        // before the server has ever seen it.
        val tempId = "pending-" + System.nanoTime()
        comments.add(
            NetReelComment(
                id = tempId,
                authorId = myId.orEmpty(),
                username = "",
                displayName = "Anda",
                body = body,
                createdAt = java.time.Instant.now().toString(),
                parentId = parent,
                replyToId = replyTo,
                replyToUsername = target?.username.orEmpty(),
                replyToBody = target?.body.orEmpty(),
                mediaUrl = gifUri?.toString() ?: gifUrl,
                mediaKind = if (gifUri != null || gifUrl != null) "image" else "",
                pending = true,
            ),
        )
        // Put the keyboard away once the comment is on its way.
        //
        // BOTH calls are needed. Hiding the IME alone does not stick: the text field
        // still holds focus, and a focused editor is exactly what makes Android bring
        // the keyboard straight back up. Focus has to be dropped first, and `force`
        // is required because this sheet is its own focus owner.
        focusManager.clearFocus(force = true)
        keyboard?.hide()

        scope.launch {
            runCatching {
                // Upload the attached GIF first (if any), then post the comment
                // referencing the confirmed media id.
                val mediaId = when {
                    gifUri != null -> uploadCommentGif(context, gifUri)
                    gifUrl != null -> uploadCommentGifFromUrl(gifUrl)
                    else -> null
                }
                SyntraClient.postReelComment(reel.id, body, parent, replyTo, mediaId)
            }
                .onSuccess {
                    onPosted() // bump the rail's comment count live
                    refresh()  // pull the server copy (correct name/time/id/parent)
                }
                .onFailure {
                    comments.removeAll { it.id == tempId }
                    input = body // restore so the text isn't lost
                    pendingGifUri = gifUri // keep the GIF too
                    pendingGifUrl = gifUrl
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
        containerColor = NexusSurface,
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
                                onLongPress = { actionsFor = c },
                                onReply = { replyingTo = c; focusRequester.requestFocus() },
                                onToggleLike = { toggleCommentLike(c) },
                                onOpenUser = onOpenUser,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // "Editing" banner, with its own way out. Without it the composer would
            // look identical to writing a new comment while actually about to
            // overwrite an old one.
            if (editing != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusAccentSoft.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Edit, null, tint = NexusAccentSoft, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Mengedit komentar",
                        color = NexusAccentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.Close, "Batal edit",
                        tint = NexusTextSecondary,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { editing = null; input = "" },
                    )
                }
            }
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
            // Attached-GIF preview — plays right here (the app-wide Coil loader has the
            // GIF decoder registered), so what you see is what gets posted.
            if (hasGif) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        AsyncImage(
                            model = pendingGifUri ?: pendingGifUrl,
                            contentDescription = "GIF komentar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { pendingGifUri = null; pendingGifUrl = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, "Hapus GIF", tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("GIF terlampir", color = NexusTextSecondary, fontSize = 12.sp)
                }
            }
            // Input row.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Attach a GIF — the same picker chat uses (gallery / GIPHY search /
                // animated text), so there is one GIF experience in the app, not two.
                Icon(
                    Icons.Filled.Gif, "Lampirkan GIF",
                    tint = if (hasGif) NexusAccentSoft else NexusTextSecondary,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showGifPicker = true },
                )
                Spacer(Modifier.width(10.dp))
                // Tag someone (@mention) so they're pinged to watch this reel.
                Icon(
                    Icons.Filled.AlternateEmail, "Tandai seseorang",
                    tint = NexusTextSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showTagPicker = true },
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (input.isEmpty()) {
                        Text(
                            when {
                                editing != null -> "Ubah komentar…"
                                replyingTo != null -> "Tulis balasan…"
                                else -> "Tambahkan komentar…"
                            },
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
                // Sendable as soon as there's text OR an attached GIF. While editing,
                // a comment that already has a GIF may legitimately be emptied of text.
                if (input.isNotBlank() || hasGif || (editing != null && editing?.mediaUrl != null)) {
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
                            if (editing != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                            if (editing != null) "Simpan" else "Kirim",
                            tint = Color.White, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    // Long-press actions. Edit is offered only to the AUTHOR: the reel owner may
    // delete a comment on their post (moderation) but must never be able to rewrite
    // someone else's words.
    actionsFor?.let { c ->
        val mine = c.authorId.isNotBlank() && c.authorId == myId
        androidx.compose.ui.window.Dialog(onDismissRequest = { actionsFor = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusSurfaceElevated, RoundedCornerShape(20.dp))
                    .padding(vertical = 10.dp),
            ) {
                if (mine) {
                    CommentActionRow(Icons.Filled.Edit, "Edit komentar", NexusTextPrimary) {
                        actionsFor = null
                        editing = c
                        replyingTo = null
                        input = c.body
                        runCatching { focusRequester.requestFocus() }
                    }
                }
                CommentActionRow(Icons.Filled.Delete, "Hapus komentar", Color(0xFFFF5D5D)) {
                    actionsFor = null
                    pendingDelete = c
                }
                CommentActionRow(Icons.Filled.Close, "Batal", NexusTextSecondary) { actionsFor = null }
            }
        }
    }

    // GIF picker — reuses the chat one, so gallery GIFs, GIPHY search and generated
    // animated text all work here without a second implementation to keep in step.
    if (showGifPicker) {
        GifPickerSheet(
            // Focus comes back to the field after picking, so you can type a caption
            // with the GIF attached instead of having to tap into the box again.
            onGif = { url ->
                pendingGifUrl = url; pendingGifUri = null; showGifPicker = false
                runCatching { focusRequester.requestFocus() }
            },
            onGifDevice = { uri ->
                pendingGifUri = uri; pendingGifUrl = null; showGifPicker = false
                runCatching { focusRequester.requestFocus() }
            },
            onDismiss = { showGifPicker = false },
        )
    }

    // Tag picker: search users and insert @username into the comment box, so the
    // tagged person gets pinged (mention notification + deeplink) to watch this reel.
    if (showTagPicker) {
        TagPeopleSheet(
            onDismiss = { showTagPicker = false },
            onPick = { username ->
                showTagPicker = false
                if (username.isNotBlank()) {
                    val prefix = if (input.isEmpty() || input.endsWith(" ")) "" else " "
                    input = input + prefix + "@" + username + " "
                    focusRequester.requestFocus()
                }
            },
        )
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
            containerColor = NexusSurfaceElevated,
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
    onToggleLike: () -> Unit = {},
    /** Tapping an `@username` inside the body opens that person's profile. */
    onOpenUser: (String) -> Unit = {},
) {
    val name = if (isMine) "Komentar Anda" else c.displayName.ifBlank { c.username }.ifBlank { "pengguna" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A not-yet-confirmed comment is drawn back a little, so it reads as "on
            // its way" rather than as a comment that is already there.
            .alpha(if (c.pending) 0.55f else 1f)
            // Long-press my own comment to delete it.
            .then(
                if (canDelete && !c.pending) {
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
            if (c.body.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    mentionedText(c.body, NexusAccentSoft, onOpenUser),
                    color = NexusTextPrimary.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
            // Attached GIF, if any — a rounded, height-capped thumbnail. A skeleton
            // holds the space (at a sane placeholder size) until the GIF decodes, so
            // the row doesn't jolt when it finally lands.
            c.mediaUrl?.let { gif ->
                Spacer(Modifier.height(6.dp))
                var loaded by remember(gif) { mutableStateOf(false) }
                // FIXED frame, and the image fills it.
                //
                // Sizing this from the image's own intrinsic size deadlocked: an
                // unloaded painter measures 0×0, Coil resolves a 0×0 target and never
                // runs the request, so `loaded` never flips and the frame never gets a
                // size. The GIF uploaded and the comment posted — nothing was ever
                // drawn. A definite box also means the skeleton is exactly the size of
                // what replaces it, so nothing jumps.
                Box(
                    modifier = Modifier
                        .size(width = 168.dp, height = 118.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    if (!loaded) OneShotSkeleton(Modifier.matchParentSize())
                    AsyncImage(
                        model = gif,
                        contentDescription = "GIF komentar",
                        contentScale = ContentScale.Crop,
                        onState = { st -> loaded = st is coil.compose.AsyncImagePainter.State.Success },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            if (c.pending) {
                // Waiting for the server. A determinate bar that eases to nearly-full
                // ONCE and stops there — deliberately not a looping spinner, which
                // says "still working" forever and never says "almost done".
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    progress.animateTo(0.92f, tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mengirim…", color = NexusTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.10f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.value)
                                .clip(RoundedCornerShape(2.dp))
                                .background(NexusAccentSoft),
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    // A comment that can change silently after it has been replied to
                    // is a way to rewrite what a conversation meant. The marker is what
                    // makes an edit a correction rather than a quiet revision.
                    if (c.editedAt != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "diedit",
                            color = NexusTextSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
        // Trailing heart, like TikTok/Instagram: tap to like, the count sits under it.
        // Filled red when I've liked it, hollow otherwise. Small hit target of its own.
        Spacer(Modifier.width(8.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Top)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onToggleLike,
                )
                .padding(top = 2.dp, start = 2.dp, end = 2.dp),
        ) {
            Icon(
                imageVector = if (c.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (c.likedByMe) "Batal suka" else "Suka",
                tint = if (c.likedByMe) Color(0xFFFF3B5C) else NexusTextSecondary,
                modifier = Modifier.size(if (isReply) 15.dp else 17.dp),
            )
            if (c.likeCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    compactCount(c.likeCount),
                    color = NexusTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** Round avatar with a colour-from-name fallback when there's no photo. */
@Composable
private fun CommentActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}

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

/**
 * Uploads a GIF picked from the phone and returns the confirmed media id.
 *
 * The bytes are sent **verbatim**. Decoding to a Bitmap and re-encoding — which is
 * what the old photo path did — keeps only the first frame, so the GIF would arrive
 * as a still image. It goes up as `kind=image` because that is what the backend's
 * `add_reel_comment` accepts for a comment attachment; the `.gif` extension and
 * mime are what make it animate on the way back down.
 */
private suspend fun uploadCommentGif(context: android.content.Context, uri: android.net.Uri): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        SyntraClient.uploadMedia("image", "gif", "image/gif", bytes)
    }

/**
 * Same, for a GIF chosen from GIPHY: fetch the bytes, then push them through our own
 * media pipeline so the comment points at our bucket rather than a third-party URL
 * that can rot or be blocked.
 */
private suspend fun uploadCommentGifFromUrl(url: String): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val bytes = runCatching {
            java.net.URL(url).openStream().use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        SyntraClient.uploadMedia("image", "gif", "image/gif", bytes)
    }

/**
 * Bottom sheet to tag (@mention) a person into a comment. Searches users by name /
 * username; picking one hands the username back so it's inserted as "@username" —
 * that person then gets a mention notification + deeplink to watch this reel.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TagPeopleSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<com.example.syntra.net.NetUser>() }
    var loading by remember { mutableStateOf(false) }

    // Debounced search. Empty query shows the people you follow as a starting point.
    LaunchedEffect(query) {
        loading = true
        delay(280)
        val fetched = runCatching {
            if (query.isBlank()) SyntraClient.getFollowing()
            else SyntraClient.searchUsers(query.trim())
        }.getOrDefault(emptyList())
        results.clear(); results.addAll(fetched)
        loading = false
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NexusSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                "Tandai seseorang",
                color = NexusTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
            Text(
                "Mereka akan diberi tahu untuk menonton video ini.",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusBackground)
                    .border(1.dp, NexusStroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Cari nama atau username…", color = NexusTextSecondary, fontSize = 14.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = NexusTextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(NexusAccentSoft),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                when {
                    loading && results.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp) }
                    results.isEmpty() -> Text(
                        if (query.isBlank()) "Belum ada orang yang kamu ikuti." else "Tidak ada yang cocok.",
                        color = NexusTextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth()) {
                        items(results, key = { it.id }) { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { onPick(u.username) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CommentAvatar(
                                    url = u.avatarMediaId?.takeIf { it.startsWith("http") },
                                    name = u.displayName.ifBlank { u.username },
                                    size = 40.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        u.displayName.ifBlank { u.username },
                                        color = NexusTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (u.username.isNotBlank()) {
                                        Text("@${u.username}", color = NexusTextSecondary, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                                Icon(Icons.Filled.AlternateEmail, null, tint = NexusAccentSoft, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
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
