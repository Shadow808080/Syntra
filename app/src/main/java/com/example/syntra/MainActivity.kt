package com.example.syntra

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.ApiException
import com.example.syntra.net.AppLock
import com.example.syntra.net.ShortsFeedCache
import com.example.syntra.net.SyntraClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.syntra.ui.theme.AppTheme
import com.example.syntra.ui.theme.SyntraTheme

/**
 * A pending "open this chat" request, set when the user taps a message notification.
 * The chat tab observes it, jumps to the conversation, then clears it.
 */
object ChatNavRequest {
    var conversationId by mutableStateOf<String?>(null)
}

/**
 * A pending "open this reel" request, set when the user taps an activity
 * notification (e.g. a comment reply). Shorts observes it, fetches the reel, and
 * opens it full-screen, then clears it.
 */
object ReelNavRequest {
    var reelId by mutableStateOf<String?>(null)
}

// FragmentActivity (not plain ComponentActivity) so BiometricPrompt — used by the
// app-lock unlock screen — has the host it needs.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paint the saved theme before the first frame so there is no flash.
        AppTheme.load(this)
        applySystemBars()
        handleNavIntent(intent)
        // Let the Shorts feed ask the Activity to shrink into a floating window.
        com.example.syntra.net.PipController.enter = { enterPip() }
        setContent {
            SyntraTheme {
                // Re-apply whenever the user switches theme, so the Android system
                // status/navigation bars follow the app palette (dark bars + light
                // icons on Midnight/dark themes, light bars + dark icons on Light).
                androidx.compose.runtime.LaunchedEffect(AppTheme.current) { applySystemBars() }
                NexusApp()
            }
        }
    }

    /** Makes the system status & navigation bars match the current theme. */
    private fun applySystemBars() {
        val palette = AppTheme.paletteOf(AppTheme.current)
        val dark = palette.isDark
        val bg = palette.background.toArgb()
        // Transparent bars over a themed window: icons flip to stay legible.
        val style = if (dark) {
            androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        // Also colour the nav bar itself to the app background on APIs that show a
        // solid bar, so a gesture pill / 3-button bar never flashes system white.
        runCatching {
            window.navigationBarColor = bg
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    override fun onStart() {
        super.onStart()
        // App is on screen → suppress message notifications (the user sees updates live).
        com.example.syntra.net.AppForeground.isForeground = true
        com.example.syntra.net.AppForeground.isForegroundState = true
        // Re-evaluate the app lock: a cold start (or a real background stint) re-locks;
        // a quick picker round-trip inside the grace window stays unlocked.
        AppLock.onForeground(this)
    }

    /** Shrink the app into a 9:16 floating window showing the current reel. */
    private fun enterPip() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .build()
        runCatching { enterPictureInPictureMode(params) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.example.syntra.net.PipController.inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // In PiP the app is "stopped" but the little window is still showing our reel —
        // keep everything playing and don't re-lock; a normal background does both.
        if (isInPictureInPictureMode) return
        // Note when we left so the lock knows how long we were away.
        AppLock.onBackground()
        // App went to the background → let the foreground service post notifications.
        com.example.syntra.net.AppForeground.isForeground = false
        // Pause all media when the app is no longer on screen: music, and — via the
        // foreground flag the feed observes — Shorts video and voice notes. Nothing
        // keeps playing behind other apps.
        com.example.syntra.net.AppForeground.isForegroundState = false
        com.example.syntra.net.MusicPlayer.pauseForExternalAudio()
        com.example.syntra.VoiceBus.pauseActive()
    }

    override fun onDestroy() {
        super.onDestroy()
        // App closed/swiped away → fully release audio so nothing lingers.
        com.example.syntra.net.MusicPlayer.stop()
        com.example.syntra.VoiceBus.pauseActive()
    }

    // A notification tapped while the app is already running delivers a NEW intent
    // here (activity is singleTop via the notification's flags) — route it too.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent(intent)
    }

    /** Pulls an "open this chat" request out of a notification intent, if present. */
    private fun handleNavIntent(intent: Intent?) {
        val cid = intent?.getStringExtra("open_conversation")
        if (!cid.isNullOrBlank()) ChatNavRequest.conversationId = cid
        val rid = intent?.getStringExtra("open_reel")
        if (!rid.isNullOrBlank()) ReelNavRequest.reelId = rid
    }

    // NOTE: we deliberately do NOT disconnect the socket in onDestroy anymore. The
    // ChatConnectionService owns the connection so messages (and notifications) keep
    // arriving while the app isn't on screen. The socket is closed on sign-out.
}

/**
 * Auth gate: nobody gets to the app without a session. [SessionStore] persists the
 * flag, so a signed-in user goes straight to the chat list on the next launch.
 */
@Composable
private fun NexusApp() {
    val context = LocalContext.current
    // The launch animation. It runs ALONGSIDE the session check below rather than
    // before it, so it never adds waiting — by the time the three marks have merged,
    // the app has usually already decided where to send the user.
    var splashDone by remember { mutableStateOf(false) }
    // null = still deciding, so we don't flash the login screen at a signed-in user.
    var signedIn by remember { mutableStateOf<Boolean?>(null) }
    // Set when the session was valid but the account no longer exists in the
    // database (deleted): the login screen shows this as the reason.
    var deletedNotice by remember { mutableStateOf<String?>(null) }
    // The backend is unreachable (outage / maintenance). We show a maintenance
    // notice instead of the login screen, because dumping a signed-in user onto
    // login during an outage looks like they were logged out.
    var serverDown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val remembered = SessionStore.isSignedIn(context)
        // Before anything else, is the server even up? If not, this is an outage,
        // not a sign-in problem — go straight to the maintenance screen. (Dev
        // builds with the backend disabled report reachable and skip this.)
        if (ApiConfig.ENABLED && !SyntraClient.serverReachable()) {
            serverDown = true
            return@LaunchedEffect
        }
        signedIn = when {
            !remembered -> false
            !ApiConfig.ENABLED -> true
            // The access token is gone after a restart; trade the refresh token
            // for a new one, otherwise every request would come back 401.
            else -> {
                val token = SessionStore.refreshToken(context)
                if (token.isNullOrBlank()) {
                    SessionStore.signOut(context)
                    false
                } else {
                    var restored = false
                    var rejected = false
                    // A flaky first second of connectivity must not log anyone out, so
                    // only an explicit rejection from the server clears the session.
                    // Two quick tries keep the splash short even when the backend is
                    // unreachable (each attempt is bounded by the 8s connect timeout).
                    repeat(2) { attempt ->
                        if (restored || rejected) return@repeat
                        runCatching { SyntraClient.restoreSession(token) }
                            .onSuccess { restored = true }
                            .onFailure { e ->
                                if ((e as? ApiException)?.code == "unauthorized") rejected = true
                                else if (attempt < 1) delay(400)
                            }
                    }
                    // Session refreshed — but a deleted user can still hold a valid
                    // Supabase token, so confirm the account really exists in the
                    // database. A 404 means it was deleted: sign out and say why.
                    if (restored) {
                        runCatching { SyntraClient.getMyProfile() }
                            .onFailure { e ->
                                if ((e as? ApiException)?.code == "not_found") {
                                    restored = false
                                    rejected = true
                                    deletedNotice =
                                        "Akun Anda telah dihapus. Silakan masuk atau daftar akun baru."
                                }
                                // Any other error (network hiccup) keeps the session.
                            }
                    }
                    when {
                        restored -> SessionStore.markSignedIn(
                            context,
                            SessionStore.signedInEmail(context).orEmpty(),
                            SyntraClient.currentRefreshToken,
                        )
                        rejected -> SessionStore.signOut(context)
                        else -> Unit // offline: keep the session, try again next launch
                    }
                    restored
                }
            }
        }
    }

    // Ask for notification permission (Android 13+) so background chat notifications
    // can actually show. Fire it once we're signed in.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — the service still runs; notifications just no-op if denied */ }

    // When signed in: start the background chat connection service (keeps the socket
    // alive for notifications, no Firebase), request notification permission, and
    // offer a battery-optimisation exemption so aggressive OEMs don't kill it.
    LaunchedEffect(signedIn) {
        if (signedIn == true && ApiConfig.ENABLED) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                AppLock.expectSystemDialog()
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            com.example.syntra.net.ChatConnectionService.start(context)
        }
    }

    when {
        // The brand animation owns the opening, and then HOLDS on its finished frame
        // until the app is actually ready. It sits ahead of every other branch so the
        // app opens the same way every time — a launch that sometimes animates and
        // sometimes doesn't reads as a glitch, not a flourish.
        //
        // Holding is the point: the session restore usually outlasts the animation, and
        // handing over to a SECOND loading screen meant the opening was two animations
        // back to back. One mark, assembled once, held until there is something real to
        // show. serverDown is excluded so a genuinely unreachable server still reaches
        // the maintenance screen instead of holding here forever.
        !splashDone || (signedIn == null && !serverDown) ->
            SyntraSplash(onDone = { splashDone = true })
        serverDown -> MaintenanceScreen(
            onAgree = { (context as? android.app.Activity)?.finishAndRemoveTask() },
        )
        signedIn == false -> AuthScreen(
            onAuthenticated = { signedIn = true; deletedNotice = null },
            notice = deletedNotice,
        )
        // Signed in, but the device-local app lock is on and this session hasn't been
        // unlocked yet → demand the PIN / fingerprint before the app is reachable.
        com.example.syntra.net.AppLockStore.isEnabled(context) && !AppLock.unlocked ->
            AppLockScreen(onUnlocked = {})
        else -> MainTabs(onSignOut = {
            // Sign out: stop the background service and close the socket.
            com.example.syntra.net.ChatConnectionService.stop(context)
            if (ApiConfig.ENABLED) SyntraClient.disconnect()
            // Every cached-for-speed store goes with the session. These hold one
            // account's messages, feed and profiles; leaving them behind would show
            // the NEXT person to sign in on this phone the previous user's chats.
            ShortsFeedCache.clear(context)
            com.example.syntra.net.MessageCache.clear(context)
            com.example.syntra.net.DiskJsonCache.clear(context)
            com.example.syntra.net.AvatarCache.clear(context)
            com.example.syntra.net.LikedMusicStore.clear(context)
            com.example.syntra.net.BlockStore.clear(context)
            // Both directions go with the session — the next account on this phone
            // must not inherit who blocked the previous one.
            com.example.syntra.net.BlockedByStore.clear(context)
            com.example.syntra.net.HiddenMessageStore.clear(context)
            com.example.syntra.net.NotInterestedStore.clear(context)
            com.example.syntra.net.LastChatStore.clear(context)
            signedIn = false
        })
    }
}

/**
 * Shown when the backend can't be reached at launch. Deliberately a dead end: the
 * app needs the server for everything, so instead of pretending to work offline we
 * ask the user to wait — and the only action, "Setuju", closes the app.
 */
@Composable
private fun MaintenanceScreen(onAgree: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onAgree)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.syntra.ui.theme.NexusBackground),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        com.example.syntra.ui.theme.NexusAccent.copy(alpha = 0.14f),
                        androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = com.example.syntra.ui.theme.NexusAccentSoft,
                    modifier = Modifier.size(44.dp),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Text(
                text = "Sedang Maintenance",
                color = com.example.syntra.ui.theme.NexusTextPrimary,
                fontSize = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            androidx.compose.material3.Text(
                text = "Aplikasi sedang dalam perbaikan. Mohon bersabar, kami akan segera kembali.",
                color = com.example.syntra.ui.theme.NexusTextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(
                                com.example.syntra.ui.theme.NexusAccentSoft,
                                com.example.syntra.ui.theme.NexusAccent,
                            ),
                        ),
                        androidx.compose.foundation.shape.RoundedCornerShape(50),
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = onAgree,
                    )
                    .padding(horizontal = 44.dp, vertical = 13.dp),
            ) {
                androidx.compose.material3.Text(
                    text = "Setuju",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Home tabs, in the same order as the bottom bar, so a swipe steps between them. */
private val tabOrder = listOf(NexusTab.CHAT, NexusTab.MUSIC, NexusTab.SHORTS, NexusTab.ROOMS, NexusTab.CALLS)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabs(onSignOut: () -> Unit) {
    val pager = rememberPagerState(pageCount = { tabOrder.size })
    val scope = rememberCoroutineScope()
    // A full-screen overlay (chat detail, story viewer, voice room…) locks the
    // pager so its own gestures don't accidentally flip to the next tab.
    var chatOverlay by remember { mutableStateOf(false) }
    var roomOverlay by remember { mutableStateOf(false) }
    var shortsOverlay by remember { mutableStateOf(false) }
    var musicOverlay by remember { mutableStateOf(false) }
    var callsOverlay by remember { mutableStateOf(false) }
    // True while a full-screen call is up (not minimized) — used to pause Shorts.
    val callBusy = CallController.isBusy

    fun goTo(tab: NexusTab) {
        scope.launch { pager.animateScrollToPage(tabOrder.indexOf(tab)) }
    }

    // Android back button: instead of quitting from any tab, retrace the tabs you
    // visited; once that history runs out, land on the home tab (Chat); only there
    // does back leave the app. Overlays (chat detail, reel viewer, room, profile…)
    // register their own BackHandler later in the tree, so those close first.
    val chatIndex = remember { tabOrder.indexOf(NexusTab.CHAT).coerceAtLeast(0) }
    val tabHistory = remember { mutableStateListOf<Int>() }
    var lastPage by remember { mutableIntStateOf(pager.currentPage) }
    var poppingBack by remember { mutableStateOf(false) }
    // Record tab changes (swipe or tap) into history — but a back-driven change is
    // the pop itself, so it must not be re-recorded. settledPage (not currentPage)
    // ignores the pages an animated jump sweeps across on its way to the target.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { cur ->
            if (cur != lastPage) {
                if (poppingBack) {
                    poppingBack = false
                } else {
                    tabHistory.add(lastPage)
                    if (tabHistory.size > 16) tabHistory.removeAt(0)
                }
                lastPage = cur
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = pager.currentPage != chatIndex) {
        val target = if (tabHistory.isNotEmpty()) tabHistory.removeAt(tabHistory.lastIndex) else chatIndex
        poppingBack = true
        scope.launch { pager.animateScrollToPage(target) }
    }

    // A tapped message notification asks to open a specific chat: jump to the CHAT
    // tab so ChatScreen (which watches the same request) can surface that chat.
    LaunchedEffect(ChatNavRequest.conversationId) {
        if (ChatNavRequest.conversationId != null) goTo(NexusTab.CHAT)
    }
    // A tapped activity notification asks to open a specific reel: jump to SHORTS,
    // which watches the same request and opens that reel full-screen.
    LaunchedEffect(ReelNavRequest.reelId) {
        if (ReelNavRequest.reelId != null) goTo(NexusTab.SHORTS)
    }

    // Pull the server's block list once per launch, so blocks made on another device
    // (or before this install) are enforced here from the first screen.
    val appContext = LocalContext.current
    LaunchedEffect(Unit) {
        if (!ApiConfig.ENABLED) return@LaunchedEffect
        runCatching { SyntraClient.getBlocked() }
            .onSuccess { com.example.syntra.net.BlockStore.sync(appContext, it) }
        // ...and who has blocked ME. Without this the blocked side starts every cold
        // start assuming nobody blocked it, and shows the blocker's real name, photo and
        // online dot until an event happens to arrive.
        runCatching { SyntraClient.getBlockedBy() }
            .onSuccess { com.example.syntra.net.BlockedByStore.sync(appContext, it) }
    }

    // Realtime: someone blocked or unblocked me. Handled at the app root so it lands
    // whatever screen is open — the store is Compose state, so every surface reading it
    // repaints on the spot, with no refresh and no reopening.
    DisposableEffect(Unit) {
        val blockListener = object : com.example.syntra.net.SocketListener {
            override fun onBlockedByUser(actorId: String, actorUsername: String, blocked: Boolean) {
                if (blocked) {
                    com.example.syntra.net.BlockedByStore.add(appContext, actorId, actorUsername)
                    // If their call or chat is on screen right now, drop it: staying in
                    // a live call with someone who just blocked you makes no sense.
                    if (CallController.isBusy) CallController.end()
                } else {
                    com.example.syntra.net.BlockedByStore.remove(appContext, actorId, actorUsername)
                }
            }
        }
        SyntraClient.addListener(blockListener)
        onDispose { SyntraClient.removeListener(blockListener) }
    }

    // Listen for incoming calls anywhere in the app and hand them to CallController,
    // which the app-root CallHost renders (as a full screen, then a floating window).
    DisposableEffect(Unit) {
        if (!ApiConfig.ENABLED) return@DisposableEffect onDispose {}
        val listener = object : com.example.syntra.net.SocketListener {
            override fun onCallIncoming(conversationId: String, callId: String, kind: String, initiatorId: String) {
                // Already in/among a call on this device — ignore (a re-sent event,
                // or a call in another chat while one is active).
                if (CallController.isBusy) return
                // Never ring myself for a call I placed (the event fans out to the
                // whole conversation, including the caller's own session).
                if (initiatorId.isNotBlank() && initiatorId == SyntraClient.myUserId) return
                scope.launch {
                    // Resolve the id + kind. The payload now carries them, so the
                    // common path needs NO extra request (the old GET .../call could
                    // race or fail and then the phone never rang). Fall back to the
                    // fetch only if an older backend sent a bare event.
                    var id = callId
                    var isVideo = kind == "video"
                    if (id.isBlank()) {
                        val active = SyntraClient.getActiveCall(conversationId) ?: return@launch
                        if (active.initiatorId == SyntraClient.myUserId) return@launch
                        if (active.status.isNotBlank() && active.status != "ringing") return@launch
                        id = active.id
                        isVideo = active.kind == "video"
                    }
                    if (id.isBlank() || CallController.isBusy) return@launch
                    // Name/avatar are a nicety — fetch best-effort, but ring even if
                    // this fails, so an unopened chat still shows an incoming call.
                    val conv = runCatching { SyntraClient.getConversations() }
                        .getOrNull()?.firstOrNull { it.id == conversationId }
                    CallController.incoming(
                        conversationId = conversationId,
                        peerName = conv?.title.orEmpty().ifBlank { "Panggilan masuk" },
                        peerId = conv?.counterpartId.orEmpty(),
                        video = isVideo,
                        callId = id,
                        // Inside a live room — host or listener alike — a call rings as
                        // a banner. Taking the screen would drop the user out of a
                        // conversation other people are part of, for something they
                        // have not accepted yet.
                        asBanner = com.example.syntra.net.AppForeground.inVoiceRoom,
                    )
                }
            }

            // call.ended teardown is owned by CallHost (shows "Panggilan berakhir"
            // briefly, then clears the call), so nothing to do here.
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    // One bottom bar below the pager — it never slides with the pages, only its
    // highlight follows. Hidden when a full-screen overlay is up (chat detail /
    // story viewer / voice room). It is STICKY: part of the layout, so content sits
    // above it and it never covers UI (FABs, action rails, last list item).
    val overlay = chatOverlay || roomOverlay || shortsOverlay || musicOverlay || callsOverlay
    LaunchedEffect(pager.currentPage) { BottomBarVisibility.visible = true }
    // Auto-hide is INSTANT (not an animated height): it relayouts only when the
    // scroll direction flips, not every frame — that per-frame relayout of five
    // kept-alive pages (incl. a video surface) was the jank.
    val barShown = !overlay && BottomBarVisibility.visible
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !overlay,
                // Only keep the immediate neighbour warm. Composing all five tabs up
                // front (a Shorts video surface, Rooms, Calls…) is what made entering
                // the home heavy — now only Chat builds on launch and the other tabs
                // compose when you actually swipe to them (lighter, WhatsApp-like). The
                // realtime socket lives in ChatConnectionService, and lists come from
                // cache, so returning to a tab is instant without keeping it composed.
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (tabOrder[page]) {
                    NexusTab.CHAT -> ChatScreen(
                        modifier = Modifier.fillMaxSize(),
                        onSignOut = onSignOut,
                        onOverlayChange = { chatOverlay = it },
                    )
                    NexusTab.MUSIC -> MusicScreen(
                        modifier = Modifier.fillMaxSize(),
                        visible = tabOrder[pager.currentPage] == NexusTab.MUSIC,
                        onOverlayChange = { musicOverlay = it },
                    )
                    NexusTab.SHORTS -> ShortsScreen(
                    modifier = Modifier.fillMaxSize(),
                    // Kept alive off-screen by the pager, so it must stop its video
                    // (and audio) whenever it isn't the tab actually being shown — and
                    // also when the whole app is backgrounded (isForegroundState).
                    visible = tabOrder[pager.currentPage] == NexusTab.SHORTS && !callBusy &&
                        com.example.syntra.net.AppForeground.isForegroundState,
                    onOverlayChange = { shortsOverlay = it },
                )
                    NexusTab.ROOMS -> RoomsScreen(
                        modifier = Modifier.fillMaxSize(),
                        onOverlayChange = { roomOverlay = it },
                        visible = tabOrder[pager.currentPage] == NexusTab.ROOMS,
                    )
                    NexusTab.CALLS -> CallsScreen(
                        modifier = Modifier.fillMaxSize(),
                        visible = tabOrder[pager.currentPage] == NexusTab.CALLS,
                        onOverlayChange = { callsOverlay = it },
                    )
                }
            }
            // Sticky bottom section: mini-player + nav bar, in the layout so content
            // above never gets covered. Shown/hidden instantly by the scroll watcher.
            if (barShown) {
                MusicMiniPlayer(onExpand = { MusicUi.showNowPlaying = true })
                NexusBottomBar(
                    selected = tabOrder[pager.currentPage],
                    onSelect = { goTo(it) },
                )
            }
        }

        // Full-screen now-playing, above the tabs (but below an active call).
        if (MusicUi.showNowPlaying) NowPlayingScreen(onClose = { MusicUi.showNowPlaying = false })

        // The call (incoming or outgoing) is rendered above everything by CallHost:
        // full-screen, or a draggable floating window when minimized.
        CallHost()
    }
}
