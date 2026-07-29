package com.example.syntra

import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AmpStories
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.NetStory
import com.example.syntra.net.NetStoryGroup
import com.example.syntra.net.NetReel
import com.example.syntra.net.AvatarCache
import com.example.syntra.net.BlockActions
import com.example.syntra.net.BlockStore
import com.example.syntra.net.BlockedByStore
import com.example.syntra.net.NetUser
import com.example.syntra.net.ProfileCache
import com.example.syntra.net.ReelDownloader
import com.example.syntra.net.NetVisitor
import com.example.syntra.net.SyntraClient
import com.example.syntra.net.VideoCache
import com.example.syntra.ui.theme.NexusAccent
import com.example.syntra.ui.theme.NexusAccentSoft
import com.example.syntra.ui.theme.NexusBackground
import com.example.syntra.ui.theme.NexusStroke
import com.example.syntra.ui.theme.NexusSurface
import com.example.syntra.ui.theme.NexusSurfaceElevated
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ProfileTab { SHORTS, SAVED }

/**
 * A TikTok-style profile page. [username] null means "me" (own profile — shows
 * the Saved tab and lets you delete your shorts); a non-null username shows
 * someone else's public profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // When set, a direct chat with this person is open on top of the profile —
    // works no matter where the profile was opened from (Shorts, feed, etc.).
    var openChatConvo by remember(username) { mutableStateOf<Conversation?>(null) }

    // Paint the last-known version of this profile immediately, then let load()
    // replace it. Opening a profile used to be a full-screen spinner in front of
    // several sequential requests — on a weak connection, tapping a name looked like
    // the app had hung. `loading` is only true when there is genuinely nothing to draw.
    // "me" for the own profile, which is opened with no username but is the one people
    // open most often — it deserves the instant paint just as much.
    val cacheKey = username ?: "me"
    val cached = remember(username) { ProfileCache.read(context, cacheKey) }
    var user by remember(username) { mutableStateOf(cached?.user) }
    var loading by remember(username) { mutableStateOf(cached == null) }
    val shorts = remember(username) {
        mutableStateListOf<NetReel>().also { list -> cached?.let { list.addAll(it.reels) } }
    }
    val saved = remember(username) { mutableStateListOf<NetReel>() }
    var tab by remember(username) { mutableStateOf(ProfileTab.SHORTS) }
    var pendingDelete by remember { mutableStateOf<NetReel?>(null) }
    // Short whose long-press action sheet is open (null = none).
    var optionsFor by remember { mutableStateOf<NetReel?>(null) }
    // Short whose caption is being edited (null = none).
    var editCaptionFor by remember { mutableStateOf<NetReel?>(null) }
    // Which people list is open over the profile (null = none).
    var peopleList by remember { mutableStateOf<PeopleList?>(null) }
    // How many follow requests are waiting. Only ever non-zero on a private account.
    var pendingRequests by remember { mutableIntStateOf(0) }
    // A profile opened FROM one of those lists.
    var openOtherProfile by remember { mutableStateOf<String?>(null) }
    // Reel awaiting a report reason.
    var reportFor by remember { mutableStateOf<NetReel?>(null) }
    // When set, opens the full-screen swipeable reel viewer at this index.
    var viewerAt by remember { mutableStateOf<Int?>(null) }
    var following by remember(username) { mutableStateOf(false) }
    // Seeded from the local mirror — this used to start `false` every time, so a
    // blocked person's profile opened as if nothing had happened.
    // Re-derived once the profile loads, because the id only arrives with it: a profile
    // opened from a reel or a notification has no username to match on, so a
    // username-only check let a blocked person's page open normally from those routes.
    var blocked by remember(username) {
        mutableStateOf(BlockStore.isBlocked(context, username = username))
    }
    LaunchedEffect(user?.id) {
        if (BlockStore.isBlocked(context, username = username, userId = user?.id)) blocked = true
    }
    // They blocked ME. Their profile must not be browsable either — but this is NOT
    // "blocked", because there is nothing here for me to undo. Separate state, separate
    // wall, no unblock button.
    val blockedByThem = BlockedByStore.isBlockedBy(context, username = username, userId = user?.id)
    // This person's active story (if any) — drives the avatar ring and the tap
    // behaviour (story vs. full-screen photo).
    var story by remember(username) { mutableStateOf<NetStoryGroup?>(null) }
    // Avatar-tap outcomes.
    var showPhoto by remember { mutableStateOf(false) }
    var showStory by remember { mutableStateOf(false) }
    var showAvatarChoice by remember { mutableStateOf(false) }

    // "Who viewed my profile" — own profile only. Stacked avatars in the header;
    // tapping opens the full list.
    val visitors = remember(username) { mutableStateListOf<NetVisitor>() }
    var visitorTotal by remember(username) { mutableIntStateOf(0) }
    var showVisitors by remember { mutableStateOf(false) }

    // Own profile when opened with no username, OR when the loaded user turns out to
    // be me (e.g. tapping my own avatar in Shorts passes my username). Either way the
    // page must show "Edit profil", never a "Follow yourself" button.
    val isMe = username == null || user?.isSelf == true
    // Opens the profile editor as a full-screen overlay, from ANY entry point.
    var showEditProfile by remember { mutableStateOf(false) }

    // Profile background/cover. Shows the server value; on the own profile it can be
    // changed inline — pick → crop → upload → PATCH → delete the OLD cover from storage.
    var coverUrlState by remember(username) { mutableStateOf<String?>(null) }
    var coverUploading by remember { mutableStateOf(false) }
    // A picked-but-not-yet-cropped background photo. While non-null, the crop editor
    // is shown full-screen so the user frames which part becomes the cover.
    var coverToCrop by remember { mutableStateOf<Bitmap?>(null) }
    // When a cover already exists, tapping it offers change/remove.
    var showCoverOptions by remember { mutableStateOf(false) }

    fun deleteCover() {
        scope.launch {
            runCatching { SyntraClient.deleteCover() }
                .onSuccess {
                    val prev = ProfileStore.coverMediaId(context)
                    if (!prev.isNullOrBlank()) runCatching { SyntraClient.deleteMedia(prev) }
                    ProfileStore.setCover(context, "", "")
                    coverUrlState = null
                    android.widget.Toast.makeText(context, "Background dihapus.", android.widget.Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    android.widget.Toast.makeText(context, "Gagal hapus background: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun uploadCover(bitmap: Bitmap) {
        scope.launch {
            coverUploading = true
            runCatching {
                val jpeg = withContext(Dispatchers.Default) { bitmapToJpeg(bitmap, 1600, 85) }
                val prev = ProfileStore.coverMediaId(context)
                val (mid, url) = SyntraClient.uploadMediaFull("image", "jpg", "image/jpeg", jpeg)
                val me = SyntraClient.updateProfile(coverMediaId = mid)
                val newUrl = me.coverUrl ?: url
                ProfileStore.setCover(context, newUrl, mid)
                // Changing the cover deletes the previous one from storage.
                if (!prev.isNullOrBlank() && prev != mid) {
                    runCatching { SyntraClient.deleteMedia(prev) }
                }
                newUrl
            }.onSuccess {
                coverUrlState = it
                android.widget.Toast.makeText(context, "Background diperbarui.", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, "Gagal ganti background: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
            coverUploading = false
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && isMe) scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, uri, 2400) }
            if (bmp != null) {
                coverToCrop = bmp
            } else {
                android.widget.Toast.makeText(context, "Gagal baca gambar.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun load() {
        // MUST be raised here. PullToRefreshBox renders its spinner from `isRefreshing`,
        // which is this flag — and load() only ever CLEARED it. So a pull refreshed the
        // data in silence: no spinner, no movement, nothing to say the gesture landed.
        loading = true
        var failed: String? = null
        try {
            user = if (isMe) SyntraClient.getMyProfile() else SyntraClient.getUser(username!!)
            user?.let {
                following = it.followStatus == "accepted" || it.followStatus == "pending"
                if (coverUrlState == null) coverUrlState = it.coverUrl
            }
            // distinctBy: the grid keys items by reel id, so one repeated id from the
            // API takes the whole profile down with "Key … was already used".
            val myShorts = if (isMe) SyntraClient.getMyReels() else SyntraClient.getUserReels(username!!)
            shorts.clear(); shorts.addAll(myShorts.distinctBy { it.id })
            if (isMe) {
                // Silent: a public account always returns an empty list, so a failure
                // here must not take the whole profile load down with it.
                runCatching { SyntraClient.getFollowRequests().size }
                    .onSuccess { pendingRequests = it }
                val sv = SyntraClient.getSavedReels()
                saved.clear(); saved.addAll(sv.distinctBy { it.id })
                // Who viewed me — best effort, never blocks the profile.
                runCatching { SyntraClient.getProfileVisitors() }.getOrNull()?.let { v ->
                    visitors.clear(); visitors.addAll(v.visitors); visitorTotal = v.total
                }
            }
            // Remember this profile so the next open draws instantly.
            user?.let { u ->
                ProfileCache.write(context, cacheKey, u, shorts.toList())
                // The profile endpoint is the ONE place that reliably returns a real
                // avatar URL alongside both the id and the username, so it is the best
                // teacher for the shared store — rooms and chat lists read it back.
                AvatarCache.put(context, u.id, u.avatarMediaId)
                AvatarCache.put(context, u.username, u.avatarMediaId)
            }
            // Find this person's story among the ones visible to me (followed + self).
            val uid = user?.id
            if (uid != null) {
                story = runCatching { SyntraClient.getStories() }.getOrNull()
                    ?.firstOrNull { it.authorId == uid && it.stories.isNotEmpty() }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            // Leave whatever loaded; the header still shows what we have. But SAY so —
            // a silently failed refresh is indistinguishable from a successful one.
            failed = e.message ?: "Gagal memuat profil"
        }
        loading = false
        failed?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val avatarUrl = user?.avatarMediaId?.takeIf { it.startsWith("http") }
    val hasPhoto = avatarUrl != null
    val hasStory = story?.stories?.isNotEmpty() == true

    // Tapping the avatar: photo only → view photo; story only → view story; both →
    // ask; neither → nothing.
    fun onAvatarTap() {
        when {
            hasStory && hasPhoto -> showAvatarChoice = true
            hasStory -> showStory = true
            hasPhoto -> showPhoto = true
        }
    }

    androidx.compose.runtime.LaunchedEffect(username) {
        if (ApiConfig.ENABLED) load() else loading = false
    }

    // A blocked person's profile is a wall, not a page. No photo, no cover, no shorts,
    // no counts — blocking someone and still browsing their profile makes the block
    // meaningless. Unblocking is offered right here so it is never a trap.
    if (blockedByThem && !isMe) {
        UnavailableProfileWall(onClose = onClose)
        return
    }
    if (blocked && !isMe) {
        BlockedProfileWall(
            name = username.orEmpty(),
            onUnblock = {
                val u = username ?: return@BlockedProfileWall
                // Server first. An optimistic unblock whose request failed was undone
                // by the next sync, so the wall came straight back.
                scope.launch {
                    if (BlockActions.unblock(context, u, user?.id)) {
                        blocked = false
                    } else {
                        Toast.makeText(
                            context,
                            "Gagal membuka blokir. Periksa koneksi lalu coba lagi.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
            onClose = onClose,
        )
        return
    }

    Box(Modifier.fillMaxSize().background(NexusBackground)) {
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { scope.launch { load() } },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            // Leave room at the bottom so the last row of shorts isn't hidden
            // behind the app's bottom navigation bar.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            // --- Header spans all three columns ---
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Column {
                    ProfileHeaderHero(
                        user = user,
                        isMe = isMe,
                        hasStory = hasStory,
                        storyAllViewed = story?.allViewed == true,
                        coverUrl = coverUrlState,
                        coverUploading = coverUploading,
                        totalLikes = shorts.sumOf { it.likeCount },
                        lastVisitor = visitors.firstOrNull(),
                        visitorTotal = visitorTotal,
                        onClose = onClose,
                        onAvatarTap = { onAvatarTap() },
                        onEditCover = {
                            // A cover already there → offer change/remove; else pick one.
                            if (coverUrlState != null) {
                                showCoverOptions = true
                            } else {
                                coverPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                        },
                        onVisitorsClick = { showVisitors = true },
                        onOpenFollowers = { peopleList = PeopleList.FOLLOWERS },
                        onOpenFollowing = { if (isMe) peopleList = PeopleList.FOLLOWING },
                    )
                    // Pending follow requests, own profile only. Shown as a row rather
                    // than buried in Settings: a request that nobody ever sees is the
                    // whole reason private accounts were a dead end.
                    if (isMe && pendingRequests > 0) {
                        FollowRequestsRow(
                            count = pendingRequests,
                            onClick = { peopleList = PeopleList.REQUESTS },
                        )
                    }
                    if (isMe) {
                        EditProfileButton(onClick = { showEditProfile = true })
                    } else if (user != null) {
                        ProfileActions(
                            following = following,
                            blocked = blocked,
                            // Chat is always offered for another user (never yourself).
                            canMessage = !blocked,
                            onMessage = {
                                val u = user ?: return@ProfileActions
                                scope.launch {
                                    runCatching {
                                        val convId = SyntraClient.createDirect(u.id)
                                        openChatConvo = Conversation(
                                            id = convId,
                                            name = u.displayName.ifBlank { u.username },
                                            message = "",
                                            time = "",
                                            counterpartId = u.id,
                                            counterpartUsername = u.username,
                                        )
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "Buka chat gagal: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onToggleFollow = {
                                val u = username ?: return@ProfileActions
                                if (following) { following = false; scope.launch { runCatching { SyntraClient.unfollow(u) } } }
                                else { following = true; scope.launch { runCatching { SyntraClient.follow(u) } } }
                            },
                            onToggleBlock = {
                                val u = username ?: return@ProfileActions
                                val uid = user?.id
                                if (blocked) {
                                    scope.launch {
                                        if (BlockActions.unblock(context, u, uid)) {
                                            blocked = false
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Gagal membuka blokir. Periksa koneksi lalu coba lagi.",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                } else {
                                    // Blocking stays optimistic ON PURPOSE: the safe
                                    // direction is to stop showing someone immediately.
                                    // Unblocking is the one that must be confirmed, since
                                    // being wrong there exposes you to someone you blocked.
                                    blocked = true
                                    following = false
                                    scope.launch {
                                        if (!BlockActions.block(context, u, uid)) {
                                            blocked = false
                                            Toast.makeText(
                                                context,
                                                "Gagal memblokir. Periksa koneksi lalu coba lagi.",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }
                            },
                        )
                    }
                    ProfileTabs(
                        tab = tab,
                        showSaved = isMe,
                        onSelect = { tab = it },
                    )
                }
            }

            val list = if (tab == ProfileTab.SAVED) saved else shorts
            if (loading && list.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexusAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    }
                }
            } else if (list.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (tab == ProfileTab.SAVED) "Belum ada video tersimpan" else "Belum ada Shorts",
                            color = NexusTextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                itemsIndexed(list, key = { _, r -> r.id }) { index, reel ->
                    ReelThumb(
                        reel = reel,
                        onLongPress = { optionsFor = reel },
                        onOpen = { viewerAt = index },
                    )
                }
            }
        }
        }
    }

    // Change / remove the profile background.
    if (showCoverOptions) {
        CoverOptionsSheet(
            onChange = {
                showCoverOptions = false
                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemove = { showCoverOptions = false; deleteCover() },
            onDismiss = { showCoverOptions = false },
        )
    }

    // Followers / following / pending requests, full-screen over the profile.
    peopleList?.let { kind ->
        PeopleListScreen(
            kind = kind,
            username = if (kind == PeopleList.FOLLOWERS && !isMe) username else null,
            onClose = {
                peopleList = null
                // Coming back from the requests list, the count has probably changed.
                if (kind == PeopleList.REQUESTS) {
                    scope.launch {
                        runCatching { SyntraClient.getFollowRequests().size }
                            .onSuccess { pendingRequests = it }
                    }
                }
            },
            onOpenProfile = { uname -> peopleList = null; openOtherProfile = uname },
        )
    }

    openOtherProfile?.let { uname ->
        ProfileScreen(username = uname, onClose = { openOtherProfile = null })
    }

    // Full-screen swipeable reel viewer (opened by tapping a thumbnail).
    viewerAt?.let { start ->
        val list = if (tab == ProfileTab.SAVED) saved else shorts
        if (list.isNotEmpty()) {
            ReelViewer(reels = list.toList(), startIndex = start, onClose = { viewerAt = null })
        }
    }

    // Long-press actions for a single short.
    optionsFor?.let { reel ->
        val mine = isMe && reel.authorId.let { it.isBlank() || it == SyntraClient.myUserId }
        ReelOptionsSheet(
            reel = reel,
            isMine = mine,
            inSavedTab = tab == ProfileTab.SAVED,
            onDelete = { optionsFor = null; pendingDelete = reel },
            onEdit = { optionsFor = null; editCaptionFor = reel },
            onDownload = {
                optionsFor = null
                android.widget.Toast.makeText(context, "Mengunduh video…", android.widget.Toast.LENGTH_SHORT).show()
                scope.launch {
                    val ok = ReelDownloader.saveVideo(context, reel.mediaUrl, "syntra-${reel.id}.mp4")
                    android.widget.Toast.makeText(
                        context,
                        if (ok) "Tersimpan di galeri (Movies/Syntra)" else "Gagal mengunduh video",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onToggleSave = {
                optionsFor = null
                // In the Saved tab this is always an un-save, so the tile leaves the
                // grid straight away rather than lingering until the next refresh.
                val now = if (tab == ProfileTab.SAVED) false else !reel.isSaved
                if (!now) saved.removeAll { it.id == reel.id }
                val i = shorts.indexOfFirst { it.id == reel.id }
                if (i >= 0) shorts[i] = shorts[i].copy(isSaved = now)
                scope.launch {
                    runCatching { SyntraClient.saveReel(reel.id, now) }
                        .onFailure {
                            android.widget.Toast.makeText(context, "Gagal: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
                android.widget.Toast.makeText(
                    context,
                    if (now) "Disimpan" else "Dihapus dari simpanan",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
            onReport = { optionsFor = null; reportFor = reel },
            onDismiss = { optionsFor = null },
        )
    }

    editCaptionFor?.let { reel ->
        EditCaptionDialog(
            initial = reel.caption,
            onDismiss = { editCaptionFor = null },
            onSave = { text ->
                editCaptionFor = null
                // Optimistic: the grid and any open viewer show the new caption at
                // once, and it goes back if the server refuses.
                val before = reel.caption
                val i = shorts.indexOfFirst { it.id == reel.id }
                if (i >= 0) shorts[i] = shorts[i].copy(caption = text)
                scope.launch {
                    runCatching { SyntraClient.updateReel(reel.id, caption = text) }
                        .onSuccess {
                            android.widget.Toast.makeText(context, "Keterangan diperbarui.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { e ->
                            val j = shorts.indexOfFirst { it.id == reel.id }
                            if (j >= 0) shorts[j] = shorts[j].copy(caption = before)
                            android.widget.Toast.makeText(context, "Gagal menyimpan: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                }
            },
        )
    }

    reportFor?.let { reel ->
        ReportReelDialog(
            onDismiss = { reportFor = null },
            onSubmit = { reason ->
                reportFor = null
                scope.launch {
                    runCatching { SyntraClient.reportReel(reel.id, reason) }
                        .onSuccess { android.widget.Toast.makeText(context, "Laporan terkirim. Terima kasih.", android.widget.Toast.LENGTH_SHORT).show() }
                        .onFailure { android.widget.Toast.makeText(context, "Gagal mengirim laporan: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
                }
            },
        )
    }

    pendingDelete?.let { reel ->
        DeleteShortDialog(
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    runCatching { SyntraClient.deleteReel(reel.id) }
                        .onSuccess { shorts.removeAll { it.id == reel.id }; saved.removeAll { it.id == reel.id } }
                }
            },
        )
    }

    // Avatar-tap: choose between story and photo when both exist.
    if (showAvatarChoice) {
        AvatarChoiceSheet(
            onDismiss = { showAvatarChoice = false },
            onStory = { showAvatarChoice = false; showStory = true },
            onPhoto = { showAvatarChoice = false; showPhoto = true },
        )
    }

    // Full-screen profile photo.
    if (showPhoto && avatarUrl != null) {
        ProfilePhotoViewer(url = avatarUrl, onClose = { showPhoto = false })
    }

    // Full-screen story viewer for this person.
    if (showStory) {
        story?.let { g ->
            ProfileStoryViewer(
                group = g,
                onClose = { showStory = false },
                onViewed = { id -> scope.launch { runCatching { SyntraClient.viewStory(id) } } },
            )
        }
    }

    // Direct chat opened from the profile's chat icon — full-screen over the profile.
    openChatConvo?.let { convo ->
        ChatDetailScreen(conversation = convo, onBack = { openChatConvo = null })
    }

    // Full list of who viewed my profile.
    if (showVisitors) {
        VisitorsSheet(
            total = visitorTotal,
            visitors = visitors.toList(),
            onDismiss = { showVisitors = false },
        )
    }

    // Crop editor for a freshly-picked background — frame what becomes the cover.
    coverToCrop?.let { bmp ->
        ImageCropScreen(
            source = bmp,
            aspectRatio = 2.2f, // wide banner, matches the profile cover
            title = "Atur background",
            onCancel = { coverToCrop = null },
            onConfirm = { cropped ->
                coverToCrop = null
                uploadCover(cropped)
            },
        )
    }

    // Profile editor — reachable from the "Edit profil" button on the own profile,
    // no matter how the profile was opened (Shorts, feed, tab). Refreshes on close so
    // a changed name/photo shows immediately.
    if (showEditProfile) {
        ProfileSettingsScreen(onClose = {
            showEditProfile = false
            scope.launch { runCatching { load() } }
        })
    }
}

/** Decodes a content URI into a bitmap, downscaled so the longest side <= [maxDim]. */
private fun decodeBitmap(context: android.content.Context, uri: android.net.Uri, maxDim: Int): Bitmap? {
    return runCatching {
        // First pass: bounds only, to pick an integer sample size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}

/** Downscales [bitmap] so its longest side <= [maxDim], then JPEG-encodes it. */
private fun bitmapToJpeg(bitmap: Bitmap, maxDim: Int, quality: Int): ByteArray {
    val w = bitmap.width
    val h = bitmap.height
    val longest = maxOf(w, h)
    val scaled = if (longest > maxDim) {
        val ratio = maxDim.toFloat() / longest
        Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    } else {
        bitmap
    }
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (scaled !== bitmap) scaled.recycle()
    return out.toByteArray()
}

/**
 * TikTok/Instagram-style profile hero: a real cover photo (or brand gradient) with
 * a scrim, an avatar overlapping its lower edge, the identity, and clean three-up
 * stats (Mengikuti / Pengikut / Suka). Own profile can change the cover inline and
 * shows the most-recent visitor as a bubble at the top-right.
 */
@Composable
private fun ProfileHeaderHero(
    user: NetUser?,
    isMe: Boolean,
    hasStory: Boolean,
    storyAllViewed: Boolean,
    coverUrl: String?,
    coverUploading: Boolean,
    totalLikes: Int,
    lastVisitor: NetVisitor?,
    visitorTotal: Int,
    onClose: () -> Unit,
    onAvatarTap: () -> Unit,
    onEditCover: () -> Unit,
    onVisitorsClick: () -> Unit,
    onOpenFollowers: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
) {
    val coverHeight = 172.dp
    Box(Modifier.fillMaxWidth()) {
        // --- Cover / background ---
        Box(Modifier.fillMaxWidth().height(coverHeight)) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4834A6), Color(0xFF6C5CE7), Color(0xFF3B68F5)),
                        ),
                    ),
                )
            }
            // Scrim: darken top a touch for the back bar, fade the bottom into the page.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.28f),
                        0.35f to Color.Transparent,
                        1f to NexusBackground,
                    ),
                ),
            )
            // Change-cover control (own profile): a quiet gray hint, no chrome — tap
            // the cover to pick a new background.
            if (isMe) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            enabled = !coverUploading,
                            onClick = onEditCover,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (coverUploading) {
                        CircularProgressIndicator(
                            color = Color.White.copy(alpha = 0.7f),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        text = if (coverUploading) "Mengunggah…" else "Ketuk untuk mengganti",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // --- Identity, pushed down so the avatar straddles the cover's lower edge ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = coverHeight - 52.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarRing(user = user, hasStory = hasStory, storyAllViewed = storyAllViewed, onTap = onAvatarTap)
            Spacer(Modifier.height(10.dp))
            Text(
                text = user?.displayName?.ifBlank { user.username } ?: "…",
                color = NexusTextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            if (user != null) {
                Spacer(Modifier.height(3.dp))
                Text("@${user.username}", color = NexusTextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            // Clean three-up stats — no card, big numbers, like TikTok/Instagram.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The counts are now doors, not decoration: tapping them opens the
                // actual lists. "Pengikut" in particular had no way of being seen
                // anywhere in the app, even though the endpoint has always existed.
                StatCell(
                    user?.followingCount ?: 0, "Mengikuti",
                    Modifier.weight(1f).clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onOpenFollowing,
                    ),
                )
                StatDivider()
                StatCell(
                    user?.followerCount ?: 0, "Pengikut",
                    Modifier.weight(1f).clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onOpenFollowers,
                    ),
                )
                StatDivider()
                StatCell(totalLikes, "Suka", Modifier.weight(1f))
            }
        }

        // --- Floating top bar over the cover (drawn last so it always taps) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroCircleIcon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onClose)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if ((user?.username ?: "").isBlank()) "Profil" else "@${user!!.username}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            // Most-recent visitor bubble (own profile) — tap to see everyone.
            if (isMe && (lastVisitor != null || visitorTotal > 0)) {
                LastVisitorBubble(last = lastVisitor, total = visitorTotal, onClick = onVisitorsClick)
            }
        }
    }
}

/**
 * The single most-recent profile visitor as an avatar, with a small eye + count
 * badge at its bottom-right. Tapping opens the full visitor list.
 */
@Composable
private fun LastVisitorBubble(last: NetVisitor?, total: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
    ) {
        // Avatar of the last visitor (or an eye placeholder if unknown).
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(40.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)))),
            contentAlignment = Alignment.Center,
        ) {
            val a = last?.avatarUrl
            when {
                !a.isNullOrBlank() -> AsyncImage(
                    model = a,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
                last != null -> Text(
                    (last.displayName.firstOrNull() ?: last.username.firstOrNull() ?: '?').uppercase(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                else -> Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        // Eye + count, bottom-right — no chrome, just the icon and number.
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(2.dp))
            Text(
                formatCount(total.coerceAtLeast(1)),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The avatar with its story ring, shared by the hero. */
@Composable
private fun AvatarRing(user: NetUser?, hasStory: Boolean, storyAllViewed: Boolean, onTap: () -> Unit) {
    val ringMod = when {
        hasStory && !storyAllViewed -> Modifier.background(
            Brush.sweepGradient(listOf(NexusAccentSoft, NexusAccent, NexusAccentSoft)),
            CircleShape,
        )
        hasStory -> Modifier.background(NexusStroke, CircleShape)
        else -> Modifier
    }
    Box(
        modifier = Modifier
            .size(if (hasStory) 104.dp else 96.dp)
            .then(ringMod)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .padding(if (hasStory) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5))))
                .border(3.dp, NexusBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = user?.avatarMediaId
            if (!avatar.isNullOrBlank() && avatar.startsWith("http")) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = (user?.displayName?.firstOrNull() ?: user?.username?.firstOrNull() ?: 'S').uppercase(),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** A round translucent icon button that sits on the banner. */
@Composable
private fun HeroCircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatCell(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCount(count), color = NexusTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = NexusTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(22.dp).background(NexusStroke.copy(alpha = 0.5f)))
}

/** 1234 -> "1,2rb", 1_500_000 -> "1,5jt". Keeps the stats card compact. */
private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fjt", n / 1_000_000.0).replace(".0", "").replace(".", ",")
    n >= 1_000 -> String.format("%.1frb", n / 1_000.0).replace(".0", "").replace(".", ",")
    else -> n.toString()
}


/** Bottom sheet listing everyone who recently viewed my profile. */
@Composable
private fun VisitorsSheet(total: Int, visitors: List<NetVisitor>, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(NexusSurface)
                .border(1.dp, NexusStroke, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false) {}
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NexusStroke),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Visibility, null, tint = NexusAccentSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pengunjung profil", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$total total", color = NexusTextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (visitors.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada yang mengunjungi profilmu.", color = NexusTextSecondary, fontSize = 13.sp)
                }
            } else {
                Column(
                    Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    visitors.forEach { v -> VisitorRow(v) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VisitorRow(v: NetVisitor) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3B68F5)))),
            contentAlignment = Alignment.Center,
        ) {
            val a = v.avatarUrl
            if (!a.isNullOrBlank()) {
                AsyncImage(
                    model = a,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    (v.displayName.firstOrNull() ?: v.username.firstOrNull() ?: '?').uppercase(),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.displayName.ifBlank { v.username }.ifBlank { "pengguna" },
                color = NexusTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (v.username.isNotBlank()) {
                Text("@${v.username}", color = NexusTextSecondary, fontSize = 12.sp)
            }
        }
        val ago = relativeTimeId(v.visitedAt)
        if (ago.isNotBlank()) {
            Text(ago, color = NexusTextSecondary, fontSize = 11.sp)
        }
    }
}

/** ISO-8601 -> short Indonesian relative time ("baru saja", "5 mnt", "2 jam", "3 hr"). */
private fun relativeTimeId(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val then = java.time.Instant.parse(iso).toEpochMilli()
        val secs = ((System.currentTimeMillis() - then) / 1000).coerceAtLeast(0)
        when {
            secs < 60 -> "baru saja"
            secs < 3600 -> "${secs / 60} mnt"
            secs < 86400 -> "${secs / 3600} jam"
            secs < 604800 -> "${secs / 86400} hr"
            else -> "${secs / 604800} mgg"
        }
    } catch (_: Exception) {
        ""
    }
}

/** Own-profile primary action: a full-width "Edit profil" button (never Follow). */
@Composable
private fun EditProfileButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .border(1.dp, NexusStroke, RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Edit, null, tint = NexusTextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Edit profil", color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Icon-forward actions on another person's profile: a primary Follow pill (icon +
 * short label), then round icon buttons for Chat and Block — mostly icons, per the
 * design direction.
 */
@Composable
private fun ProfileActions(
    following: Boolean,
    blocked: Boolean,
    canMessage: Boolean,
    onMessage: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Follow / Following (primary) — icon + short label.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (following) Color.Transparent else NexusAccent)
                .border(1.dp, if (following) NexusStroke else Color.Transparent, RoundedCornerShape(12.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onToggleFollow,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (following) Icons.Filled.Check else Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = if (following) NexusTextPrimary else Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (following) "Mengikuti" else "Ikuti",
                color = if (following) NexusTextPrimary else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Chat (icon only) — opens a direct chat with this person.
        if (canMessage) {
            ProfileIconButton(
                icon = Icons.Filled.ChatBubbleOutline,
                tint = NexusTextPrimary,
                onClick = onMessage,
            )
        }
        // Block / Unblock (icon only).
        ProfileIconButton(
            icon = if (blocked) Icons.Filled.LockOpen else Icons.Filled.Block,
            tint = if (blocked) NexusTextSecondary else Color(0xFFFF6B6B),
            onClick = onToggleBlock,
        )
    }
}

/** Square icon action used across the profile action row. */
@Composable
private fun ProfileIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NexusSurfaceElevated)
            .border(1.dp, NexusStroke, RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ProfileTabs(tab: ProfileTab, showSaved: Boolean, onSelect: (ProfileTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NexusStroke, RoundedCornerShape(0.dp)),
    ) {
        TabCell(Icons.Filled.GridView, active = tab == ProfileTab.SHORTS, modifier = Modifier.weight(1f)) {
            onSelect(ProfileTab.SHORTS)
        }
        if (showSaved) {
            TabCell(Icons.Filled.Bookmark, active = tab == ProfileTab.SAVED, modifier = Modifier.weight(1f)) {
                onSelect(ProfileTab.SAVED)
            }
        }
    }
}

@Composable
private fun TabCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) NexusTextPrimary else NexusTextSecondary, modifier = Modifier.size(22.dp))
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(NexusAccentSoft),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReelThumb(reel: NetReel, onLongPress: () -> Unit, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(0.66f)
            .background(NexusSurface)
            // Tap opens, press-and-hold opens the actions. The delete affordance used
            // to be a red badge stamped on every one of your own tiles — permanent
            // clutter over the artwork, and the single most destructive action was the
            // easiest one to hit by accident while scrolling.
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen,
                onLongClick = onLongPress,
            ),
    ) {
        AsyncImage(
            model = reel.mediaUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        // View count, bottom-left, like TikTok.
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(2.dp))
            Text("${reel.viewCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Actions for one short, opened by pressing and holding its tile.
 *
 * What's offered depends on whose reel it is and which tab you're on, so the sheet
 * never shows something that would just fail: only the owner gets "Hapus", only the
 * Saved tab gets "Hapus dari simpanan", and reporting is pointless on your own post.
 */
@Composable
private fun ReelOptionsSheet(
    reel: NetReel,
    isMine: Boolean,
    inSavedTab: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDownload: () -> Unit,
    onToggleSave: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp),
        ) {
            // Header: which short this is, plus how it's doing. The numbers are the
            // reason most people long-press their own post in the first place.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            ) {
                AsyncImage(
                    model = reel.mediaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp, 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NexusSurfaceElevated),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reel.caption.ifBlank { "Tanpa keterangan" },
                        color = NexusTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReelStat(Icons.Filled.Visibility, reel.viewCount)
                        Spacer(Modifier.width(12.dp))
                        ReelStat(Icons.Filled.Favorite, reel.likeCount)
                        Spacer(Modifier.width(12.dp))
                        ReelStat(Icons.Filled.ChatBubbleOutline, reel.commentCount)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = NexusStroke)
            Spacer(Modifier.height(4.dp))

            if (isMine && !inSavedTab) {
                CoverOptionRow(Icons.Filled.Edit, "Edit keterangan", NexusTextPrimary, onEdit)
            }
            CoverOptionRow(Icons.Filled.Download, "Unduh video", NexusTextPrimary, onDownload)
            if (inSavedTab) {
                CoverOptionRow(
                    Icons.Filled.BookmarkRemove,
                    "Hapus dari simpanan",
                    NexusTextPrimary,
                    onToggleSave,
                )
            } else if (!isMine) {
                CoverOptionRow(
                    if (reel.isSaved) Icons.Filled.BookmarkRemove else Icons.Filled.Bookmark,
                    if (reel.isSaved) "Hapus dari simpanan" else "Simpan",
                    NexusTextPrimary,
                    onToggleSave,
                )
            }
            if (!isMine) {
                CoverOptionRow(Icons.Filled.Flag, "Laporkan", NexusTextPrimary, onReport)
            }
            if (isMine && !inSavedTab) {
                CoverOptionRow(Icons.Filled.Delete, "Hapus short", Color(0xFFFF5D5D), onDelete)
            }
            CoverOptionRow(Icons.Filled.Close, "Batal", NexusTextSecondary, onDismiss)
        }
    }
}

/**
 * Edits a short's caption in place.
 *
 * Only the caption. Visibility and comment settings are deliberately left out even
 * though the endpoint accepts them — this dialog is reached from a long-press on a
 * thumbnail, and quietly changing who can see a post is not something that should
 * live one accidental tap away from "Hapus".
 */
/** The "N orang menunggu persetujuan" row on your own private profile. */
@Composable
private fun FollowRequestsRow(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NexusAccent.copy(alpha = 0.12f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PersonAdd, null, tint = NexusAccentSoft, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "$count permintaan mengikuti menunggu",
            color = NexusTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text("Lihat", color = NexusAccentSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditCaptionDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val max = 2200
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(20.dp),
        ) {
            Text("Edit keterangan", color = NexusTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .background(NexusSurfaceElevated, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (text.isEmpty()) {
                    Text("Tulis keterangan…", color = NexusTextSecondary, fontSize = 14.sp)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= max) text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = NexusTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(NexusAccentSoft),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${text.length} / $max",
                color = NexusTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(14.dp))
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
                Spacer(Modifier.width(4.dp))
                Text(
                    "Simpan",
                    color = if (text.trim() == initial.trim()) NexusTextSecondary else NexusAccentSoft,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(
                            enabled = text.trim() != initial.trim(),
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSave(text.trim()) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReelStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = NexusTextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(formatCount(value), color = NexusTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun DeleteShortDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(40.dp)
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(20.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(20.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Hapus Short ini?", color = NexusTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Video akan dihapus permanen dan hilang dari feed semua orang.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(NexusSurfaceElevated)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text("Batal", color = NexusTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color(0xFFFF5D5D))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onConfirm,
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text("Hapus", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Avatar tap: choice sheet, photo viewer, story viewer
// ---------------------------------------------------------------------------

/**
 * Shown when a person has BOTH a story and a profile photo — pick which to open.
 *
 * Two plain rows, matching the attachment sheet in chat. The old version was a pair
 * of coloured tiles fronted by a media-player "play" triangle, which reads as "start
 * a video" rather than "there is a story here"; a ring is what a story looks like
 * everywhere else in this app, so that's the icon.
 */
@Composable
private fun AvatarChoiceSheet(onDismiss: () -> Unit, onStory: () -> Unit, onPhoto: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(20.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(20.dp))
                .padding(vertical = 8.dp),
        ) {
            Text(
                "Buka",
                color = NexusTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            )
            ChoiceRow(
                icon = Icons.Outlined.AmpStories,
                label = "Story",
                detail = "Lihat story yang sedang aktif",
                onClick = onStory,
            )
            ChoiceRow(
                icon = Icons.Outlined.AccountCircle,
                label = "Foto profil",
                detail = "Lihat foto profil ukuran penuh",
                onClick = onPhoto,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NexusSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = NexusTextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, color = NexusTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(detail, color = NexusTextSecondary, fontSize = 12.sp)
        }
    }
}

/** Change or remove the profile background. */
@Composable
private fun CoverOptionsSheet(onChange: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusSurface, RoundedCornerShape(22.dp))
                .border(1.dp, NexusStroke, RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp),
        ) {
            Text(
                "Background profil",
                color = NexusTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(10.dp))
            CoverOptionRow(Icons.Filled.Photo, "Ganti background", NexusTextPrimary, onChange)
            CoverOptionRow(Icons.Filled.Delete, "Hapus background", Color(0xFFFF5D5D), onRemove)
            CoverOptionRow(Icons.Filled.Close, "Batal", NexusTextSecondary, onDismiss)
        }
    }
}

@Composable
private fun CoverOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}


/**
 * What you see instead of a blocked person's profile.
 *
 * Deliberately shows nothing of theirs — not the avatar, not the cover, not a single
 * count. Those are exactly the details a block is meant to put away.
 */
/**
 * The wall shown to someone who HAS BEEN blocked.
 *
 * Says only that the account is unavailable. It deliberately does not say "you were
 * blocked by X", and offers no action: naming the cause turns a block into a
 * confrontation, and there is nothing this person can do about it anyway.
 */
@Composable
private fun UnavailableProfileWall(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
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
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(84.dp).background(NexusSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PersonOff, null,
                    tint = NexusTextSecondary, modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Pengguna", color = NexusTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Profil ini tidak tersedia.",
                color = NexusTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BlockedProfileWall(name: String, onUnblock: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(NexusSurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Block, null,
                    tint = NexusTextSecondary, modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (name.isBlank()) "Pengguna diblokir" else "@$name diblokir",
                color = NexusTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Kamu tidak bisa melihat profil, foto, atau Shorts mereka. " +
                    "Mereka juga tidak bisa mengirimimu pesan.",
                color = NexusTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NexusSurfaceElevated)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onUnblock,
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    "Buka blokir",
                    color = NexusTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Full-screen profile photo with a close button. */
@Composable
private fun ProfilePhotoViewer(url: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Foto profil",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, "Tutup", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Minimal full-screen story viewer for one person: segmented progress bar, tap
 * right/left to advance/rewind, auto-advance on a timer, image or video. Marks
 * each segment viewed as it shows.
 */
@Composable
private fun ProfileStoryViewer(
    group: NetStoryGroup,
    onClose: () -> Unit,
    onViewed: (storyId: String) -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val stories = group.stories
    if (stories.isEmpty()) { onClose(); return }
    var index by remember { mutableIntStateOf(0) }
    val current = stories.getOrNull(index) ?: run { onClose(); return }
    val isVideo = current.mediaKind == "video"

    // Play the story's attached song (30s preview) while it's on screen. One player,
    // swapped when the segment changes, released on close.
    val music = current.music
    DisposableEffect(current.id) {
        var mp: android.media.MediaPlayer? = null
        if (music != null && music.previewUrl.isNotBlank() && !isVideo) {
            runCatching {
                mp = android.media.MediaPlayer().apply {
                    setDataSource(music.previewUrl)
                    isLooping = true
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
            }
        }
        onDispose { runCatching { mp?.release() } }
    }

    // Mark viewed + auto-advance. A music story lingers for the preview length; plain
    // images 5s; videos use their own duration.
    LaunchedEffect(index) {
        onViewed(current.id)
        val dur = when {
            isVideo && current.durationMs > 0 -> current.durationMs
            music != null -> 15000L
            else -> 5000L
        }
        kotlinx.coroutines.delay(dur)
        if (index < stories.lastIndex) index++ else onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(stories.size) {
                detectTapGestures { offset ->
                    if (offset.x < size.width * 0.35f) {
                        if (index > 0) index-- else onClose()
                    } else {
                        if (index < stories.lastIndex) index++ else onClose()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isVideo) {
            // Resolve to the on-disk cached copy first (download once); a story
            // video re-watched — or the story re-opened — then costs no more egress.
            var vsrc by remember(current.id) { mutableStateOf<String?>(null) }
            LaunchedEffect(current.id) { vsrc = VideoCache.resolve(context, current.mediaUrl) }
            vsrc?.let { src ->
                androidx.compose.runtime.key(current.id) {
                    AndroidView(
                        factory = { ctx ->
                            android.view.TextureView(ctx).apply {
                                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                        runCatching {
                                            val mp = android.media.MediaPlayer()
                                            tag = mp
                                            mp.setSurface(android.view.Surface(st))
                                            mp.setDataSource(src)
                                            mp.isLooping = true
                                            mp.setOnPreparedListener { it.start() }
                                            mp.prepareAsync()
                                        }
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) = Unit
                                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                                        (tag as? android.media.MediaPlayer)?.let { runCatching { it.release() } }
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) = Unit
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            AsyncImage(
                model = current.mediaUrl,
                contentDescription = "Story",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Segmented progress bar at the top: filled = watched/current, dim = ahead.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            stories.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= index) Color.White else Color.White.copy(alpha = 0.35f)),
                )
            }
        }

        // Close button.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 22.dp, end = 10.dp)
                .size(40.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, "Tutup", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        // "Now playing" music pill at the bottom, when this story has a song.
        music?.let { m ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${m.title} · ${m.artist}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }
        }
    }
}
