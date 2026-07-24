package com.example.syntra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paint the saved theme before the first frame so there is no flash.
        AppTheme.load(this)
        enableEdgeToEdge()
        setContent {
            SyntraTheme {
                NexusApp()
            }
        }
    }

    override fun onDestroy() {
        // Close the realtime socket with the app; ChatScreen reopens it on start.
        if (ApiConfig.ENABLED) SyntraClient.disconnect()
        super.onDestroy()
    }
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

    LaunchedEffect(Unit) {
        val remembered = SessionStore.isSignedIn(context)
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

    when (signedIn) {
        null -> AuthSplash()
        false -> AuthScreen(
            onAuthenticated = { signedIn = true; deletedNotice = null },
            notice = deletedNotice,
        )
        true -> MainTabs(onSignOut = { signedIn = false })
    }
}

/** Home tabs, in the same order as the bottom bar, so a swipe steps between them. */
private val tabOrder = listOf(NexusTab.CHAT, NexusTab.SHORTS, NexusTab.ROOMS, NexusTab.CALLS)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabs(onSignOut: () -> Unit) {
    val pager = rememberPagerState(pageCount = { tabOrder.size })
    val scope = rememberCoroutineScope()
    // A full-screen overlay (chat detail, story viewer, voice room…) locks the
    // pager so its own gestures don't accidentally flip to the next tab.
    var chatOverlay by remember { mutableStateOf(false) }
    var roomOverlay by remember { mutableStateOf(false) }
    // A call ringing on this device (I'm the receiver), null when idle.
    var incoming by remember { mutableStateOf<IncomingCall?>(null) }

    fun goTo(tab: NexusTab) {
        scope.launch { pager.animateScrollToPage(tabOrder.indexOf(tab)) }
    }

    // Listen for incoming calls anywhere in the app and raise the ringing screen.
    DisposableEffect(Unit) {
        if (!ApiConfig.ENABLED) return@DisposableEffect onDispose {}
        val listener = object : com.example.syntra.net.SocketListener {
            override fun onCallIncoming(conversationId: String) {
                scope.launch {
                    val call = SyntraClient.getActiveCall(conversationId) ?: return@launch
                    // I started this call — don't ring my own device.
                    if (call.initiatorId == SyntraClient.myUserId) return@launch
                    val conv = runCatching { SyntraClient.getConversations() }
                        .getOrNull()?.firstOrNull { it.id == conversationId }
                    incoming = IncomingCall(
                        conversationId = conversationId,
                        callId = call.id,
                        video = call.kind == "video",
                        peerName = conv?.title.orEmpty().ifBlank { "Panggilan masuk" },
                        peerId = conv?.counterpartId.orEmpty(),
                    )
                }
            }

            override fun onCallEnded(reason: String) {
                // Caller hung up before I answered — drop the ringing screen.
                incoming = null
            }
        }
        SyntraClient.addListener(listener)
        onDispose { SyntraClient.removeListener(listener) }
    }

    // One fixed bottom bar below the pager — it never slides with the pages, only
    // its highlight follows. It hides when a full-screen overlay is up so chat
    // detail / story viewer / voice room can cover the whole screen.
    val overlay = chatOverlay || roomOverlay
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
                    NexusTab.SHORTS -> ShortsScreen(
                    modifier = Modifier.fillMaxSize(),
                    // Kept alive off-screen by the pager, so it must stop its video
                    // (and audio) whenever it isn't the tab actually being shown.
                    visible = tabOrder[pager.currentPage] == NexusTab.SHORTS && incoming == null,
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
                NexusBottomBar(
                    selected = tabOrder[pager.currentPage],
                    onSelect = { goTo(it) },
                )
            }
        }

        // Incoming-call ringing screen, above everything.
        incoming?.let { call ->
            CallScreen(
                peerName = call.peerName,
                conversationId = call.conversationId,
                video = call.video,
                incoming = true,
                incomingCallId = call.callId,
                peerId = call.peerId,
                onClose = { incoming = null },
            )
        }
    }
}

/** A call ringing on this device that the user can answer or decline. */
private data class IncomingCall(
    val conversationId: String,
    val callId: String,
    val video: Boolean,
    val peerName: String,
    val peerId: String,
)
