package com.example.syntra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.syntra.net.ApiConfig
import com.example.syntra.net.ApiException
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paint the saved theme before the first frame so there is no flash.
        AppTheme.load(this)
        applySystemBars()
        handleNavIntent(intent)
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
    }

    override fun onStop() {
        super.onStop()
        // App went to the background → let the foreground service post notifications.
        com.example.syntra.net.AppForeground.isForeground = false
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
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            com.example.syntra.net.ChatConnectionService.start(context)
        }
    }

    when {
        serverDown -> MaintenanceScreen(
            onAgree = { (context as? android.app.Activity)?.finishAndRemoveTask() },
        )
        signedIn == null -> AuthSplash()
        signedIn == false -> AuthScreen(
            onAuthenticated = { signedIn = true; deletedNotice = null },
            notice = deletedNotice,
        )
        else -> MainTabs(onSignOut = {
            // Sign out: stop the background service and close the socket.
            com.example.syntra.net.ChatConnectionService.stop(context)
            if (ApiConfig.ENABLED) SyntraClient.disconnect()
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
    // True while a full-screen call is up (not minimized) — used to pause Shorts.
    val callBusy = CallController.isBusy

    fun goTo(tab: NexusTab) {
        scope.launch { pager.animateScrollToPage(tabOrder.indexOf(tab)) }
    }

    // A tapped message notification asks to open a specific chat: jump to the CHAT
    // tab so ChatScreen (which watches the same request) can surface that chat.
    LaunchedEffect(ChatNavRequest.conversationId) {
        if (ChatNavRequest.conversationId != null) goTo(NexusTab.CHAT)
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
                    )
                }
            }

            // call.ended teardown is owned by CallHost (shows "Panggilan berakhir"
            // briefly, then clears the call), so nothing to do here.
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    // One fixed bottom bar below the pager — it never slides with the pages, only
    // its highlight follows. It hides when a full-screen overlay is up so chat
    // detail / story viewer / voice room can cover the whole screen.
    val overlay = chatOverlay || roomOverlay || shortsOverlay || musicOverlay
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !overlay,
                // Keep all four tabs alive so swiping back doesn't reload/reconnect.
                beyondViewportPageCount = tabOrder.size,
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
                    // (and audio) whenever it isn't the tab actually being shown.
                    visible = tabOrder[pager.currentPage] == NexusTab.SHORTS && !callBusy,
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
                    )
                }
            }
            if (!overlay) {
                // Persistent music mini-player above the nav bar (renders only when a
                // track is loaded). Tapping it opens the full now-playing screen.
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
